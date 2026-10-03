#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import ssl
import urllib.parse
import urllib.request

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0"
CTX = ssl.create_default_context()


def fetch(url: str, headers: dict | None = None) -> tuple[int, str]:
    req = urllib.request.Request(url, headers={"User-Agent": UA, **(headers or {})})
    try:
        with urllib.request.urlopen(req, timeout=35, context=CTX) as resp:
            return resp.getcode() or 200, resp.read().decode("utf-8", "ignore")
    except Exception as e:
        return 0, str(e)


def main() -> None:
    # ComicK
    for u in [
        "https://api.comick.fun/v1.0/search?page=1&limit=5&sort=follow",
        "https://api.comick.io/v1.0/search?page=1&limit=5&sort=follow",
        "https://comick.io/_next/data/",
        "https://api.comick.fun/chapter/hid/get_images",
    ]:
        code, body = fetch(u, {"Accept": "application/json"})
        print("COMICK", u, code, body[:100].replace("\n", " "))

    # Bato / xbato
    for u in [
        "https://xbato.com/v3x-search",
        "https://bato.to/v3x-search",
        "https://dto.to/v3x-search",
    ]:
        code, body = fetch(u)
        print("BATO", u, code, len(body), "item", body.count("item-title") + body.count("series-list"))

    # Flame
    for u in [
        "https://flamecomics.xyz/series",
        "https://flamecomics.com/series",
        "https://flamecomics.xyz/api/comics",
        "https://api.flamecomics.xyz/api/comics",
    ]:
        code, body = fetch(u, {"Accept": "application/json,text/html"})
        print("FLAME", u, code, len(body), body[:80].replace("\n", " "))

    # Cubari / git
    code, body = fetch("https://cubari.moe/")
    print("CUBARI", code, len(body))

    # MangaFire
    code, body = fetch("https://mangafire.to/home")
    print("MFIRE", code, len(body), "unit", body.count("unit item"), "manga", body.count("/manga/"))

    # MangaPark
    code, body = fetch("https://mangapark.net/")
    print("MPARK", code, len(body), body[:60].replace("\n", " "))
    code, body = fetch("https://mangapark.net/search?sortby=field_score")
    print("MPARK2", code, len(body), "qtip", body.count("qtip"), "manga", body.count("/title/"))

    # Omni / temple scans style
    for name, u in [
        ("temple", "https://templetoons.com/"),
        ("rook", "https://rookscans.com/"),
        ("luna", "https://lunarscan.org/"),
        ("omega", "https://omegascans.org/"),
        ("hivetoon", "https://hivetoon.com/"),
        ("reaperscans", "https://reaperscans.com/"),
    ]:
        code, body = fetch(u)
        print(name, code, len(body), "bsx", body.count("bsx"), "series", body.count("/series/"), "cf", "Just a moment" in body)


if __name__ == "__main__":
    main()
