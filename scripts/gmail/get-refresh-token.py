#!/usr/bin/env python3
"""One-time: get a Gmail refresh token (gmail.readonly) for the bank-balance sync.

Usage:  python3 scripts/gmail/get-refresh-token.py <CLIENT_ID> <CLIENT_SECRET>

Opens the Google consent page in your browser, catches the redirect on localhost, and prints the
refresh token. Put it in the Lambda env var GMAIL_REFRESH_TOKEN. Never commit it.
Needs an OAuth client of type "Desktop app" and the consent screen published to Production
(tokens from a "Testing" app expire after 7 days). No third-party packages required.
"""
import http.server
import json
import secrets
import sys
import urllib.parse
import urllib.request
import webbrowser

SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
PORT = 8765
REDIRECT = f"http://127.0.0.1:{PORT}"


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    client_id, client_secret = sys.argv[1], sys.argv[2]
    state = secrets.token_urlsafe(16)
    auth_url = "https://accounts.google.com/o/oauth2/v2/auth?" + urllib.parse.urlencode({
        "client_id": client_id, "redirect_uri": REDIRECT, "response_type": "code",
        "scope": SCOPE, "access_type": "offline", "prompt": "consent", "state": state,
    })
    result = {}

    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            q = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
            result.update({k: v[0] for k, v in q.items()})
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b"Done. You can close this tab.")

        def log_message(self, *args):
            pass

    print("Opening browser for consent...\nIf it does not open, visit:\n" + auth_url)
    webbrowser.open(auth_url)
    http.server.HTTPServer(("127.0.0.1", PORT), Handler).handle_request()

    if result.get("state") != state or "code" not in result:
        sys.exit(f"Authorization failed: {result.get('error', 'state mismatch')}")

    body = urllib.parse.urlencode({
        "code": result["code"], "client_id": client_id, "client_secret": client_secret,
        "redirect_uri": REDIRECT, "grant_type": "authorization_code",
    }).encode()
    with urllib.request.urlopen("https://oauth2.googleapis.com/token", body) as r:
        tokens = json.load(r)
    if "refresh_token" not in tokens:
        sys.exit("No refresh_token returned. Revoke the app at myaccount.google.com/permissions and retry.")
    print("\nGMAIL_REFRESH_TOKEN=" + tokens["refresh_token"])


if __name__ == "__main__":
    main()
