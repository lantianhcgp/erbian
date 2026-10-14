#!/usr/bin/env python3
"""模拟器 webhook 桩：8644 端口收上行 POST，把 method/path/签名/报文落盘。
只用于 CI 验收（真机上真身是本机 Hermes gateway）。"""
import http.server, json, time, sys

OUT = sys.argv[1] if len(sys.argv) > 1 else "build/acceptance/uplink_received.txt"

class H(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        n = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(n).decode("utf-8", "replace")
        line = {
            "t": time.strftime("%H:%M:%S"),
            "path": self.path,
            "sig": self.headers.get("X-Erbian-Signature", "")[:24] + "...",
            "body": body,
        }
        with open(OUT, "a", encoding="utf-8") as f:
            f.write(json.dumps(line, ensure_ascii=False) + "\n")
        self.send_response(202)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(b'{"status":"ok"}')

    def log_message(self, *a):
        pass

http.server.ThreadingHTTPServer(("0.0.0.0", 8644), H).serve_forever()
