#!/usr/bin/env python3
import re, urllib.request, http.cookiejar

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
jar = http.cookiejar.CookieJar()
op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))

def req(url):
    r = urllib.request.Request(url, headers={"User-Agent": UA})
    with op.open(r, timeout=25) as resp:
        return resp.read().decode("utf-8", "replace")

html = req("https://mangabuff.ru/manga/odinochnyi-igrok-bagoyuzer/3/188")
print("title", re.search(r"<title>([^<]+)", html).group(1)[:120])
# reader images
for pat in [
    r'data-src="([^"]+)"',
    r'reader__[^"]*src="([^"]+)"',
    r'class="[^"]*page[^"]*"[^>]+src="([^"]+)"',
    r'/img/manga/chapters/[^"\s]+',
    r'https://[^"\s]+(?:jpg|png|webp)',
]:
    found = re.findall(pat, html)
    print(pat, "->", len(found), found[:3])
# snippet around pages
idx = html.find("reader")
print("reader idx", idx)
if idx > 0:
    print(html[idx:idx+800])
