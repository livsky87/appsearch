# Jetpack AppSearch 기술 문서

이 문서는 `appsearch` 앱에서 Jetpack AppSearch를 어떻게 사용하는지 설명합니다.

## 개요

AppSearch는 AndroidX에서 제공하는 **로컬 전문 검색(full-text search)** 프레임워크입니다. 이 앱은 `PlatformStorage` 백엔드를 사용해 기기 내부에 인덱스를 저장하며, 외부 서버 없이 BM25 기반 관련성 점수로 검색합니다.

- **라이브러리 버전**: AppSearch 1.1.0
- **백엔드**: `androidx.appsearch:appsearch-platform-storage`
- **세션 생성**: `PlatformStorage.createSearchSessionAsync`
- **데이터베이스 이름**: `appsearch_db` (`AppSearchRepository`)

```kotlin
PlatformStorage.createSearchSessionAsync(
    PlatformStorage.SearchContext.Builder(context, "appsearch_db").build(),
)
```

스키마 등록은 `@Document` 어노테이션이 붙은 Kotlin data class를 `SetSchemaRequest`에 등록하는 방식입니다. 앱 최초 인덱싱/검색 시 `ensureInitialized()`에서 한 번 수행합니다.

## 문서 스키마

앱은 **출처 메타데이터**와 **검색 대상 청크** 두 종류의 문서를 저장합니다.

### SourceDocument (출처 메타)

| 필드 | 타입 | 용도 |
|------|------|------|
| `namespace` | String | `"sources"` (고정) |
| `id` | String | `sourceId` (밀리초 타임스탬프 문자열) |
| `sourceType` | Long | `MANUAL` / `SHARE_TEXT` / `SHARE_WEB` |
| `title` | String | 표시 제목 |
| `url` | String | 웹 URL (해당 시) |
| `imageUrl` | String | og:image 등 |
| `previewText` | String | 미리보기 (최대 120자) |
| `chunkCount` | Long | 연결된 청크 수 |
| `creationTimestampMillis` | Long | 생성 시각 |

- **검색 대상 아님**: 출처 목록·메타 조회용
- **목록 조회**: `search("*")` + `RANKING_STRATEGY_CREATION_TIMESTAMP` 또는 `SourceRegistry`(SharedPreferences) 캐시

### TextChunkDocument (검색 단위)

| 필드 | 타입 | 인덱싱 설정 | 용도 |
|------|------|-------------|------|
| `namespace` | String | — | `"text_chunks"` |
| `id` | String | — | `{sourceId}_{chunkIndex}` |
| `sourceId` | Long | — | 출처 FK |
| `chunkIndex` | Long | — | 청크 순서 |
| `sourceType` | Long | — | 출처 유형 |
| `creationTimestampMillis` | Long | — | 생성 시각 |
| `content` | String | **PREFIXES + PLAIN** | **전문 검색 필드** |

`content` 필드만 전문 검색이 활성화됩니다.

```kotlin
@Document.StringProperty(
    indexingType = INDEXING_TYPE_PREFIXES,
    tokenizerType = TOKENIZER_TYPE_PLAIN,
)
val content: String
```

- `INDEXING_TYPE_PREFIXES`: 접두사(prefix) 매칭 지원 → `TERM_MATCH_PREFIX`와 조합
- `TOKENIZER_TYPE_PLAIN`: 공백·구두점 기준 토큰화 (한국어 문장에 적합한 기본 설정)

## 인덱싱 파이프라인

```
IndexRequest (text, sourceType, title, url, …)
    │
    ▼
TextChunker.chunk(text)          # 300자 청크, 50자 overlap
    │
    ▼
SourceDocument 1건 + TextChunkDocument N건
    │
    ▼
PutDocumentsRequest → putAsync → requestFlushAsync
    │
    ▼
SourceRegistry.save(SourceRecord)  # UI용 메타 캐시
```

### 청킹 (TextChunker)

| 파라미터 | 값 | 설명 |
|----------|-----|------|
| `MAX_CHUNK_SIZE` | 300 | 청크 최대 글자 수 |
| `OVERLAP_SIZE` | 50 | 인접 청크 겹침 |

1. `BreakIterator`(문장)로 문장 분리
2. 300자 초과 문장은 단어 단위(`BreakIterator.getWordInstance`)로 분할
3. overlap prefix를 붙여 문맥 유지

Android에서 청킹 전략을 고르는 방법, overlap 비율, BreakIterator 사용법 등은 **[청킹 가이드](chunking.md)** 를 참고하세요.

### sourceId

`System.currentTimeMillis()`를 sourceId로 사용합니다. 동일 밀리초 중복 가능성은 있으나 단일 사용자 로컬 앱에서는 실용적입니다.

## 검색 스펙

검색은 **TextChunkDocument**만 대상으로 합니다.

```kotlin
SearchSpec.Builder()
    .addFilterSchemas(TextChunkDocument.SCHEMA_TYPE)
    .addFilterNamespaces(TextChunkDocument.NAMESPACE)
    .setRankingStrategy(SearchSpec.RANKING_STRATEGY_RELEVANCE_SCORE)
    .setTermMatch(SearchSpec.TERM_MATCH_PREFIX)
    .setSnippetCount(50)
    .setSnippetCountPerProperty(5)
    .setMaxSnippetSize(120)
    .build()
```

| 옵션 | 값 | 의미 |
|------|-----|------|
| `RANKING_STRATEGY_RELEVANCE_SCORE` | BM25 관련성 | 점수 높은 순 정렬 |
| `TERM_MATCH_PREFIX` | 접두사 일치 | `"아침"` → `"아침에"` 매칭 |
| `snippetCount` | 50 | 결과당 스니펫 수 상한 |
| `maxSnippetSize` | 120 | 스니펫 최대 길이 |

### 검색 결과 매핑

`SearchResult` → `ChunkSearchResult`:

- `rankingSignal`: BM25 관련성 점수
- `genericDocument` → `TextChunkDocument` (전체 `content`)
- `matchInfos`: 스니펫·매칭 구간 (UI 하이라이트 보조)
- `sourceRegistry`: `sourceId`로 제목·URL 보강

UI에서는 AppSearch 스니펫 대신 **전체 청크 `content`**에서 검색어 exact match를 `QueryHighlightFormatter`로 강조합니다.

## 출처 목록·상세 조회

| 작업 | 방식 |
|------|------|
| 목록 | `SourceRegistry.listAll()` (우선), 없으면 AppSearch `SourceDocument` wildcard 검색 |
| 메타 | `getByDocumentId` (`sources` namespace) |
| 상세 본문 | `getByDocumentId`로 `{sourceId}_0` … `{sourceId}_{N-1}` 청크 일괄 조회 후 `chunkIndex` 정렬 |

AppSearch는 청크 본문 검색에 최적화되어 있고, 출처 UI는 SharedPreferences 캐시(`SourceRegistry`)로 빠르게 목록을 제공합니다.

## 스키마 마이그레이션

```kotlin
SetSchemaRequest.Builder()
    .addDocumentClasses(SourceDocument::class.java, TextChunkDocument::class.java)
    .setForceOverride(true)
    .build()
```

스키마 변경 시 `setForceOverride(true)`로 재등록합니다. **기존 인덱스 데이터는 초기화될 수 있으므로** 스키마 변경 후에는 앱 데이터 삭제 또는 재주입을 권장합니다.

## AppSearch vs SourceRegistry

| 저장소 | 역할 | 기술 |
|--------|------|------|
| AppSearch | 청크 전문 검색, 출처 메타 영속 | PlatformStorage |
| SourceRegistry | 주입 기록 목록·메타 캐시 | SharedPreferences (JSON) |

검색은 항상 AppSearch `TextChunkDocument.content`를 사용합니다. 기록 화면은 Registry를 우선 읽고, Registry가 비어 있으면 AppSearch에서 복구합니다.

## 관련 문서

- [청킹 가이드](chunking.md) — Android 텍스트 분할 전략
- [Jetpack AppSearch 개요](https://developer.android.com/jetpack/androidx/releases/appsearch)
- [AppSearch 검색 API](https://developer.android.com/reference/androidx/appsearch/app/AppSearchSession)
- 앱 구현: `AppSearchRepository.kt`, `SourceDocument.kt`, `TextChunkDocument.kt`
