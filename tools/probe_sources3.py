#!/usr/bin/env python3
from __future__ import annotations

import re
import ssl
import urllib.request

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
CTX = ssl.create_default_context()


def fetch(url: str, headers: dict | None = None) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": UA, **(headers or {})})
    with urllib.request.urlopen(req, timeout=35, context=CTX) as resp:
        return resp.read().decode("utf-8", "ignore")


def urls_from_thzq(html: str) -> list[str]:
    m = re.search(r"var\s+thzq\s*=\s*(\[[\s\S]*?\]);", html)
    if not m:
        return []
    return re.findall(r"'(https?://[^']+)'", m.group(1))


def main() -> None:
    chap = "https://mangakatana.com/manga/were-newlyweds-but-i-cant-have-sex-with-her-the-team-captain.27902/c6.1"
    imgs = urls_from_thzq(fetch(chap))
    print("MK pages", len(imgs), imgs[:2])

    h = fetch("https://asuracomic.net/browse?page=1")
    comics = [c for c in dict.fromkeys(re.findall(r'href="(/comics/[^"#?]+)"', h)) if c.count("-") > 0]
    print("AS browse", len(comics), comics[:3])
    if comics:
        th = fetch("https://asuracomic.net" + comics[0])
        chaps = list(dict.fromkeys(re.findall(r'href="(/comics/[^"]+/chapter/[^"]+)"', th)))
        print("AS chaps", len(chaps), chaps[:2])
        if chaps:
            ph = fetch("https://asuracomic.net" + chaps[0])
            imgs = [
                i
                for i in re.findall(r'(?:data-src|src)="(https?://[^"]+)"', ph)
                if "asura-images" in i or "/images/" in i
            ]
            if not imgs:
                imgs = [
                    i
                    for i in re.findall(r'(?:data-src|src)="(https?://[^"]+\.(?:jpg|jpeg|png|webp)[^"]*)"', ph, re.I)
                    if "logo" not in i and "icon" not in i and "cover" not in i
                ]
            print("AS imgs", len(imgs), imgs[:2], "markers", ph.count("cdn.asurascans"))

    h = fetch(
        "https://weebcentral.com/search/data?limit=12&offset=0&sort=Popularity&order=Descending"
        "&official=Any&anime=Any&adult=Any&display_mode=Full%20Display"
    )
    series = list(dict.fromkeys(re.findall(r'href="(https://weebcentral.com/series/[^"]+)"', h)))
    print("WC series", len(series), series[:2])
    if series:
        th = fetch(series[0])
        chaps = list(dict.fromkeys(re.findall(r'href="(https://weebcentral.com/chapters/[^"]+)"', th)))
        print("WC chaps", len(chaps), chaps[:2])
        if not chaps:
            # full chapter list endpoint?
            sid = series[0].rstrip("/").split("/")[-2]
            print("WC sid", sid)
            try:
                lst = fetch(f"https://weebcentral.com/series/{sid}/full-chapter-list")
                chaps = list(dict.fromkeys(re.findall(r'href="(https://weebcentral.com/chapters/[^"]+)"', lst)))
                print("WC full list", len(chaps), chaps[:2])
            except Exception as e:
                print("WC full err", e)
        if chaps:
            ph = fetch(chaps[0])
            imgs = [
                i
                for i in re.findall(r'(?:data-src|src)="(https?://[^"]+)"', ph)
                if any(x in i for x in [".jpg", ".jpeg", ".png", ".webp"])
            ]
            print("WC imgs", len(imgs), imgs[:2])

    h = fetch("https://rizzfables.com/series/?status=&type=&order=popular")
    items = list(dict.fromkeys(re.findall(r'href="(https://rizzfables.com/series/[^"]+/)"', h)))
    items = [u for u in items if u.rstrip("/").count("/") >= 4]
    print("RZ items", len(items), items[:3])
    if items:
        th = fetch(items[0])
        chaps = list(dict.fromkeys(re.findall(r'href="(https://rizzfables.com/[^"]+)"', th)))
        chaps = [c for c in chaps if "chapter" in c.lower() or re.search(r"/series/[^/]+/[^/]+/?$", c)]
        # themesia chapter links often /series/slug/chapter-xx/
        chaps2 = re.findall(r'id="chapterlist"[\s\S]*?</ul>', th)
        print("RZ chapterlist", bool(chaps2), "len", len(chaps2[0]) if chaps2 else 0)
        if chaps2:
            chaps = list(dict.fromkeys(re.findall(r'href="(https://rizzfables.com/[^"]+)"', chaps2[0])))
            print("RZ chaps", len(chaps), chaps[:2])
            if chaps:
                ph = fetch(chaps[0])
                # #readerarea img
                block = re.search(r'id="readerarea"[\s\S]*?</div>', ph)
                area = block.group(0) if block else ph
                imgs = re.findall(r'(?:data-src|src)="(https?://[^"]+)"', area)
                print("RZ imgs", len(imgs), imgs[:2])

    h = fetch("https://vortexscans.org/series?page=1")
    items = [u for u in dict.fromkeys(re.findall(r'href="(/series/[^"#?]+)"', h)) if u.count("/") == 2]
    print("VX items", len(items), items[:3])
    if items:
        th = fetch("https://vortexscans.org" + items[0])
        chaps = list(dict.fromkeys(re.findall(r'href="(/series/[^"]+/chapter[^"]*)"', th)))
        print("VX chaps", len(chaps), chaps[:2])
        if chaps:
            ph = fetch("https://vortexscans.org" + chaps[0])
            imgs = [
                i
                for i in re.findall(r'(?:data-src|src)="(https?://[^"]+)"', ph)
                if "logo" not in i.lower() and "cover" not in i.lower() and "icon" not in i.lower()
            ]
            print("VX imgs", len(imgs), imgs[:2])

    # ManhuaPlus ajax chapters
    h = fetch("https://manhuaplus.com/manga/martial-peak/", headers={"Referer": "https://manhuaplus.com/"})
    print("MP martial", len(h), "chap", h.count("wp-manga-chapter"))
    mid = re.search(r'data-post=["\']?(\d+)|mangaid["\s:=]+(\d+)|"manga_id"\s*:\s*(\d+)|post-(\d+)', h, re.I)
    print("MP id", mid.groups() if mid else None)
    i = h.find("wp-manga-chapter")
    print("MP snip", re.sub(r"\s+", " ", h[i : i + 500]) if i >= 0 else "none")


if __name__ == "__main__":
    main()
