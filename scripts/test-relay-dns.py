"""Controlled DNS + WebDAV for the explicitly selected emulator, no user data."""
import base64
import http.server
import json
import pathlib
import socketserver
import struct
import threading

root = pathlib.Path(__file__).resolve().parents[1]
enabled = False
queries = []
stop = threading.Event()

class Dns(socketserver.BaseRequestHandler):
    def handle(self):
        packet, sock = self.request
        try:
            end = 12
            labels = []
            while packet[end]:
                length = packet[end]
                labels.append(packet[end+1:end+1+length].decode('ascii'))
                end += length+1
            end += 1
            qtype = struct.unpack('!H',packet[end:end+2])[0]
            end += 4
            host = '.'.join(labels)
            valid = host.endswith('.relay-fixture.test') and not host.startswith('offline.')
            answer = valid and enabled and qtype == 1
            queries.append({'host':host,'enabled':enabled,'type':qtype})
            code = 0 if valid and enabled else 3
            reply = packet[:2]+struct.pack('!HHHHH',0x8180|code,1,int(answer),0,0)+packet[12:end]
            if answer: reply += b'\xc0\x0c'+struct.pack('!HHIH',1,1,1,4)+bytes([10,0,2,2])
            sock.sendto(reply,self.client_address)
        except (ValueError,IndexError,UnicodeError): pass

class WebDav(http.server.BaseHTTPRequestHandler):
    def log_message(self,*args): pass
    def do_GET(self):
        global enabled
        if self.path == '/dns/on': enabled=True;body=b'on'
        elif self.path == '/dns/off': enabled=False;body=b'off'
        elif self.path == '/stop-fixture': stop.set();body=b'stopping'
        elif self.path == '/stats': body=json.dumps(queries).encode()
        else: self.send_error(404);return
        self.send_response(200);self.end_headers();self.wfile.write(body)
    def do_PROPFIND(self):
        if self.headers.get('Authorization') != 'Basic '+base64.b64encode(b'user:fixture-password').decode(): self.send_error(401);return
        prefix=self.path.rstrip('/')+'/'
        def row(url,directory):
            return '<d:response><d:href>'+url+'</d:href><d:propstat><d:prop><d:resourcetype>'+('<d:collection/>' if directory else '')+'</d:resourcetype><d:getcontentlength>7</d:getcontentlength><d:getetag>&quot;v1&quot;</d:getetag></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>'
        body=('<d:multistatus xmlns:d="DAV:">'+row(prefix,True)+(row(prefix+'recover.txt',False) if self.headers.get('Depth')=='1' else '')+'</d:multistatus>').encode()
        self.send_response(207);self.send_header('Content-Type','application/xml');self.end_headers();self.wfile.write(body)

dns=socketserver.ThreadingUDPServer(('127.0.0.1',53),Dns)
http=http.server.ThreadingHTTPServer(('0.0.0.0',0),WebDav)
for server in (dns,http): threading.Thread(target=server.serve_forever,daemon=True).start()
root.joinpath('.build/relay-dns-fixture.json').write_text(json.dumps({'port':http.server_port,'dns':'127.0.0.1','hostSuffix':'relay-fixture.test'}),encoding='utf-8')
print('Isolated DNS/WebDAV ready on port '+str(http.server_port),flush=True)
try: stop.wait()
finally:
    dns.shutdown();dns.server_close();http.shutdown();http.server_close()
