#!/usr/bin/env python3
import json, re, urllib.request, http.cookiejar, urllib.parse

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
jar = http.cookiejar.CookieJar()
op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))

def req(url, data=None, headers=None):
    h = {"User-Agent": UA}
    if headers:
        h.update(headers)
    r = urllib.request.Request(url, data=data, headers=h)
    with op.open(r, timeout=25) as resp:
        return resp.read()

slug = "odinochnyi-igrok-bagoyuzer"
html = req(f"https://mangabuff.ru/manga/{slug}").decode("utf-8", "replace")
csrf = re.search(r'csrf-token" content="([^"]+)"', html).group(1)
mid_m = re.search(r'class="manga"[^>]*data-id="(\d+)"', html) or re.search(r'data-id="(\d+)"', html)
mid = mid_m.group(1)
print("csrf", csrf, "mid", mid)
chs = re.findall(r'href="(/manga/[^"]+/[^"]+)"', html)
print("inline chapters", len(chs), chs[:5])
# chapters__item
items = re.findall(r'chapters__item[^>]*>.*?href="([^"]+)"', html, re.S)
print("item hrefs", items[:5])
data = urllib.parse.urlencode({"manga_id": mid}).encode()
try:
    ch = req(
        "https://mangabuff.ru/chapters/load",
        data=data,
        headers={
            "X-Requested-With": "XMLHttpRequest",
            "Accept": "application/json",
            "Content-Type": "application/x-www-form-urlencoded",
            "X-CSRF-TOKEN": csrf,
            "Referer": f"https://mangabuff.ru/manga/{slug}",
            "Origin": "https://mangabuff.ru",
        },
    )
    print("ch status ok", ch[:1000])
except Exception as e:
    print("ch err", e)

if chs:
    ph = req("https://mangabuff.ru" + chs[0]).decode("utf-8", "replace")
    print("chapter title", re.search(r"<title>([^<]+)", ph).group(1)[:100])
    imgs = re.findall(r'(?:src|data-src)="([^"]+)"', ph)
    print("imgs", [i for i in imgs if any(x in i for x in ("chapter", "page", "/img/", "storage", "upload"))][:10])
