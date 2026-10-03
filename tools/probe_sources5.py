#!/usr/bin/env python3
from __future__ import annotations

import re
import ssl
import urllib.request

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
CTX = ssl.create_default_context()


def fetch(url: str, headers: dict | None = None) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": UA, **(headers or {})})
    with urllib.request.urlopen(req, timeout=40, context=CTX) as resp:
        return resp.read().decode("utf-8", "ignore")


def main() -> None:
    lst = fetch("https://weebcentral.com/series/01J76XY7E9FNDZ1DBBM6PBJPFK/full-chapter-list")
    chaps = list(dict.fromkeys(re.findall(r'href="(/chapters/[^"]+)"', lst)))
    print("WC rel chaps", len(chaps), chaps[:3])
    if chaps:
        img_url = "https://weebcentral.com" + chaps[0].rstrip("/") + "/images?is_prev=False&reading_style=long_strip"
        ph = fetch(img_url)
        imgs = re.findall(r'<img[^>]+src="(https?://[^"]+)"', ph)
        print("WC imgs", len(imgs), imgs[:2], "section", ph.count("x-data"))

    # Asura: look for chapter image paths in scripts
    ph = fetch("https://asuracomic.net/comics/the-genius-professor-wants-to-take-it-easy-53fc8424/chapter/17")
    for pat in ["pages", "images", "chapter/", "webp", "astro", "img"]:
        print("AS count", pat, ph.count(pat))
    # find large data blobs
    for m in re.finditer(r"(https://cdn\.asurascans\.com/[^\"'\s]+)", ph):
        u = m.group(1)
        if "/covers/" not in u and "/profiles/" not in u:
            print("AS url", u[:140])
    # try chapter API patterns used by iken
    for u in [
        "https://api.asurascans.com/api/series/the-genius-professor-wants-to-take-it-easy-53fc8424",
        "https://asuracomic.net/api/series/the-genius-professor-wants-to-take-it-easy-53fc8424",
        "https://api.asuragroup.com/",
    ]:
        try:
            h = fetch(u)
            print("API", u, len(h), h[:80].replace("\n", " "))
        except Exception as e:
            print("API", u, e)

    # Vortex chapter - look for img in scripts / next
    ph = fetch("https://vortexscans.org/series/the-wind-mage/chapter-26")
    print("VX len", len(ph))
    print("VX next", "__NEXT_DATA__" in ph)
    urls = re.findall(r"(https://storage\.vortexscans\.org/[^\"'\\]+)", ph)
    print("VX urls", len(urls))
    for u in list(dict.fromkeys(urls))[:15]:
        print(" ", u)
    # maybe images behind /_next or api
    apis = re.findall(r"(/api/[^\"']+)", ph)
    print("VX apis", list(dict.fromkeys(apis))[:20])

    # Rizz themesia chapters
    h = fetch("https://rizzfables.com/series")
    items = list(dict.fromkeys(re.findall(r'href="(https://rizzfables.com/series/[^"/]+/)"', h)))
    print("RZ items", len(items), items[:3])
    if items:
        th = fetch(items[0])
        block = re.search(r'id="chapterlist"[\s\S]*?</ul>', th)
        print("RZ chapterlist", bool(block))
        if block:
            chaps = list(dict.fromkeys(re.findall(r'href="([^"]+)"', block.group(0))))
            print("RZ chaps", len(chaps), chaps[:2])
            ph = fetch(chaps[0])
            area = re.search(r'id="readerarea"[\s\S]*?(?:</div>\s*){2}', ph)
            src = area.group(0) if area else ph
            imgs = re.findall(r'(?:data-src|src)="(https?://[^"]+)"', src)
            print("RZ imgs", len(imgs), imgs[:2])

    # Demonic HTML
    h = fetch("https://demonicscans.org/")
    print("DM anchors", re.findall(r'href="(/[^"]+)"', h)[:30])


if __name__ == "__main__":
    main()
