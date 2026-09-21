"""Explicit maintainer command. Uses Git Credential Manager; never prints credentials."""
import argparse
import json
import pathlib
import subprocess
import urllib.error
import urllib.parse
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
REPO = 'thdlrt/AndroidToolbox'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['create-repo', 'release', 'verify'])
    parser.add_argument('--version', default='0.2.1')
    args = parser.parse_args()
    credentials = subprocess.run(['git', 'credential', 'fill'], input='protocol=https\nhost=github.com\n\n', text=True, capture_output=True, check=True)
    token = dict(line.split('=', 1) for line in credentials.stdout.splitlines() if '=' in line)['password']

    def api(path, data=None, method=None, binary=False):
        url = path if path.startswith('https://uploads.github.com/') else 'https://api.github.com' + path
        headers = {'Authorization': 'Bearer ' + token, 'User-Agent': 'AndroidToolbox-maintainer', 'Accept': 'application/vnd.github+json'}
        body = data if binary else None if data is None else json.dumps(data).encode()
        if body is not None: headers['Content-Type'] = 'application/octet-stream' if binary else 'application/json'
        with urllib.request.urlopen(urllib.request.Request(url, data=body, headers=headers, method=method), timeout=120) as response:
            return json.load(response)

    if api('/user')['login'] != REPO.split('/')[0]:
        raise RuntimeError('Authenticated GitHub account does not match repository owner')
    if args.action == 'create-repo':
        try:
            repo = api('/repos/' + REPO)
            raise RuntimeError('Repository already exists; inspect before reusing: ' + repo['html_url'])
        except urllib.error.HTTPError as error:
            if error.code != 404: raise
        repo = api('/user/repos', {'name': REPO.split('/')[1], 'description': '原生安卓工具箱，以 FN Connect 回家 VPN 为首个工具，支持应用内更新。', 'private': False, 'has_issues': True, 'auto_init': False})
        print(repo['html_url'])
    elif args.action == 'release':
        version = args.version
        apk = ROOT / 'outputs' / ('AndroidToolbox-' + version + '.apk')
        checksum = apk.with_suffix('.apk.sha256')
        if not apk.is_file() or not checksum.is_file(): raise RuntimeError('Build the APK and checksum first')
        notes = (ROOT / 'docs' / ('RELEASE-' + version + '.md')).read_text(encoding='utf-8')
        release = api('/repos/' + REPO + '/releases', {'tag_name': 'v' + version, 'name': '安卓工具箱 ' + version, 'body': notes, 'draft': True, 'prerelease': False})
        for path in (apk, checksum):
            url = release['upload_url'].split('{')[0] + '?name=' + urllib.parse.quote(path.name)
            uploaded = api(url, path.read_bytes(), binary=True)
            print('Uploaded:', uploaded['name'], uploaded['size'])
        published = api('/repos/' + REPO + '/releases/' + str(release['id']), {'draft': False}, method='PATCH')
        print(published['html_url'])
    else:
        repo = api('/repos/' + REPO)
        release = api('/repos/' + REPO + '/releases/latest')
        print(json.dumps({'repository': repo['html_url'], 'private': repo['private'], 'license': repo.get('license'), 'release': release['html_url'], 'assets': [{k: a[k] for k in ['name', 'size', 'browser_download_url']} for a in release['assets']]}, ensure_ascii=False))


if __name__ == '__main__':
    main()
