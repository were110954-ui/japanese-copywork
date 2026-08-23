# 아키텍처

```text
Naver Blog ──(OkHttp + Jsoup)──> In-app NaverCrawler ──> Room cache
                                         ▲                  │
                           WorkManager/전체 동기화            ▼
                                  Compose UI ── WebView ruby + JS bridge
```

APK는 외부 백엔드에 의존하지 않는다. 앱 내부 크롤러가 원문을 수집하고 이후 오프라인에서는 Room의 기사와 필사 내용을 보여준다. `sourceUrl`을 unique key로 사용해 동기화를 멱등 처리한다. Python 폴더는 개발용 참조 구현이다.

## Room 스키마

| 테이블 | 키 | 주요 필드 |
|---|---|---|
| `articles` | `id`, unique `sourceUrl` | category, title, publishedAt, japaneseHtml/Text, koreanText |
| `drafts` | `articleId` | text, updatedAt |
| `article_states` | `articleId` | completed, bookmarked, completedAt |
| `vocabulary` | `word` | reading, meaning, partOfSpeech, onyomi, kunyomi, savedAt |

`articles.thumbnailUrl`은 `og:image` 또는 본문 첫 이미지에서 추출한다. 목록은 Coil의 디스크/메모리 캐시를 사용하며 URL이 없으면 카테고리별 미니멀 아이콘을 렌더링한다. 상세 화면은 600dp 이상에서 6:4 Split View로 전환되고 원문 WebView, 번역 패널, 필사 에디터가 서로 독립적으로 스크롤된다.

## 파싱 전략

1. 네이버 `PostTitleListAsync` 공개 응답에서 `logNo`를 수집하고 모바일 글 URL로 정규화한다.
2. 각 글에서 `se-main-container`, `post-view`, `#postViewArea` 순서로 본문을 찾는다.
3. `[일본어 원문]`/`[한국어 번역문]` 헤더 사이의 DOM 형제 또는 줄 단위 텍스트를 분리한다.
4. 헤더가 없는 현재 글은 가나/한글 문자 비율로 교차 문단을 분리하고 링크 미리보기 앞에서 멈춘다.
5. 원문 ruby가 있으면 보존하고, 없으면 pykakasi의 문맥 변환 결과로 `<ruby><rt>`를 생성한다.
6. 제목 키워드 `天声人語|천성인어`와 `春秋|춘추`로 분류한다.
7. 구조가 예상과 다르면 해당 글만 건너뛰고 로그에 원인을 남긴다.

## 사전

현재 샘플은 즉시 동작하도록 소형 내장 사전과 서버 `/api/dictionary/{word}`를 사용한다. 제품화 시 JMdict/KANJIDIC2 라이선스 고지를 포함하고 Room FTS 테이블로 변환하면 완전 오프라인 검색이 가능하다. WebView의 JavaScript는 클릭한 CJK 문자/단어를 Android bridge로 전달하고, Compose Dialog는 바깥 탭 시 자동으로 닫힌다.

## 운영 주의

- 개인 학습용 메타데이터/본문 저장 범위를 검토하고 원문 재배포를 피한다.
- User-Agent, timeout, 재시도, 1초 이상의 요청 간격을 유지한다.
- 운영에서는 API 인증, HTTPS, rate limit, 수집 실패 알림, DB 백업을 추가한다.
