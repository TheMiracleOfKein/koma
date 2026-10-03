#!/usr/bin/env python3
import json
import re
import ssl
import urllib.request

CTX = ssl.create_default_context()


def fetch(url: str, headers=None) -> str:
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": "Mozilla/5.0",
            "Accept": "application/json,text/html",
            "Referer": "https://omegascans.org/",
            **(headers or {}),
        },
    )
    with urllib.request.urlopen(req, timeout=30, context=CTX) as resp:
        return resp.read().decode("utf-8", "ignore")


# find non-nsfw-ish title? omega is mostly adult. Still a working source.
slug = "milf-hunting-in-another-world"
ch = "chapter-1"
for u in [
    f"https://api.omegascans.org/chapter/{slug}/{ch}",
    f"https://api.omegascans.org/series/{slug}/{ch}",
    f"https://api.omegascans.org/chapter?series_slug={slug}&chapter_slug={ch}",
    f"https://omegascans.org/series/{slug}/{ch}",
    f"https://omegascans.org/comics/{slug}/{ch}",
]:
    try:
        body = fetch(u)
        print("OK", u, len(body), body[:100].replace("\n", " "))
        imgs = re.findall(r'(?:data-src|src|url)\"?\s*[:=]\s*\"(https?://[^\"]+)\"', body)
        print(" imgs", len(imgs), imgs[:2])
    except Exception as e:
        print("NO", u, e)
