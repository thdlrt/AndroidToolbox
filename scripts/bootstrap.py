"""Prepare the pinned Android toolchain under this E: project, without C: caches."""
from pathlib import Path
import hashlib
import shutil
import subprocess
import tarfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / '.build'


def extract_native(path, destination):
    """Materialize header links; Windows runners need not grant symlink rights."""
    destination = destination.resolve()
    with tarfile.open(path) as archive:
        members = archive.getmembers()
        archive.extractall(destination, members=[m for m in members if not (m.issym() or m.islnk())], filter='data')
        for member in members:
            if not (member.issym() or member.islnk()):
                continue
            target = destination / member.name
            if not target.resolve().is_relative_to(destination):
                raise RuntimeError('Archive link escapes destination')
            # extractfile resolves the archived link inside the archive itself.
            with archive.extractfile(member) as source:
                target.parent.mkdir(parents=True, exist_ok=True)
                if target.is_symlink():
                    target.unlink()
                with target.open('wb') as output:
                    shutil.copyfileobj(source, output)


def download(name, url, digest, algorithm='sha256'):
    path = CACHE / name
    def valid():
        if not path.is_file(): return False
        h = hashlib.new(algorithm)
        with path.open('rb') as stream:
            for block in iter(lambda: stream.read(1024*1024), b''): h.update(block)
        return h.hexdigest() == digest
    if not valid():
        subprocess.run(['curl.exe','-4','-fSL','--retry','2','--connect-timeout','15','--max-time','600','-o',str(path),url],check=True)
    if not valid(): raise RuntimeError('Checksum mismatch: '+name)
    return path


def main():
    if not ROOT.drive: raise RuntimeError('This bootstrap targets Windows; see README for build requirements')
    CACHE.mkdir(exist_ok=True)
    sdk=CACHE/'sdk'
    packages=[
        ('platform-36.zip','platform-36_r02.zip','2c1a80dd4d9f7d0e6dd336ec603d9b5c55a6f576','android-36',sdk/'platforms/android-36'),
        ('build-tools-36.zip','build-tools_r36_windows.zip','f16ccffd34de8790dede813a6c7d8e2c11a27b50','android-16',sdk/'build-tools/36.0.0'),
    ]
    for name,url,digest,top,target in packages:
        if target.is_dir(): continue
        path=download(name,'https://dl.google.com/android/repository/'+url,digest,'sha1')
        staging=CACHE/'sdk-extract'
        with zipfile.ZipFile(path) as archive: archive.extractall(staging)
        shutil.copytree(staging/top,target)
    if not (CACHE/'android-ndk-r27c/ndk-build.cmd').is_file():
        path=download('ndk-r27c.zip','https://dl.google.com/android/repository/android-ndk-r27c-windows.zip','ac5f7762764b1f15341094e148ad4f847d050c38','sha1')
        with zipfile.ZipFile(path) as archive: archive.extractall(CACHE)
    if not (CACHE/'gradle-9.5.0/bin/gradle.bat').is_file():
        path=download('gradle-9.5.0-bin.zip','https://services.gradle.org/distributions/gradle-9.5.0-bin.zip','553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746')
        with zipfile.ZipFile(path) as archive: archive.extractall(CACHE)
    if not (CACHE/'native-source/hev-socks5-tunnel-2.17.1/Android.mk').is_file():
        path=download('hev-source.tar.xz','https://github.com/heiher/hev-socks5-tunnel/releases/download/2.17.1/hev-socks5-tunnel-2.17.1.tar.xz','a7b86050091c5a268d81de70b95d3bb0871ba4136160662b6496596761c2f9a7')
        extract_native(path, CACHE/'native-source')
    (ROOT/'local.properties').write_text('sdk.dir='+sdk.as_posix().replace(':',r'\:')+'\n',encoding='utf-8')
    print('Toolchain ready:',CACHE)


if __name__=='__main__': main()
