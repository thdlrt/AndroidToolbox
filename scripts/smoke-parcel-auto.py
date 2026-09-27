"""Explicit emulator-only automatic SMS import acceptance with a local synthetic AI endpoint."""
import argparse,json,pathlib,subprocess,threading
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
root=pathlib.Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--serial',required=True);args=parser.parse_args()
if not args.serial.startswith('emulator-'):raise SystemExit('SDK emulator required')
adb=str(root/'.build/sdk/platform-tools/adb.exe')
def run(*a):return subprocess.run([adb,'-s',args.serial,*a],check=True,text=True,capture_output=True).stdout
seen=set();calls=[]
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def do_POST(self):
  data=json.loads(self.rfile.read(int(self.headers['Content-Length'])))
  sources=json.loads(data['messages'][1]['content'])['sources'];calls.append(len(sources))
  if len(calls)==1:self.send_response(503);self.end_headers();return
  items=[]
  for source in sources:
   assert source['id'] not in seen,'Already processed source was submitted again'
   seen.add(source['id']);fixture='AUTO_FIXTURE_064' in source['body']
   items.append(dict(source_ids=[source['id']],code='064-123' if fixture else '',tracking_number='',carrier='',location='Fixture station' if fixture else '',locker='',status='待取' if fixture else '非快递',deadline=None))
  body=json.dumps({'choices':[{'finish_reason':'stop','message':{'content':json.dumps({'items':items},ensure_ascii=False)}}]},ensure_ascii=False).encode()
  self.send_response(200);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
server=ThreadingHTTPServer(('0.0.0.0',0),Handler);threading.Thread(target=server.serve_forever,daemon=True).start()
try:
 run('shell','pm','grant','net.lanbridge.android.debug','android.permission.READ_SMS')
 run('emu','sms','send','1555000064','AUTO_FIXTURE_064 code 064-123 Fixture station')
 result=run('shell','am','instrument','-w','-e','endpoint',f'http://10.0.2.2:{server.server_port}/v1','net.lanbridge.android.debug.test/net.lanbridge.android.ParcelAutoInstrumentation')
 print(result)
 assert 'PASS:' in result,result
 assert len(calls)>=2 and seen,'No real fixture request'
 print(f'PASS local HTTP: {len(calls)} requests, {len(seen)} sources processed exactly once after 503 retry')
finally:server.shutdown();server.server_close()
