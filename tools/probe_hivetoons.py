#!/usr/bin/env python3
from __future__ import annotations

import re
import ssl
import urllib.request

UA = "Mozilla/5.0"
CTX = ssl.create_default_context()


def fetch(url: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Referer": "https://hivetoons.org/"})
    with urllib.request.urlopen(req, timeout=30, context=CTX) as resp:
        return resp.read().decode("utf-8", "ignore")


def main() -> None:
    base = "https://hivetoons.org"
    h = fetch(f"{base}/series")
    titles = [u for u in dict.fromkeys(re.findall(r'href="(/series/[^"/]+)"', h)) if "/chapter" not in u]
    print("titles", len(titles), titles[:5])
    th = fetch(base + titles[0])
    chaps = [u for u in dict.fromkeys(re.findall(r'href="(/series/[^"]+/chapter-[^"]+)"', th))]
    print("chaps", len(chaps), chaps[:3])
    if not chaps:
        chaps = [u for u in dict.fromkeys(re.findall(r'href="(/series/[^"]+)"', th)) if "chapter" in u]
        print("chaps2", len(chaps), chaps[:3])
    ph = fetch(base + chaps[0])
    print("page len", len(ph))
    for pat in ["reader", "page", "cdn", "webp", "img", "__NEXT", "astro"]:
        print(pat, ph.count(pat))
    imgs = [
        u
        for u in dict.fromkeys(re.findall(r'(?:data-src|src)="(https?://[^"]+)"', ph))
        if any(x in u for x in [".jpg", ".png", ".webp", "cdn", "chapter", "media"])
        and "logo" not in u
        and "icon" not in u
    ]
    print("imgs", len(imgs), imgs[:3])
    # api
    for u in [
        f"{base}/api/series",
        "https://api.hivetoons.org/api/query?page=1&perPage=10",
        "https://api.hivetoon.com/api/query?page=1&perPage=10",
    ]:
        try:
            print("API", u, fetch(u)[:120].replace("\n", " "))
        except Exception as e:
            print("API", u, e)


if __name__ == "__main__":
    main()
