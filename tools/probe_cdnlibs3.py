#!/usr/bin/env python3
import json
import ssl
import urllib.parse
import urllib.request

CTX = ssl.create_default_context()
API = "https://api.cdnlibs.org/api"
H = {
    "User-Agent": "Mozilla/5.0",
    "Accept": "application/json",
    "Site-Id": "1",
    "Origin": "https://mangalib.me",
    "Referer": "https://mangalib.me/",
}


def fetch(url: str) -> str:
    req = urllib.request.Request(url, headers=H)
    with urllib.request.urlopen(req, timeout=30, context=CTX) as resp:
        return resp.read().decode()


raw = json.loads(fetch(f"{API}/manga?page=1&site_id[]=1&sort_by=last_chapter_at"))
for item in raw["data"][:15]:
    if item.get("is_licensed"):
        continue
    slug = item.get("slug_url") or item.get("slug")
    ch = json.loads(fetch(f"{API}/manga/{slug}/chapters"))
    data = ch.get("data", [])
    print("try", slug, "licensed", item.get("is_licensed"), "chapters", len(data) if isinstance(data, list) else data)
    if isinstance(data, list) and data:
        c0 = data[0]
        print(" chap0", c0)
        vol = c0.get("volume") or "1"
        num = c0.get("number") or "1"
        q = f"number={urllib.parse.quote(str(num))}&volume={urllib.parse.quote(str(vol))}"
        if c0.get("branch_id") is not None:
            q += f"&branch_id={c0['branch_id']}"
        pages = json.loads(fetch(f"{API}/manga/{slug}/chapter?{q}"))
        pdata = pages.get("data", {})
        print(" pages", len(pdata.get("pages", [])), "keys", pdata.keys())
        break
else:
    print("none found")
