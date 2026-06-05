#!/usr/bin/env python3
"""Tiny rate-limited static file server for testing the auto-update download UI.

Serves a directory over HTTP, streaming responses at a fixed byte/second rate so the
download progress bar is observable (e.g. to close the dialog mid-download and verify
the transfer keeps running in the background).

Usage: throttled-feed-server.py <dir> <port> <bytes_per_sec>
"""
import os
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DIRECTORY = sys.argv[1]
PORT = int(sys.argv[2])
RATE = int(sys.argv[3])  # bytes per second
CHUNK = max(1, RATE // 20)  # ~20 writes per second


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        path = os.path.normpath(os.path.join(DIRECTORY, self.path.lstrip("/")))
        if not path.startswith(DIRECTORY) or not os.path.isfile(path):
            self.send_response(404)
            self.end_headers()
            return
        size = os.path.getsize(path)
        self.send_response(200)
        self.send_header("Content-Length", str(size))
        self.send_header("Content-Type", "application/octet-stream")
        self.end_headers()
        with open(path, "rb") as f:
            while True:
                chunk = f.read(CHUNK)
                if not chunk:
                    break
                try:
                    self.wfile.write(chunk)
                except BrokenPipeError:
                    return
                time.sleep(CHUNK / RATE)

    def log_message(self, *args):
        pass


print(f"Throttled feed server: dir={DIRECTORY} port={PORT} rate={RATE} B/s", flush=True)
ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
