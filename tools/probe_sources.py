#!/usr/bin/env python3
"""Probe popular catalogue sites for KomaKt source expansion."""
from __future__ import annotations

import json
import os
import re
import ssl
import urllib.parse
import urllib.request
from pathlib import Path

TMP = Path(os.environ.get("TEMP", "."))
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
CTX = ssl.create_default_context()


def fetch(url: str, headers: dict[str, str] | None = None, timeout: int = 30) -> tuple[int, str]:
    req = urllib.request.Request(url, headers={"User-Agent": UA, **(headers or {})})
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=CTX) as resp:
            body = resp.read().decode("utf-8", "ignore")
            return resp.getcode() or 200, body
    except Exception as e:
        return 0, f"ERR:{e}"


def main() -> None:
    # MangaDex
    q = urllib.parse.urlencode(
        [
            ("limit", "2"),
            ("order[followedCount]", "desc"),
            ("includes[]", "cover_art"),
            ("contentRating[]", "safe"),
            ("contentRating[]", "suggestive"),
            ("availableTranslatedLanguage[]", "en"),
        ]
    )
    code, body = fetch(f"https://api.mangadex.org/manga?{q}")
    print("MD list", code, len(body))
    md_id = None
    if code == 200:
        data = json.loads(body)["data"]
        md_id = data[0]["id"]
        title = data[0]["attributes"]["title"]
        print("MD title", title, md_id)
        code2, feed = fetch(
            f"https://api.mangadex.org/manga/{md_id}/feed?"
            + urllib.parse.urlencode(
                [
                    ("limit", "3"),
                    ("translatedLanguage[]", "en"),
                    ("order[chapter]", "desc"),
                    ("includeEmptyPages", "0"),
                ]
            )
        )
        print("MD feed", code2)
        ch = json.loads(feed)["data"][0]["id"]
        code3, at = fetch(f"https://api.mangadex.org/at-home/server/{ch}")
        pages = json.loads(at)["chapter"]["data"]
        print("MD pages", code3, len(pages), json.loads(at).get("baseUrl"))

    # MangaKatana
    code, html = fetch("https://mangakatana.com/latest")
    print("MK", code, len(html), "book_list", html.count("book_list"), "/manga/", html.count("/manga/"))
    m = re.search(r'href="(https://mangakatana.com/manga/[^"]+)"', html)
    print("MK first", m.group(1) if m else None)
    if m:
        code, th = fetch(m.group(1))
        print("MK title", code, "chapters", len(re.findall(r"/manga/[^\"']+/c\d+", th)))
        cm = re.search(r'href="(https://mangakatana.com/manga/[^"]+/c[^"]+)"', th)
        print("MK chap", cm.group(1) if cm else None)
        if cm:
            code, ph = fetch(cm.group(1))
            imgs = re.findall(r'<img[^>]+(?:data-src|src)="(https?://[^"]+)"', ph)
            print("MK pages", code, len(imgs), imgs[:2])

    # Manganato
    code, html = fetch("https://manganato.com/genre-all")
    print("MN", code, len(html), "genres-item", html.count("genres-item"), "item-title", html.count("item-title"))
    m = re.search(r'href="(https://(?:www\.)?manganato\.com/manga-[^"]+)"', html)
    if not m:
        m = re.search(r'href="(https://chapmanganato\.[^"]+/manga-[^"]+)"', html)
    print("MN first", m.group(1) if m else None)
    if m:
        code, th = fetch(m.group(1), headers={"Referer": "https://manganato.com/"})
        print("MN title", code, "chapter-name", th.count("chapter-name"), "row-content-chapter", th.count("row-content-chapter"))
        cm = re.search(r'href="(https://[^"]+/chapter-\d[^"]*)"', th)
        print("MN chap", cm.group(1) if cm else None)
        if cm:
            code, ph = fetch(cm.group(1), headers={"Referer": m.group(1)})
            imgs = re.findall(r'<img[^>]+(?:data-src|src)="(https?://[^"]+)"', ph)
            print("MN pages", code, len([i for i in imgs if "logo" not in i.lower()][:50]), imgs[:3])

    # Asura
    code, html = fetch("https://asuracomic.net/series?page=1")
    comics = re.findall(r'href="(/series/[^"#]+)"', html)
    print("AS", code, len(html), "series", len(comics), "uniq", len(set(comics)))
    print("AS sample", list(dict.fromkeys(comics))[:5])
    if comics:
        path = list(dict.fromkeys(comics))[0]
        code, th = fetch("https://asuracomic.net" + path)
        print("AS title", code, "chapter", th.count("/chapter/"), len(re.findall(r'href="(/series/[^"]+/chapter/[^"]+)"', th)))
        cm = re.search(r'href="(/series/[^"]+/chapter/[^"]+)"', th)
        print("AS chap", cm.group(1) if cm else None)
        if cm:
            code, ph = fetch("https://asuracomic.net" + cm.group(1))
            imgs = re.findall(r'<img[^>]+(?:data-src|src)="(https?://[^"]+)"', ph)
            print("AS pages", code, len(imgs), [i for i in imgs if "cover" not in i][:2])

    # ManhuaPlus Madara
    code, html = fetch("https://manhuaplus.com/manga/?m_orderby=views", headers={"Referer": "https://manhuaplus.com/"})
    print("MP", code, "items", html.count("page-item-detail"), "cf", "Just a moment" in html)
    links = list(dict.fromkeys(re.findall(r'href="(https://manhuaplus.com/manga/[^"?]+/)"', html)))
    links = [u for u in links if "/page/" not in u]
    print("MP titles", len(links), links[:2])
    if links:
        code, th = fetch(links[0], headers={"Referer": "https://manhuaplus.com/"})
        print("MP title", code, "chap nodes", th.count("wp-manga-chapter"))
        cm = re.search(r'href="(https://manhuaplus.com/manga/[^"]+)"[^>]*>\s*Chapter', th, re.I)
        if not cm:
            cm = re.search(r'li class="wp-manga-chapter[\s\S]*?href="(https://manhuaplus.com/manga/[^"]+)"', th)
        print("MP chap", cm.group(1) if cm else None)
        if cm:
            chap = cm.group(1)
            if "style=" not in chap:
                chap = chap.rstrip("/") + "/?style=list"
            code, ph = fetch(chap, headers={"Referer": links[0]})
            imgs = re.findall(r'(?:data-src|src)="(https?://[^"]+)"', ph)
            print("MP pages", code, len([i for i in imgs if "wp-manga" in ph]), ph.count("page-break"), imgs[:2])

    # WeebCentral
    code, html = fetch("https://weebcentral.com/")
    print("WC", code, len(html), "cf", "Just a moment" in html, "Popular", "Popular" in html)

    # Vortex / Rizz MangaThemesia-ish
    for name, url in [
        ("vortex", "https://vortexscans.org/"),
        ("rizz", "https://rizzfables.com/"),
        ("flame", "https://flamecomics.xyz/"),
        ("demonic", "https://demonicscans.org/"),
    ]:
        code, html = fetch(url)
        print(name, code, len(html), "cf", "Just a moment" in html, "listupd", html.count("listupd"), "bsx", html.count("bsx"))


if __name__ == "__main__":
    main()
