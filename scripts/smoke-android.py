"""Run against an explicitly selected test emulator, never an arbitrary phone."""
import argparse
import os
import pathlib
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT=pathlib.Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser()
parser.add_argument('--serial',required=True)
parser.add_argument('--check-update',action='store_true')
args=parser.parse_args()
if not args.serial.startswith('emulator-'): raise SystemExit('Use a test emulator')
adb=str(pathlib.Path(os.environ.get('ANDROID_HOME',ROOT/'.build/sdk'))/'platform-tools/adb.exe')

def run(*cmd):
    return subprocess.check_output([adb,'-s',args.serial,*cmd],text=True,encoding='utf-8',errors='replace')

def screen():
    run('shell','uiautomator','dump','/sdcard/toolbox-smoke.xml')
    return ET.fromstring(run('shell','cat','/sdcard/toolbox-smoke.xml'))

def wait(text,timeout=20):
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        for n in screen().iter('node'):
            if text in n.get('text',''): return n
    raise AssertionError('Missing UI text: '+text)

def click(text):
    wait(text)
    n=next(n for n in screen().iter('node') if n.get('text')==text and n.get('clickable')=='true')
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
    run('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
    time.sleep(.3)

run('shell','am','force-stop','net.lanbridge.android')
run('shell','am','start','-n','net.lanbridge.android/.MainActivity')
click('全部工具')
wait('显示在工具首页')
while True:
    pins=[n for n in screen().iter('node') if n.get('text')=='显示在工具首页' and n.get('checked')=='true']
    if not pins: break
    x1,y1,x2,y2=map(int,re.findall(r'\d+',pins[0].get('bounds')))
    run('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
click('工具首页');wait('暂无常用工具')
run('shell','am','force-stop','net.lanbridge.android')
run('shell','am','start','-n','net.lanbridge.android/.MainActivity')
wait('暂无常用工具')
click('选择常用工具');click('显示在工具首页');click('工具首页');click('打开')
wait('NAS 地址');wait('飞牛管理员');click('‹ 工具箱')
click('设置');wait('应用更新');wait('第三方许可')
if args.check_update:
    click('检查更新');wait('已是最新正式版',timeout=90)
print('PASS: catalog, favorite persistence, VPN page, back navigation, settings'+(', live release check' if args.check_update else ''))
