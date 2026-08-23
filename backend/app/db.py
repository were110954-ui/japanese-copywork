import sqlite3
from pathlib import Path

from .models import ParsedArticle

DB_PATH = Path(__file__).resolve().parent.parent / "data" / "articles.db"


def connect() -> sqlite3.Connection:
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(DB_PATH)
    db.row_factory = sqlite3.Row
    db.execute("""CREATE TABLE IF NOT EXISTS articles (
        id INTEGER PRIMARY KEY AUTOINCREMENT, source_url TEXT NOT NULL UNIQUE,
        category TEXT NOT NULL, title TEXT NOT NULL, published_at TEXT NOT NULL,
        japanese_html TEXT NOT NULL, japanese_text TEXT NOT NULL,
        korean_text TEXT NOT NULL, thumbnail_url TEXT NOT NULL DEFAULT '',
        fetched_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    )""")
    columns = {row[1] for row in db.execute("PRAGMA table_info(articles)")}
    if "thumbnail_url" not in columns:
        db.execute("ALTER TABLE articles ADD COLUMN thumbnail_url TEXT NOT NULL DEFAULT ''")
    return db


def upsert(article: ParsedArticle) -> int:
    with connect() as db:
        db.execute("""INSERT INTO articles
            (source_url, category, title, published_at, japanese_html, japanese_text, korean_text, thumbnail_url)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(source_url) DO UPDATE SET category=excluded.category, title=excluded.title,
            published_at=excluded.published_at, japanese_html=excluded.japanese_html,
            japanese_text=excluded.japanese_text, korean_text=excluded.korean_text,
            thumbnail_url=excluded.thumbnail_url,
            fetched_at=CURRENT_TIMESTAMP""",
            (article.source_url, article.category.value, article.title, article.published_at.isoformat(),
             article.japanese_html, article.japanese_text, article.korean_text, article.thumbnail_url))
        return db.execute("SELECT id FROM articles WHERE source_url=?", (article.source_url,)).fetchone()[0]


def list_articles(category: str | None = None):
    with connect() as db:
        sql = "SELECT id, source_url, category, title, published_at, japanese_text, korean_text, thumbnail_url, fetched_at FROM articles"
        args = ()
        if category:
            sql += " WHERE category=?"
            args = (category,)
        return [dict(row) for row in db.execute(sql + " ORDER BY published_at DESC, id DESC", args)]


def get_article(article_id: int):
    with connect() as db:
        row = db.execute("SELECT * FROM articles WHERE id=?", (article_id,)).fetchone()
        return dict(row) if row else None
