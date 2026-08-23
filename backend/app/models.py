from dataclasses import dataclass
from datetime import date
from enum import StrEnum


class Category(StrEnum):
    TENSEIJINGO = "tenseijingo"
    SHUNJU = "shunju"


@dataclass(slots=True)
class ParsedArticle:
    source_url: str
    category: Category
    title: str
    published_at: date
    japanese_html: str
    japanese_text: str
    korean_text: str
    thumbnail_url: str
