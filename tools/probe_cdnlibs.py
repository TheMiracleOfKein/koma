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


def fetch(url: str, site_id: str = "1") -> str:
    headers = dict(H)
    headers["Site-Id"] = site_id
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=30, context=CTX) as resp:
        return resp.read().decode()


raw = json.loads(fetch(f"{API}/manga?page=1&site_id[]=1&sort_by=rating_score"))
item = raw["data"][0]
slug = item.get("slug_url") or item.get("slug")
print("slug", slug, item.get("rus_name"))
ch = json.loads(fetch(f"{API}/manga/{slug}/chapters"))
print("chapters type", type(ch), list(ch.keys()) if isinstance(ch, dict) else len(ch))
data = ch.get("data", ch) if isinstance(ch, dict) else ch
if isinstance(data, dict):
    data = data.get("chapters") or data
print("n", len(data) if isinstance(data, list) else data)
chap = data[0] if isinstance(data, list) else None
print("chap0", chap)
if chap:
    vol = chap.get("volume") or chap.get("chapter_volume") or "1"
    num = chap.get("number") or chap.get("chapter_number") or "1"
    bid = chap.get("branch_id")
    q = f"number={urllib.parse.quote(str(num))}&volume={urllib.parse.quote(str(vol))}"
    if bid:
        q += f"&branch_id={bid}"
    pages = json.loads(fetch(f"{API}/manga/{slug}/chapter?{q}"))
    pdata = pages.get("data", pages)
    plist = pdata.get("pages", [])
    print("pages", len(plist), plist[:1])
