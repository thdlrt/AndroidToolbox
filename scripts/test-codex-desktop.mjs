import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {Script} from 'node:vm';
import {createBridge,PAGE} from './codex-desktop.mjs';

const token='desktop-fixture-access-only',running='11111111-1111-4111-8111-111111111111',idle='22222222-2222-4222-8222-222222222222',waiting='33333333-3333-4333-8333-333333333333';
export class FixtureClient {
  constructor(){this.calls=[];this.sent=[];this.failSend=false;this.reads=0;}
  async tool(name,args){
    this.calls.push({name,args});
    if(name==='list_threads')return {pinnedThreads:[{id:idle,hostId:'local',kind:'codex',status:'idle',title:'已完成的会话',cwd:'E:/other'}],threads:[{id:running,hostId:'local',kind:'codex',status:'active',title:'正在处理的电脑任务',cwd:'E:/workspace'},{id:waiting,hostId:'local',kind:'codex',status:'needsInput',title:'等待电脑确认',cwd:'C:/work'},{id:idle,hostId:'local',kind:'codex',status:'idle'}, {id:'chatgpt-only',kind:'chatgpt',title:'excluded'}]};
    if(name==='read_thread'){
      this.reads++;
      return {thread:{id:args.threadId,status:{type:args.threadId===running?'active':'idle'}},page:{hasMore:!args.cursor,nextCursor:args.cursor?null:'fixture-older'},turns:[{id:args.cursor?'older':'latest',status:'inProgress',items:args.cursor?[{type:'agentMessage',id:'old-output',text:'更早的电脑消息'}]:[{type:'userMessage',id:'u1',content:[{type:'text',text:'请继续这条电脑会话'}]},{type:'agentMessage',id:'a1',phase:'commentary',text:'已读取实时输出 · '+this.reads},{type:'commandExecution',id:'cmd1',command:'git status --short',status:'completed',output:{text:'文件检查正常'}},{type:'reasoning',id:'secret-reasoning',text:'DO_NOT_EXPOSE'},{type:'agentMessage',id:'a2',text:this.sent.at(-1)?.prompt||'<img src=x onerror="alert(1)">'}]}]};
    }
    if(name==='send_message_to_thread'){this.sent.push(args);if(this.failSend)throw Error('fixture ambiguous timeout');return {threadId:args.threadId,sent:true};}
    throw Error('unexpected tool '+name);
  }
}
async function withServer(run){const client=new FixtureClient(),server=createBridge({token,client});await new Promise(r=>server.listen(0,'127.0.0.1',r));const base='http://127.0.0.1:'+server.address().port;
  const request=async(route,body,extra={})=>{const response=await fetch(base+route,{method:body?'POST':'GET',headers:{Authorization:'Bearer '+token,...(body?{'Content-Type':'application/json'}:{}),...extra},...(body?{body:JSON.stringify(body)}:{})});return {status:response.status,data:await response.json()};};
  try{await run({client,request,base});}finally{server.closeAllConnections();await new Promise(r=>server.close(r));}}
test('token required; no wildcard CORS or secrets in page',()=>withServer(async({base})=>{const r=await fetch(base+'/api/threads');assert.equal(r.status,401);assert.equal(r.headers.get('access-control-allow-origin'),null);assert.ok(!PAGE.includes(token));assert.ok(PAGE.includes('[hidden]{display:none!important}'));new Script(PAGE.match(/<script>([\s\S]*)<\/script>/)[1]);}));
test('live and waiting sessions precede idle; duplicates and ChatGPT excluded',()=>withServer(async({request})=>{const r=await request('/api/threads');assert.equal(r.status,200);assert.deepEqual(r.data.threads.map(x=>x.id),[waiting,running,idle]);}));
test('cross-origin POST is refused before desktop dispatch',()=>withServer(async({request,client})=>{const r=await request('/api/send',{threadId:running,hostId:'local',prompt:'test',requestId:idle},{Origin:'http://evil.example'});assert.equal(r.status,403);assert.equal(client.calls.length,0);}));
test('only allowlisted desktop threads and routes can be used',()=>withServer(async({request,client})=>{assert.equal((await request('/api/read',{threadId:'44444444-4444-4444-8444-444444444444',hostId:'local'})).status,404);assert.equal((await request('/api/tool',{name:'exec'})).status,404);assert.equal((await request('/api/read',{threadId:running,hostId:'remote-attacker'})).status,404);assert.ok(!client.calls.some(x=>x.name==='read_thread'));}));
test('read uses current Desktop client, bounded output, cursor; reasoning excluded',()=>withServer(async({request,client})=>{const r=await request('/api/read',{threadId:running,hostId:'local',cursor:'fixture-older'});assert.equal(r.status,200);assert.equal(client.calls.at(-1).args.cursor,'fixture-older');assert.equal(client.calls.at(-1).args.maxOutputCharsPerItem,1800);const current=await request('/api/read',{threadId:running,hostId:'local'});assert.ok(!JSON.stringify(current.data).includes('DO_NOT_EXPOSE'));}));
test('send targets exact native thread and is idempotent under concurrent retries',()=>withServer(async({request,client})=>{const body={threadId:running,hostId:'local',prompt:'中文续聊，无需导入',requestId:idle};const results=await Promise.all([request('/api/send',body),request('/api/send',body)]);assert.ok(results.every(x=>[200,409].includes(x.status)));assert.equal(client.sent.length,1);assert.deepEqual(client.sent[0],{threadId:running,hostId:'local',prompt:body.prompt});assert.equal((await request('/api/send',body)).status,200);assert.equal((await request('/api/send',{...body,prompt:'changed'})).status,409);}));
test('ambiguous send error is never automatically dispatched twice',()=>withServer(async({request,client})=>{client.failSend=true;const body={threadId:running,hostId:'local',prompt:'uncertain',requestId:idle};assert.equal((await request('/api/send',body)).status,502);assert.equal((await request('/api/send',body)).status,409);assert.equal(client.sent.length,1);}));
test('malformed request rejected without sending',()=>withServer(async({request,client})=>{assert.equal((await request('/api/send',{threadId:running,hostId:'local',prompt:'',requestId:idle})).status,400);assert.equal(client.sent.length,0);}));

if(process.argv.includes('--serve')){
  const client=new FixtureClient(),server=createBridge({token,client});
  server.listen(0,'0.0.0.0',()=>{
    const file=path.resolve('.build/desktop-fixture.json'),port=server.address().port;
    fs.writeFileSync(file,JSON.stringify({port,token,running,origin:'http://10.0.2.2:'+(port-1),pid:process.pid}));console.log('Desktop fixture ready on port '+port);
  });
}
