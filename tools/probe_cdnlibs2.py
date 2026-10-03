#!/usr/bin/env python3
import json
import ssl
import urllib.request

CTX = ssl.create_default_context()
API = "https://api.cdnlibs.org/api"
H = {
    "User-Agent": "Mozilla/5.0",
    "Accept": "application/json",
    "Site-Id": "1",
    "Origin": "https://mangalib.me",
    "Referer": "https://mangalib.me/",
    "Client-Time-Zone": "Europe/Moscow",
}


def fetch(url: str) -> str:
    req = urllib.request.Request(url, headers=H)
    with urllib.request.urlopen(req, timeout=30, context=CTX) as resp:
        return resp.read().decode()


raw = json.loads(fetch(f"{API}/manga?page=1&site_id[]=1&sort_by=rating_score"))
item = raw["data"][0]
print("item keys", item.keys())
slug = item.get("slug_url") or item.get("slug")
print("slug", slug)
# detail
detail = json.loads(fetch(f"{API}/manga/{slug}"))
print("detail keys", detail.keys(), detail.get("data", detail).keys() if isinstance(detail.get("data", detail), dict) else None)
for path in [
    f"/manga/{slug}/chapters",
    f"/manga/{slug}/chapter-list",
    f"/chapters?manga_id={item['id']}",
    f"/manga/{item['id']}/chapters",
]:
    try:
        body = fetch(API + path)
        j = json.loads(body)
        data = j.get("data", j)
        if isinstance(data, dict):
            print(path, "dict keys", data.keys(), "chapters", type(data.get("chapters")))
            if isinstance(data.get("chapters"), list):
                print("  n", len(data["chapters"]), data["chapters"][:1])
        elif isinstance(data, list):
            print(path, "list", len(data), data[:1])
        else:
            print(path, type(data), str(data)[:120])
    except Exception as e:
        print(path, e)
