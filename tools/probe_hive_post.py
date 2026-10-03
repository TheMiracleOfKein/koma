#!/usr/bin/env python3
import json
import ssl
import urllib.request

CTX = ssl.create_default_context()
API = "https://api.hivetoons.org"


def fetch(url: str) -> str:
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": "Mozilla/5.0",
            "Accept": "application/json",
            "Referer": "https://hivetoons.org/",
            "Origin": "https://hivetoons.org",
        },
    )
    with urllib.request.urlopen(req, timeout=30, context=CTX) as resp:
        return resp.read().decode()


post = json.loads(fetch(f"{API}/api/post?postSlug=study-group"))
print(post.keys())
print("post keys", post["post"].keys())
ch = post.get("post", {}).get("chapters") or post.get("chapters")
print("chapters", type(ch), (len(ch) if isinstance(ch, list) else ch))
if isinstance(ch, list) and ch:
    print("chap0", ch[0])

q = json.loads(fetch(f"{API}/api/query?page=1&perPage=1&orderBy=total_views"))
print("query chapters", q["posts"][0].get("chapters"))

# chapter pages API guesses
for u in [
    f"{API}/api/chapter?postSlug=study-group&number=342",
    f"{API}/api/pages?postSlug=study-group&chapterSlug=chapter-342",
    f"{API}/api/post/chapter?postSlug=study-group&chapterSlug=chapter-342",
]:
    try:
        print("OK", u, fetch(u)[:150].replace("\n", " "))
    except Exception as e:
        print("NO", e)
