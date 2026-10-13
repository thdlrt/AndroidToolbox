"""One-tap clipboard import and home grid acceptance; synthetic data, explicitly selected emulator."""
import argparse,json,pathlib,subprocess,threading,time
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
root=pathlib.Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--serial',required=True);args=parser.parse_args()
if not args.serial.startswith('emulator-'):raise SystemExit('Explicit SDK emulator required')
adb=[str(root/'.build/sdk/platform-tools/adb.exe'),'-s',args.serial]
out=root/'outputs/clipboard-home-validation';out.mkdir(parents=True,exist_ok=True)
def run(*a):return subprocess.check_output(adb+list(a),text=True,encoding='utf-8',errors='replace',timeout=60)
calls=[];fixture_code='CLIP-606-'+format(time.time_ns(),'x');fixture_body=f'CLIPBOARD_FIXTURE 取件码 {fixture_code}，请到北门驿站A柜取件。'
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def do_POST(self):
  data=json.loads(self.rfile.read(int(self.headers['Content-Length'])))
  sources=json.loads(data['messages'][1]['content'])['sources'];calls.append(sources)
  time.sleep(2) # Keep the job active long enough to exercise the second-click busy guard.
  items=[dict(source_ids=[s['id']],code=fixture_code,tracking_number='',carrier='',location='北门驿站',locker='A柜',status='待取',deadline=None) for s in sources]
  body=json.dumps({'choices':[{'finish_reason':'stop','message':{'content':json.dumps({'items':items},ensure_ascii=False)}}]},ensure_ascii=False).encode()
  self.send_response(200);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
server=ThreadingHTTPServer(('0.0.0.0',0),Handler);threading.Thread(target=server.serve_forever,daemon=True).start()
font=run('shell','settings','get','system','font_scale').strip();results=[]
try:
 for mode,size,density,scale,columns,clipboard in [('phone-320','960x1800','480','1.0','2',True),('phone-411','1080x1920','420','1.0','2',False),('large-font','960x1800','480','1.3','2',False),('fold-800','1800x1800','360','1.0','3',False)]:
  run('shell','wm','size',size);run('shell','wm','density',density);run('shell','settings','put','system','font_scale',scale)
  result=run('shell','am','instrument','-w','-e','mode',mode,'-e','columns',columns,'-e','clipboard','true' if clipboard else 'false','-e','code',fixture_code,'-e','endpoint',f'http://10.0.2.2:{server.server_port}/v1','net.lanbridge.android.debug.test/net.lanbridge.android.ClipboardHomeInstrumentation')
  print(result,flush=True);results.append(result);assert 'PASS:' in result,result
  run('pull',f'/sdcard/Android/data/net.lanbridge.android.debug/files/clipboard-home-{mode}.png',str(out/(mode+'.png')))
  if clipboard:run('pull','/sdcard/Android/data/net.lanbridge.android.debug/files/clipboard-home-imported.png',str(out/'imported.png'))
 assert len(calls)==1 and len(calls[0])==1,calls
 assert calls[0][0]['body']==fixture_body
 print('PASS local HTTP: exactly one request, no invalid input uploads, no second-click duplicate; original clipboard text retained',flush=True)
finally:
 (out/'result.txt').write_text('\n'.join(results),encoding='utf-8')
 run('shell','wm','size','reset');run('shell','wm','density','reset')
 if font=='null':run('shell','settings','delete','system','font_scale')
 else:run('shell','settings','put','system','font_scale',font)
 server.shutdown();server.server_close()
