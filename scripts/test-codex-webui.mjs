/** Real bundled Codex engine + shared broker + WebUI. All homes are isolated. */
import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { createServer } from 'node:http';
import { createRequire } from 'node:module';
import { mkdir, writeFile, readFile } from 'node:fs/promises';
import path from 'node:path';
import readline from 'node:readline';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const webuiRoot = process.argv[2];
const codexPath = process.argv[3];
assert(webuiRoot && codexPath, 'Usage: node scripts/test-codex-webui.mjs WEBUI_ROOT CODEX_EXE [--serve]');
const WebSocket = createRequire(path.join(webuiRoot, 'package.json'))('ws');
const work = path.join(root, '.build', `webui-validation-${randomUUID().slice(0, 8)}`);
const home = path.join(work, 'codex');
const data = path.join(work, 'data');
const cwd = path.join(work, 'workspace');
await Promise.all([home, data, cwd, path.join(work, 'other-workspace')].map(p => mkdir(p, { recursive: true })));
const token = 'isolated-webui-integration-fixture';
const checks = [], requests = [], children = [], logs = [];
let stop;
const stopped = new Promise(resolve => stop = resolve);
let hold = false, releases = [];
const markdown = 'REAL_STREAM_OK\n\n## 安卓渲染\n\n- 第一项\n- 第二项\n\n| 列 | 值 |\n| --- | --- |\n| 测试 | 通过 |\n\n```js\nconsole.log("手机");\n```\n\n<script>window.fixtureXss=1</script>';
const model = createServer(async (req, res) => {
  if (req.method === 'GET') {
    if (req.url === '/stop-fixture') { res.end('stopping'); stop(); return; }
    if (req.url === '/release') { hold = false; releases.splice(0).forEach(f => f()); res.end('released'); return; }
    if (req.url === '/hold') { hold = true; res.end('holding'); return; }
    if (req.url === '/requests') { res.setHeader('Content-Type','application/json'); res.end(JSON.stringify(requests)); return; }
    res.writeHead(404).end(); return;
  }
  const chunks = []; for await (const chunk of req) chunks.push(chunk);
  const body = JSON.parse(Buffer.concat(chunks)); requests.push(body);
  const id = randomUUID(), mid = `msg_${id}`;
  res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache' });
  const event = value => { if (!res.destroyed) res.write(`event: ${value.type}\ndata: ${JSON.stringify(value)}\n\n`); };
  event({type:'response.created',response:{id,status:'in_progress'}});
  event({type:'response.output_item.added',output_index:0,item:{id:mid,type:'message',role:'assistant',status:'in_progress',content:[]}});
  event({type:'response.content_part.added',item_id:mid,output_index:0,content_index:0,part:{type:'output_text',text:'',annotations:[]}});
  event({type:'response.output_text.delta',item_id:mid,output_index:0,content_index:0,delta:'REAL_STREAM_'});
  if (hold) await Promise.race([new Promise(resolve => releases.push(resolve)), new Promise(resolve => res.once('close',resolve))]);
  if (res.destroyed) return;
  for (const delta of markdown.slice('REAL_STREAM_'.length).match(/.{1,12}|\n/gs)) {
    event({type:'response.output_text.delta',item_id:mid,output_index:0,content_index:0,delta});
    await new Promise(resolve => setTimeout(resolve, 25));
    if (res.destroyed) return;
  }
  const item = {id:mid,type:'message',role:'assistant',status:'completed',content:[{type:'output_text',text:markdown,annotations:[]}]};
  event({type:'response.output_text.done',item_id:mid,output_index:0,content_index:0,text:markdown});
  event({type:'response.output_item.done',output_index:0,item});
  event({type:'response.completed',response:{id,status:'completed',output:[item],usage:{input_tokens:24,output_tokens:40,total_tokens:64}}});
  res.end();
});
await new Promise(resolve => model.listen(0,'0.0.0.0',resolve));
const modelPort = model.address().port;
await writeFile(path.join(home,'config.toml'), `model = "gpt-5.4"\nmodel_provider = "fixture"\nmodel_reasoning_effort = "medium"\napproval_policy = "never"\nsandbox_mode = "danger-full-access"\ncheck_for_update_on_startup = false\n[model_providers.fixture]\nname = "Isolated fixture"\nbase_url = "http://127.0.0.1:${modelPort}/v1"\nwire_api = "responses"\nrequires_openai_auth = false\nsupports_websockets = false\nrequest_max_retries = 0\nstream_max_retries = 0\n[analytics]\nenabled = false\n[feedback]\nenabled = false\n`);
const listener = createServer(); await new Promise(resolve => listener.listen(0,'127.0.0.1',resolve));
const port = listener.address().port; await new Promise(resolve => listener.close(resolve));
const pipe = `\\\\.\\pipe\\webui-real-${randomUUID()}`;
const env = {...process.env, CODEX_HOME:home, CODEX_WEBUI_DATA_DIR:data, CODEX_WEBUI_TOKEN:token, CODEX_WEBUI_DESKTOP_LIFECYCLE:'0', CODEX_WEBUI_TERMINAL_QR:'0', CODEX_DESKTOP_BRIDGE_PIPE:pipe, CODEX_DESKTOP_BRIDGE_CODEX_PATH:codexPath};
for (const key of ['CODEX_THREAD_ID','CODEX_SESSION_ID','CODEX_APP_TOOLS_PIPE_PATH','CODEX_CLI_PATH','CODEX_DESKTOP_BRIDGE_CODEX_ARGS_JSON']) delete env[key];
function launch(script,args=[]) {
  const proc = spawn(process.execPath,[path.join(webuiRoot,script),...args],{cwd,env,stdio:['pipe','pipe','pipe'],windowsHide:true}); children.push(proc);
  if(script!=='server/desktop-broker.js')proc.stdout.resume();
  let errors='';proc.stderr.on('data',chunk=>errors+=chunk); logs.push(()=>errors); return proc;
}
function queue(input) {
  const values=[],waiters=[];
  const add=value=>{values.push(value); for(const wake of waiters.splice(0))wake();};
  input(add);
  return {values,async next(predicate,timeout=15000){const end=Date.now()+timeout;for(;;){const i=values.findIndex(predicate);if(i>=0)return values.splice(i,1)[0];if(Date.now()>=end)throw Error('Event timeout: '+predicate.toString());await new Promise(resolve=>{waiters.push(resolve);setTimeout(resolve,100).unref();});}}};
}
let desktopId=1;
let broker;
if(process.argv.includes('--launcher')){
  assert(path.resolve(data).startsWith(path.resolve(work)+path.sep),'Unsafe launcher fixture directory');
  const install=spawnSync('pwsh.exe',['-NoProfile','-File',path.join(webuiRoot,'scripts/install-desktop-bridge.ps1'),'-DataDir',data],{encoding:'utf8',windowsHide:true});
  assert.equal(install.status,0,install.stderr||install.stdout);
  broker=spawn(path.join(data,'desktop-bridge/runtime/windows-v3/codex-webui-bridge.exe'),['app-server','--analytics-default-enabled'],{cwd,env,stdio:['pipe','pipe','pipe'],windowsHide:true});
  children.push(broker);let errors='';broker.stderr.on('data',chunk=>errors+=chunk);logs.push(()=>errors);
}else broker=launch('server/desktop-broker.js',['app-server','--analytics-default-enabled']);
const desktop=queue(add=>readline.createInterface({input:broker.stdout}).on('line',line=>{try{add(JSON.parse(line));}catch{}}));
async function desktopRpc(method,params={}){const id=desktopId++;broker.stdin.write(JSON.stringify({id,method,params})+'\n');const value=await desktop.next(v=>v.id===id);if(value.error)throw Error(method+': '+JSON.stringify(value.error));return value.result;}
let web, web2;
function assertCheck(value,label){assert(value,label);checks.push(label);console.log('PASS: '+label);}
async function connectWeb(){const ws=new WebSocket(`ws://127.0.0.1:${port}/ws?token=${token}`);const messages=queue(add=>ws.on('message',raw=>add(JSON.parse(raw))));await new Promise((resolve,reject)=>{ws.once('open',resolve);ws.once('error',reject);});let nextId=1;return {ws,messages,async rpc(method,params={}){const requestId=nextId++;ws.send(JSON.stringify({type:'rpc',requestId,method,params}));const value=await messages.next(v=>v.requestId===requestId);if(value.error)throw Error(method+': '+JSON.stringify(value.error));return value.result;}};}
const input=text=>[{type:'text',text,text_elements:[]}];
const notification=(method,threadId,turnId)=>v=>v.type==='codex-notification'&&v.notification.method===method&&(!threadId||v.notification.params?.threadId===threadId)&&(!turnId||v.notification.params?.turn?.id===turnId||v.notification.params?.turnId===turnId);
try {
  await desktopRpc('initialize',{clientInfo:{name:'isolated_desktop',version:'1.0.0'},capabilities:{experimentalApi:true}});
  broker.stdin.write(JSON.stringify({method:'initialized'})+'\n');
  // This process's environment is private to the isolated fixture.
  env.PORT=String(port);env.HOST='0.0.0.0';
  launch('server/index.js');
  for(let n=0;n<80;n++){try{const r=await fetch(`http://127.0.0.1:${port}/api/info?token=${token}`);if(r.ok)break;}catch{}await new Promise(r=>setTimeout(r,100));}
  const unauth=await fetch(`http://127.0.0.1:${port}/api/workspaces`);
  assertCheck(unauth.status===401,'unauthenticated workspace access rejected');
  web=await connectWeb();web2=await connectWeb();
  const models=await web.rpc('model/list',{limit:50});assertCheck(models.data.length>0,'real engine model catalog');
  const created=await desktopRpc('thread/start',{cwd,model:'gpt-5.4',permissions:':danger-full-access',approvalPolicy:'never'});
  const threadId=created.thread.id;
  await desktopRpc('thread/name/set',{threadId,name:'真实共享引擎安卓验收'});
  const seed=await desktopRpc('turn/start',{threadId,input:input('DESKTOP_SEED_SAMPLE')});
  await desktop.next(v=>v.method==='turn/completed'&&v.params?.turn?.id===seed.turn.id,25000);
  const list=await web.rpc('thread/list',{limit:100});
  assertCheck(list.data.some(t=>t.id===threadId),'desktop-created thread visible on web');
  const resumed=await web.rpc('thread/resume',{threadId});assertCheck(resumed.thread.id===threadId,'same runtime thread attachment');
  await web.rpc('thread/settings/update',{threadId,model:'gpt-5.4',effort:'high'});
  const settings=await desktopRpc('thread/resume',{threadId});
  assertCheck(settings.model==='gpt-5.4'&&settings.reasoningEffort==='high','web model and effort authoritative on desktop');
  web.messages.values.length=0;
  hold=true;
  const turn=await desktopRpc('turn/start',{threadId,input:input('DESKTOP_STREAM_TEST')});
  const turnId=turn.turn.id;
  await web.messages.next(notification('item/agentMessage/delta',threadId,turnId));
  assertCheck(true,'desktop response streams to web before completion');
  const steer=await web.rpc('turn/steer',{threadId,expectedTurnId:turnId,input:input('PHONE_STEER_TEST'),clientUserMessageId:randomUUID()});
  assertCheck(!!steer,'real active turn steer accepted');
  await web.rpc('thread/queue/add',{threadId,input:input('PHONE_QUEUE_TEST'),clientUserMessageId:randomUUID()});
  const pending=await desktopRpc('thread/queue/list',{threadId});
  assertCheck(pending.data.length===1,'web queue visible through desktop runtime');
  hold=false;releases.splice(0).forEach(f=>f());
  await web.messages.next(notification('turn/completed',threadId,turnId),25000);
  const history=await desktopRpc('thread/turns/list',{threadId,itemsView:'full',limit:20});
  assertCheck(JSON.stringify(history).includes('PHONE_STEER_TEST'),'steering persisted in same thread');
  assertCheck(requests.some(r=>JSON.stringify(r).includes('PHONE_STEER_TEST')),'steering reaches model input');
  // This engine consumes its durable queue automatically at the next turn boundary.
  const queuedStart=await web.messages.next(v=>notification('turn/started',threadId)(v)&&v.notification.params.turn.id!==turnId,25000);
  await web.messages.next(notification('turn/completed',threadId,queuedStart.notification.params.turn.id),25000);
  assertCheck(requests.some(r=>JSON.stringify(r).includes('PHONE_QUEUE_TEST')),'queued input executes after current turn');
  const delta=await web2.messages.next(notification('item/agentMessage/delta',threadId));
  assertCheck(!!delta,'second web client receives shared stream');
  web2.ws.close();web2=await connectWeb();
  const afterReconnect=await web2.rpc('thread/turns/list',{threadId,itemsView:'full',limit:20});
  assertCheck(JSON.stringify(afterReconnect).includes('REAL_STREAM_OK'),'reconnect recovers authoritative history');
  hold=true;
  const active=await web.rpc('turn/start',{threadId,input:input('PHONE_INTERRUPT_TEST')});
  await web.messages.next(notification('item/agentMessage/delta',threadId,active.turn.id));
  await web.rpc('turn/interrupt',{threadId,turnId:active.turn.id});
  const terminal=await desktop.next(v=>v.method==='turn/completed'&&v.params?.turn?.id===active.turn.id);
  assertCheck(terminal.params.turn.status==='interrupted','web interrupt acknowledged on desktop');
  hold=false;releases.splice(0).forEach(f=>f());
  // Persist a finished sample and prepare controllable sessions for browser/WebView validation.
  const sample=await desktopRpc('turn/start',{threadId,input:input('ANDROID_RENDER_SAMPLE')});
  await web.messages.next(notification('turn/completed',threadId,sample.turn.id),25000);
  await desktopRpc('thread/settings/update',{threadId,model:'gpt-5.4',effort:'medium'});
  const info={port,token,threadId,modelPort,work,cwd,checks};
  await writeFile(path.join(root,'.build/webui-fixture.json'),JSON.stringify(info));
  await writeFile(path.join(work,'receipt.json'),JSON.stringify({checks,requestCount:requests.length},null,2));
  console.log(`Real engine fixture ready: ${checks.length} checks; port ${port}`);
  if(process.argv.includes('--serve'))await stopped;
} catch(error) {
  await writeFile(path.join(work,'errors.log'),logs.map(get=>get()).join('\n'));
  console.error(error.stack);console.error('Diagnostic files: '+work);process.exitCode=1;
} finally {
  web?.ws.terminate();web2?.ws.terminate();hold=false;releases.splice(0).forEach(f=>f());
  for(const child of children.reverse()){if(child.exitCode===null){child.kill();await Promise.race([new Promise(r=>child.once('exit',r)),new Promise(r=>setTimeout(r,2000))]);}}
  model.closeAllConnections();await new Promise(resolve=>model.close(resolve));
}
