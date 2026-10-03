import json
import ssl
import urllib.request

CTX = ssl.create_default_context()


def fetch(u: str) -> str:
    r = urllib.request.Request(
        u,
        headers={
            "User-Agent": "Mozilla/5.0",
            "Accept": "application/json",
            "Referer": "https://asuracomic.net/",
        },
    )
    with urllib.request.urlopen(r, timeout=30, context=CTX) as resp:
        return resp.read().decode()


raw = fetch("https://api.asurascans.com/api/series?page=1&order=desc&orderBy=total_views")
item = json.loads(raw)["data"][0]
print("keys", item.keys())
print({k: item.get(k) for k in item if k in ["id", "slug", "public_url", "title", "cover", "cover_url"]})
slug = item["slug"]
for s in [slug, f"{slug}-53fc8424", f"{slug}-{item['id']}"]:
    try:
        ch = fetch(f"https://api.asurascans.com/api/series/{s}/chapters")
        print("chapters OK", s, len(json.loads(ch).get("data", [])))
        num = json.loads(ch)["data"][0]["number"]
        pages = fetch(f"https://api.asurascans.com/api/series/{s}/chapters/{num}")
        n = len(json.loads(pages)["data"]["chapter"]["pages"])
        print(" pages", n)
    except Exception as e:
        print("chapters NO", s, e)
