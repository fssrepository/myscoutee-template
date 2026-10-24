#!/usr/bin/env python3
"""Serve a built Pages artifact locally, including its real 404 redirect document."""
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
ROOT=Path(__file__).resolve().parents[5]/'frontend/docs'
assert (ROOT/'app-version.json').is_file() and (ROOT/'404.html').is_file()
class Pages(SimpleHTTPRequestHandler):
 def __init__(self,*args,**kwargs):super().__init__(*args,directory=str(ROOT),**kwargs)
 def do_GET(self):
  if not self.path.startswith('/myscoutee-template-frontend/'):
   self.send_error(404);return
  self.path=self.path[len('/myscoutee-template-frontend'):]
  super().do_GET()
 def send_error(self,code,message=None,explain=None):
  if code!=404:return super().send_error(code,message,explain)
  body=(ROOT/'404.html').read_bytes()
  self.send_response(404);self.send_header('Content-Type','text/html');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
 def log_message(self,*args):pass
print('Pages artifact ready at http://127.0.0.1:18888/myscoutee-template-frontend/',flush=True)
ThreadingHTTPServer(('127.0.0.1',18888),Pages).serve_forever()
