"""Phone/folded-window regression checks on an explicitly chosen emulator.

Configure the loopback relay fixture first. No user phone or NAS is used.
"""
import argparse
import pathlib
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True)
parser.add_argument('--package', default='net.lanbridge.android.debug', choices=['net.lanbridge.android.debug','net.lanbridge.android'])
args = parser.parse_args()
if not args.serial.startswith('emulator-'): raise SystemExit('Select a test emulator')
root = pathlib.Path(__file__).resolve().parents[1]
adb = [str(root/'.build/sdk/platform-tools/adb.exe'), '-s', args.serial]
output = root/'outputs/ui-refinement'
output.mkdir(parents=True, exist_ok=True)

def run(*cmd): return subprocess.check_output(adb+list(cmd),timeout=30)
def screen():
    run('shell','uiautomator','dump','/sdcard/adaptive-ui.xml')
    return list(ET.fromstring(run('shell','cat','/sdcard/adaptive-ui.xml')).iter('node'))
def match(label): return [n for n in screen() if label in (n.get('text'),n.get('content-desc'))]
def click(label, long=False):
    nodes=match(label);assert nodes,label
    n=next((n for n in nodes if n.get('clickable')=='true'),nodes[0])
    x,y,X,Y=map(int,re.findall(r'\d+',n.get('bounds')));x=str((x+X)//2);y=str((y+Y)//2)
    run('shell','input',*(['swipe',x,y,x,y,'800'] if long else ['tap',x,y]))
def wait(label):
    end=time.monotonic()+15
    while time.monotonic()<end:
        if match(label):return
    raise AssertionError(label)
def capture(name): (output/(name+'.png')).write_bytes(run('exec-out','screencap','-p'))
def size(w,h,density):
    run('shell','wm','size',f'{w}x{h}');run('shell','wm','density',str(density))
    time.sleep(.5)

try:
    size(1080,1920,420)
    run('shell','am','force-stop',args.package)
    run('shell','am','start','-n',args.package+'/net.lanbridge.android.RelayActivity')
    wait('打开文件夹 folder');assert not match('首页')
    capture('phone-files')
    click('打开文件夹 folder');wait('打开文件 nested.txt')
    click('打开文件 nested.txt',long=True);wait('已选 1 项')
    size(1800,1800,360)
    wait('首页');wait('已选 1 项');wait('nested.txt')
    click('回家 VPN');wait('NAS 地址')
    click('文件中转站');wait('已选 1 项');wait('nested.txt')
    capture('fold-selection')
    click('取消选择');click('上一级');wait('打开文件夹 folder');capture('fold-files')
    click('更多操作');wait('按时间清理');wait('上传日志');capture('fold-more')
    click('中转目录');wait('远端中转路径')
    fields=[n for n in screen() if n.get('class')=='android.widget.EditText']
    assert len(fields)==1 and fields[0].get('text')=='inbox','Relay duplicated account fields or lost migrated path'
    capture('fold-path')
    click('设置');wait('WebDAV 连接');capture('fold-settings')
    click('回家 VPN');wait('NAS 地址');wait('飞牛管理员');capture('fold-vpn')
    size(1080,1920,420);wait('NAS 地址');assert not match('首页')
    click('返回工具箱');click('工具首页');wait('打开回家 VPN');capture('phone-home')
    print('PASS: phone/800dp square layout, live resize and tool switching preserve folder+selection, shared connection path-only UI, global navigation, VPN compact layout')
finally:
    run('shell','wm','size','reset');run('shell','wm','density','reset')
