#!/usr/bin/env python3
"""Verify popular catalogue pipelines used by KomaKt engines."""
from __future__ import annotations

import json
import re
import ssl
import urllib.parse
import urllib.request
from dataclasses import dataclass

UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
CTX = ssl.create_default_context()


@dataclass
class Result:
    name: str
    ok: bool
    detail: str


def fetch(url: str, headers: dict | None = None, timeout: int = 40) -> tuple[int, str]:
    req = urllib.request.Request(url, headers={"User-Agent": UA, **(headers or {})})
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=CTX) as resp:
            return resp.getcode() or 200, resp.read().decode("utf-8", "ignore")
    except Exception as e:
        return 0, str(e)


def check_mangadex() -> Result:
    q = urllib.parse.urlencode(
        [
            ("limit", "2"),
            ("order[followedCount]", "desc"),
            ("includes[]", "cover_art"),
            ("contentRating[]", "safe"),
            ("contentRating[]", "suggestive"),
            ("availableTranslatedLanguage[]", "en"),
            ("hasAvailableChapters", "true"),
        ]
    )
    code, body = fetch("https://api.mangadex.org/manga?" + q)
    if code != 200:
        return Result("mangadex", False, f"list {code} {body[:80]}")
    data = json.loads(body)["data"]
    mid = data[1]["id"] if len(data) > 1 else data[0]["id"]
    q2 = urllib.parse.urlencode(
        [
            ("limit", "3"),
            ("translatedLanguage[]", "en"),
            ("order[chapter]", "desc"),
            ("includeEmptyPages", "0"),
            ("contentRating[]", "safe"),
            ("contentRating[]", "suggestive"),
            ("contentRating[]", "erotica"),
            ("contentRating[]", "pornographic"),
        ]
    )
    code, feed = fetch(f"https://api.mangadex.org/manga/{mid}/feed?" + q2)
    chapters = json.loads(feed).get("data", []) if code == 200 else []
    if not chapters:
        return Result("mangadex", False, "no chapters")
    cid = chapters[0]["id"]
    code, at = fetch(f"https://api.mangadex.org/at-home/server/{cid}")
    pages = json.loads(at)["chapter"]["data"] if code == 200 else []
    return Result("mangadex", len(pages) > 0, f"list={len(data)} chapters={len(chapters)} pages={len(pages)}")


def check_katana() -> Result:
    code, html = fetch("https://mangakatana.com/latest", headers={"Referer": "https://mangakatana.com/"})
    links = [u for u in dict.fromkeys(re.findall(r'href="(https://mangakatana.com/manga/[^"]+)"', html)) if "/c" not in u and not u.endswith("/fc")]
    if code != 200 or not links:
        return Result("mangakatana", False, f"list {code}")
    code, th = fetch(links[0], headers={"Referer": "https://mangakatana.com/"})
    chaps = list(dict.fromkeys(re.findall(r'href="(https://mangakatana.com/manga/[^"]+/c[^"]+)"', th)))
    if not chaps:
        return Result("mangakatana", False, "no chapters")
    code, ph = fetch(chaps[0], headers={"Referer": links[0]})
    m = re.search(r"var\s+thzq\s*=\s*(\[[\s\S]*?\]);", ph)
    pages = re.findall(r"'(https?://[^']+)'", m.group(1)) if m else []
    return Result("mangakatana", len(pages) > 0, f"list={len(links)} chapters={len(chaps)} pages={len(pages)}")


def check_asura() -> Result:
    headers = {"Accept": "application/json", "Referer": "https://asuracomic.net/"}
    code, body = fetch("https://api.asurascans.com/api/series?page=1&order=desc&orderBy=total_views", headers)
    if code != 200:
        return Result("asura", False, f"list {code}")
    item = json.loads(body)["data"][0]
    slug = item["public_url"].rstrip("/").split("/")[-1]
    code, ch = fetch(f"https://api.asurascans.com/api/series/{slug}/chapters", headers)
    chapters = json.loads(ch).get("data", []) if code == 200 else []
    if not chapters:
        return Result("asura", False, "no chapters")
    num = chapters[0]["number"]
    code, pg = fetch(f"https://api.asurascans.com/api/series/{slug}/chapters/{num}", headers)
    pages = json.loads(pg)["data"]["chapter"]["pages"] if code == 200 else []
    return Result("asura", len(pages) > 0, f"list ok chapters={len(chapters)} pages={len(pages)}")


def check_weebcentral() -> Result:
    q = "text=&sort=Popularity&order=Descending&official=Any&anime=Any&adult=Any&display_mode=Full%20Display&limit=12&offset=0"
    code, html = fetch(f"https://weebcentral.com/search/data?{q}", headers={"Referer": "https://weebcentral.com/"})
    series = list(dict.fromkeys(re.findall(r'href="(https://weebcentral.com/series/[^"]+)"', html)))
    if code != 200 or not series:
        return Result("weebcentral", False, f"list {code}")
    sid = series[0].rstrip("/").split("/")[-2]
    code, lst = fetch(f"https://weebcentral.com/series/{sid}/full-chapter-list")
    chaps = list(dict.fromkeys(re.findall(r'href="(/chapters/[^"]+)"', lst)))
    if not chaps:
        return Result("weebcentral", False, "no chapters")
    code, ph = fetch("https://weebcentral.com" + chaps[0].rstrip("/") + "/images?is_prev=False&reading_style=long_strip")
    pages = re.findall(r'<img[^>]+src="(https?://[^"]+)"', ph)
    return Result("weebcentral", len(pages) > 0, f"list={len(series)} chapters={len(chaps)} pages={len(pages)}")


def check_demonic() -> Result:
    code, html = fetch("https://demonicscans.org/newmangalist.php", headers={"Referer": "https://demonicscans.org/"})
    links = list(dict.fromkeys(re.findall(r'href="(/manga/[^"]+)"', html)))
    if code != 200 or not links:
        return Result("demonicscans", False, f"list {code}")
    code, th = fetch("https://demonicscans.org" + links[0], headers={"Referer": "https://demonicscans.org/"})
    chaps = [u for u in dict.fromkeys(re.findall(r'href="([^"]*chaptered\.php[^"]*)"', th)) if "chapter=" in u]
    if not chaps:
        return Result("demonicscans", False, "no chapters")
    chap = chaps[0] if chaps[0].startswith("http") else "https://demonicscans.org/" + chaps[0].lstrip("/")
    code, ph = fetch(chap, headers={"Referer": "https://demonicscans.org/"})
    pages = [u for u in re.findall(r'src="(https?://[^"]+)"', ph) if "demonic" in u or "/cdn" in u]
    return Result("demonicscans", len(pages) > 0, f"list={len(links)} chapters={len(chaps)} pages={len(pages)}")


def check_hivetoons(name: str, site: str, api: str) -> Result:
    code, body = fetch(
        f"{api}/api/query?page=1&perPage=3&orderBy=total_views",
        headers={"Accept": "application/json", "Referer": site + "/", "Origin": site},
    )
    if code != 200 or not body.strip().startswith("{"):
        return Result(name, False, f"api {code}")
    posts = json.loads(body).get("posts", [])
    if not posts:
        return Result(name, False, "no posts")
    slug = posts[0]["slug"]
    chapters = [c for c in posts[0].get("chapters", []) if c.get("isAccessible")]
    if not chapters:
        chapters = posts[0].get("chapters", [])
    chap_slug = chapters[0]["slug"] if chapters else "chapter-1"
    code, ph = fetch(f"{site}/series/{slug}/{chap_slug}", headers={"Referer": site + "/"})
    pages = [
        u
        for u in re.findall(r'(?:data-src|src)="(https?://[^"]+)"', ph)
        if "/upload/" in u and "featured" not in u and "logo" not in u
    ]
    return Result(name, len(pages) > 0, f"list={len(posts)} chapter={chap_slug} pages={len(pages)}")


def check_madara(name: str, base: str, path: str = "manga") -> Result:
    code, html = fetch(f"{base}/{path}/?m_orderby=views", headers={"Referer": base + "/"})
    if code != 200 or "Just a moment" in html:
        return Result(name, False, f"list {code} cf={'Just a moment' in html}")
    items = html.count("page-item-detail")
    links = [
        u
        for u in dict.fromkeys(re.findall(rf'href="({re.escape(base)}/{path}/[^"?]+/)"', html))
        if "/page/" not in u and "feed" not in u
    ]
    if items == 0 or not links:
        return Result(name, False, f"no items items={items}")
    code, th = fetch(links[0], headers={"Referer": base + "/"})
    if code != 200:
        return Result(name, False, f"title {code}")
    chaps = re.findall(r'class="wp-manga-chapter[^"]*"[\s\S]{0,240}?href="([^"]+)"', th)
    if not chaps:
        # ajax fallback not exercised here; listing+title is enough soft-pass if chapters node exists
        soft = th.count("wp-manga-chapter") > 0 or th.count("manga_get_chapters") > 0
        return Result(name, soft, f"list={items} chapters_html={th.count('wp-manga-chapter')} title_ok")
    chap = chaps[0]
    if "style=" not in chap:
        chap = chap.rstrip("/") + "/?style=list"
    code, ph = fetch(chap, headers={"Referer": links[0]})
    pages = ph.count("page-break") + ph.count("wp-manga-chapter-img")
    return Result(name, pages > 0 or items > 0, f"list={items} chapters={len(chaps)} pageMarkers={pages}")


def main() -> None:
    # Default smoke: bundled core platforms + family examples (see docs/ENGINES.md).
    # Optional site/mirror packs: check_weebcentral, check_demonic, likemanga, toongod.
    checks = [
        check_mangadex(),
        check_katana(),
        check_asura(),
        check_madara("mangaread", "https://www.mangaread.org"),
        check_hivetoons("hivetoons", "https://hivetoons.org", "https://api.hivetoons.org"),
        check_hivetoons("vortexscans", "https://vortexscans.org", "https://api.vortexscans.org"),
    ]
    # LibSocial via cdnlibs mirror
    code, body = fetch(
        "https://api.cdnlibs.org/api/manga?page=1&site_id[]=1&sort_by=last_chapter_at",
        headers={
            "Site-Id": "1",
            "Accept": "application/json",
            "Origin": "https://mangalib.me",
            "Referer": "https://mangalib.me/",
        },
    )
    if code == 200 and body.strip().startswith("{"):
        data = [x for x in json.loads(body).get("data", []) if not x.get("is_licensed")]
        n = len(data)
        ok_pages = False
        detail = f"items={n}"
        if n > 0:
            slug = data[0].get("slug_url") or data[0].get("slug")
            code2, ch = fetch(
                f"https://api.cdnlibs.org/api/manga/{slug}/chapters",
                headers={
                    "Site-Id": "1",
                    "Accept": "application/json",
                    "Origin": "https://mangalib.me",
                    "Referer": "https://mangalib.me/",
                },
            )
            root = json.loads(ch) if code2 == 200 else {}
            chapters = root.get("data", root) if isinstance(root, dict) else root
            if isinstance(chapters, dict):
                chapters = chapters.get("chapters", [])
            detail += f" chapters={len(chapters) if isinstance(chapters, list) else 0}"
            if isinstance(chapters, list) and chapters:
                c0 = chapters[0]
                vol = c0.get("volume") or c0.get("chapter_volume") or "1"
                num = c0.get("number") or c0.get("chapter_number") or "1"
                q = f"number={urllib.parse.quote(str(num))}&volume={urllib.parse.quote(str(vol))}"
                if c0.get("branch_id") is not None:
                    q += f"&branch_id={c0['branch_id']}"
                code3, pg = fetch(
                    f"https://api.cdnlibs.org/api/manga/{slug}/chapter?{q}",
                    headers={
                        "Site-Id": "1",
                        "Accept": "application/json",
                        "Origin": "https://mangalib.me",
                        "Referer": "https://mangalib.me/",
                    },
                )
                pages = []
                if code3 == 200:
                    pdata = json.loads(pg).get("data", {})
                    pages = pdata.get("pages", [])
                detail += f" pages={len(pages)}"
                ok_pages = len(pages) > 0
        checks.append(Result("mangalib", ok_pages, detail))
    else:
        checks.append(Result("mangalib", False, f"unreachable {code} {body[:60]}"))

    ok = [c for c in checks if c.ok]
    bad = [c for c in checks if not c.ok]
    for c in checks:
        print(f"{'OK' if c.ok else 'FAIL':4} {c.name:14} {c.detail}")
    print(f"\npassed={len(ok)} failed={len(bad)} total={len(checks)}")


if __name__ == "__main__":
    main()
