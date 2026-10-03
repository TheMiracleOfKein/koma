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
    ph = fetch("https://asuracomic.net/comics/the-genius-professor-wants-to-take-it-easy-53fc8424/chapter/17")
    imgs = list(dict.fromkeys(re.findall(r'src="(https://cdn\.asurascans\.com/[^"]+)"', ph)))
    pages = [i for i in imgs if "/covers/" not in i]
    print("AS pages", len(pages), pages[:3])

    ph = fetch("https://vortexscans.org/series/the-wind-mage/chapter-26")
    imgs = list(
        dict.fromkeys(re.findall(r'(?:data-src|src)="(https://storage\.vortexscans\.org/[^"]+)"', ph))
    )
    pages = [i for i in imgs if "/chapter" in i or "/upload/chapters" in i or "/pages" in i or re.search(r"/\d+\.(jpg|png|webp)", i)]
    if len(pages) < 3:
        pages = [i for i in imgs if "featured" not in i and "cover" not in i]
    print("VX pages", len(pages), pages[:3], "all", imgs[:5])

    lst = fetch("https://weebcentral.com/series/01J76XY7E9FNDZ1DBBM6PBJPFK/full-chapter-list")
    chaps = list(dict.fromkeys(re.findall(r'href="(https://weebcentral.com/chapters/[^"]+)"', lst)))
    print("WC full", len(lst), "chaps", len(chaps), chaps[:2], "snip", re.sub(r"\s+", " ", lst[:300]))
    if chaps:
        img_url = chaps[0].rstrip("/") + "/images?is_prev=False&reading_style=long_strip"
        ph = fetch(img_url)
        imgs = re.findall(r'<img[^>]+src="(https?://[^"]+)"', ph)
        print("WC imgs", len(imgs), imgs[:2])

    for u in [
        "https://rizzfables.com/",
        "https://rizzfables.com/series",
        "https://rizzfables.com/manga/?order=popular",
        "https://demonicscans.org/",
        "https://demonicscans.org/manga.php?list",
    ]:
        try:
            h = fetch(u)
            print(
                "SITE",
                u,
                len(h),
                "bsx",
                h.count("bsx"),
                "page-item",
                h.count("page-item-detail"),
                "manga.php",
                h.count("manga.php"),
            )
        except Exception as e:
            print("SITE", u, e)

    # Madara clones that worked
    for name, base, path in [
        ("mangaread", "https://www.mangaread.org", "manga"),
        ("manhuaplus", "https://manhuaplus.com", "manga"),
    ]:
        try:
            h = fetch(f"{base}/{path}/?m_orderby=views", headers={"Referer": base + "/"})
            print(name, "list", len(h), "items", h.count("page-item-detail"))
        except Exception as e:
            print(name, e)


if __name__ == "__main__":
    main()
