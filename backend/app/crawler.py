import asyncio
import logging

import httpx

from .db import upsert
from .parser import parse_article, parse_post_links

LIST_URL = ("https://blog.naver.com/PostTitleListAsync.naver?blogId=bioluck&viewdate="
            "&currentPage=1&categoryNo=&parentCategoryNo=&countPerPage=30")
HEADERS = {"User-Agent": "Mozilla/5.0 (compatible; JapaneseCopywork/1.0; personal-study)"}
log = logging.getLogger(__name__)


async def crawl(limit: int = 30) -> dict:
    saved, skipped, errors = 0, 0, []
    timeout = httpx.Timeout(15, connect=10)
    async with httpx.AsyncClient(headers=HEADERS, timeout=timeout, follow_redirects=True) as client:
        listing = await client.get(LIST_URL, headers={**HEADERS, "Referer": "https://blog.naver.com/bioluck"})
        listing.raise_for_status()
        links = parse_post_links(listing.content.decode("utf-8", errors="replace"), LIST_URL)[:limit]
        for url in links:
            try:
                await asyncio.sleep(1.0)
                response = await client.get(url)
                response.raise_for_status()
                upsert(parse_article(response.content.decode("utf-8", errors="replace"), str(response.url)))
                saved += 1
            except ValueError:
                skipped += 1
            except Exception as exc:  # Continue so one changed/deleted post cannot abort the batch.
                log.exception("Failed to crawl %s", url)
                errors.append({"url": url, "error": type(exc).__name__})
    return {"found": len(links), "saved": saved, "skipped": skipped, "errors": errors}
