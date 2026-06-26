#!/usr/bin/env python3
"""
Yggdrasil auth mock for Minecraft offline multiplayer.
Handles all authserver + sessionserver endpoints so the client
thinks it's talking to Mojang.
"""
from http.server import HTTPServer, BaseHTTPRequestHandler
import json, sys, uuid

class YggdrasilHandler(BaseHTTPRequestHandler):
    proto = 1
    err_headers = 0

    def _json(self, data, code=200):
        body = json.dumps(data).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", len(body))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        path = self.path.split("?")[0]
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length) if length else b"{}"

        # /authserver/authenticate — login
        if path in ("/authserver/authenticate", "/api/authlib-injector/authserver/authenticate"):
            try:
                req = json.loads(raw)
                username = req.get("username", "Player")
            except:
                username = "Player"
            uid = str(uuid.uuid5(uuid.NAMESPACE_DNS, username))
            self._json({
                "accessToken": "matrix-token-" + uid[:8],
                "clientToken": "matrix-client",
                "availableProfiles": [{
                    "id": uid.replace("-", ""),
                    "name": username,
                    "properties": []
                }],
                "selectedProfile": {
                    "id": uid.replace("-", ""),
                    "name": username,
                    "properties": []
                }
            })
            return

        # /authserver/refresh — refresh token
        if "/refresh" in path:
            try:
                req = json.loads(raw)
                uid = req.get("accessToken", "0")[-8:]
            except:
                uid = "00000000"
            self._json({
                "accessToken": "matrix-token-" + uid,
                "clientToken": "matrix-client",
                "selectedProfile": {
                    "id": "00000000000000000000000000000000",
                    "name": "MatrixAgent"
                }
            })
            return

        # /authserver/validate — validate token
        if "/validate" in path:
            self.send_response(204)
            self.end_headers()
            return

        # /sessionserver/session/minecraft/join — join server
        if "/join" in path:
            self.send_response(204)
            self.end_headers()
            return

        # Default: OK
        self._json({})

    def do_GET(self):
        path = self.path.split("?")[0]

        # /sessionserver/session/minecraft/hasJoined — server check
        if "/hasJoined" in path:
            username = "MatrixAgent"
            if "username=" in self.path:
                username = self.path.split("username=")[1].split("&")[0]
            uid = str(uuid.uuid5(uuid.NAMESPACE_DNS, username)).replace("-", "")
            self._json({
                "id": uid,
                "name": username,
                "properties": []
            })
            return

        # /sessionserver/session/minecraft/profile — player profile  
        if "/profile" in path:
            uid = "00000000000000000000000000000000"
            self._json({
                "id": uid,
                "name": "MatrixAgent",
                "properties": []
            })
            return

        # /api/authlib-injector — metadata
        if "/api/authlib-injector" in path:
            self._json({
                "meta": {
                    "serverName": "MATRIX Offline Auth",
                    "implementationName": "matrix-yggdrasil-mock",
                    "implementationVersion": "1.0"
                },
                "skinDomains": [],
                "signaturePublickey": ""
            })
            return

        # Root — server info
        self._json({"status": "MATRIX Yggdrasil Mock", "version": "1.0"})

    def log_message(self, fmt, *args):
        pass

if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 25567
    print(f"Yggdrasil mock: http://127.0.0.1:{port}")
    HTTPServer(("127.0.0.1", port), YggdrasilHandler).serve_forever()
