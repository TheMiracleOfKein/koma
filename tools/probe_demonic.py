#!/usr/bin/env python3
from __future__ import annotations

import re
import ssl
import urllib.request

UA = "Mozilla/5.0"
CTX = ssl.create_default_context()


def fetch(url: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Referer": "https://demonicscans.org/"})
    with urllib.request.urlopen(req, timeout=40, context=CTX) as resp:
        return resp.read().decode("utf-8", "ignore")


def main() -> None:
    ph = fetch("https://demonicscans.org/chaptered.php?manga=59&chapter=339")
    print("DM page len", len(ph))
    for pat in ["img", "chapter-image", "lazy", "data-src", "scans"]:
        print(pat, ph.count(pat))
    imgs = re.findall(r'(?:data-src|src)="(https?://[^"]+)"', ph)
    real = [i for i in imgs if any(x in i.lower() for x in [".jpg", ".png", ".webp", "cdn", "scan"])]
    print("imgs", len(real), real[:3])
    # list page popular
    h = fetch("https://demonicscans.org/advanced.php")
    print("adv", len(h), re.findall(r'href="(/manga/[^"]+)"', h)[:5])
    h = fetch("https://demonicscans.org/newmangalist.php")
    print("new", len(h), re.findall(r'href="(/manga/[^"]+)"', h)[:5])


if __name__ == "__main__":
    main()
