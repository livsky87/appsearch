# 개발 가이드

## 환경

| 항목 | 버전 |
|------|------|
| Android Gradle Plugin | 9.0.1 |
| Kotlin | 2.0.21 |
| compileSdk / targetSdk | 36 |
| minSdk | 36 |
| JDK | 17 |

`gradle.properties` (AGP 9 호환):

```properties
android.newDsl=false
android.builtInKotlin=false
```

## 빌드

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

./gradlew assembleDebug      # Debug APK
./gradlew assembleRelease    # Release APK (CI: debug keystore 서명)
./gradlew bundleRelease      # AAB
./gradlew test               # JVM 단위 테스트
```

APK 출력: `app/build/outputs/apk/debug/app-debug.apk`

## 버전 관리

`version.properties`:

```properties
major=1
minor=2
patch=0
versionCode=3
```

| 스크립트 / Workflow | 동작 |
|---------------------|------|
| `scripts/bump-version.sh patch\|minor` | 버전 수동 bump |
| `.github/workflows/bump-version.yml` | `main` push 시 patch bump (version bump 커밋 제외) |
| `.github/workflows/release.yml` | 수동 dispatch → minor bump + Release APK/AAB |

## 테스트

단위 테스트 (`app/src/test/`):

- `TextChunkerTest` — 청킹·overlap
- `HtmlContentExtractorTest` — HTML·네이버 선택자·JSON-LD
- `UrlResolverTest` — bridge URL unwrap
- `WebContentValidatorTest` — 오류 페이지 검증
- `QueryHighlightFormatterTest` — 검색어 하이라이트
- `ShareIntentParserTest` — URL/텍스트 공유 파싱

```bash
./gradlew test
```

스키마(`SourceDocument`, `TextChunkDocument`) 변경 후에는 **앱 데이터 삭제** 또는 재설치를 권장합니다 (`setForceOverride(true)`).

## 공유 기능 디버깅

1. Logcat 필터: `AppSearchShare`
2. 공유 직전부터 `adb logcat -s AppSearchShare` 실행
3. 확인 포인트:
   - `[ShareParser]` — `ogUrl`, fetch URL 목록
   - `[WebExtract]` — okhttp/webview `bodyLen`
   - `[Index]` — `chunkCount`, `sourceId`
   - `[Registry]` — `total` 개수

## 릴리즈 (GitHub Actions)

1. GitHub → **Actions** → **Release** → **Run workflow**
2. (선택) Release notes 입력
3. 완료 후 [Releases](https://github.com/livsky87/appsearch/releases)에서 APK/AAB 다운로드

로컬에서 수동 릴리즈 태그를 만들 경우 `version.properties`와 `versionCode`를 맞춰 주세요.

## 관련 문서

- [AppSearch 기술 문서](appsearch.md)
- [청킹 가이드](chunking.md)
- [아키텍처](architecture.md)
