from fastapi import FastAPI, HTTPException, Query

from .crawler import crawl
from .db import get_article, list_articles
from .models import Category

app = FastAPI(title="Japanese Copywork API", version="1.0.0")

DICTIONARY = {
    "猛": {"reading": "もう", "romanization": "mō", "meaning": "사납다, 맹렬하다", "part_of_speech": "한자", "onyomi": "モウ", "kunyomi": "たけ-し"},
    "猛暑": {"reading": "もうしょ", "romanization": "mōsho", "meaning": "맹렬한 더위, 폭염", "part_of_speech": "명사", "onyomi": "モウ・ショ", "kunyomi": "たけ-し・あつ-い"},
    "春": {"reading": "はる・シュン", "meaning": "봄", "part_of_speech": "한자", "onyomi": "シュン", "kunyomi": "はる"},
    "秋": {"reading": "あき・シュウ", "meaning": "가을", "part_of_speech": "한자", "onyomi": "シュウ", "kunyomi": "あき"},
    "天": {"reading": "あめ・テン", "meaning": "하늘", "part_of_speech": "한자", "onyomi": "テン", "kunyomi": "あめ・あま"},
    "声": {"reading": "こえ・セイ", "meaning": "소리, 목소리", "part_of_speech": "한자", "onyomi": "セイ・ショウ", "kunyomi": "こえ"},
    "人": {"reading": "ひと・ジン・ニン", "meaning": "사람", "part_of_speech": "한자", "onyomi": "ジン・ニン", "kunyomi": "ひと"},
    "語": {"reading": "かたる・ゴ", "meaning": "말, 이야기하다", "part_of_speech": "한자", "onyomi": "ゴ", "kunyomi": "かた-る"},
}


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/api/articles")
def articles(category: Category | None = Query(default=None)):
    return list_articles(category.value if category else None)


@app.get("/api/articles/{article_id}")
def article(article_id: int):
    result = get_article(article_id)
    if not result:
        raise HTTPException(404, "Article not found")
    return result


@app.post("/api/sync")
async def sync(limit: int = Query(30, ge=1, le=100)):
    return await crawl(limit)


@app.get("/api/dictionary/{word}")
def dictionary(word: str):
    return {"word": word, **DICTIONARY.get(word, {
        "reading": "", "romanization": "", "meaning": "사전 데이터 없음",
        "part_of_speech": "", "onyomi": "", "kunyomi": ""
    })}
