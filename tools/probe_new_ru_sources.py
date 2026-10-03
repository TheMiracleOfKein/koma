#!/usr/bin/env python3
"""Probe MangaBuff / Remanga / InkStory quickly."""
import json, re, urllib.request, http.cookiejar

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

def get(url, jar=None, headers=None, data=None):
    h = {"User-Agent": UA, "Accept": "*/*"}
    if headers:
        h.update(headers)
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar or http.cookiejar.CookieJar()))
    req = urllib.request.Request(url, data=data, headers=h)
    with opener.open(req, timeout=25) as r:
        body = r.read()
        return r.status, r.geturl(), r.headers.get("content-type"), body

jar = http.cookiejar.CookieJar()
st, url, ct, body = get("https://mangabuff.ru/", jar)
html = body.decode("utf-8", "replace")
csrf = re.search(r'csrf-token" content="([^"]+)"', html)
print("home", st, "csrf", csrf.group(1) if csrf else None)

st, _, _, sug = get(
    "https://mangabuff.ru/search/suggestions?q=solo",
    jar,
    {"X-Requested-With": "XMLHttpRequest", "Accept": "application/json"},
)
items = json.loads(sug.decode())
print("suggestions", len(items), items[0] if items else None)
slug = items[0]["slug"]
mid = items[0]["id"]
for u in [
    f"https://mangabuff.ru/manga/{slug}",
    f"https://mangabuff.ru/manga/{mid}-{slug}",
    f"https://mangabuff.ru/manga/{mid}",
]:
    try:
        st, final, _, b = get(u, jar)
        print("try", u, "->", st, final, "len", len(b), "title", re.search(r"<title>([^<]+)", b.decode("utf-8","replace")).group(1)[:60])
    except Exception as e:
        print("try", u, "ERR", e)

# chapters load
csrf_tok = csrf.group(1) if csrf else ""
data = f"manga_id={mid}".encode()
try:
    st, _, _, ch = get(
        "https://mangabuff.ru/chapters/load",
        jar,
        {
            "X-Requested-With": "XMLHttpRequest",
            "Accept": "application/json",
            "Content-Type": "application/x-www-form-urlencoded",
            "X-CSRF-TOKEN": csrf_tok,
            "Referer": f"https://mangabuff.ru/manga/{slug}",
        },
        data=data,
    )
    print("chapters", st, ch[:500])
except Exception as e:
    print("chapters ERR", e)

# remanga img with cookie
rjar = http.cookiejar.CookieJar()
get("https://api.remanga.org/api/titles/?count=1", rjar, {"Referer": "https://remanga.org/"})
img = "https://img.reimg.org/images/255653/8cfcdbf2c5fa2f036000644cf5717169/184571f7fad6358b36f5dc07d5528f83.webp"
try:
    st, _, _, b = get(img, rjar, {"Referer": "https://remanga.org/", "Accept": "image/*"})
    print("remanga img", st, len(b))
except Exception as e:
    print("remanga img ERR", e)
