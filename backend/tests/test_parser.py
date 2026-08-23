from pathlib import Path

from app.models import Category
from app.parser import parse_article, parse_post_links

FIXTURES = Path(__file__).parent / "fixtures"


def test_parse_links_deduplicates_and_normalizes():
    links = parse_post_links((FIXTURES / "list.html").read_text(encoding="utf-8"), "https://m.blog.naver.com/")
    assert links == ["https://m.blog.naver.com/bioluck/123", "https://m.blog.naver.com/bioluck/456", "https://m.blog.naver.com/bioluck/789"]


def test_parse_marked_sections_and_ruby():
    article = parse_article((FIXTURES / "post.html").read_text(encoding="utf-8"), "https://example/post")
    assert article.category == Category.TENSEIJINGO
    assert article.published_at.isoformat() == "2026-08-22"
    assert "猛暑" in article.japanese_text
    assert "<ruby>" in article.japanese_html
    assert article.korean_text == "무더운 날이 이어진다."
    assert article.thumbnail_url == "https://example.com/cover.jpg"


def test_parse_alternating_japanese_and_korean_paragraphs():
    article = parse_article((FIXTURES / "post_alternating.html").read_text(encoding="utf-8"), "https://example/post2")
    assert "蚊に刺される" in article.japanese_text
    assert "薬を塗る" in article.japanese_text
    assert "모기에 물린다" in article.korean_text
    assert "첨부" not in article.japanese_text
    assert "<ruby>" in article.japanese_html
