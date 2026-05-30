# Android 텍스트 청킹 가이드

이 문서는 Android 온디바이스 검색(AppSearch) 환경에서 **긴 텍스트를 어떻게 나누면 좋은지** 정리합니다.  
`appsearch` 앱의 `TextChunker` 설계 근거와, 다른 전략을 도입할 때 고려할 점을 함께 다룹니다.

## 왜 청킹이 필요한가

AppSearch는 **문서(document) 단위**로 인덱싱·검색·스니펫을 반환합니다. 긴 기사나 메모를 하나의 문서로 넣으면:

| 문제 | 설명 |
|------|------|
| **Recall 저하** | BM25는 짧은 구간에서 매칭된 문서에 유리합니다. 긴 문서 한 건은 관련 구절이 희석됩니다. |
| **스니펫 품질** | `maxSnippetSize`(예: 120자)만큼만 잘려 나와 맥락이 부족할 수 있습니다. |
| **저장 제약** | `PlatformStorage`는 문서 크기·개수에 **플랫폼 한도**가 있습니다. ([AppSearch 스토리지 비교](https://developer.android.com/develop/ui/views/search/appsearch)) |
| **UI 표현** | 검색 결과를 “어느 구간에서 맞았는지” 청크 단위로 보여 주기 쉽습니다. |

반면 청크가 **너무 작거나** **문장 중간에서 잘리면** 의미가 깨져 검색 품질이 떨어집니다.  
청킹은 **검색 단위 크기**와 **의미 경계 보존** 사이의 균형 문제입니다.

## AppSearch 관점에서의 제약

### PlatformStorage vs LocalStorage

| 항목 | PlatformStorage (본 앱) | LocalStorage |
|------|-------------------------|--------------|
| APK 크기 | 작음 (시스템 서비스 경유) | 큼 |
| 문서 크기·개수 | **제한 있음** | 상대적으로 유연 |
| Binder 지연 | 있음 | 없음 |

본 앱은 `PlatformStorage`를 사용하므로, **한 청크의 `content` 길이를 적당히 제한**하고 **청크 수를 과도하게 늘리지 않는** 편이 안전합니다.

### 인덱싱 설정과의 관계

`TextChunkDocument.content`는 다음 설정으로 인덱싱됩니다.

```kotlin
indexingType = INDEXING_TYPE_PREFIXES
tokenizerType = TOKENIZER_TYPE_PLAIN
```

- **PREFIXES**: 접두사 검색 → `TERM_MATCH_PREFIX`와 함께 “아침” → “아침에” 매칭
- **PLAIN**: 공백·구두점 기준 토큰화 (한국어·영어 혼합 텍스트에 무난)

청크 **내용**은 자연어 문장·단어 경계를 지키는 것이 토큰화·매칭 품질에 유리합니다.  
임의 위치에서 글자 수만으로 자르면 조사·어미가 분리되어 prefix 검색이 기대와 다르게 동작할 수 있습니다.

## 청킹 전략 비교

일반적인 전문 검색·RAG 문헌에서 쓰이는 전략을 Android 온디바이스 검색에 맞게 요약했습니다.

| 전략 | 방식 | 장점 | 단점 | Android 적합도 |
|------|------|------|------|----------------|
| **고정 길이 (character/token)** | N자/N토큰마다 분할 | 구현 단순, 크기 예측 가능 | 문장·단어 중간 절단 | △ (단독 사용 비권장) |
| **문장 단위 (sentence)** | 문장 경계에서 분할 후 패킹 | 의미 보존, 한국어에 유리 | 매우 긴 문장 추가 처리 필요 | ◎ |
| **재귀 분할 (recursive)** | `\n\n` → `\n` → `. ` → 공백 → 문자 순 시도 | 구조화 텍스트·Markdown에 강함 | 구현·튜닝 복잡 | ○ |
| **단어 단위 (word)** | BreakIterator word로 패킹 | 긴 문장 처리, 단어 중간 절단 방지 | CJK에서 “단어” 정의가 모호 | ○ (보조 단계) |
| **의미/임베딩 (semantic)** | 문장 임베딩 유사도로 경계 | 주제 전환 반영 | 온디바이스 연산·모델 필요 | △ (고급) |
| **구조 인식 (HTML/Markdown)** | `#`, `article`, `h2` 등 기준 | 웹·문서 구조 반영 | 추출 품질에 의존 | ○ (웹 주입 시) |

**실무 권장 (온디바이스 BM25 검색)**  
1. **1차**: 문장 경계 (`BreakIterator.getSentenceInstance`)  
2. **2차**: 청크 최대 크기 초과 시 단어 경계 (`BreakIterator.getWordInstance`)  
3. **3차**: overlap 10~20%로 경계 맥락 유지  
4. **선택**: HTML/Markdown이면 헤더·단락(`\n\n`) 우선 분할

참고: [Elasticsearch chunking strategies](https://www.elastic.co/search-labs/blog/chunking-strategies-elasticsearch), [RAG chunking 개요](https://www.coveo.com/blog/rag-chunking-information/)

## Android에서 경계 찾기: BreakIterator

Android/Java 표준 **`java.text.BreakIterator`** 는 로케일별로 문자·단어·문장·줄 경계를 찾습니다.  
`String.split()`이나 고정 인덱스 슬라이스보다 **한국어·영어 혼합 텍스트**에 적합합니다.

```kotlin
val iterator = BreakIterator.getSentenceInstance(Locale.getDefault())
iterator.setText(text)
var start = iterator.first()
var end = iterator.next()
while (end != BreakIterator.DONE) {
    val sentence = text.substring(start, end).trim()
    // sentence 단위 처리
    start = end
    end = iterator.next()
}
```

| BreakIterator | 용도 |
|---------------|------|
| `getSentenceInstance(locale)` | 문장 분리 (청킹 1차 단위) |
| `getWordInstance(locale)` | 단어 분리 (긴 문장 쪼개기) |
| `getLineInstance(locale)` | 줄바꿈 (UI용, 청킹에는 보조) |

**주의**

- `Locale.getDefault()`는 사용자 기기 설정을 따릅니다. 앱 언어와 다를 수 있으므로, 필요 시 `Locale.KOREAN` / `Locale.ENGLISH`를 명시합니다.
- API 24+에서는 `android.icu.text.BreakIterator`가 더 정확한 경우가 있으나, `java.text`만으로도 대부분 충분합니다.
- 이메일·URL·숫자.소수점 등은 문장 경계 오탐 가능 → 웹 추출 본문에서는 드물게 영향.

공식 참고: [BreakIterator (Android)](https://developer.android.com/reference/java/text/BreakIterator)

## Overlap(겹침) 가이드

인접 청크 사이에 **일부 텍스트를 겹치게** 두면, 답이 청크 경계에 걸쳐 있을 때 recall이 올라갑니다.

| 가이드 | 권장 |
|--------|------|
| overlap 비율 | 청크 크기의 **10~20%** ([RAG 실무](https://amirteymoori.com/rag-text-chunking-strategies/)) |
| 본 앱 설정 | 300자 청크, **50자 overlap (~17%)** |
| 경계 | overlap 구간은 **단어 경계**에서 자르기 (공백 기준 trim) |

**트레이드오ff**

- overlap ↑ → recall ↑, 인덱스 문서 수 ↑, 저장·인덱싱 시간 ↑  
- overlap 0 → 구현 단순, 경계에서 맥락 유실

온디바이스 AppSearch에서는 임베딩 RAG처럼 overlap을 50%까지 키울 필요는 없고, **10~20%**가 비용 대비 효과가 좋습니다.

## 청크 크기 가이드

고정 숫자는 콘텐츠·검색 UI에 따라 달라지지만, **문자 수 기준** 온디바이스 힌트는 다음과 같습니다.

| 청크 크기 (문자) | 적합한 경우 |
|------------------|-------------|
| 150~250 | 짧은 메모, 카드형 스니펫 중심 UI |
| **250~400** | **뉴스 기사·블로그** (본 앱: 300) |
| 500~800 | 긴 문맥이 중요한 기술 문서 |
| 1000+ | 단일 청크가 너무 커져 PlatformStorage·스니펫 효율 저하 |

**측정 단위**

- AppSearch PLAIN 토큰izer는 **토큰 ≈ 단어/구두점 단위**이므로, 한국어 300자 ≈ 대략 100~200 토큰 전후(내용에 따라 다름).
- 임베딩 모델(512/1024 토큰)을 쓰지 않는 **BM25 전문 검색**은 문자·문장 기준으로 튜닝해도 무방합니다.

## 웹·공유 콘텐츠 특화

브라우저·네이버 앱에서 공유한 HTML/기사는 **추출 단계**와 **청킹 단계**를 분리하는 것이 좋습니다.

```
웹 페이지
  → HtmlContentExtractor (#dic_area, article, og:description …)
  → 순수 텍스트
  → TextChunker (문장·단어·overlap)
  → AppSearch TextChunkDocument[]
```

| 단계 | 권장 |
|------|------|
| 추출 | DOM 구조·사이트별 선택자로 **본문만** 분리 (광고·내비 제외) |
| 정규화 | 연속 공백·줄바꿈 collapse (`\s+` → ` `) |
| 청킹 | 추출된 plain text에 **동일 chunker** 적용 (출처 타입 무관) |

구조 인식 청킹을 확장하려면 `HtmlContentExtractor`에서 `\n\n` 단락 리스트를 chunker에 넘기는 **recursive** 방식을 추가할 수 있습니다.

## 본 앱(`TextChunker`) 구현 요약

현재 파이프라인 (`TextChunker.kt`):

```
입력 텍스트
  → BreakIterator 문장 분리
  → 문장 > 300자 → BreakIterator 단어 분할
  → 문장/단어 단위 pack (max 300자)
  → 청크 경계마다 이전 청크 tail 50자 overlap (단어 경계 trim)
  → List<String> 청크
```

| 상수 | 값 |
|------|-----|
| `MAX_CHUNK_SIZE` | 300 |
| `OVERLAP_SIZE` | 50 |

이 조합은 **뉴스·공유 웹 기사·직접 입력**을 같은 검색 인덱스에 넣기 위한 균형점입니다.

### 개선 아이디어 (향후)

| 항목 | 설명 |
|------|------|
| Recursive separator | `\n\n`, `\n`, `. ` 순 분할 (Markdown·긴 메모) |
| 출처별 크기 | `SHARE_WEB` 300, `MANUAL` 400 등 `SourceType`별 tuning |
| Locale 명시 | `Locale.KOREAN` 고정 또는 앱 locale 연동 |
| 구조 청킹 | HTML heading 기준 섹션 → 섹션 내부 sentence chunk |
| Parent-Child | AppSearch 1.1 `indexableNestedProperties` / 큰 출처 문서 + 작은 검색 청크 ([AppSearch 릴리즈 노트](https://developer.android.com/jetpack/androidx/releases/appsearch)) |
| 임베딩 검색 | AppSearch 1.1+ `Features.SEARCH_SPEC_SEARCH_STRINGS_BY_EMBEDDING` — semantic chunking과 결합 (연산 비용 주의) |

## 전략 선택 체크리스트

새 청킹 방식을 도입할 때:

1. **검색 백엔드가 BM25인가, 임베딩인가?** → BM25는 문장+적당한 크기+overlap이면 충분한 경우가 많음  
2. **PlatformStorage 문서 한도**를 넘지 않는가? (청크 수 × 평균 길이)  
3. **한국어 비율**이 높은가? → BreakIterator sentence + word  
4. **HTML/Markdown**인가? → recursive 또는 DOM 섹션 우선  
5. **overlap 10~20%** 적용했는가?  
6. **단위 테스트**로 max 크기·overlap·빈 입력을 검증했는가? (`TextChunkerTest`)

## 관련 문서

- [AppSearch 기술 문서](appsearch.md) — 스키마, 검색 스펙, 인덱싱 파이프라인
- [아키텍처](architecture.md) — 웹 추출 → indexContent 흐름
- [개발 가이드](development.md) — 테스트 실행

## 참고 링크

- [AppSearch 개요 (Android Developers)](https://developer.android.com/develop/ui/views/search/appsearch)
- [AppSearch Jetpack 릴리즈](https://developer.android.com/jetpack/androidx/releases/appsearch)
- [BreakIterator API](https://developer.android.com/reference/java/text/BreakIterator)
- [SearchResult.TextMatchInfo (스니펫)](https://developer.android.com/reference/kotlin/androidx/appsearch/app/SearchResult.TextMatchInfo)
- [Elasticsearch: Chunking strategies](https://www.elastic.co/search-labs/blog/chunking-strategies-elasticsearch)
- [MobileRAG: on-device RAG (sentence sliding window)](https://arxiv.org/html/2507.01079)
