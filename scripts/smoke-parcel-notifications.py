"""System NotificationListener acceptance on an explicitly selected SDK emulator only."""
import argparse,json,pathlib,subprocess,threading,time
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
root=pathlib.Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--serial',required=True);parser.add_argument('--adb-port',default='5037');args=parser.parse_args()
if not args.serial.startswith('emulator-'):raise SystemExit('SDK emulator required')
adb=[str(root/'.build/sdk/platform-tools/adb.exe'),'-P',args.adb_port,'-s',args.serial]
def run(*a):return subprocess.check_output(adb+list(a),text=True,encoding='utf-8',errors='replace',timeout=100)
out=root/'outputs/parcel-notification-validation';out.mkdir(parents=True,exist_ok=True)
code=str(time.time_ns())[-8:];body=f'【丰巢】凭取件码{code}至南大仙林菜根谭室内14号丰巢柜取件。快递员p.fcbox.com/NotificationFixture';calls=[];probes=[]
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*a):pass
 def do_GET(self):
  probes.append(len(calls));self.send_response(200 if not calls else 409);self.end_headers()
 def do_POST(self):
  request=json.loads(self.rfile.read(int(self.headers['Content-Length'])));sources=json.loads(request['messages'][1]['content'])['sources'];calls.append(sources)
  assert len(sources)==1 and sources[0]['body']==body and sources[0]['id'].startswith('notification:')
  if len(calls)==1:self.send_response(503);self.end_headers();return
  row=dict(source_ids=[sources[0]['id']],code='',tracking_number='',carrier='',location='',locker='',status='非快递',deadline=None)
  response=json.dumps({'choices':[{'finish_reason':'stop','message':{'content':json.dumps({'items':[row]},ensure_ascii=False)}}]},ensure_ascii=False).encode();self.send_response(200);self.send_header('Content-Length',str(len(response)));self.end_headers();self.wfile.write(response)
server=ThreadingHTTPServer(('0.0.0.0',0),Handler);threading.Thread(target=server.serve_forever,daemon=True).start();component='net.lanbridge.android.debug/net.lanbridge.android.ParcelNotificationService'
listeners=run('shell','settings','get','secure','enabled_notification_listeners').strip();already=component in listeners
try:
 run('shell','pm','revoke','net.lanbridge.android.debug','android.permission.READ_SMS');run('shell','pm','grant','net.lanbridge.android.debug','android.permission.POST_NOTIFICATIONS')
 # Grant after instrumentation has started: instrumentation restarts its app process,
 # which would otherwise kill the newly bound listener and trigger system retry backoff.
 run('shell','cmd','notification','disallow_listener',component)
 process=subprocess.Popen(adb+['shell','am','instrument','-w','-e','code',code,'-e','endpoint',f'http://10.0.2.2:{server.server_port}/v1','net.lanbridge.android.debug.test/net.lanbridge.android.ParcelNotificationInstrumentation'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace')
 time.sleep(2);run('shell','cmd','notification','allow_listener',component);result=process.communicate(timeout=110)[0]
 print(result,flush=True);(out/'result.txt').write_text(result,'utf-8');assert 'PASS:' in result,result;assert len(calls)==2,len(calls);assert calls[0]==calls[1],calls;assert probes==[0],probes
 run('pull','/sdcard/Android/data/net.lanbridge.android.debug/files/parcel-notifications.png',str(out/'settings.png'))
 print('PASS HTTP: no AI while only system listener was active; one exact candidate retained after HTTP 503 and retried unchanged; notification updates and unselected/disabled/OTP sources caused no request',flush=True)
finally:
 run('shell','cmd','notification','allow_listener' if already else 'disallow_listener',component)
 server.shutdown();server.server_close()
