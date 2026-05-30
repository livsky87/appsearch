# 아키텍처

## 레이어 구조

```
┌─────────────────────────────────────────────────────────┐
│  UI (Compose)                                           │
│  SearchScreen · InjectScreen · HistoryScreen · Detail   │
└──────────────────────────┬──────────────────────────────┘
                           │ ViewModel
┌──────────────────────────▼──────────────────────────────┐
│  Domain                                                 │
│  IndexRequest · SourceRecord · ChunkSearchResult        │
└──────────────────────────┬──────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────┐
│  Data                                                   │
│  AppSearchRepository · ShareProcessor · WebContent*     │
│  SourceRegistry · TextChunker                           │
└──────────────────────────┬──────────────────────────────┘
                           │
              ┌────────────┴────────────┐
              ▼                         ▼
     PlatformStorage (AppSearch)   SharedPreferences
```

- **UI**: Compose + Hilt ViewModel, 단방향 상태(`StateFlow`)
- **Domain**: 순수 모델·요청 DTO, Android 프레임워크 비의존
- **Data**: AppSearch 세션, HTTP/WebView, SharedPreferences

## Activity 구성

| Activity | Intent | 역할 |
|----------|--------|------|
| `MainActivity` | `MAIN` / `LAUNCHER` | 검색·주입·기록 UI |
| `ShareTrampolineActivity` | `SEND` / `text/plain` | 투명 Activity, 공유 처리 후 종료 |

공유 시 메인 UI를 띄우지 않습니다. `ShareTrampolineActivity`가 `ShareProcessor`로 주입하고 토스트만 표시합니다.

## 화면·내비게이션

```
Search (start)
  ├── Inject
  └── History
        └── History Detail /{sourceId}
```

`AppNavGraph`에서 Navigation Compose로 연결합니다.

## 데이터 주입 경로

### 1. 직접 입력 (MANUAL)

`InjectScreen` → `InjectViewModel` → `AppSearchRepository.indexContent`

### 2. 공유 텍스트 (SHARE_TEXT)

`ShareIntentParser` → `ShareProcessor` → `indexContent`

### 3. 공유 웹 (SHARE_WEB)

```
ShareIntentParser
  ├── EXTRA_TEXT (URL)
  ├── ogUrl, originalUrl (네이버 앱 등)
  └── title, ogDescription, ogImage
        │
        ▼
WebContentExtractor
  ├── OkHttp + Jsoup (다중 URL 시도)
  ├── sharedHtml (있을 경우)
  └── WebView (기사 URL 직접 로드)
        │
        ▼
UrlResolver (bridge·단축 URL → n.news.naver.com 등)
        │
        ▼
indexContent
```

### ShareIntentParser (네이버 등)

네이버 앱 공유 Intent extras 예시:

| Extra | 용도 |
|-------|------|
| `ogUrl` | 실제 기사 URL (최우선 fetch) |
| `originalUrl` | `link.naver.com/bridge?url=…` (unwrap) |
| `android.intent.extra.TEXT` | `naver.me` 단축 URL |
| `title` / `ogDescription` | 제목·요약 힌트 |

`UrlResolver.selectBestFetchUrl()`이 `n.news.naver.com` 등 직접 기사 URL을 우선 선택합니다.

## 웹 추출 전략

`HtmlContentExtractor` 본문 후보 (가장 긴 텍스트 선택):

- CSS: `#dic_area`, `#newsct_article`, `article`, `main` …
- Meta: `og:description`, `description`
- JSON-LD: `articleBody`, `description`

`WebContentValidator`로 WebView 오류 페이지·짧은 bridge 페이지 본문을 걸러냅니다.

## 검색 UI

1. `SearchViewModel` → `AppSearchRepository.search(query)`
2. `SearchResultCard`: 관련성 점수, 출처 배지, 청크 본문
3. `QueryHighlightFormatter`: 청크 전체 텍스트에서 검색어 exact match 하이라이트

## DI (Hilt)

| 모듈 | 제공 |
|------|------|
| `AppModule` | IO Dispatcher 등 |
| `WebModule` | `OkHttpClient` |

`AppSearchRepository`, `ShareProcessor`, `WebContentExtractor` 등은 `@Singleton`.

## 디버그

공유·인덱싱 흐름은 Logcat 태그 **`AppSearchShare`**로 추적합니다.

```bash
adb logcat -s AppSearchShare
```

자세한 내용은 [development.md](development.md)를 참고하세요.

## 텍스트 청킹

인덱싱 전 `TextChunker`로 본문을 나눕니다. 전략 선택·overlap·BreakIterator 사용법은 [청킹 가이드](chunking.md)를 참고하세요.
