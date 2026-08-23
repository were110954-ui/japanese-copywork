# 日語 筆寫ノート (일본어 필사 노트)

네이버 블로그의 천성인어/춘추 전체 글을 앱이 직접 수집하고 일본어 원문을 보며 필사하는 독립형 Android 앱입니다. `backend`는 파서 회귀 테스트와 개발 비교용이며 APK 실행에 필요하지 않습니다.

## 구조

```text
.
├─ android/                       # Kotlin + Jetpack Compose Android 앱
│  ├─ app/src/main/java/com/bioluck/copywork/
│  │  ├─ data/                   # Room, API, Repository
│  │  ├─ sync/                   # WorkManager 일일 동기화
│  │  └─ ui/                     # 목록/상세/루비 WebView
│  └─ app/src/main/res/
├─ backend/
│  ├─ app/                       # FastAPI + BeautifulSoup 크롤러/파서
│  ├─ tests/fixtures/            # 실제 구조를 흉내 낸 회귀 테스트 HTML
│  ├─ data/                      # SQLite(실행 시 생성)
│  └─ requirements.txt
└─ docs/ARCHITECTURE.md
```

## 1. 앱 동기화 방식

첫 실행 시 Room이 비어 있으면 앱 내부 `NaverCrawler`가 `PostTitleListAsync.naver`의 `currentPage=1...마지막`을 순회한 뒤 모든 사설 본문을 저장합니다. 이후 매일 WorkManager가 새 글만 확인합니다. **전체 동기화** 버튼은 누락된 과거 기사까지 다시 전수 검사하며, 저장된 `sourceUrl`은 건너뜁니다.

Python 백엔드는 앱 실행에 필요하지 않습니다. 파서를 별도로 비교·시험하려는 개발자만 아래 명령을 사용합니다.

## 2. 선택 사항: Python 파서 테스트 서버

Python 3.11 이상을 권장합니다.

```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

- API 문서: `http://localhost:8000/docs`
- 즉시 수집: `POST /api/sync`
- 목록: `GET /api/articles?category=tenseijingo`
- 상세: `GET /api/articles/{id}`

정기 실행은 운영 서버에서 Windows 작업 스케줄러/cron으로 `POST /api/sync`를 하루 한 번 호출하세요. 앱도 시작 시 및 WorkManager로 동기화를 요청합니다. 네이버 약관, robots 정책, 저작권을 확인하고 개인 학습 범위에서 요청 간격을 지켜 사용해야 합니다.

## 3. Android 실행/APK

1. Android Studio에서 `android` 폴더를 엽니다.
2. JDK 17과 Android SDK Platform 35/Build Tools를 설치하고 Gradle Sync를 실행합니다. `local.properties`에는 `sdk.dir=C:\\Users\\사용자명\\AppData\\Local\\Android\\Sdk` 형식으로 SDK 경로를 지정합니다.
3. 별도 서버 주소 설정은 없습니다. 기기가 인터넷에 연결되면 앱이 HTTPS로 네이버를 직접 읽습니다.
4. 디버그 APK: Android Studio **Build > Build APK(s)**. CLI를 쓰려면 설치된 Gradle로 한 번 `gradle wrapper`를 실행한 뒤 `.\gradlew.bat :app:assembleDebug`를 실행합니다.
5. 결과: `android/app/build/outputs/apk/debug/app-debug.apk`.

릴리스는 Android Studio의 **Generate Signed App Bundle or APK**에서 keystore를 만든 뒤 `release` APK를 생성합니다. HTTP는 개발 편의를 위해 허용되어 있으므로 배포 서버는 HTTPS를 사용하고 `usesCleartextTraffic`을 제거하세요.

## 테스트

```powershell
cd backend
pytest
```

크롤러는 표식형 본문과 현재의 일본어/한국어 교차 문단형 본문을 모두 처리합니다. 기존 `<ruby>`는 보존하고 일반 원문은 `pykakasi`로 읽기를 생성합니다. 고유명사까지 출판물 수준으로 교정해야 한다면 MeCab+UniDic 결과를 `add_furigana()`에 연결하세요.

## 화면과 로컬 데이터

- 기사 목록: 카테고리 및 완료/미완료 필터, 즉시 새로고침
- 상세: 루비 원문, 한국어 번역 스위치, 북마크, 자동 저장 필사, 완료 처리
- 사전: 본문 한자 터치, 음독/훈독/뜻 표시, 단어장 저장
- 하단 메뉴: 기사 목록, 저장 단어장, 완료 편수·연속 학습 통계
- 태블릿: 600dp 이상에서 원문 60% / 번역·필사 40%의 독립 스크롤 Split View
- 썸네일: 블로그 대표 이미지 캐싱, 이미지가 없으면 카테고리 아이콘 표시
- 통계: 필사 완료 즉시 최근 7일 Line Chart 갱신

앱 아이콘 생성 프롬프트와 Adaptive Icon 적용 순서는 [`docs/APP_ICON_PROMPT.md`](docs/APP_ICON_PROMPT.md)에 정리했습니다.

Room에는 `articles`, `drafts`, `article_states`, `vocabulary` 네 테이블을 둡니다. 첫 전체 수집은 글 수에 따라 수 분 이상 걸릴 수 있으므로 Wi-Fi와 충전을 권장합니다.
