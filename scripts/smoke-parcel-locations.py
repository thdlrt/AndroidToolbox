"""Native parcel UI acceptance on an explicitly selected emulator, using disposable fixtures."""
import argparse,pathlib,re,subprocess,time,xml.etree.ElementTree as ET
root=pathlib.Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--serial',required=True);args=parser.parse_args()
if not args.serial.startswith('emulator-'):raise SystemExit('Explicit emulator required')
adb=str(root/'.build/sdk/platform-tools/adb.exe');out=root/'outputs/parcel-location-validation';out.mkdir(parents=True,exist_ok=True)
def run(*a):return subprocess.check_output([adb,'-s',args.serial,*a],text=True,encoding='utf-8',errors='replace',timeout=20)
def nodes():
 run('shell','uiautomator','dump','/sdcard/parcel-ui.xml')
 return list(ET.fromstring(run('shell','cat','/sdcard/parcel-ui.xml')).iter('node'))
def wait(text):
 until=time.monotonic()+25
 while time.monotonic()<until:
  for n in nodes():
   if n.get('text')==text or n.get('content-desc')==text:return n
 raise AssertionError('Missing UI: '+text)
def tap(n):
 x,y,X,Y=map(int,re.findall(r'\d+',n.get('bounds')));run('shell','input','tap',str((x+X)//2),str((y+Y)//2))
def click(text):tap(wait(text));time.sleep(.2)
def shot(name):
 (out/(name+'.png')).write_bytes(subprocess.check_output([adb,'-s',args.serial,'exec-out','screencap','-p'],timeout=15))
proc=subprocess.Popen([adb,'-s',args.serial,'shell','am','instrument','-w','net.lanbridge.android.debug.test/net.lanbridge.android.ParcelLocationUiInstrumentation'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace')
try:
 wait('取件助手');wait('取件码：80000002');shot('groups')
 click('取件设置');click('整理地点');wait('南大仙林菜根谭（3 条）');shot('organize')
 click('预览整理');wait('请先勾选地点')
 click('南大仙林菜根谭（3 条）');click('南大菜根谭（1 条）');wait('将整理 4 条记录（含已归档）')
 click('预览整理');wait('确认统一地点');shot('preview');click('返回修改')
 wait('整理地点');click('预览整理');click('确认整理');shot('after-confirm');wait('已统一 4 条记录的地点，后续导入沿用此规则');shot('result')
 run('shell','run-as','net.lanbridge.android.debug','touch','cache/parcel-ui-done')
 output=proc.communicate(timeout=30)[0];(out/'ui-result.txt').write_text(output,encoding='utf-8');print(output)
 assert 'PASS:' in output,output
except Exception:
 try:diagnostic=proc.communicate(timeout=35)[0]
 except subprocess.TimeoutExpired:
  proc.terminate();diagnostic=proc.communicate(timeout=5)[0]
 (out/'ui-result.txt').write_text(diagnostic,encoding='utf-8');print(diagnostic)
 raise
finally:
 if proc.poll() is None:proc.terminate()
