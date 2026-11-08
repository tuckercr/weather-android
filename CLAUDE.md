# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## The product

Sunny & Misty is a weather app in which Sunny (a dog) and Misty (a cat) act out the forecast, with
walk advice for dog owners and notifications. This repo is the Android app (Kotlin, Jetpack Compose,
minSdk 29). The iOS app is a separate repo, tuckercr/weather-iOS (checked out at
`~/xcode/cats-dogs-iOS`). The two share one design, one Firebase project and the same Remote Config
keys, and features ship on both with the same wording. iOS comments often name their Android
counterpart ("Same rules as Android's `util/StaleWeather.kt`"): keep the two in step, or record the
gap in NOTES.md.

- The app is only ever called "Sunny & Misty"; the package is `com.fangjet.weather`. "Furcast" was
  an earlier name that was dropped: don't use it.
- README.md documents behaviour in detail (walk rules, every Remote Config key and default,
  notifications, analytics gates, Play Store assets). Update it when behaviour it describes changes.

## Commands

```bash
./gradlew assembleDebug                    # debug APK, installs as com.fangjet.weather.debug
./gradlew ktlintCheck                      # style; CI fails on it
./gradlew lint                            # Android Lint (e.g. RestrictedApi); CI fails on errors
./gradlew test                             # JVM unit tests in :app and :weather-api
./gradlew :app:testDebugUnitTest --tests 'com.fangjet.weather.worker.WorkManagerSchedulerTest'
./gradlew :weather-api:test --tests 'com.fangjet.weather.data.ForecastAggregatorTest'
./gradlew :app:connectedDebugAndroidTest   # Compose UI tests on an emulator
./gradlew :app:bundleRelease               # signed release bundle
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- Run a single test with the module's own task, as above: `--tests` on the root `test` task fails
  in the module that has no matching test.
- Release builds need `OWM_API_KEY` and the keystore entries (`KEYSTORE_PATH`, `KEYSTORE_PASSWORD`,
  `KEY_ALIAS`, `KEY_PASSWORD`) in `local.properties`, and stop at the start if any is missing. Bump
  `build` in `app/build.gradle.kts` for every Play upload; build N is version 1.0.(N-1). Without `app/google-services.json`
  Firebase does nothing and Remote Config uses the in-app defaults.
- Connected tests uninstall the app afterwards, wiping its data: run them on an emulator, never on
  the user's phone. Installing a debug build on the phone is fine; don't drive its UI.
- The debug build can be inspected over adb: `adb shell run-as com.fangjet.weather.debug` reaches
  the DataStore (`files/datastore/weather_prefs.preferences_pb`) and the WorkManager database
  (`no_backup/androidx.work.workdb`), and `adb shell dumpsys jobscheduler` shows when jobs are due.
- Debug builds can post sample notifications or run the real workers now from adb; README has the
  commands ("Trying notifications").

## Architecture

- **Modules.** `:weather-api` holds the API DTOs and parsing and the weather, geocoding and radar
  repositories, including `ForecastAggregator` and `OpenMeteoForecastSource`. It stays free of
  Firebase: `ForecastSourcePolicy` carries the `open_meteo_enabled` switch in from the app. `:app`
  has everything else.
- **`:app` layout.** `data/` (`PreferencesRepository` on DataStore: cities, settings and the
  per-city cache; `RemoteConfigRepository`), `domain/` (`PetWeatherThresholds`, `SeasonalTheme`),
  `model/` (the ViewModels, exposing `StateFlow`; `WeatherForecastViewModel` holds the screen's
  clock, moved on by `tick()`, and builds the `WeatherScreen` the weather screen draws;
  `CurrentWeatherViewModel` runs the screen's refresh and My Location steps), `ui/`
  (Compose screens; `navigation/WeatherNavHost`
  routes onboarding and screens; `ui/pets/` has the moods, the scene and `WalkAdvisor`), `util/`
  (`StaleWeather`, `NextRain`, `TodayDetails` and other pure rules), `worker/`, `analytics/`, `di/`
  (Hilt).
- **Forecasts.** Cities with coordinates get their current reading and forecast from Open-Meteo
  (hourly, UV, pavement heat, the 15-minute precipitation behind `NextRain`, air quality in the
  same request), with OpenWeather's reading as the backup and My Location named by the phone
  (`DevicePlaceNames`); name-only cities fall back to OpenWeather's reading and 3-hourly forecast. Open-Meteo's WMO codes are mapped to
  OpenWeather-style condition and icon codes, so moods and icons work the same for both.
- **Cache first.** `data/LocationWeatherRepository` is the one way to a saved location's weather:
  its cache, and refreshes that fetch in the user's units and cache the result. The weather view
  model and the workers all use it. Cached weather and forecast paint at once and stay on screen
  if a refresh fails; `util/StaleWeather.kt` decides how an old reading is shown.
- **Time zones.** Everything shown for a city uses the city's UTC offset
  (`CurrentWeather.offsetSeconds`), including "today", night captions and seasonal themes. Only the
  8:30 briefing uses the device clock.
- **Pets.** `petMoodFor` maps conditions to one of 9 moods, which choose the hero card's colour, ink
  and caption; `PetScene` draws the pets. A `SeasonalTheme` (built-in `res/raw/seasonal_themes.json`,
  replaceable from Remote Config `seasonal_themes`) can override the hero colours, caption and
  decorations on its dates. `WalkAdvisor` rates walks for the walk card, the briefing and the walk
  nudge, with `PetWeatherThresholds` (Remote Config, validated, always °C).
- **Background work.** `WorkManagerScheduler` (called from `WeatherApplication.onCreate`) schedules a
  periodic `WeatherUpdateWorker` refresh (Remote Config interval; rain alerts are checked after each
  refresh) and the daily `WeatherNotificationWorker` at 8:30, which posts the briefing and plans a
  one-off `WalkTimeWorker` that checks the weather again when it's due. Each 8:30 run sets the next
  one back to 8:30 with WorkManager's next-schedule override, since daily periodic work otherwise
  drifts later after every late run. The logic lives in plain `*Logic` classes so it runs in JVM
  tests. `NotificationPoster` posts on one channel per kind: Walk time only exists with a dog and
  Rain alerts only while they're on, and channels are never deleted.
- **Remote Config.** `RemoteConfigRepository` reads every key with an in-app default and validation,
  so a bad console value can't break the app. The parameters are deployed from this repo
  (`firebase/remoteconfig.template.json`, see `firebase/README.md`).

## Working on this project (the same in both repos)

- **NOTES.md** lists deferred items, open decisions and ideas, with identical content in both
  repos. Add anything deferred or left undecided to both; move finished items to Done.
- **Git.** Branch from `origin/main` (Android: `origin/master`) and open a PR per change; don't
  push without being asked. `local/device-build` is a local-only branch that merges unpushed
  branches for installing on the user's phones: never push it or branch from it. Force-pushing is
  blocked.
- **Commits and PRs.** Subjects read "Area: what changed" ("Hero: day or night by the clock for an
  old cached reading"), and the body says why. Never add AI attribution (no "Generated with Claude
  Code", no `Co-Authored-By: Claude`) to commits, PRs or comments, even if a tool's defaults add
  one; write PR bodies with `--body-file`, not `--fill`.
- **Public repos.** Both repos are public: keep phone numbers, personal emails, device identifiers
  and other personal details out of committed files. `listing-review.html` (iOS) is a local review
  page kept out of git with `.git/info/exclude`.
- **Secrets.** Never print or commit `Secrets.plist` and `GoogleService-Info.plist` (iOS),
  `local.properties` and `app/google-services.json` (Android).
- **Copy.** No em dashes in user-facing text. British spelling, as in the rest of the app ("colour",
  "grey"). Captions are in the pets' voice. Keep wording identical across the two apps.
- **Ask first** before anything outward-facing: pushes, PRs, Remote Config deploys, TestFlight or
  Play uploads, and App Store Connect or Play Console changes.
- **Remote Config.** The template lives in the Android repo (`firebase/remoteconfig.template.json`).
  A key's default must match there, in Android's `remote_config_defaults.xml` and
  `RemoteConfigRepository.kt`, and in iOS's `RemoteConfigRepository.swift`. Before deploying
  (`firebase deploy --only remoteconfig` from the Android repo), fetch what's live with
  `firebase remoteconfig:get -o <file>` and compare, so the deploy changes only what's intended.
- **Timing.** For notifications and background work, check when they actually run on a device
  (Android Doze and WorkManager, iOS background refresh), not just that the code schedules them.
