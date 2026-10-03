#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import ssl
import urllib.request

UA = "Mozilla/5.0"
CTX = ssl.create_default_context()


def fetch(url: str) -> str:
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": UA,
            "Accept": "application/json",
            "Referer": "https://asuracomic.net/",
            "Origin": "https://asuracomic.net",
        },
    )
    with urllib.request.urlopen(req, timeout=40, context=CTX) as resp:
        return resp.read().decode("utf-8", "ignore")


def main() -> None:
    slug = "the-genius-professor-wants-to-take-it-easy-53fc8424"
    ch = json.loads(fetch(f"https://api.asurascans.com/api/series/{slug}/chapters"))
    print("chap0", ch["data"][0])
    cid = ch["data"][0]["id"]
    num = ch["data"][0]["number"]
    cslug = ch["data"][0].get("slug")
    guesses = [
        f"https://api.asurascans.com/api/chapter/{cid}",
        f"https://api.asurascans.com/api/chapters/{cid}",
        f"https://api.asurascans.com/api/series/chapter/{cid}",
        f"https://api.asurascans.com/api/series/{slug}/chapter/{num}",
        f"https://api.asurascans.com/api/series/{slug}/chapters/{num}",
        f"https://api.asurascans.com/api/series/{slug}/chapters/{cslug}",
        f"https://api.asurascans.com/api/pages?chapter_id={cid}",
        f"https://api.asurascans.com/api/chapter/{cid}/pages",
        f"https://api.asurascans.com/api/chapters/{cid}/pages",
        f"https://api.asurascans.com/api/read/{cid}",
        f"https://api.asurascans.com/api/series/{slug}/{cslug}",
        f"https://api.asurascans.com/api/comics/{slug}/chapter/{num}",
    ]
    for u in guesses:
        try:
            h = fetch(u)
            print("OK", u, h[:180].replace("\n", " "))
        except Exception as e:
            print("NO", u, e)

    # Demonic
    req = urllib.request.Request(
        "https://demonicscans.org/manga/Overgeared",
        headers={"User-Agent": "Mozilla/5.0"},
    )
    with urllib.request.urlopen(req, timeout=40, context=CTX) as resp:
        h = resp.read().decode("utf-8", "ignore")
    print("DM", len(h))
    print("DM ch links", re.findall(r'href="([^"]+)"', h)[:40])


if __name__ == "__main__":
    main()
