#!/usr/bin/env python3
# PLACEHOLDER example script. Your real example server script was not in the uploads
# (only smali1-4.zip, Design2.jpg, Desire1.jpg), so this tiny stand-in shows the wiring.
# Replace this file with yours; the app will run assets/scripts/<name> inside Termux.
import http.server
import socketserver

PORT = 8765

class Handler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.end_headers()
        self.wfile.write(b"Hello from Exor + Termux\n")

with socketserver.TCPServer(("0.0.0.0", PORT), Handler) as srv:
    print(f"example_server listening on :{PORT}")
    srv.serve_forever()
