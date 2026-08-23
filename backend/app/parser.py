import html
import re
from datetime import date, datetime
from urllib.parse import parse_qs, urljoin, urlparse

from bs4 import BeautifulSoup, Tag

from .models import Category, ParsedArticle

try:
    from pykakasi import kakasi
except ImportError:  # Minimal deployments keep parsing, but skip generated ruby.
    kakasi = None

ORIGINAL = re.compile(r"[\[【]?\s*(?:일본어\s*원문|原文|日本語)[\]】]?", re.I)
TRANSLATION = re.compile(r"[\[【]?\s*(?:한국어\s*번역문?|번역|韓国語)[\]】]?", re.I)
DATE_PATTERNS = (r"(20\d{2})[.\-/년\s]+(\d{1,2})[.\-/월\s]+(\d{1,2})", r"(20\d{2})(\d{2})(\d{2})")


def add_furigana(paragraphs: list[str]) -> str:
    converter = kakasi() if kakasi else None
    rendered: list[str] = []
    for paragraph in paragraphs:
        if converter is None:
            rendered.append(html.escape(paragraph))
            continue
        parts: list[str] = []
        for token in converter.convert(paragraph):
            original, reading = token["orig"], token["hira"]
            escaped = html.escape(original)
            if re.search(r"[一-龯々]", original) and reading and reading != original:
                parts.append(f"<ruby>{escaped}<rt>{html.escape(reading)}</rt></ruby>")
            else:
                parts.append(escaped)
        rendered.append("".join(parts))
    return "".join(f"<p>{line}</p>" for line in rendered)


def normalize_post_url(href: str, base: str) -> str | None:
    absolute = urljoin(base, href)
    parsed = urlparse(absolute)
    query = parse_qs(parsed.query)
    log_no = (query.get("logNo") or [None])[0]
    blog_id = (query.get("blogId") or [None])[0]
    match = re.search(r"blog\.naver\.com/([^/]+)/([0-9]+)", absolute)
    if match:
        blog_id, log_no = match.groups()
    if not log_no:
        return None
    return f"https://m.blog.naver.com/{blog_id or 'bioluck'}/{log_no}"


def parse_post_links(html_text: str, base: str) -> list[str]:
    soup = BeautifulSoup(html_text, "html.parser")
    result: list[str] = []
    for tag in soup.select("a[href]"):
        url = normalize_post_url(tag.get("href", ""), base)
        if url and url not in result:
            result.append(url)
    # Naver's PostTitleListAsync endpoint returns JSON whose post IDs are not links.
    blog_id = (parse_qs(urlparse(base).query).get("blogId") or ["bioluck"])[0]
    for log_no in re.findall(r'["\']logNo["\']\s*:\s*["\']?(\d+)', html_text):
        url = f"https://m.blog.naver.com/{blog_id}/{log_no}"
        if url not in result:
            result.append(url)
    return result


def category_from(*values: str) -> Category | None:
    value = " ".join(values)
    if re.search(r"天声人語|천성인어", value, re.I):
        return Category.TENSEIJINGO
    if re.search(r"春秋|춘추", value, re.I):
        return Category.SHUNJU
    return None


def parse_date(*values: str) -> date:
    value = " ".join(values)
    for pattern in DATE_PATTERNS:
        match = re.search(pattern, value)
        if match:
            try:
                return date(*(int(part) for part in match.groups()))
            except ValueError:
                pass
    return datetime.now().date()


def _content_root(soup: BeautifulSoup) -> Tag:
    return (soup.select_one(".se-main-container") or soup.select_one(".post-view")
            or soup.select_one("#postViewArea") or soup.body or soup)


def _split_sections(root: Tag) -> tuple[str, str, str]:
    lines = [line.strip() for line in root.get_text("\n", strip=True).splitlines() if line.strip()]
    mode = None
    japanese: list[str] = []
    korean: list[str] = []
    for line in lines:
        if ORIGINAL.fullmatch(line):
            mode = "ja"
            continue
        if TRANSLATION.fullmatch(line):
            mode = "ko"
            continue
        if mode == "ja":
            japanese.append(line)
        elif mode == "ko":
            korean.append(line)

    # Preserve publisher-provided ruby if it is isolated in an original section.
    ruby_html = ""
    safe_html = ""
    marker = next((t for t in root.find_all(string=ORIGINAL) if ORIGINAL.fullmatch(t.strip())), None)
    if marker:
        fragments: list[str] = []
        node = marker.parent
        while node := node.find_next_sibling():
            text = node.get_text(" ", strip=True)
            if TRANSLATION.fullmatch(text):
                break
            fragments.append(str(node))
        if fragments and any("<ruby" in fragment.lower() for fragment in fragments):
            ruby_html = "".join(fragments)

    ja_text = "\n".join(japanese).strip()
    ko_text = "\n".join(korean).strip()
    if not ja_text:
        # Current SmartEditor posts often alternate a Japanese paragraph and its
        # Korean translation without section headers. Classify substantial lines
        # by script and stop before attached-link previews.
        japanese, korean = [], []
        for line in lines:
            if line.startswith(("http://", "https://")):
                break
            kana = len(re.findall(r"[ぁ-ゖァ-ヺ]", line))
            hangul = len(re.findall(r"[가-힣]", line))
            if kana >= 3 and kana > hangul:
                japanese.append(line.lstrip("▼").strip())
            elif hangul >= 3 and hangul > kana:
                korean.append(line.lstrip("▼").strip())
        ja_text, ko_text = "\n".join(japanese), "\n".join(korean)
        safe_html = add_furigana(japanese)
    if not ja_text:
        raise ValueError("일본어 원문 표식 또는 본문을 찾지 못했습니다")
    safe_html = ruby_html or safe_html or add_furigana(japanese)
    return safe_html, ja_text, ko_text


def parse_article(html_text: str, source_url: str) -> ParsedArticle:
    soup = BeautifulSoup(html_text, "html.parser")
    title_tag = soup.select_one("meta[property='og:title']")
    title = (title_tag.get("content", "") if title_tag else "") or (
        soup.select_one(".se-title-text, .pcol1, h1").get_text(" ", strip=True)
        if soup.select_one(".se-title-text, .pcol1, h1") else "제목 없음"
    )
    root = _content_root(soup)
    thumbnail_url = ""
    og_image = soup.select_one("meta[property='og:image']")
    if og_image:
        thumbnail_url = og_image.get("content", "").strip()
    if not thumbnail_url:
        for image in root.select("img[src], img[data-lazy-src], img[data-src]"):
            candidate = (image.get("data-lazy-src") or image.get("data-src") or image.get("src") or "").strip()
            if candidate and not candidate.startswith("data:"):
                thumbnail_url = urljoin(source_url, candidate)
                break
    category = category_from(title, root.get_text(" ", strip=True)[:300])
    if category is None:
        raise ValueError("천성인어/춘추 글이 아닙니다")
    published = parse_date(
        (soup.select_one("meta[property='article:published_time']") or {}).get("content", ""),
        (soup.select_one(".se_publishDate, .date") or root).get_text(" ", strip=True)[:100],
        title,
    )
    japanese_html, japanese_text, korean_text = _split_sections(root)
    return ParsedArticle(source_url, category, title.strip(), published, japanese_html, japanese_text, korean_text, thumbnail_url)
