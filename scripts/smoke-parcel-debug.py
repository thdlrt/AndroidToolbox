"""Test the actual manual scan buttons against SDK inbox fixtures; no physical devices."""
import argparse,json,pathlib,subprocess,threading,time
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
root=pathlib.Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--serial',required=True);parser.add_argument('--adb-port',default='5037');args=parser.parse_args()
if not args.serial.startswith('emulator-'):raise SystemExit('SDK emulator required')
adb=[str(root/'.build/sdk/platform-tools/adb.exe'),'-P',args.adb_port,'-s',args.serial]
out=root/'outputs/parcel-debug-validation';out.mkdir(parents=True,exist_ok=True)
def run(*a):return subprocess.check_output(adb+list(a),text=True,encoding='utf-8',errors='replace',timeout=90)
stamp=time.time_ns();marker='DBGTEST_'+format(stamp,'x');code=str(stamp)[-8:];calls=[]
body=f'【丰巢】凭取件码{code}至南大仙林菜根谭室内14号丰巢柜取件。快递员p.fcbox.com/{marker}'
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*a):pass
 def do_POST(self):
  request=json.loads(self.rfile.read(int(self.headers['Content-Length'])));sources=json.loads(request['messages'][1]['content'])['sources'];calls.append(sources)
  assert len(sources)==1 and sources[0]['body']==body
  row=dict(source_ids=[sources[0]['id']],code=code if len(calls)>1 else '',tracking_number='',carrier='',location='南大仙林菜根谭' if len(calls)>1 else '',locker='室内14号丰巢柜' if len(calls)>1 else '',status='待取' if len(calls)>1 else '非快递',deadline=None)
  response=json.dumps({'choices':[{'finish_reason':'stop','message':{'content':json.dumps({'items':[row]},ensure_ascii=False)}}]},ensure_ascii=False).encode();self.send_response(200);self.send_header('Content-Length',str(len(response)));self.end_headers();self.wfile.write(response)
server=ThreadingHTTPServer(('0.0.0.0',0),Handler);threading.Thread(target=server.serve_forever,daemon=True).start();heads=run('shell','settings','get','global','heads_up_notifications_enabled').strip()
try:
 run('shell','settings','put','global','heads_up_notifications_enabled','0');run('shell','pm','grant','net.lanbridge.android.debug','android.permission.READ_SMS')
 run('emu','sms','send','106310',body);run('emu','sms','send','106310',f'{marker} 验证码889900')
 result=run('shell','am','instrument','-w','-e','marker',marker,'-e','code',code,'-e','endpoint',f'http://10.0.2.2:{server.server_port}/v1','net.lanbridge.android.debug.test/net.lanbridge.android.ParcelDebugInstrumentation')
 print(result,flush=True);(out/'result.txt').write_text(result,'utf-8');assert 'PASS:' in result,result;assert len(calls)==1,len(calls)
 print('PASS HTTP: read-only diagnosis made no request; only the matching fcbox SMS uploaded; cancel made no request; explicit retry and strict fallback recovered OTHER classification; linked pending/archived sources made no request',flush=True)
 for name in ['diagnostic','logs','ai-result','input']:run('pull',f'/sdcard/Android/data/net.lanbridge.android.debug/files/parcel-processing-{name}.png',str(out/(name+'.png')))
finally:
 run('shell','settings','delete' if heads=='null' else 'put','global','heads_up_notifications_enabled',*(() if heads=='null' else (heads,)));server.shutdown();server.server_close()
