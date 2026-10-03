#!/usr/bin/env python3
from __future__ import annotations

import json
import ssl
import urllib.request

UA = "Mozilla/5.0"
CTX = ssl.create_default_context()
API = "https://api.hivetoons.org"


def fetch(url: str) -> str:
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": UA,
            "Accept": "application/json",
            "Referer": "https://hivetoons.org/",
            "Origin": "https://hivetoons.org",
        },
    )
    with urllib.request.urlopen(req, timeout=30, context=CTX) as resp:
        return resp.read().decode("utf-8", "ignore")


def main() -> None:
    q = json.loads(fetch(f"{API}/api/query?page=1&perPage=2&orderBy=total_views"))
    post = q["posts"][0]
    print("post keys", list(post.keys()))
    slug = post["slug"]
    print("slug", slug, post.get("postTitle"))
    for path in [
        f"/api/post?postSlug={slug}",
        f"/api/post?slug={slug}",
        f"/api/chapters?postSlug={slug}",
        f"/api/chapter?postSlug={slug}&chapterSlug=chapter-1",
        f"/api/query?postSlug={slug}",
        f"/api/series/{slug}",
        f"/api/comics/{slug}",
    ]:
        try:
            body = fetch(API + path)
            print("OK", path, body[:180].replace("\n", " "))
        except Exception as e:
            print("NO", path, e)

    # HTML chapter already works; if API thin, HTML engine is fine
    print("done")


if __name__ == "__main__":
    main()
