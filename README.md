# appsearch

Android 기기에서 텍스트·웹 페이지를 수집하고, **Jetpack AppSearch**로 로컬 전문(full-text) 검색하는 앱입니다.  
공유, 직접 입력, 웹 추출로 주입한 내용을 청크 단위로 인덱싱하고 BM25 관련성 점수로 검색합니다.

## 주요 기능

| 기능 | 설명 |
|------|------|
| **실시간 검색** | 입력한 검색어로 AppSearch 전문 검색, 관련성 점수·스니펫 표시 |
| **텍스트 주입** | 앱 내 직접 입력으로 텍스트 인덱싱 |
| **공유 대상** | 다른 앱에서 `공유` 선택 시 UI 없이 백그라운드 주입 + 토스트 결과 |
| **웹 추출** | OkHttp/Jsoup, WebView, 네이버 `ogUrl`·bridge URL 해석으로 기사 본문 추출 |
| **주입 기록** | 출처별 목록·상세(전체 본문·청크) 확인 |
| **검색 하이라이트** | 검색어 exact match 구간 강조 |

## 기술 스택

- **UI**: Jetpack Compose, Material 3, Navigation Compose
- **DI**: Hilt
- **검색**: [Jetpack AppSearch](https://developer.android.com/jetpack/androidx/releases/appsearch) 1.1.0 (`PlatformStorage`)
- **비동기**: Kotlin Coroutines + Guava `ListenableFuture` 연동
- **웹**: OkHttp, Jsoup, WebView
- **이미지**: Coil

**요구 사항**: minSdk 36, targetSdk 36, JDK 17

## 프로젝트 구조

```
app/src/main/java/com/yoon/js/appsearch/
├── MainActivity.kt              # 검색·주입·기록 UI
├── ShareTrampolineActivity.kt   # 공유 전용 투명 Activity (주입 + 토스트)
├── data/
│   ├── model/                   # AppSearch @Document 스키마
│   ├── repository/              # AppSearchRepository, SourceRegistry
│   ├── chunking/                # TextChunker (300자 / 50자 overlap)
│   ├── share/                   # ShareIntentParser, ShareProcessor
│   └── web/                     # WebContentExtractor, UrlResolver
├── domain/model/                # IndexRequest, SourceRecord, ChunkSearchResult …
├── ui/
│   ├── search/                  # SearchScreen, 하이라이트
│   ├── inject/                  # InjectScreen
│   └── history/                 # HistoryScreen, SourceDetailScreen
└── di/                          # Hilt 모듈
```

자세한 레이어·데이터 흐름은 [docs/architecture.md](docs/architecture.md)를 참고하세요.

## 문서

| 문서 | 내용 |
|------|------|
| [docs/appsearch.md](docs/appsearch.md) | AppSearch 스키마, 인덱싱·검색 스펙, BM25 |
| [docs/chunking.md](docs/chunking.md) | Android 온디바이스 텍스트 청킹 전략·BreakIterator·overlap |
| [docs/architecture.md](docs/architecture.md) | 앱 아키텍처, 공유·웹 추출 흐름, 화면 구성 |
| [docs/development.md](docs/development.md) | 빌드, 테스트, 버전·릴리즈, 디버그 로그 |

## 빠른 시작

```bash
# JDK 17 (Android Studio JBR 등)
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# Debug APK
./gradlew assembleDebug

# 단위 테스트
./gradlew test
```

에뮬레이터 또는 기기에 Debug APK 설치 후:

1. **직접 주입**: 검색 화면 → 주입 → 텍스트 입력
2. **공유 주입**: 브라우저·네이버 앱 등에서 `공유` → `appsearch` 선택
3. **검색**: 검색 화면에서 키워드 입력
4. **기록**: 검색 화면 → 주입 기록 → 카드 탭으로 상세 확인

## 릴리즈

[GitHub Releases](https://github.com/livsky87/appsearch/releases)에서 APK/AAB를 받을 수 있습니다.

- `main` push 시 patch 버전 자동 bump (`.github/workflows/bump-version.yml`)
- 수동 릴리즈: Actions → **Release** workflow dispatch (minor bump + APK/AAB 업로드)

## 라이선스

이 저장소의 라이선스는 별도 명시가 없습니다. 사용·배포 전 저장소 소유자에게 확인하세요.
