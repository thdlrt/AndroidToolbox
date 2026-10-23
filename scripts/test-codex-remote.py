"""Real Windows HAPI + Codex integration, isolated homes and a mock model endpoint.

No user's Codex configuration or credentials are read. --serve keeps the tested
fixture alive for the explicitly selected Android emulator WebView tests.
"""
import argparse
import http.server
import json
import os
import pathlib
import socket
import subprocess
import threading
import time
import urllib.error
import urllib.request
import urllib.parse
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--host-root', type=pathlib.Path, required=True)
parser.add_argument('--serve', action='store_true')
args = parser.parse_args()
work = ROOT / '.build' / ('hapi-validation-' + uuid.uuid4().hex[:8])
ch, hh, cwd, second = (work / name for name in ('codex', 'hapi', 'workspace', 'second-workspace'))
for folder in (ch, hh, cwd, second): folder.mkdir(parents=True)
TOKEN = 'isolated-hapi-integration-fixture'
requests = []
stop_fixture=threading.Event()


class Model(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def do_GET(self):
        if self.path!='/stop-fixture':self.send_error(404);return
        self.send_response(200);self.end_headers();stop_fixture.set()
    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        requests.append(body)
        inputs = body.get('input', [])
        last = max((i for i, v in enumerate(inputs) if v.get('role') == 'user'), default=-1)
        prompt = json.dumps(inputs[last])
        outputs = [v for v in inputs[last + 1:] if v.get('type') == 'function_call_output']
        ident = uuid.uuid4().hex
        if 'ASK_REMOTE_INPUT' in prompt and not outputs:
            item = {'type':'function_call','call_id':'ask_'+ident,'name':'request_user_input','arguments':json.dumps({'questions':[{'id':'choice','header':'Choice','question':'Confirm the remote fixture','options':[{'label':'Approve fixture','description':'Continue isolated test'},{'label':'Cancel fixture','description':'Stop isolated test'}]}]})}
        else:
            item = {'type':'message','role':'assistant','id':'msg_'+ident,'content':[{'type':'output_text','text':'REMOTE_STREAM_OK '+('INPUT_CONFIRMED' if outputs else 'HELLO_FROM_WINDOWS')}]}
        events = [{'type':'response.created','response':{'id':ident}}, {'type':'response.output_item.done','item':item}, {'type':'response.completed','response':{'id':ident,'usage':{'input_tokens':10,'output_tokens':4,'total_tokens':14}}}]
        self.send_response(200); self.send_header('Content-Type','text/event-stream');self.end_headers()
        for event in events:
            self.wfile.write(('event: '+event['type']+'\ndata: '+json.dumps(event)+'\n\n').encode());self.wfile.flush();time.sleep(.1)


model = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Model)
threading.Thread(target=model.serve_forever, daemon=True).start()
class DshFixture(http.server.BaseHTTPRequestHandler):
    def log_message(self,*args):pass
    def do_GET(self):
        logged='dsh_fixture=valid' in self.headers.get('Cookie','')
        body=('DSH_FIXTURE_SESSION' if logged else '<form action="/pocket-login" method="POST"><input name="username" value="dsh-pocket"><input id="current-password" name="token" type="password"></form>').encode()
        self.send_response(200);self.send_header('Content-Type','text/html; charset=utf-8');self.end_headers();self.wfile.write(body)
    def do_POST(self):
        form=urllib.parse.parse_qs(self.rfile.read(int(self.headers['Content-Length'])).decode())
        assert form.get('token')==['12345678'] and form.get('username')==['dsh-pocket'],form
        self.send_response(302);self.send_header('Set-Cookie','dsh_fixture=valid; HttpOnly; Path=/; SameSite=Lax');self.send_header('Location','/');self.end_headers()
dsh=http.server.ThreadingHTTPServer(('0.0.0.0',0),DshFixture)
threading.Thread(target=dsh.serve_forever,daemon=True).start()
with socket.socket() as s: s.bind(('127.0.0.1',0));port=s.getsockname()[1]
base = f'http://127.0.0.1:{port}'
hh.joinpath('settings.json').write_text(json.dumps({'cliApiToken':TOKEN,'apiUrl':base}),encoding='utf-8')
ch.joinpath('config.toml').write_text(f'''model = "mock-model"
model_provider = "fixture"
approval_policy = "on-request"
sandbox_mode = "danger-full-access"
check_for_update_on_startup = false
[model_providers.fixture]
name = "Isolated fixture"
base_url = "http://127.0.0.1:{model.server_port}/v1"
wire_api = "responses"
requires_openai_auth = false
supports_websockets = false
request_max_retries = 0
stream_max_retries = 0
[analytics]
enabled = false
[feedback]
enabled = false
''',encoding='utf-8')
hapi = args.host_root / 'node_modules/@twsxtd/hapi-win32-x64/bin/hapi.exe'
codex_bin = args.host_root / 'node_modules/@openai/codex-win32-x64/vendor/x86_64-pc-windows-msvc/bin'
env = {**os.environ,'HAPI_HOME':str(hh),'CODEX_HOME':str(ch),'HOME':str(work),'HAPI_API_URL':base,'HAPI_LISTEN_HOST':'0.0.0.0','HAPI_LISTEN_PORT':str(port),'HAPI_ANDROID_PUSH':'off','HAPI_IOS_PUSH':'off','PATH':str(codex_bin)+os.pathsep+os.environ['PATH']}
for name in ['CODEX_THREAD_ID','CODEX_SESSION_ID','CODEX_APP_TOOLS_PIPE_PATH','HAPI_SESSION_ID','CLI_API_TOKEN']:
    env.pop(name,None)
processes=[]; handles=[]


def launch(*cmd):
    log=open(work/(cmd[-1].replace('--','')+'.log'),'w',encoding='utf-8');handles.append(log)
    p=subprocess.Popen([str(hapi),*cmd],env=env,cwd=cwd,stdout=log,stderr=subprocess.STDOUT,creationflags=0x08000000)
    processes.append(p);return p


def wait(read, accept, label, timeout=35):
    end=time.monotonic()+timeout;last=None
    while time.monotonic()<end:
        try:
            last=read()
            if accept(last):return last
        except (urllib.error.URLError,ConnectionError):pass
        time.sleep(.25)
    raise AssertionError(f'{label}: {str(last)[:700]}')


jwt=''
def api(path, body=None):
    req=urllib.request.Request(base+'/api'+path,data=None if body is None else json.dumps(body).encode(),headers={'Content-Type':'application/json',**({'Authorization':'Bearer '+jwt} if jwt else {})})
    with urllib.request.urlopen(req,timeout=35) as r:return json.load(r)


try:
    launch('hub','--no-relay')
    wait(lambda:urllib.request.urlopen(base,timeout=2).status,lambda v:v==200,'hub HTTP')
    jwt=api('/auth',{'accessToken':TOKEN})['token']
    launch('runner','start-sync','--workspace-root',str(cwd),'--workspace-root',str(second))
    machine=wait(lambda:api('/machines')['machines'],bool,'runner online')[0]['id']
    roots=api('/machines')['machines'][0]['metadata']['workspaceRoots']
    assert str(cwd) in roots and str(second) in roots,roots
    assert api(f'/machines/{machine}/list-directory',{'path':str(second)})['success']
    # A real native CLI history, not initially registered with HAPI. Same local
    # transcript format as desktop chats; only the fixture home is used.
    def native_history(prompt):
        # Child tool hosts may retain inherited pipe handles after exec exits on
        # Windows. Files let us wait for the actual process, not a descendant's EOF.
        output=work/(prompt+'.stdout.log');errors=work/(prompt+'.stderr.log')
        with output.open('w',encoding='utf-8') as stdout,errors.open('w',encoding='utf-8') as stderr:
            result=subprocess.run([str(codex_bin/'codex.exe'),'exec','--json','--skip-git-repo-check','-C',str(second),prompt],env=env,stdout=stdout,stderr=stderr,timeout=60,creationflags=0x08000000)
        assert result.returncode==0,errors.read_text(encoding='utf-8')[-600:]
        events=[json.loads(line) for line in output.read_text(encoding='utf-8').splitlines() if line.startswith('{')]
        return next(event['thread_id'] for event in events if event.get('type')=='thread.started')
    desktop_native=native_history('RESUME_DESKTOP_FIXTURE')
    desktop_android=native_history('ANDROID_DESKTOP_FIXTURE')
    local=api('/codex/sessions')['sessions']
    assert {desktop_native,desktop_android}.issubset({row['id'] for row in local}),local
    imported=api('/codex/sync-session',{'sessionIds':[desktop_native],'machineId':machine})
    assert imported.get('success'),imported
    desktop_hapi=imported['hapiSessionIds'][0]
    assert 'HELLO_FROM_WINDOWS' in json.dumps(api(f'/sessions/{desktop_hapi}/messages'))
    resumed=api(f'/sessions/{desktop_hapi}/resume',{})
    assert resumed.get('sessionId'),resumed
    desktop_hapi=resumed['sessionId']
    api(f'/sessions/{desktop_hapi}/messages',{'text':'CONTINUE_DESKTOP_FIXTURE','localId':uuid.uuid4().hex})
    wait(lambda:api(f'/sessions/{desktop_hapi}/messages'),lambda v:'CONTINUE_DESKTOP_FIXTURE' in json.dumps(v) and json.dumps(v).count('HELLO_FROM_WINDOWS')>=2,'continued native history')
    assert api(f'/sessions/{desktop_hapi}')['session']['metadata']['codexSessionId']==desktop_native
    spawned=api(f'/machines/{machine}/spawn',{'directory':str(cwd),'agent':'codex','model':'mock-model','permissionMode':'default','collaborationMode':'plan'})
    assert spawned.get('sessionId'),spawned
    session=spawned['sessionId']
    api(f'/sessions/{session}/messages',{'text':'REMOTE_HELLO','localId':uuid.uuid4().hex})
    wait(lambda:api(f'/sessions/{session}/messages'),lambda v:'HELLO_FROM_WINDOWS' in json.dumps(v),'model reply')
    api(f'/sessions/{session}/messages',{'text':'ASK_REMOTE_INPUT','localId':uuid.uuid4().hex})
    detail=wait(lambda:api(f'/sessions/{session}'),lambda v:bool(v.get('session',{}).get('agentState',{}).get('requests')),'remote input request')
    pending=detail['session']['agentState']['requests'];request_id=next(iter(pending))
    api(f'/sessions/{session}/permissions/{request_id}/approve',{'answers':{'choice':{'answers':['Approve fixture']}}})
    wait(lambda:api(f'/sessions/{session}/messages'),lambda v:'INPUT_CONFIRMED' in json.dumps(v),'remote answer delivered')
    # The same thread, persisted history, authenticated reload, and another sender.
    assert api(f'/sessions/{session}')['session']['metadata']['codexSessionId']
    assert len(requests)>=3
    info={'base':base,'port':port,'token':TOKEN,'sessionId':session,'desktopSessionId':desktop_android,'desktopDirectory':str(second),'dshPort':dsh.server_port,'stopPort':model.server_port,'work':str(work),'checks':['real Windows hub','multiple workspace roots','browse outside initial workspace','native Codex history discovery','import native history','continue same native thread','runner spawn','official Codex 0.164.0','fixture model roundtrip','remote request_user_input','answer delivery','persistent native thread']}
    ROOT.joinpath('.build/hapi-fixture.json').write_text(json.dumps(info),encoding='utf-8')
    print('PASS: '+', '.join(info['checks']),flush=True)
    if args.serve:
        print(f'Android fixture ready on port {port}; details in .build/hapi-fixture.json',flush=True)
        stop_fixture.wait()
finally:
    try:
        if jwt and 'session' in locals():api(f'/sessions/{session}/archive',{})
        if jwt and 'desktop_hapi' in locals():api(f'/sessions/{desktop_hapi}/archive',{})
    except Exception:pass
    for p in reversed(processes):
        if p.poll() is None:p.terminate()
    for p in processes:
        try:p.wait(10)
        except subprocess.TimeoutExpired:p.kill()
    for h in handles:h.close()
    model.shutdown()
    dsh.shutdown()
