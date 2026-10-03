import re
import ssl
import urllib.request

UA = "Mozilla/5.0"
CTX = ssl.create_default_context()


def fetch(url: str, ref: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Referer": ref})
    with urllib.request.urlopen(req, timeout=40, context=CTX) as resp:
        return resp.read().decode("utf-8", "ignore")


html = fetch("https://manhuaplus.com/manga/?m_orderby=views", "https://manhuaplus.com/")
links = [
    u
    for u in dict.fromkeys(re.findall(r'href="(https://manhuaplus.com/manga/[^"?]+/)"', html))
    if "/page/" not in u and "feed" not in u
]
print("title", links[0])
th = fetch(links[0], "https://manhuaplus.com/")
chaps = re.findall(r'class="wp-manga-chapter[^"]*"[\s\S]{0,240}?href="([^"]+)"', th)
print("chap", chaps[0], "n", len(chaps))
chap = chaps[0].rstrip("/") + "/?style=list"
ph = fetch(chap, links[0])
print("len", len(ph), "pagebreak", ph.count("page-break"), "reading", ph.count("reading-content"))
print("img tags", len(re.findall(r"<img", ph)))
srcs = re.findall(r'(?:data-src|src)="(https?://[^"]+)"', ph)[:5]
print(srcs)

# Extra madara candidates
for name, base in [
    ("mangagalaxy", "https://mangagalaxy.me"),
    ("nightscans", "https://night-scans.com"),
    ("resetscans", "https://reset-scans.com"),
    ("harimanga", "https://harimanga.net"),
    ("mangaclash", "https://toonclash.com"),
    ("1stkiss", "https://1stkissmanga.me"),
    ("manhuaus", "https://manhuaus.com"),
    ("webtoonxyz", "https://www.webtoon.xyz"),
    ("s2manga", "https://s2manga.com"),
    ("mangatx", "https://mangatx.com"),
    ("immortal", "https://isekaiscan.com"),
    ("mangaweebs", "https://mangaweebs.in"),
]:
    try:
        h = fetch(f"{base}/manga/?m_orderby=views", base + "/")
        print(name, "len", len(h), "items", h.count("page-item-detail"), "cf", "Just a moment" in h)
    except Exception as e:
        print(name, "ERR", e)
