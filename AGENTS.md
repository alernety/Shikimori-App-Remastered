# Shikimori App Remastered — Agent Guide

## Tech stack

- Single `:app` module (settings.gradle only includes `:app`).
- Kotlin + some Java (Dagger modules are Java).
- **MVP** via Moxy (`MvpPresenter`, `@InjectViewState`).
- **DI** via Dagger Android (kapt: `dagger-compiler`, `dagger-android-processor`).
- **Navigation** via Cicerone (`Router`, local `NavigatorHolder`).
- **Async** via RxJava 2 (Single, Completable, Observable).
- **Network** via Retrofit + OkHttp + Gson.
- **DB** via Room + RxJava 2 adapter (migrated from StorIO — storio-* dirs are dead code).
- **Images** via Glide + okhttp3 integration.
- **Video** via ExoPlayer + mediasession extension.
- **Firebase**: analytics, crashlytics, messaging, firestore, storage.
- **ViewBinding** enabled.

## Build

```bash
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # release (minify+shrink+zipalign)
```

- Gradle 8.10.2 / AGP 8.7.2 / Kotlin 1.9.24 / Java 17 (source & target).
- MinSdk 21, TargetSdk 34, CompileSdk 34.
- Versions in `dependencies.gradle` via `ext.versions` (no version catalog).
- `multiDexEnabled true`, `useAndroidX=true`, `enableJetifier=true`.

## Secrets

All keys/tokens are in **`gradle.properties`** (committed to repo, not `.env`):
`ShikimoriClientId`, `ShikimoriClientSecret`, `ShikimoriBaseUrl`, `VideoBaseUrl`, `VkRandomToken`.
Injected as `BuildConfig` fields in `build.gradle:37-43`.

## Versioning

- `gradle.properties`: `AI_VERSION_CODE=141` (manual).
- `app/build.gradle`: `versionName "0.8.7"`, `versionCode 1` (overridden by `advanced-build-version` plugin).
- `google-services.json` is committed.

## Architecture patterns

```
view/          — Activities & Fragments (Moxy views)
presenter/     — MvpPresenter subclasses (RxJava orchestration)
di/            — Dagger modules in Java (@Module, @Binds, @ContributesAndroidInjector)
entity/        — data/ (API responses), domain/ (business models), presentation/ (ViewModels)
data/
  network/     — Retrofit API interfaces + impl
  local/       — Room DAOs, SharedPrefs sources, download/service sources
  repository/  — Repository interface + Impl (injected into interactors)
domain/        — Interactors (business logic layer)
```

- `BasePresenter` extends `MvpPresenter` → `onFirstViewAttach()` calls `initData()`.
- Presenters injected via `@Provide` in per-feature Dagger modules.
- Activities/Fragments scoped with `@ActivityScope`, injected via `AndroidInjection.inject(this)`.

## No tests

This project has **zero tests**. No test directory, no test deps in build.gradle. Do not assume any test runner or framework exists.

## Code style

- No ktlint, detekt, spotless, or `lint.xml` configured.
- No `.editorconfig`.
- No CI configuration (no `.github/` workflows).
- Formatting conventions: follow existing code — 4-space indent in Kotlin, 4-space indent in Java.

## ProGuard

Release builds use `proguard-rules.pro` + default Android ProGuard rules.
Key keeps: Glide, Firebase, GMS, Protobuf, ExoPlayer, jsoup, WebView JS interfaces.

## Dead code

`storio-*`, `storio-common-annotations-processor/`, `storio-content-resolver*`, `storio-sqlite*` directories are leftovers from Room migration. Not included in `settings.gradle`. Do not touch.

## Known quirks

- `.idea/`, `.gradle/`, `build/` are gitignored but `.project`, `.settings/`, `.classpath` (Eclipse files) are committed.
- `libs/` contains pre-built `.aar`/`.jar` files (drawer-behavior, kotlin-permissions, cicerone, flexbox, ExoPlayer 2.9.6) — some duplicate what's in the Gradle dep graph.
- Gradle daemon uses JetBrains JDK 21 toolchain (`gradle/gradle-daemon-jvm.properties`).
