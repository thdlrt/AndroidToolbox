// AndroidToolbox desktop companion. Uses the existing Codex Desktop process.
// No app-server, thread import, provider credentials, or general-purpose RPC proxy.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import readline from 'node:readline';
import {spawn} from 'node:child_process';
import {fileURLToPath, pathToFileURL} from 'node:url';

const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const active=new Set(['active','inProgress','running']);
const waiting=new Set(['needsInput','needsAttention','waiting','waitingForApproval']);
const unpack=r=>{
  if(r?.isError)throw new Error((r.content||[]).filter(x=>x.type==='text').map(x=>x.text).join(' ').slice(0,500));
  if(r?.structuredContent)return r.structuredContent;
  return JSON.parse(r?.content?.find(x=>x.type==='text')?.text||'{}');
};
function candidates(home){
  const base=path.join(home,'sessions'), files=[];
  function walk(dir,depth){
    if(depth>4)return;
    let entries=[];try{entries=fs.readdirSync(dir,{withFileTypes:true});}catch{return;}
    for(const e of entries){const p=path.join(dir,e.name);if(e.isDirectory())walk(p,depth+1);
      else if(e.name.endsWith('.jsonl')){const id=e.name.match(/([0-9a-f-]{36})\.jsonl$/i)?.[1];if(id&&uuid.test(id))files.push({id,time:fs.statSync(p).mtimeMs});}}
  }
  walk(base,0);return [...new Set(files.sort((a,b)=>b.time-a.time).slice(0,12).map(x=>x.id))];
}

export class DesktopClient {
  constructor({home=process.env.CODEX_HOME||path.join(os.homedir(),'.codex')}={}){
    this.home=home;this.pending=new Map();this.sequence=0;this.context='';this.connection=null;this.connecting=null;
  }
  async connect(){
    if(this.connection)return;
    if(this.connecting)return this.connecting;
    this.connecting=this.open().finally(()=>{this.connecting=null;});return this.connecting;
  }
  async open(){
    const root=path.join(this.home,'plugins','cache','openai-bundled','codex-app-tools');
    let versions=[];try{versions=fs.readdirSync(root).map(x=>path.join(root,x,'server.mjs')).filter(x=>fs.existsSync(x)).sort((a,b)=>fs.statSync(b).mtimeMs-fs.statSync(a).mtimeMs);}catch{}
    if(!versions.length)throw new Error('未找到 Codex Desktop app tools，请先安装并打开桌面 Codex。');
    let pipes=[];try{pipes=fs.readdirSync('\\\\.\\pipe\\').filter(x=>x.startsWith('codex-app-tools-')||x.startsWith('codex-browser-use-')).sort((a,b)=>Number(b.startsWith('codex-app-tools-'))-Number(a.startsWith('codex-app-tools-'))).map(x=>'\\\\.\\pipe\\'+x);}catch{}
    if(process.env.CODEX_APP_TOOLS_PIPE_PATH)pipes.unshift(process.env.CODEX_APP_TOOLS_PIPE_PATH);
    const ids=candidates(this.home);
    if(uuid.test(process.env.CODEX_THREAD_ID||''))ids.unshift(process.env.CODEX_THREAD_ID);
    if(!ids.length)throw new Error('请在电脑 Codex 中打开一个已有会话，再刷新。');
    for(const pipe of new Set(pipes)){
      const child=spawn(process.execPath,[versions[0]],{cwd:path.dirname(versions[0]),env:{...process.env,CODEX_APP_TOOLS_PIPE_PATH:pipe},windowsHide:true,stdio:['pipe','pipe','pipe']});
      this.connection=child;
      readline.createInterface({input:child.stdout}).on('line',line=>{try{const r=JSON.parse(line),p=this.pending.get(r.id);if(p){this.pending.delete(r.id);clearTimeout(p.timer);r.error?p.reject(new Error(r.error.message)):p.resolve(r.result);}}catch{}});
      child.stderr.resume();
      child.stdin.on('error',()=>this.disconnect(child));
      child.on('error',()=>this.disconnect(child));child.on('exit',()=>this.disconnect(child));
      try{
        await this.call('initialize',{protocolVersion:'2025-03-26',capabilities:{},clientInfo:{name:'androidtoolbox_desktop_remote',version:'0.9.2'}},8000);
        child.stdin.write(JSON.stringify({jsonrpc:'2.0',method:'notifications/initialized'})+'\n');
        const names=(await this.call('tools/list',{},8000)).tools.map(x=>x.name);
        if(!['list_threads','read_thread','send_message_to_thread'].every(x=>names.includes(x)))throw new Error('接口版本不支持');
        for(const id of [...new Set(ids)]){
          try{this.context=id;await this.toolRaw('list_threads',{limit:1},10000);return;}catch{}
        }
      }catch{}
      this.disconnect(child);child.kill();
    }
    throw new Error('无法连接电脑 Codex。请保持桌面应用打开，并打开至少一个已有会话后重试。');
  }
  disconnect(child){
    if(child!==this.connection)return;
    this.connection=null;for(const p of this.pending.values()){clearTimeout(p.timer);p.reject(new Error('电脑 Codex 连接已断开，请刷新重连。'));}this.pending.clear();
  }
  call(method,params,timeout=30000){
    return new Promise((resolve,reject)=>{
      const id=++this.sequence,timer=setTimeout(()=>{this.pending.delete(id);reject(new Error('电脑 Codex 响应超时；发送请求不会自动重试，请先检查会话。'));},timeout);
      this.pending.set(id,{resolve,reject,timer});
      this.connection.stdin.write(JSON.stringify({jsonrpc:'2.0',id,method,params})+'\n',error=>{if(error){clearTimeout(timer);this.pending.delete(id);reject(error);}});
    });
  }
  async toolRaw(name,args,timeout){return unpack(await this.call('tools/call',{name,arguments:args,_meta:{'openai/threadId':this.context}},timeout));}
  async tool(name,args){await this.connect();try{return await this.toolRaw(name,args);}catch(error){this.close();throw error;}}
  close(){const p=this.connection;if(p){this.disconnect(p);p.stdin.end();p.kill();}}
}

export function createBridge({token,client,clock=()=>Date.now()}){
  if(typeof token!=='string'||token.length<16)throw new Error('需要本机 HAPI 访问令牌');
  let cache=null,cacheAt=0,listing=null;
  const sends=new Map();
  const rows=async(force=false)=>{
    if(!force&&cache&&clock()-cacheAt<2000)return cache;
    if(listing)return listing;
    listing=(async()=>{
      const data=await client.tool('list_threads',{limit:50}),seen=new Set();
      cache=[...(data.pinnedThreads||[]),...(data.threads||[])].filter(t=>t.kind==='codex'&&uuid.test(t.id)&&!seen.has((t.hostId||'local')+':'+t.id)&&seen.add((t.hostId||'local')+':'+t.id)).map(t=>({id:t.id,hostId:t.hostId||'local',title:t.title||t.id,cwd:t.cwd||'',status:typeof t.status==='object'?t.status.type:t.status,updatedAt:t.updatedAt||0,pinned:!!t.pinnedIndex}));
      cache.sort((a,b)=>rank(a.status)-rank(b.status)||stamp(b.updatedAt)-stamp(a.updatedAt));cacheAt=clock();return cache;
    })().finally(()=>{listing=null;});return listing;
  };
  const rank=s=>waiting.has(s)?0:active.has(s)?1:2;
  const stamp=t=>t<1e12?t*1000:t;
  async function selected(body){
    if(!uuid.test(body.threadId||'')||typeof body.hostId!=='string'||body.hostId.length>200)throw Object.assign(new Error('请选择有效的电脑会话。'),{status:400});
    let found=(await rows()).find(t=>t.id===body.threadId&&t.hostId===body.hostId);
    if(!found)found=(await rows(true)).find(t=>t.id===body.threadId&&t.hostId===body.hostId);
    if(!found)throw Object.assign(new Error('会话已不在桌面最近列表中，请刷新后重新选择。'),{status:404});return found;
  }
  const server=http.createServer(async(req,res)=>{
    const headers={'Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer','X-Frame-Options':'DENY','Content-Security-Policy':"default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; connect-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'; form-action 'self'"};
    const reply=(status,data)=>{res.writeHead(status,{...headers,'Content-Type':'application/json; charset=utf-8'});res.end(JSON.stringify(data));};
    try{
      const url=new URL(req.url,'http://localhost');
      // Cross-origin writes/reads are never granted, even with a valid token.
      if(req.headers.origin&&new URL(req.headers.origin).host!==req.headers.host)return reply(403,{error:'不允许跨站请求'});
      if(req.headers['sec-fetch-site']==='cross-site')return reply(403,{error:'不允许跨站请求'});
      if(req.method==='GET'&&url.pathname==='/favicon.ico'){res.writeHead(204,headers);res.end();return;}
      if(req.method==='GET'&&url.pathname==='/'){
        res.writeHead(200,{...headers,'Content-Type':'text/html; charset=utf-8'});res.end(PAGE);return;
      }
      const supplied=Buffer.from((req.headers.authorization||'').replace(/^Bearer /,'')),expected=Buffer.from(token);
      if(supplied.length!==expected.length||!crypto.timingSafeEqual(supplied,expected))return reply(401,{error:'请填写访问令牌，或在安卓连接设置中重新保存。'});
      if(req.method==='GET'&&url.pathname==='/api/threads')return reply(200,{threads:await rows(),fetchedAt:clock(),limit:50});
      if(req.method!=='POST'||!['/api/read','/api/send'].includes(url.pathname))return reply(404,{error:'接口不存在'});
      if(!(req.headers['content-type']||'').startsWith('application/json'))return reply(415,{error:'需要 JSON 请求'});
      const body=await new Promise((resolve,reject)=>{let data='',size=0;req.setEncoding('utf8');req.on('data',chunk=>{size+=Buffer.byteLength(chunk);if(size>65536){reject(Object.assign(new Error('内容过长'),{status:413}));req.destroy();}else data+=chunk;});req.on('end',()=>{try{resolve(JSON.parse(data));}catch{reject(Object.assign(new Error('JSON 无效'),{status:400}));}});req.on('error',reject);});
      const thread=await selected(body),args={threadId:thread.id,hostId:thread.hostId};
      if(url.pathname==='/api/read'){
        if(body.cursor!==undefined&&(typeof body.cursor!=='string'||body.cursor.length>4096))return reply(400,{error:'历史游标无效'});
        const data=await client.tool('read_thread',{...args,turnLimit:body.cursor?5:3,includeOutputs:true,maxOutputCharsPerItem:1800,...(body.cursor?{cursor:body.cursor}:{})});
        // Send bounded, human-readable items; never raw context or hidden reasoning.
        const turns=(data.turns||[]).map(t=>({...t,items:(t.items||[]).filter(i=>['userMessage','agentMessage','commandExecution','mcpToolCall','dynamicToolCall','fileChange','webSearch','contextCompaction'].includes(i.type)).slice(-100)}));
        return reply(200,{thread:data.thread,page:data.page,turns,fetchedAt:clock()});
      }
      if(typeof body.prompt!=='string'||!body.prompt.trim()||body.prompt.length>20000||!uuid.test(body.requestId||''))return reply(400,{error:'请填写消息（最多 20000 字）。'});
      const key=thread.hostId+':'+thread.id+':'+body.requestId;
      // Reserve before dispatch: ambiguous timeouts must never start a second turn.
      const digest=crypto.createHash('sha256').update(body.prompt).digest('hex');
      if(sends.has(key)){
        const previous=sends.get(key);if(previous.digest!==digest)return reply(409,{error:'消息标识已用于其他内容'});
        return reply(previous.status,previous.result);
      }
      if(sends.size>2000)for(const [k,v] of sends)if(clock()-v.at>86400000)sends.delete(k);
      sends.set(key,{at:clock(),digest,status:409,result:{error:'该消息已提交或结果尚未确认，请先查看电脑会话，不要重复发送。'}});
      try{
        const result=await client.tool('send_message_to_thread',{...args,prompt:body.prompt});
        const response={accepted:true,result};sends.set(key,{at:clock(),digest,status:200,result:response});return reply(200,response);
      }catch(error){return reply(502,{error:'发送结果未确认：'+error.message+' 请检查会话后再决定是否重发。'});}
    }catch(error){reply(error.status||503,{error:String(error.message||error).slice(0,600)});}
  });
  server.on('close',()=>client.close?.());return server;
}

export const PAGE=String.raw`<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><title>Codex 电脑会话</title><style>
:root{font-family:system-ui,-apple-system,"Segoe UI",sans-serif;color:#20242c;background:#f7f8fa;color-scheme:light dark}*{box-sizing:border-box}body{margin:0}button,input,textarea{font:inherit}button{cursor:pointer;border:1px solid #dce1e9;background:#fff;color:inherit;border-radius:9px;padding:9px 12px}button:disabled{opacity:.5;cursor:default}button:focus-visible,input:focus,textarea:focus{outline:2px solid #427cf1;outline-offset:2px}input,textarea{border:1px solid #dce1e9;border-radius:9px;background:#fff;color:inherit;padding:10px;min-width:0}input{width:100%}#app{height:100dvh;display:grid;grid-template-columns:300px minmax(0,1fr)}aside{border-right:1px solid #e1e5eb;display:flex;flex-direction:column;min-height:0;background:#fff}header{padding:14px;display:flex;align-items:center;gap:8px;border-bottom:1px solid #e1e5eb;min-height:61px}header strong{flex:1;font-size:16px}#connection{font-size:12px;color:#687385}#filters{display:flex;gap:6px;padding:10px 12px}#filters button{padding:6px 9px;font-size:13px}button.selected{background:#eaf1ff;border-color:#b6cdfd;color:#2159b7}#search{padding:0 12px 10px}#list{overflow:auto;padding:0 8px 12px;flex:1}.row{display:block;width:100%;border:0;text-align:left;margin:3px 0;padding:11px 10px;background:transparent}.row.selected{background:#edf3ff}.row b{display:block;font-size:14px;font-weight:550;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.meta{color:#738096;font-size:12px;margin-top:5px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.badge{font-size:11px;border-radius:5px;padding:2px 5px;background:#edf0f4;color:#667180;margin-right:6px}.badge.live{background:#e6f7ed;color:#217245}.badge.wait{background:#fff1d3;color:#925c00}main{display:flex;flex-direction:column;min-height:0;min-width:0}#chatTitle{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}#chatMeta{font-size:12px;color:#738096;display:block;margin-top:3px;font-weight:400}#messages{flex:1;overflow:auto;padding:18px 16px;scroll-behavior:smooth}.empty{padding:24px 12px;color:#738096;text-align:center;font-size:14px}.message{max-width:920px;margin:0 auto 14px;overflow-wrap:anywhere}.message .role{font-size:12px;color:#738096;margin:0 0 6px}.bubble{white-space:pre-wrap;line-height:1.65;font-size:14px;background:#fff;border:1px solid #e5e8ee;padding:12px 14px;border-radius:12px}.user .bubble{background:#eaf1ff;border-color:#d8e5ff}.tools{font-size:12px;max-width:920px;margin:0 auto 10px;color:#5a6679}.tools summary{cursor:pointer;padding:8px 10px;border-radius:7px;background:#eef0f4}.tools pre{white-space:pre-wrap;overflow-wrap:anywhere;max-height:240px;overflow:auto;padding:10px;background:#fff;border-radius:7px}.notice{font-size:12px;color:#8b6117;max-width:920px;margin:10px auto}#error{background:#fff0eb;color:#9c3824;padding:10px 14px;font-size:13px;white-space:pre-wrap}#error:empty{display:none}#compose{padding:10px 14px max(12px,env(safe-area-inset-bottom));border-top:1px solid #e1e5eb;background:#fff}#compose form{display:flex;align-items:flex-end;gap:8px;max-width:920px;margin:auto}textarea{flex:1;resize:vertical;min-height:44px;max-height:160px}#send{background:#2864cf;border-color:#2864cf;color:white}#hint{max-width:920px;margin:6px auto 0;font-size:11px;color:#738096}#back{display:none}#login{max-width:380px;margin:15vh auto;padding:24px}#login h1{font-size:20px}#login button{width:100%;margin-top:12px}#loginError{color:#a33;font-size:13px}#older{display:block;margin:0 auto 18px}#follow{font-size:12px;white-space:nowrap}#foot{font-size:11px;color:#738096;padding:8px 12px;border-top:1px solid #edf0f4}
@media(max-width:700px){#app{display:block}aside,main{height:100dvh}main{display:none}body.chat aside{display:none}body.chat main{display:flex}#back{display:inline-block}#messages{padding:14px 10px}header{padding:10px 12px}.bubble{font-size:14px}#compose{padding-left:10px;padding-right:10px}#foot{font-size:10px}}
@media(prefers-color-scheme:dark){:root{background:#15171c;color:#e3e7ee}aside,#compose,button,input,textarea,.bubble,.tools pre{background:#1d2027;color:#e3e7ee}header,aside,#compose,.bubble,button,input,textarea{border-color:#333946}.row.selected,button.selected,.user .bubble{background:#243752;color:#d8e8ff}.tools summary{background:#292e39}.badge{background:#333946;color:#b6c2d4}.badge.live{background:#193f2c;color:#8ee3b0}.badge.wait{background:#493c1d;color:#f4cd7a}#error{background:#452b25;color:#ffc8b8}#foot{border-color:#333946}}
[hidden]{display:none!important}
</style></head><body><section id="login" hidden><h1>Codex 电脑会话</h1><form id="loginForm"><label for="secret">访问令牌</label><input id="secret" type="password" autocomplete="current-password" required><button>连接</button><p id="loginError"></p></form></section>
<div id="app" hidden><aside><header><strong>电脑会话</strong><span id="connection">连接中</span><button id="refresh" aria-label="刷新会话">↻</button></header><div id="filters"><button data-filter="all" class="selected">全部</button><button data-filter="active">运行中</button><button data-filter="waiting">待处理</button></div><div id="search"><input id="query" type="search" placeholder="筛选标题或目录（可选）" aria-label="筛选会话"></div><div id="list"></div><div id="foot">桌面最近 50 个会话及置顶 · 每 3 秒更新</div></aside>
<main><header><button id="back" aria-label="返回会话列表">‹</button><strong><span id="chatTitle">选择会话</span><span id="chatMeta"></span></strong><label id="follow"><input type="checkbox" id="autoFollow" checked style="width:auto"> 跟随</label></header><div id="messages"><div class="empty">直接选择正在运行的电脑会话</div></div><div id="error" role="alert"></div><div id="compose"><form id="messageForm"><textarea id="prompt" rows="2" maxlength="20000" placeholder="给这个电脑会话发消息" aria-label="消息" disabled></textarea><button id="send" disabled>发送</button></form><div id="hint">发送到同一个桌面会话；审批、停止和交互选项请在电脑端处理。</div></div></main></div>
<script>
(()=>{'use strict';const $=id=>document.getElementById(id),state={threads:[],selected:null,filter:'all',busy:false,sending:false,turns:[],page:null,fingerprint:'',generation:0,pending:null,sendError:''};const active=new Set(['active','inProgress','running']),waiting=new Set(['needsInput','needsAttention','waiting','waitingForApproval']);let token=localStorage.getItem('codex_desktop_token')||'',timer;
const label=s=>active.has(s)?'运行中':waiting.has(s)?'待处理':s==='idle'?'空闲':s==='notLoaded'?'未加载':s==='error'||s==='systemError'?'异常':s||'未知';
const escape=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const key=t=>t.hostId+':'+t.id;
async function api(route,body){const controller=new AbortController(),timeout=setTimeout(()=>controller.abort(),route==='/api/send'?35000:20000);try{const r=await fetch(route,{method:body?'POST':'GET',headers:{Authorization:'Bearer '+token,...(body?{'Content-Type':'application/json'}:{})},cache:'no-store',signal:controller.signal,...(body?{body:JSON.stringify(body)}:{})});const d=await r.json();if(r.status===401){showLogin();throw Error(d.error);}if(!r.ok)throw Error(d.error||'连接失败');return d;}catch(e){if(e.name==='AbortError')throw Error(route==='/api/send'?'发送超时，结果未确认；请先检查电脑会话。':'电脑连接超时，将自动重连。');throw e;}finally{clearTimeout(timeout);}}
function showLogin(){clearTimeout(timer);$('login').hidden=false;$('app').hidden=true;}
function showApp(){$('login').hidden=true;$('app').hidden=false;}
function error(e){$('error').textContent=e?e.message||String(e):state.sendError;}
function renderList(){const q=$('query').value.trim().toLowerCase(),visible=state.threads.filter(t=>(state.filter==='all'||(state.filter==='active'?active:waiting).has(t.status))&&(!q||(t.title+' '+t.cwd).toLowerCase().includes(q)));const html=visible.length?visible.map(t=>'<button class="row '+(state.selected&&key(t)===key(state.selected)?'selected':'')+'" data-key="'+escape(key(t))+'"><b>'+escape(t.title)+'</b><div class="meta"><span class="badge '+(active.has(t.status)?'live':waiting.has(t.status)?'wait':'')+'">'+label(t.status)+'</span>'+escape(t.cwd||t.hostId)+'</div></button>').join(''):'<div class="empty">'+(state.filter==='active'?'目前没有正在运行的会话':state.filter==='waiting'?'目前没有待处理会话':'没有匹配会话')+'</div>';if($('list').innerHTML!==html)$('list').innerHTML=html;}
function renderChat(){const t=state.selected;if(!t)return;$('chatTitle').textContent=t.title;$('chatMeta').textContent=label(t.status)+' · '+(t.cwd||t.hostId);$('prompt').disabled=false;$('send').disabled=state.sending;
 const pane=$('messages'),nearBottom=pane.scrollHeight-pane.scrollTop-pane.clientHeight<90,details=[...pane.querySelectorAll('details[open]')].map(x=>x.dataset.item);
 const html=state.turns.map(turn=>(turn.items||[]).map(item=>{const id=escape(item.id||''),text=item.text||(item.content||[]).filter(x=>x.type==='text').map(x=>x.text).join('\n');if(item.type==='agentMessage'||item.type==='userMessage'){if(!text)return '';return '<article class="message '+(item.type==='userMessage'?'user':'agent')+'"><div class="role">'+(item.type==='userMessage'?'你':item.phase==='commentary'?'Codex · 进展':'Codex')+'</div><div class="bubble">'+escape(text)+'</div></article>';}
 if(item.type==='contextCompaction')return '<div class="notice">上下文已整理</div>';
 const title=item.type==='commandExecution'?item.command:item.type==='fileChange'?'文件改动':item.tool||item.type;let detail=item.output?.text||item.result?.content?.filter(x=>x.type==='text').map(x=>x.text).join('\n')||JSON.stringify(item.changes||item.arguments||item.result||{},null,2);if(item.output?.truncated)detail+='\n…（输出已截断）';return '<details class="tools" data-item="'+id+'" '+(details.includes(item.id)?'open':'')+'><summary>'+escape(String(title||'工具调用').slice(0,160))+' · '+escape(item.status||'')+'</summary><pre>'+escape(detail)+'</pre></details>';}).join('')).join('');
 const mark=JSON.stringify([html,state.page?.nextCursor]);if(mark!==state.fingerprint){state.fingerprint=mark;const oldHeight=pane.scrollHeight,oldTop=pane.scrollTop;pane.innerHTML=(state.page?.hasMore?'<button id="older">加载更早记录</button>':'')+(html||'<div class="empty">暂无可显示的消息</div>');if($('autoFollow').checked&&nearBottom)pane.scrollTop=pane.scrollHeight;else pane.scrollTop=oldTop;}
}
async function read(older=false){if(!state.selected)return;const generation=state.generation,t=state.selected,d=await api('/api/read',{threadId:t.id,hostId:t.hostId,...(older&&state.page?.nextCursor?{cursor:state.page.nextCursor}:{})});if(generation!==state.generation)return;const incoming=(d.turns||[]).slice().reverse();if(older){const known=new Set(state.turns.map(x=>x.id));state.turns=[...incoming.filter(x=>!known.has(x.id)),...state.turns];state.page=d.page;}else {const ids=new Set(incoming.map(x=>x.id));state.turns=[...state.turns.filter(x=>!ids.has(x.id)),...incoming];if(!state.page)state.page=d.page;else if(state.turns.length<=3)state.page=d.page;}
 if(d.thread){state.selected={...state.selected,status:d.thread.status?.type||d.thread.status||state.selected.status};}renderChat();}
async function select(t){state.selected=t;state.generation++;state.turns=[];state.page=null;state.fingerprint='';state.pending=null;state.sendError='';$('prompt').value='';$('messages').innerHTML='<div class="empty">读取电脑会话…</div>';document.body.classList.add('chat');renderList();renderChat();try{await read();$('messages').scrollTop=$('messages').scrollHeight;error(null);}catch(e){error(e);}}
async function refresh(){if(state.busy||document.hidden)return;state.busy=true;try{const d=await api('/api/threads');state.threads=d.threads;showApp();$('connection').textContent='已连接';renderList();if(state.selected){const t=state.threads.find(t=>key(t)===key(state.selected));if(t)state.selected=t;await read();}error(null);}catch(e){$('connection').textContent='连接失败';error(e);$('loginError').textContent=e.message;}finally{state.busy=false;clearTimeout(timer);if(!$('app').hidden)timer=setTimeout(refresh,3000);}}
$('loginForm').onsubmit=e=>{e.preventDefault();token=$('secret').value.trim();localStorage.setItem('codex_desktop_token',token);refresh();};$('refresh').onclick=refresh;$('query').oninput=renderList;
$('filters').onclick=e=>{const f=e.target.dataset.filter;if(f){state.filter=f;for(const b of $('filters').children)b.classList.toggle('selected',b.dataset.filter===f);renderList();}};
$('list').onclick=e=>{const row=e.target.closest('[data-key]');if(row){const t=state.threads.find(t=>key(t)===row.dataset.key);if(t)select(t);}};
$('back').onclick=()=>document.body.classList.remove('chat');$('messages').onclick=async e=>{if(e.target.id==='older'){e.target.disabled=true;try{await read(true);}catch(x){error(x);e.target.disabled=false;}}};
$('messageForm').onsubmit=async e=>{e.preventDefault();if(!state.selected||state.sending)return;const prompt=$('prompt').value.trim();if(!prompt)return;const selected=state.selected,generation=state.generation;state.sending=true;state.sendError='';$('send').disabled=true;$('prompt').disabled=true;error(null);if(!state.pending||state.pending.prompt!==prompt||state.pending.threadId!==selected.id||state.pending.hostId!==selected.hostId)state.pending={threadId:selected.id,hostId:selected.hostId,prompt,requestId:(crypto.randomUUID?crypto.randomUUID():'10000000-1000-4000-8000-100000000000'.replace(/[018]/g,c=>(+c^crypto.getRandomValues(new Uint8Array(1))[0]&15>>+c/4).toString(16)))};
 try{await api('/api/send',state.pending);if(generation===state.generation){$('prompt').value='';state.pending=null;$('connection').textContent='已发送到电脑';await read();}}catch(x){if(generation===state.generation){state.sendError=x.message;error(x);}}finally{state.sending=false;if(state.selected){$('prompt').disabled=false;$('send').disabled=false;}}};
$('prompt').oninput=()=>{state.sendError='';error(null);};document.addEventListener('visibilitychange',()=>{if(!document.hidden)refresh();else clearTimeout(timer);});window.addEventListener('pagehide',()=>clearTimeout(timer));if(token)refresh();else showLogin();
})();
</script></body></html>`;

if(process.argv[1]&&import.meta.url===pathToFileURL(path.resolve(process.argv[1])).href){
  const root=path.dirname(fileURLToPath(import.meta.url)),settings=JSON.parse(fs.readFileSync(path.join(root,'remote.json'),'utf8'));
  const secret=JSON.parse(fs.readFileSync(path.join(root,'data','settings.json'),'utf8')).cliApiToken;
  const port=Number(settings.port)+1;
  if(!Number.isInteger(port)||port<1025||port>65535)throw new Error('原服务端口应为 1024–65534');
  const server=createBridge({token:secret,client:new DesktopClient()});
  server.listen(port,settings.listenHost,()=>console.log('Codex Desktop companion listening on port '+port));
  process.on('SIGTERM',()=>server.close());process.on('SIGINT',()=>server.close());
}
