"""Loopback-only synthetic WebDAV fixture; no NAS credentials or user files.

Run while testing the explicitly selected Android emulator. Its host alias is
10.0.2.2; connection: http://10.0.2.2:18764/dav/, inbox, user / pass.
"""
import argparse
import hashlib
import json
import threading
import time
from email.utils import formatdate
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import quote, unquote, urlsplit
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[1]
files = {'/dav/inbox/welcome.txt': b'android fixture welcome',
         '/dav/inbox/old.txt': b'old fixture', '/dav/inbox/folder/nested.txt': b'nested fixture'}
modified = {p: time.time() - (864000 if 'old.txt' in p else 0) for p in files}
directories = {'/dav/', '/dav/inbox/', '/dav/inbox/folder/'}
requests = []
lock = threading.Lock()


def etag(path):
    return '"' + hashlib.sha256(files[path]).hexdigest() + '"'


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args): pass

    def handle_request(self):
        body = self.rfile.read(int(self.headers.get('Content-Length', '0')))
        if self.command == 'PUT' and b'slow-queue' in body: time.sleep(8)
        with lock:
            code, output = self.response(body)
            self.send_response(code)
            self.send_header('Content-Length', str(len(output)))
            self.end_headers()
            self.wfile.write(output)
            report = {'files': {k: v.decode('utf-8', errors='replace') for k, v in files.items()}, 'requests': requests}
            target = ROOT / '.build/relay-validation-state.json'
            temporary = target.with_suffix('.tmp')
            temporary.write_text(json.dumps(report, ensure_ascii=False), encoding='utf-8')
            # Windows may briefly lock the report while the smoke test reads it.
            for attempt in range(20):
                try:
                    temporary.replace(target)
                    break
                except PermissionError:
                    if attempt == 19: raise
                    time.sleep(.02)

    def response(self, body):
        path = unquote(urlsplit(self.path).path)
        requests.append([self.command, path])
        if self.headers.get('Authorization') != 'Basic dXNlcjpwYXNz': return 401, b''
        method = self.command
        directory = path.rstrip('/') + '/'
        if method == 'PROPFIND':
            if path not in files and directory not in directories: return 404, b''
            own = directory if directory in directories else path
            entries = [own]
            if self.headers.get('Depth') == '1':
                entries += [p for p in list(files) + list(directories) if p != own and p.rstrip('/').rsplit('/', 1)[0] + '/' == own]
            rows = []
            for item in entries:
                collection = item in directories
                properties = '<d:resourcetype>' + ('<d:collection/>' if collection else '') + '</d:resourcetype>'
                if not collection:
                    properties += f'<d:getcontentlength>{len(files[item])}</d:getcontentlength><d:getetag>{escape(etag(item))}</d:getetag><d:getlastmodified>{formatdate(modified[item], usegmt=True)}</d:getlastmodified>'
                rows.append(f'<d:response><d:href>{quote(item)}</d:href><d:propstat><d:prop>{properties}</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>')
            return 207, ('<d:multistatus xmlns:d="DAV:">' + ''.join(rows) + '</d:multistatus>').encode()
        if method == 'MKCOL':
            if directory in directories: return 405, b''
            if directory.rstrip('/').rsplit('/', 1)[0] + '/' not in directories: return 409, b''
            directories.add(directory); return 201, b''
        if method == 'PUT':
            if path in files and self.headers.get('If-None-Match') == '*': return 412, b''
            files[path] = body; modified[path] = time.time(); return 201, b''
        if path not in files: return 404, b''
        if self.headers.get('If-Match') not in (None, '*', etag(path)): return 412, b''
        if method == 'GET': return 200, files[path]
        if method == 'DELETE':
            del files[path]; del modified[path]; return 204, b''
        if method == 'MOVE':
            destination = self.headers.get('Destination', '')
            if destination.startswith('http'): return 502, b''  # fnOS fallback.
            destination = unquote(destination)
            if destination in files and self.headers.get('Overwrite') == 'F': return 412, b''
            files[destination] = files.pop(path); modified[destination] = modified.pop(path)
            return 201, b''
        return 405, b''

    do_PROPFIND = do_MKCOL = do_PUT = do_MOVE = do_GET = do_DELETE = handle_request


if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('--port', type=int, default=18764)
    args = parser.parse_args()
    (ROOT / '.build').mkdir(exist_ok=True)
    print(f'Synthetic WebDAV listening at 127.0.0.1:{args.port}', flush=True)
    ThreadingHTTPServer(('127.0.0.1', args.port), Handler).serve_forever()
