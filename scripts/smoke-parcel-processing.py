"""Explicit SDK emulator acceptance; synthetic inbox fixtures and a local HTTP model."""
import argparse,json,pathlib,re,subprocess,threading,time
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
root=pathlib.Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--serial',required=True);args=parser.parse_args()
if not args.serial.startswith('emulator-'):raise SystemExit('Explicit SDK emulator required')
adb=[str(root/'.build/sdk/platform-tools/adb.exe'),'-s',args.serial]
out=root/'outputs/parcel-processing-validation';out.mkdir(parents=True,exist_ok=True)
def run(*arguments):return subprocess.check_output(adb+list(arguments),text=True,encoding='utf-8',errors='replace',timeout=90)
fixture_stamp=time.time_ns();marker='FTRTEST_'+format(fixture_stamp,'x');calls=[]
station_code='68-4-'+str(fixture_stamp)[-9:];english_code='EN-'+format(fixture_stamp,'x');custom_code='CODE-'+format(fixture_stamp,'x')
fixtures=[f'{marker} 【驿小哥】菜鸟速递包裹已到某大学六栋菜根谭快递点，请23:30前凭{station_code}来取，取件通知。',f'{marker} You can PICK UP item {english_code} at North.',f'{marker} 自定义站点：凭{custom_code}。',f'{marker} 验证码889900，五分钟内有效。']
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*arguments):pass
 def do_POST(self):
  data=json.loads(self.rfile.read(int(self.headers['Content-Length'])))
  try:
   assert data['enable_thinking'] is True and data['thinking_budget']==16384
   assert 'reasoning_effort' not in data and 'extra_body' not in data
   sources=json.loads(data['messages'][1]['content'])['sources'];calls.append(sources)
   assert all(s['body'].startswith(marker) and '验证码' not in s['body'] for s in sources)
   items=[]
   for source in sources:
    body=source['body'];code=station_code if '驿小哥' in body else english_code if 'PICK UP' in body else custom_code
    items.append(dict(source_ids=[source['id']],code=code,tracking_number='',carrier='菜鸟速递' if '驿小哥' in body else '',location='合成验收站',locker='',status='待取',deadline='23:30前' if '驿小哥' in body else None))
   result={'choices':[{'finish_reason':'stop','message':{'reasoning_content':'Synthetic fixture reasoning','content':json.dumps({'items':items},ensure_ascii=False)}}]}
   response=json.dumps(result,ensure_ascii=False).encode();self.send_response(200)
  except Exception as error:response=type(error).__name__.encode();self.send_response(400)
  self.send_header('Content-Length',str(len(response)));self.end_headers();self.wfile.write(response)
server=ThreadingHTTPServer(('0.0.0.0',0),Handler);threading.Thread(target=server.serve_forever,daemon=True).start()
heads_up=run('shell','settings','get','global','heads_up_notifications_enabled').strip()
try:
 run('shell','settings','put','global','heads_up_notifications_enabled','0')
 run('shell','pm','grant','net.lanbridge.android.debug','android.permission.READ_SMS')
 for body in fixtures:run('emu','sms','send','106300',body)
 result=run('shell','am','instrument','-w','-e','marker',marker,'-e','endpoint',f'http://10.0.2.2:{server.server_port}/v1','net.lanbridge.android.debug.test/net.lanbridge.android.ParcelProcessingInstrumentation')
 print(result,flush=True);(out/'result.txt').write_text(result,encoding='utf-8');assert 'PASS:' in result,result
 assert len(calls)==2 and len(calls[0])==2 and len(calls[1])==1,calls
 print('PASS HTTP: only 3 matching SMS in 2 requests; no OTP upload; medium thinking sent for manual and automatic imports',flush=True)
 for name in ['custom-keywords','default-keywords','thinking-medium']:run('pull',f'/sdcard/Android/data/net.lanbridge.android.debug/files/parcel-processing-{name}.png',str(out/(name+'.png')))
finally:
 if heads_up=='null':run('shell','settings','delete','global','heads_up_notifications_enabled')
 else:run('shell','settings','put','global','heads_up_notifications_enabled',heads_up)
 server.shutdown();server.server_close()
