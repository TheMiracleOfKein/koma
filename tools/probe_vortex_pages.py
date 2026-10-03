#!/usr/bin/env python3
import re
import ssl
import urllib.request

CTX = ssl.create_default_context()


def fetch(url: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0", "Referer": "https://vortexscans.org/"})
    with urllib.request.urlopen(req, timeout=30, context=CTX) as resp:
        return resp.read().decode("utf-8", "ignore")


ph = fetch("https://vortexscans.org/series/a-demons-life-from-birth/chapter-5")
imgs = [
    u
    for u in dict.fromkeys(re.findall(r'(?:data-src|src)="(https?://[^"]+)"', ph))
    if "storage.vortexscans" in u and "featured" not in u and "logo" not in u
]
print("pages", len(imgs), imgs[:3])
print("upload", ph.count("/upload/"), "chapter", ph.count("chapter"))
