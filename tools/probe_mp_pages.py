import re
import ssl
import urllib.request

UA = "Mozilla/5.0"
CTX = ssl.create_default_context()
req = urllib.request.Request(
    "https://manhuaplus.com/manga/martial-peak/chapter-3862/?style=list",
    headers={"User-Agent": UA, "Referer": "https://manhuaplus.com/manga/martial-peak/"},
)
with urllib.request.urlopen(req, timeout=40, context=CTX) as resp:
    ph = resp.read().decode("utf-8", "ignore")

for pat in [
    "page-break",
    "wp-manga-chapter-img",
    "reading-content",
    "data-src",
    "cdn",
    ".jpg",
    ".webp",
    "chapter_image",
    "image-text",
    "protected",
]:
    print(pat, ph.count(pat))

# find interesting blobs
for m in re.finditer(r"(https?://[^\"'\s]+\.(?:jpg|jpeg|png|webp)[^\"'\s]*)", ph, re.I):
    u = m.group(1)
    if "logo" not in u and "icon" not in u and "avatar" not in u:
        print("URL", u[:160])
        break
else:
    print("no direct image urls")

idx = ph.find("reading-content")
print("snip", re.sub(r"\s+", " ", ph[idx : idx + 500]) if idx >= 0 else "none")
# script with chapter images
for m in re.finditer(r"chapter_preloaded_images\s*=\s*(\[[\s\S]*?\]);", ph):
    print("preloaded", m.group(1)[:200])
for m in re.finditer(r"var\s+images\s*=\s*(\[[\s\S]*?\]);", ph):
    print("images var", m.group(1)[:200])
