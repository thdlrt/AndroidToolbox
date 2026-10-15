"""Actual exported 5G text-share import on an explicitly selected SDK emulator."""
import argparse,json,pathlib,subprocess,threading,time
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
root=pathlib.Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--serial',required=True);parser.add_argument('--adb-port',default='5037');args=parser.parse_args()
if not args.serial.startswith('emulator-'):raise SystemExit('SDK emulator required')
adb=[str(root/'.build/sdk/platform-tools/adb.exe'),'-P',args.adb_port,'-s',args.serial]
def run(*a):return subprocess.check_output(adb+list(a),text=True,encoding='utf-8',errors='replace',timeout=90)
out=root/'outputs/parcel-share-validation';out.mkdir(parents=True,exist_ok=True)
code=str(time.time_ns())[-8:];body=f'【丰巢】凭取件码{code}至南大仙林菜根谭室内14号丰巢柜取件。快递员p.fcbox.com/ShareFixture';calls=[]
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*a):pass
 def do_POST(self):
  request=json.loads(self.rfile.read(int(self.headers['Content-Length'])));sources=json.loads(request['messages'][1]['content'])['sources'];calls.append(sources)
  assert len(sources)==1 and sources[0]['body']==body and sources[0]['sender']=='短信分享'
  row=dict(source_ids=[sources[0]['id']],code='',tracking_number='',carrier='',location='',locker='',status='非快递',deadline=None)
  response=json.dumps({'choices':[{'finish_reason':'stop','message':{'content':json.dumps({'items':[row]},ensure_ascii=False)}}]},ensure_ascii=False).encode();self.send_response(200);self.send_header('Content-Length',str(len(response)));self.end_headers();self.wfile.write(response)
server=ThreadingHTTPServer(('0.0.0.0',0),Handler);threading.Thread(target=server.serve_forever,daemon=True).start()
try:
 run('shell','pm','revoke','net.lanbridge.android.debug','android.permission.READ_SMS')
 result=run('shell','am','instrument','-w','-e','code',code,'-e','endpoint',f'http://10.0.2.2:{server.server_port}/v1','net.lanbridge.android.debug.test/net.lanbridge.android.ParcelShareInstrumentation')
 print(result,flush=True);(out/'result.txt').write_text(result,'utf-8');assert 'PASS:' in result,result;assert len(calls)==1,len(calls)
 run('pull','/sdcard/Android/data/net.lanbridge.android.debug/files/parcel-share.png',str(out/'imported.png'))
 print('PASS HTTP: one exact shared source; empty share and Activity recreation caused no AI call; mock OTHER recovered by strict fallback; READ_SMS denied throughout',flush=True)
finally:server.shutdown();server.server_close()
