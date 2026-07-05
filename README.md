# ReplyVault (Android)

A native Android wrapper around the **Nightshift — Study Companion** PWA, built with
Kotlin + WebView. The original HTML/CSS/JS is unchanged and runs from the app's
`assets/` folder — fully offline, no ads, no analytics, no network permission.

| | |
|---|---|
| App name | ReplyVault |
| Package | `com.hellovixora.replyvault` |
| Language | Kotlin |
| Min SDK | 24 (Android 7.0) |
| Target / Compile SDK | 35 |
| Build tool | Gradle (Kotlin DSL), AGP 8.5.2 |

## Opening the project

1. Open Android Studio (Koala 2024.1.1 or newer).
2. **File → Open** and select the `ReplyVault/` folder (the one containing `settings.gradle.kts`).
3. Let Gradle sync. See **"About the Gradle wrapper jar"** below — this is the one
   piece you may need to regenerate yourself before the first sync.
4. Run on a device/emulator with **Run ▸ app**.

### About the Gradle wrapper jar

This project includes `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.properties`
(pointing at Gradle 8.7), but **not** the binary `gradle/wrapper/gradle-wrapper.jar` —
it's a compiled binary that can't be produced in a text-only environment without
downloading it from `services.gradle.org`, and this response was generated without
network access. You have two easy options:

- **Easiest:** just open the project in Android Studio. Its "Gradle sync" will detect
  the missing wrapper jar and offer to regenerate it automatically (or you can use
  **File ▸ Sync Project with Gradle Files**).
- **Command line:** if you have any Gradle installation available locally, run
  `gradle wrapper --gradle-version 8.7` once from the project root — this generates
  the jar for you and you're set from then on.

Everything else in the project is complete and ready to build.

## What's included

```
ReplyVault/
├── settings.gradle.kts
├── build.gradle.kts                 (root)
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/wrapper/gradle-wrapper.properties
├── play-store-icon.png              (512×512, for Play Console listing)
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/
        │   └── study-app.html       (your PWA, unmodified)
        ├── java/com/hellovixora/replyvault/
        │   ├── ReplyVaultApp.kt     (Application class)
        │   ├── SplashActivity.kt    (SplashScreen API launcher)
        │   ├── MainActivity.kt      (WebView host — see below)
        │   ├── WebAppInterface.kt   (JS bridge for blob: downloads)
        │   └── DownloadHelper.kt    (MediaStore / legacy file saving)
        └── res/
            ├── layout/activity_main.xml
            ├── values/{colors,strings,themes}.xml
            ├── values-night/themes.xml
            ├── drawable/{ic_launcher_foreground,ic_splash_icon}.xml
            ├── mipmap-anydpi-v26/ic_launcher{,_round}.xml   (adaptive icon)
            ├── mipmap-{m,h,x,xx,xxx}hdpi/ic_launcher{,_round}.png (legacy icon, API <26)
            └── xml/{backup_rules,data_extraction_rules,file_paths}.xml
```

## How each requirement is implemented

- **Loads `study-app.html` from assets** — `MainActivity` loads
  `file:///android_asset/study-app.html`; nothing else is required for it to run,
  since it has no external script/asset dependencies (only optional Google Fonts
  `<link>` tags, which simply no-op offline and fall back to system fonts).
- **JavaScript / DOM Storage / File access enabled** — set on `WebSettings` in
  `MainActivity.configureWebView()`.
- **Offline support** — the entire app is local; no `INTERNET` permission is
  even declared in the manifest.
- **Splash screen** — `SplashActivity` uses `androidx.core.splashscreen`
  (`Theme.ReplyVault.Splash`), which renders the system Splash Screen API on
  Android 12+ and a compatible fallback on API 24–30, then hands off to
  `MainActivity` immediately so the WebView is never created just to show a
  splash frame (keeps cold start fast).
- **Material 3** — `Theme.ReplyVault` extends `Theme.Material3.DayNight.NoActionBar`,
  themed with the app's own amber/teal palette in `colors.xml`.
- **Edge-to-edge UI** — `enableEdgeToEdge()` + transparent system bars +
  window-insets padding applied to the native error view (the WebView content
  itself draws under the system bars, as is standard for edge-to-edge web content).
- **Back button support** — `OnBackPressedDispatcher` callback: goes back through
  WebView history first, then falls through to normal system back behavior.
- **Download support** — a single `DownloadListener` handles three cases:
  `data:` URIs (decoded directly), `blob:` URIs (pulled out via a small JS
  bridge — `window.AndroidDownloader` — since blobs can only be read from the
  page's own JS context), and regular `http(s)` URLs (handed to
  `DownloadManager`). Files land in the public Downloads folder via
  `MediaStore` on API 29+, or the legacy Downloads directory (with a runtime
  `WRITE_EXTERNAL_STORAGE` permission prompt) on API 24–28.
- **File picker support** — `WebChromeClient.onShowFileChooser()` is wired to
  an `ActivityResultContracts.GetContent()` launcher, so any `<input type="file">`
  in the page works.
- **Dark mode** — `res/values-night/themes.xml` provides the dark palette;
  `DayNight` parent theme switches automatically with system setting.
- **Keeps screen state on rotation** — `MainActivity` declares
  `android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|uiMode"`,
  so the Activity (and its WebView) is never destroyed/recreated on rotation —
  running timers, in-memory JS state, and scroll position all survive rotation
  untouched.
- **No internet required / no ads** — no `INTERNET` permission, no ad SDKs, no
  analytics, no third-party dependencies beyond standard AndroidX/Material libraries.
- **Fast startup** — minimal `Application` class, no heavy initialization,
  lightweight `SplashActivity` that defers WebView creation to `MainActivity`.

## Launcher icon

`icon.svg` was converted into:
- An **adaptive icon** (API 26+): `ic_launcher_background` (a solid color,
  `#0E1420`, matching the original SVG's background rect) + `ic_launcher_foreground`
  (a hand-converted vector drawable of the crescent-moon mark, glow, and
  progress bar — SVG path data maps almost 1:1 onto Android's vector drawable
  path-data syntax, so this is a faithful, infinitely-scalable recreation, not an
  approximation).
- **Legacy PNG launcher icons** (API <26) at mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi,
  rasterized directly from `icon.svg` (including its rounded-square background),
  for `ic_launcher.png` and `ic_launcher_round.png`.
- A 512×512 `play-store-icon.png` at the project root for use in the Play
  Console listing (not packaged into the APK).

## Notes / things you may want to adjust

- The HTML references Google Fonts over `https://fonts.googleapis.com`. Since
  the app has no `INTERNET` permission, these requests simply fail silently and
  the page falls back to the closest system font — everything still renders
  correctly. If you'd like pixel-perfect font matching offline, download the
  three font families (Fraunces, Inter, JetBrains Mono) as `.ttf`/`.woff2`
  files, place them under `app/src/main/assets/fonts/`, and swap the
  `<link>` tag in `study-app.html` for local `@font-face` rules.
- `WRITE_EXTERNAL_STORAGE` is declared with `android:maxSdkVersion="28"` — it
  is never requested or needed on Android 10+, where `MediaStore` handles
  downloads without any permission.
- No `INTERNET` permission is present at all — if you later add a feature that
  genuinely needs the network, remember to add it back to the manifest.
