#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import ssl
import urllib.request

UA = "Mozilla/5.0"
CTX = ssl.create_default_context()


def fetch(url: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=25, context=CTX) as resp:
        return resp.read().decode("utf-8", "ignore")


def probe(name: str, list_url: str, link_re: str) -> None:
    h = fetch(list_url)
    links = list(dict.fromkeys(re.findall(link_re, h)))
    print(name, "list", len(h), "links", len(links), links[:3])
    if not links:
        return
    path = links[0]
    url = path if path.startswith("http") else "https://" + list_url.split("/")[2] + path
    th = fetch(url)
    chaps = list(
        dict.fromkeys(
            re.findall(r'href="([^"]*(?:chapter|ch-)[^"]*)"', th, re.I)
        )
    )
    print(name, "chaps", len(chaps), chaps[:3])
    # next data?
    print(name, "next", "__NEXT_DATA__" in th, "api", len(re.findall(r"/api/[^\"']+", th)[:10]))


def main() -> None:
    probe("hive", "https://hivetoon.com/series", r'href="(/series/[^"#?]+)"')
    probe("omega", "https://omegascans.org/series", r'href="(/series/[^"#?]+)"')
    # lunascans - madara?
    h = fetch("https://lunarscan.org/")
    print("luna markers", h.count("page-item-detail"), h.count("wp-manga"), h.count("bsx"), "cf", "Just a moment" in h)
    print("luna hrefs", re.findall(r'href="(https?://[^"]+|/[^"]+)"', h)[:20])

    # Hive API guess (iken)
    for u in [
        "https://api.hivetoon.com/api/query?page=1&perPage=12&orderBy=total_views",
        "https://hivetoon.com/api/query?page=1",
        "https://api.omegascans.org/query?page=1&perPage=12",
    ]:
        try:
            body = fetch(u)
            print("API", u, body[:120].replace("\n", " "))
        except Exception as e:
            print("API", u, e)


if __name__ == "__main__":
    main()
