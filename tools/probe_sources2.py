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
    with urllib.request.urlopen(req, timeout=35, context=CTX) as resp:
        return resp.read().decode("utf-8", "ignore")


def main() -> None:
    # MangaDex chapter with contentRating filter on feed
    q = urllib.parse.urlencode(
        [
            ("limit", "3"),
            ("order[followedCount]", "desc"),
            ("includes[]", "cover_art"),
            ("contentRating[]", "safe"),
            ("contentRating[]", "suggestive"),
            ("contentRating[]", "erotica"),
            ("availableTranslatedLanguage[]", "en"),
            ("hasAvailableChapters", "true"),
        ]
    )
    data = json.loads(fetch("https://api.mangadex.org/manga?" + q))["data"]
    md_id = data[1]["id"]  # skip potentially blocked #1
    print("MD pick", md_id, data[1]["attributes"]["title"])
    q2 = urllib.parse.urlencode(
        [
            ("limit", "5"),
            ("translatedLanguage[]", "en"),
            ("order[chapter]", "desc"),
            ("includeEmptyPages", "0"),
            ("contentRating[]", "safe"),
            ("contentRating[]", "suggestive"),
            ("contentRating[]", "erotica"),
            ("contentRating[]", "pornographic"),
        ]
    )
    feed = json.loads(fetch(f"https://api.mangadex.org/manga/{md_id}/feed?" + q2))
    print("MD feed total", feed.get("total"), "n", len(feed.get("data", [])))
    if feed.get("data"):
        ch = feed["data"][0]["id"]
        at = json.loads(fetch(f"https://api.mangadex.org/at-home/server/{ch}"))
        print("MD pages", len(at["chapter"]["data"]), at["baseUrl"])

    h = fetch("https://mangakatana.com/latest")
    links = list(dict.fromkeys(re.findall(r'href="(https://mangakatana.com/manga/[^"]+)"', h)))
    print("MK links", len(links), links[:2])
    if links:
        idx = h.find(links[0])
        print("MK snip", re.sub(r"\s+", " ", h[max(0, idx - 180) : idx + 220])[:400])
        th = fetch(links[0])
        chaps = list(dict.fromkeys(re.findall(r'href="(https://mangakatana.com/manga/[^"]+/c[^"]+)"', th)))
        print("MK chaps", len(chaps), chaps[:2])
        if chaps:
            ph = fetch(chaps[0])
            imgs = re.findall(
                r'(?:data-src|src)="(https?://[^"]+\.(?:jpg|jpeg|png|webp)[^"]*)"',
                ph,
                re.I,
            )
            print("MK imgs", len(imgs), imgs[:2])

    h = fetch("https://manganato.com/genre-all")
    print("MN hosts", sorted(set(re.findall(r"https://([a-z0-9.-]+)/", h)))[:20])
    print("MN anchors", re.findall(r'href="(https://[^"]+)"', h)[:20])
    for url in [
        "https://www.natomanga.com/genre-all",
        "https://www.mangakakalot.gg/",
        "https://chapmanganato.me/",
    ]:
        try:
            hh = fetch(url)
            print(
                "ALT",
                url,
                len(hh),
                "genres-item",
                hh.count("genres-item"),
                "list-truyen",
                hh.count("list-truyen-item"),
                "content-genres",
                hh.count("content-genres-item"),
            )
        except Exception as e:
            print("ALT", url, e)

    h = fetch("https://asuracomic.net/series?page=1")
    print("AS hrefs", re.findall(r'href="(/[^"]+)"', h)[:40])
    print("AS comics count", h.count("/comics/"), "series count", h.count("/series/"))
    m = re.search(r'<script id="__NEXT_DATA__"[^>]*>(\{.*?\})</script>', h)
    print("AS next", bool(m))
    if m:
        d = json.loads(m.group(1))
        props = d.get("props", {}).get("pageProps", {})
        print("AS pageProps", list(props.keys())[:30])
        for k, v in props.items():
            if isinstance(v, (list, dict)):
                print(" ", k, type(v).__name__, (len(v) if hasattr(v, "__len__") else ""))

    # WeebCentral / Vortex HTML samples
    for name, url in [
        ("WC", "https://weebcentral.com/search/data?limit=24&offset=0&sort=Popularity&order=Descending&official=Any&anime=Any&adult=Any&display_mode=Full%20Display"),
        ("WC2", "https://weebcentral.com/"),
        ("VX", "https://vortexscans.org/series"),
        ("RZ", "https://rizzfables.com/series"),
        ("FL", "https://flamecomics.xyz/series"),
        ("DM", "https://demonicscans.org/manga.php?list"),
    ]:
        try:
            hh = fetch(url)
            print(name, len(hh), "json?", hh.strip()[:1] == "{", "bsx", hh.count("bsx"), "series", hh.count("/series/"), "snip", re.sub(r"\s+", " ", hh[:180]))
        except Exception as e:
            print(name, e)

    # ManhuaPlus chapter pages
    h = fetch("https://manhuaplus.com/manga/?m_orderby=views", headers={"Referer": "https://manhuaplus.com/"})
    links = [
        u
        for u in dict.fromkeys(re.findall(r'href="(https://manhuaplus.com/manga/[^"?]+/)"', h))
        if "/page/" not in u
    ]
    print("MP links", links[:2])
    if links:
        th = fetch(links[0], headers={"Referer": "https://manhuaplus.com/"})
        chaps = re.findall(r'li class="wp-manga-chapter[\s\S]{0,300}?href="(https://manhuaplus.com/manga/[^"]+)"', th)
        print("MP chaps", len(chaps), chaps[:1])
        if chaps:
            chap = chaps[0]
            if "style=" not in chap:
                chap = chap.rstrip("/") + "/?style=list"
            ph = fetch(chap, headers={"Referer": links[0]})
            print("MP page-break", ph.count("page-break"), "imgs", len(re.findall(r"page-break", ph)))


if __name__ == "__main__":
    main()
