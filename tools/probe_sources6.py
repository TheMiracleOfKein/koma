#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import ssl
import urllib.parse
import urllib.request

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
CTX = ssl.create_default_context()


def fetch(url: str, headers: dict | None = None) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": UA, **(headers or {})})
    with urllib.request.urlopen(req, timeout=40, context=CTX) as resp:
        return resp.read().decode("utf-8", "ignore")


def main() -> None:
    # Asura API exploration
    slug = "the-genius-professor-wants-to-take-it-easy-53fc8424"
    for u in [
        f"https://api.asurascans.com/api/series/{slug}",
        f"https://api.asurascans.com/api/series/{slug}/chapters",
        f"https://api.asurascans.com/api/comics/{slug}",
        "https://api.asurascans.com/api/series?page=1&order=desc&orderBy=total_views",
        "https://api.asurascans.com/api/series?page=1",
        "https://api.asurascans.com/series?page=1&per_page=20",
    ]:
        try:
            h = fetch(u, headers={"Accept": "application/json", "Referer": "https://asuracomic.net/"})
            print("ASAPI", u, len(h), h[:120].replace("\n", " "))
        except Exception as e:
            print("ASAPI", u, e)

    detail = json.loads(
        fetch(
            f"https://api.asurascans.com/api/series/{slug}",
            headers={"Accept": "application/json", "Referer": "https://asuracomic.net/"},
        )
    )
    print("detail keys", detail.keys() if isinstance(detail, dict) else type(detail))
    if isinstance(detail, dict):
        for k, v in detail.items():
            if isinstance(v, (list, dict)):
                print(" ", k, type(v).__name__, len(v) if hasattr(v, "__len__") else "")
            else:
                print(" ", k, v)
        # dump nested useful
        data = detail.get("data") or detail.get("series") or detail
        if isinstance(data, dict):
            print("data keys", list(data.keys())[:40])
            ch = data.get("chapters") or data.get("chapter_list")
            print("chapters type", type(ch), (len(ch) if isinstance(ch, list) else ch))
            if isinstance(ch, list) and ch:
                print("chap0", ch[0])

    # chapter pages endpoint guesses
    for u in [
        "https://api.asurascans.com/api/series/chapter/17",
        f"https://api.asurascans.com/api/series/{slug}/chapter/17",
        "https://api.asurascans.com/api/chapter/chapter-17",
        "https://api.asurascans.com/api/chapter?series_id=2017&chapter=17",
    ]:
        try:
            h = fetch(u, headers={"Accept": "application/json", "Referer": "https://asuracomic.net/"})
            print("CH", u, len(h), h[:150].replace("\n", " "))
        except Exception as e:
            print("CH", u, e)

    # Rizz relative
    h = fetch("https://rizzfables.com/series")
    items = list(dict.fromkeys(re.findall(r'href="(/series/[^"/]+/?)"', h)))
    print("RZ rel", len(items), items[:5])
    if items:
        th = fetch("https://rizzfables.com" + items[0])
        block = re.search(r'id="chapterlist"[\s\S]*?</ul>', th)
        print("RZ list", bool(block))
        if block:
            chaps = list(dict.fromkeys(re.findall(r'href="([^"]+)"', block.group(0))))
            print("RZ chaps", len(chaps), chaps[:2])
            chap = chaps[0] if chaps[0].startswith("http") else "https://rizzfables.com" + chaps[0]
            ph = fetch(chap)
            area = re.search(r'id="readerarea"[\s\S]*?</div>', ph)
            src = area.group(0) if area else ""
            imgs = re.findall(r'(?:data-src|src)="(https?://[^"]+)"', src or ph)
            print("RZ imgs", len(imgs), imgs[:2])

    # Demonic title + chapter
    h = fetch("https://demonicscans.org/manga/Overgeared")
    print("DM title len", len(h), "ch", h.count("chapter"), "list", h.count("list-chapter") + h.count("chp-title"))
    chaps = list(dict.fromkeys(re.findall(r'href="(/[^"]*chapter[^"]*)"', h, re.I)))
    print("DM chaps", len(chaps), chaps[:3])
    if not chaps:
        chaps = list(dict.fromkeys(re.findall(r'href="(/manga/[^"]+)"', h)))
        print("DM manga hrefs", chaps[:10])
    # lastupdates
    h = fetch("https://demonicscans.org/lastupdates.php")
    print("DM last", len(h), re.findall(r'href="(/manga/[^"]+)"', h)[:5])


if __name__ == "__main__":
    main()
