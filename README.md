# Sunny & Misty

![Android CI](https://github.com/tuckercr/weather-android/actions/workflows/android.yml/badge.svg)
![ktlint](https://github.com/tuckercr/weather-android/actions/workflows/ktlint.yml/badge.svg)

A clean, modern Android weather app, where Sunny the dog and Misty the cat act out the forecast.
Track current conditions, an hourly and multi-day forecast, and an animated precipitation radar for
any number of saved cities, see when to walk the dog, get a morning briefing, walk-time nudges and rain alerts, and switch
between metric and imperial units. Built with Jetpack Compose and Material 3.

There is also a native iOS version of the same product, built in Swift and SwiftUI:
[tuckercr/weather-iOS](https://github.com/tuckercr/weather-iOS). The two apps share one design,
one Firebase project and the same Remote Config keys, and new features ship on both.

Originally a coding-challenge project, the app has since been extended as a personal showcase of
Android best practices: clean architecture, unidirectional data flow, Hilt DI, background work with
WorkManager, and CI on GitHub Actions.

## Features

- **Current conditions** for any city: temperature, feels-like, today's high and low (from the
  day's forecast, over all of its hours), humidity, wind
  speed, direction and gusts ("0 mph" and "Calm" when calm), visibility ("Clear" from
  10 km), cloud cover, UV index (daytime, from Open-Meteo), and sunrise/sunset.
- **Pets that react to the weather**: Sunny the dog and Misty the cat, drawn in Compose, whose scene
  and caption follow the conditions (fair, sunny, cloudy, rain, storm, snow, hot, cold, night). A
  clear day follows the Feels Like tile's bands: fair (a pale yellow card and the sun) when it's
  chilly or mild, sunny (amber, Sunny in sunglasses) when it's warm, above `walk_comfort_max_c`,
  and hot from `pet_hot_threshold_c`. Captions
  name them ("Sunny wants puddles. Misty wants a towel."), and at night follow the city's time of
  night (evening, late night, before dawn). The caption follows the walk verdict (see **One walk
  verdict** below): when now isn't the time for a walk, captions that suggest one ("Perfect walkies
  weather! Off we go.") are left out, and when the reason is one the scene can't show it speaks to
  that instead, in place of a seasonal theme's caption too: bad air by its pollutant ("Smoky air
  today. Quick potty breaks, then back on the sofa."), hot pavement, or a gale and worse. The night
  sky shows the moon's real phase, lit from the other side in the southern hemisphere.
- **Best Walk Time** card: "Now is a great time", the next good hour ("Around 8 AM"), or "Not a good
  day for walkies" when nothing in the next 24 hours qualifies. A good hour is under 30% rain
  chance, at a comfortable feels-like temperature, within walking hours and in daylight, and
  below the warm-pavement limit (estimated, with Open-Meteo); when it is raining now, now never counts,
  whatever the hourly forecast says. A suggestion on the city's next calendar day reads "Tomorrow
  around…", judged in the city's own UTC offset. The paw is tinted by the rating, and a caption
  gives the reason when it's more than a wait: the pavement now ("Paw check: about 55° now. Too hot
  for paws."), heat or cold, the air by its pollutant ("Smoky air. Potty breaks only.", "High
  ozone", "Dusty air"), a gale ("Gale force winds. Potty breaks only.") or thunderstorms. At
  hurricane force the card becomes a "Danger" warning ("Hurricane force winds. Stay inside, away
  from the windows."), shown even without a dog. Users can turn it off in Settings (*I have a
  dog*, which hides the Paw Check tile too), and `walk_card_enabled` hides it remotely.
- **Bad air in the walk planner**: hours at US "Unhealthy" or worse (Europe's "Poor" and up) are
  never suggested. The band is tunable with `walk_bad_air_band`.
- **One walk verdict**: `walkCheckFor` (`ui/pets/WalkCheck.kt`) judges an hour once, and the hero's
  caption, the walk card, the Air Quality tile's note and the notifications all read it, so they
  can't disagree. The screen gets it from `WeatherForecastViewModel.walkAdvice`, and the notification
  worker from the same `walkAdviceForReading`. Each factor has a small rule and a level, and the worst one wins:

  | Level | When |
  |---|---|
  | Great | Nothing in the way |
  | Fine | A walk, with a caveat: air one band short of bad, for older and flat-faced dogs |
  | Wait | Rain or snow, a near gale (Beaufort 7), pavement getting hot, feels-like outside the comfort range, dark or outside the walking hours |
  | Potty breaks only | Pavement too hot, too hot or too cold for pets, bad air (`walk_bad_air_band`), a gale (Beaufort 8) |
  | Stay in | Dangerously hot pavement, the worst air band, thunderstorms, storm force wind (Beaufort 10) |
  | Danger | Hurricane force wind (Beaufort 12) or gusts, or hurricane force wind forecast in the next 6 hours |

  The reason carries its detail (the pollutant, the wind's force, the pavement's band) for each
  screen to word. At the same level storms come first, then rain and snow, wind, the pavement,
  heat, the air, cold and the dark. A walk now is fine or better; the hour under way is judged by
  the current reading's feels-like, condition, wind and gusts (with the worse of rain now and the
  hour's storms), the rest by the forecast. With `air_quality_enabled` off the air is left out, even
  from a forecast cached before it went off, and without a dog (*I have a dog* off) so is the
  pavement.
- **Next rain** line under the top card, only when rain or snow is due in the next 2 hours: "Rain
  starting around 3:15 PM", "Rain stopping around 4:30 PM" or "Rain for the next 2 hours", from
  Open-Meteo's 15-minute forecast (0.1 mm in 15 minutes counts; it stops after 30 dry minutes).
  Rules in `util/NextRain.kt`. `next_rain_enabled` hides it remotely.
- **Hourly strip** with the next 24 forecast slots (hours from Open-Meteo, 3-hour slots on the
  OpenWeatherMap fallback): time, condition icon and temperature, with the rain chance (hidden
  under 10%, otherwise to the nearest 10%, as everywhere in the app) under a smaller icon in the
  icon's own space. Every hour is the same height, so the card never changes height with the rain
  and never ends in a gap. Hours that have
  already passed are dropped at display time (`fromCurrentSlot`), so a cached forecast or an app
  left open never shows or advises on past hours. `hourly_forecast_enabled` hides the strip
  remotely.
  Each sunrise and sunset still to come sits between the hours it falls between, as a slim
  marker: the time ("6:50", the hours either side give AM or PM), a sun on the horizon with an
  arrow up or down, and a thin line. The times are Open-Meteo's daily sunrise and sunset, so the
  evening's strip has tomorrow's sunrise; a day without one (polar day or night) has no marker.
  Cities on the OpenWeatherMap fallback only have today's, from the current reading. TalkBack
  reads "Sunset, 6:50 PM". Rules in `util/SunEvents.kt`.
- **Rain outlook** card straight after the hourly strip (before TODAY on phones, before the radar on
  tablets and in landscape): the chance of rain for each of the next 10 hours as bars, with a dashed
  line at 50%. The headline says when rain is likeliest ("Rain likely around 4 PM" from 50%, "A
  chance of rain now" below that), and the detail when it dries up ("Drying out after 7 PM." or
  "The chance stays up past 12 AM.") and, from 0.5 mm (0.01 in), how much falls in all ("About 2.5
  mm in all."). Tap a bar for its hour, chance and amount. The hourly icon comes from Open-Meteo's
  best single model and the chance from its ensemble, so they can disagree; the chart shows the
  outlook plainly. It only appears when some hour reaches 20%, and then takes the place of the
  next-rain line, using its words as the headline. Rules in `util/RainOutlook.kt`;
  `rain_outlook_enabled`, `rain_outlook_hours` and `rain_outlook_min_chance` tune it remotely.
- **Today tiles**: Feels Like, Wind, Fluff Factor, Paw Check (with a dog) and Air Quality in pairs, then
  Daylight (with the UV index) and Misty's View (the sky and how far she can see) across the full width. An odd tile out, usually Air Quality
  when Paw Check is showing, takes the full width rather than leaving a gap.
- **Feels Like** tile: the feels-like temperature with its band beside it (Cold, Chilly, Mild, Warm
  or Hot), a five-colour bar and how far it is from the actual temperature ("8° warmer than it is",
  or "Same as it is" within a degree). The bands come from the pets' thresholds in Remote Config:
  Cold and Hot at `pet_cold_threshold_c` and `pet_hot_threshold_c`, Mild across the walk comfort
  range, and Chilly and Warm between. The bar's end bands reach 10 °C past the pets' limits.
- **Wind** tile: the speed beside a windsock drawn in Compose, which hangs limp when it's calm and
  lifts steadily until it flies straight out from a strong breeze (24 mph, 10.8 m/s). Underneath,
  where the wind comes from ("From the SW") and the gusts when they're at least 5 mph (2.2 m/s)
  above the wind ("From the SW, gusts 24"); "Calm" when there's none. TalkBack also hears the
  Beaufort name ("moderate breeze"). Gusts come with the current reading from both providers.
- **Paw Check** tile (with a dog): the estimated temperature of dark pavement in direct sun ("About
  128 °F"), five paws from icy to dangerous with the pavement's own full size and colour (blue,
  green, orange, red and maroon), and a line about paws ("Too hot for paws"). Icy and below freezing
  share the blue paw, safe and fine underfoot the green one. The limits are the `walk_*_pavement_c`
  Remote Config keys.
- **Fluff Factor** tile: the humidity with the dew point under it ("Dew point 50°"), beside Misty,
  whose fur goes from sleek to full floof as it gets muggy, and her line about it. Mugginess goes by
  dew point, since humidity alone rises and falls with the temperature: Sticky from 16 °C / 61 °F
  ("Misty's fluffing up"), Muggy from 18 °C / 64 °F ("Puffy fur, panting pup", or "Misty's getting
  puffy" without a dog) and Soupy from 21 °C / 70 °F ("Misty has gone full floof"). Below that it's
  Dry under 35% humidity (static sparks, "Dry air, static fur") and Comfy otherwise ("Misty's fur
  is behaving").
- **Air Quality** tile: the index on the city's scale (Europe's for cities on a European clock, the
  US one elsewhere), its band on a six-colour bar, and what it means for a walk, or for people
  without a dog. TalkBack also reads the main pollutant: the one with the highest index, except that
  particles are dust ("coarse particles") when PM2.5 is under a third of PM10 by mass, since a dust
  storm can push PM2.5's index off the scale while PM10 gets none. The walk card, hero and
  notifications name it ("Smoky air", "Dusty air", "High ozone"). It comes from Open-Meteo's hourly
  air quality forecast, fetched with the forecast in one request and cached with it, so it shows
  offline too. `air_quality_enabled` switches it off.
- **Update prompts**: below `android_min_supported_version` the app shows only "Time for an update";
  below `android_min_recommended_version` a banner offers the update, and *Not now* hides it for a
  week. Both open the Play Store listing.
- **Weather icons** bundled as vector drawables, drawn in the pets' style with their navy outline so
  they read on light and dark cards. Night icons show the moon's phase for that hour, worked out
  from the date (no API).
- **Multi-day forecast** shown inline, aggregated to the city's local-timezone days with tappable
  day details: each hour's temperature and rain chance. `week_forecast_enabled` hides the rest of
  the week's day rows remotely.
- **Rain probability** surfaced per hour and per day.
- **Animated radar**: a RainViewer precipitation loop (recent past plus short-range nowcast) over
  an OpenStreetMap base, with a timeline scrubber, colour legend, play/pause, and an
  "© OpenStreetMap contributors · Radar by RainViewer" credit linked under the map. The timeline is
  refetched once it is 10 minutes old (on resume and while the screen stays resumed), and the whole
  section can be switched off remotely with `radar_enabled`.
- **Multiple saved cities** with a scrollable tab row (once two or more are saved) and a Manage
  Locations screen: tap a city to make it active, drag to reorder (TalkBack users get Move up and
  Move down actions), or remove it.
- **Current location** resolved with one tap via fused location services.
- **Local time everywhere**: forecast days, hourly slots, sunrise/sunset, and the radar all render
  in the selected city's timezone.
- **Notifications** for one city, My Location when it's saved and otherwise the first in the list,
  each with its own switch in Settings and its own Android channel:
  - *Morning briefing* at 8:30 AM device time (on): "52° and partly cloudy in Knoxville / High
    70°, low 45°, 60% chance of rain. Sunny's picked 1 PM for a walk." The walk sentence only comes
    with a dog at home; the rain chance from 40%.
  - *Walk time* (only with a dog): the morning run plans a nudge for the day's best walk time, which
    checks again when it's due and only goes if now is still a great time (and there's still a dog).
  - *Rain alerts* (off): the background refresh alerts up to an hour before rain or snow starts,
    from the 15-minute forecast, between 7 AM and 9 PM city time, once per shower. Switched off for
    everyone in the first release (`rain_notifications_enabled`), since an alert depends on a
    refresh landing in the hour before the rain.
  `daily_weather_notifications_enabled` switches the briefing off remotely.
- **Unit selection**: System default, Celsius (metric), or Fahrenheit (imperial), chosen in
  Settings. Visibility follows in miles or km (see [Units](#units)).
- **Offline-first**: per-city caching paints instantly, then refreshes silently in the background,
  with cached-data fallback, pull-to-refresh, and inline retry when the network fails. The data
  also refreshes on returning to the foreground (throttled by `foreground_refresh_min_minutes`) and
  every 30 minutes while the screen stays open, and every refresh moves the clock on, so a screen
  left on overnight keeps up.

## Screenshots

| Pets, walk time & hourly | Today tiles & air quality | Radar & rest of week | Day detail | Settings |
|---|---|---|---|---|
| ![Sunny and Misty react to the weather, with best walk time and the hourly strip](./screenshots/current.png) | ![Today's tiles: feels like, wind, humidity and Paw check in pairs, then Air quality, daylight and Misty's View across the full width](./screenshots/today.png) | ![Animated RainViewer radar and the rest of the week](./screenshots/forecast.png) | ![Hour-by-hour temperature and rain chance for a day](./screenshots/day.png) | ![Settings with units, I have a dog and notifications](./screenshots/settings.png) |

On tablets and in landscape, the pets sit beside today's details and the week splits into two columns:

![Phone in landscape on a rainy day, with the pets beside today's details](./screenshots/landscape.png)

![Tablet layout with the pets beside today's tiles, Air quality across the full width](./screenshots/tablet.png)

## Getting started

1. Create a free API key at [openweathermap.org](https://openweathermap.org/api).
2. Add it to `local.properties` (never committed to source control):
   ```
   OWM_API_KEY=your_key_here
   ```
3. Build and run. New keys may take up to 2 hours to activate.

The key is injected at build time into `BuildConfig.OWM_API_KEY`. On GitHub it is stored as an
Actions secret and injected automatically, so no key lives in the repository.

Firebase is optional: without `app/google-services.json` the app builds and runs normally, with
Remote Config falling back to its in-app defaults and analytics and crash reporting doing nothing.
See [Firebase setup](#firebase-setup) to wire it up.

Debug builds install as `com.fangjet.weather.debug` (version name suffixed `-debug`), so they sit
alongside the Play build and report to their own Firebase app.

The app supports Android 10+ (minSdk 29) and targets SDK 37. From the command line:

```bash
./gradlew assembleDebug                   # debug APK
./gradlew test                            # JVM unit tests (:app and :weather-api)
./gradlew :app:connectedDebugAndroidTest  # Compose UI tests on a device or emulator
./gradlew ktlintCheck                     # code style
```

### Release builds

Release builds are minified and resource-shrunk with R8, and signed with the keystore that
`local.properties` names in `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`.
A release APK or bundle won't build without those or without `OWM_API_KEY`: the build stops at the
start and lists what's missing, rather than producing an unsigned app or one with no weather. Debug
builds, tests and lint don't need them. CI writes these from the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
and `KEY_PASSWORD` Actions secrets when they are set, and uploads the debug and release APKs as
build artifacts.

### Play Store

[`screenshots/play-store/`](screenshots/play-store/) holds the store screenshots, taken on an
emulator with a clean status bar:

- Phone, 1080 × 1920 (9:16; Play rejects anything longer than 2:1, so not the Pixel's 1080 × 2400).
  Upload in this order: `01-welcome`, `02-today`, `03-rain-walk`, `04-radar-week`,
  `05-day-detail`, `06-settings`.
- Tablet: `tablet-01` (1600 × 2560, portrait) and `tablet-02` (2560 × 1600, landscape), for both
  the 7-inch and 10-inch tablet slots.

## Architecture & tech stack

| Layer | Choice | Notes |
|---|---|---|
| UI | Jetpack Compose + Material 3 | Single-Activity, screen-level composables, light/dark |
| State | `StateFlow` + `collectAsStateWithLifecycle` | Unidirectional data flow |
| DI | Hilt | Repositories and ViewModels are injected |
| Navigation | Compose Navigation | String route constants in `WeatherDestinations` |
| Background work | WorkManager | Periodic weather refresh (and rain alerts) + the 8:30 AM briefing and walk-time nudge |
| Networking | Retrofit + OkHttp + kotlinx.serialization | Suspend functions, no RxJava |
| Radar | RainViewer + OpenStreetMap tiles | Animated precipitation loop stitched on a Compose `Canvas`; frame speed via Remote Config |
| Images | Coil, vector drawables | Coil loads the radar and base-map tiles; the weather icons are bundled vector drawables |
| Persistence | DataStore Preferences | Onboarding flags, saved cities, active city, unit override, the walk card and *Share usage statistics* settings, per-city cache |
| Analytics | Firebase Analytics, Remote Config, Crashlytics | Optional; no-op without `google-services.json`. No advertising ID; user opt-out and a remote kill switch; Crashlytics in release builds only |
| Modules | `:app`, `:weather-api` | API DTOs, parsing, and the weather, geocoding, and radar repositories split into `:weather-api`, which stays free of Firebase (`ForecastSourcePolicy` carries `open_meteo_enabled` in from the app) |
| Data sources | OpenWeatherMap `/weather` & `/forecast` + Geocoding; Open-Meteo 7-day hourly forecast; RainViewer radar frames; OpenStreetMap tiles | Free tiers; Open-Meteo, RainViewer, and OpenStreetMap need no key |
| CI | GitHub Actions | ktlint, Android Lint, JVM unit tests, debug and release builds, and Compose UI tests (API 34 emulator) on every push to and pull request against `master` |

## App flow

1. **Loading**: reads onboarding state from `DataStore` and routes to the right screen.
2. **Onboarding**: first launch walks through Welcome, notification permission, and location
   permission. Existing installs are migrated so they are never re-prompted.
3. **Current Weather**: shows the active city's conditions, an hourly strip, an animated
   precipitation radar, the pets and best walk time, and the upcoming days inline. Switch between
   saved cities with the tabs or pull to refresh. On My Location, pull to refresh reads the phone's
   position first, whatever the 15-minute limit, so My Location moves (by the usual 1 km rule)
   before its weather is fetched; otherwise My Location re-reads the position at launch and on each
   return to the app, at most every 15 minutes. With no locations yet, it offers *Add location*.
   For cities with coordinates the conditions are Open-Meteo's current reading for the spot, so
   they agree with the hourly strip; OpenWeatherMap's `data/2.5/weather` reading is the backup
   when Open-Meteo fails or `open_meteo_enabled` is off, and serves cities saved by name only. My
   Location is named by the phone's own lookup (Android's `Geocoder`): the town, else the county,
   else the state, kept until My Location moves 1 km (`DevicePlaceNames`), and "My Location"
   when the lookup fails.
4. **Add location**: a full screen, opened from *Add location* in Manage Locations or on the empty
   weather screen, and closed with its X. Results from the
   [Geocoding API](https://openweathermap.org/api/geocoding-api) appear right under the field, the
   name above its state and country, with the letters typed so far in bold. Tapping a row adds it
   (its coordinates are saved, so the result is unambiguous). The button above the keyboard says
   what it will do, and the keyboard's Search key does the same: "Add Malmö, SE" adds the first row
   named what was typed (accents ignored, so "Malmo" finds Malmö). Otherwise it asks "Pick a town
   or city from the list", as it also will when a row above the match has 20 times its population,
   so "Nas" never adds Nås, Sweden (results with populations come with the planned Open-Meteo
   search; OpenWeather's have none). A place already saved (the same label, or within 2 km; My Location aside)
   is marked *Saved* and its button reads "Go to London, GB", which shows its weather. If the search
   fails (offline, say), the button adds the typed name and its coordinates are looked up later.
   *Add My Location* shows while My Location isn't saved. After adding, the app goes back to where
   it was opened from; Manage Locations confirms with "Malmö added". Without location access it
   asks first, every time: Android doesn't say when it has stopped showing its dialog (after a
   second "Don't allow", or "Don't allow" in system settings) or started again ("Ask every time"),
   so the request is the test. A refusal that comes back within 350 ms had no dialog, so it opens
   the app's page in system settings instead, and My Location is added on return if location was
   allowed there. Onboarding's *Use my location* works the same way; coming back without allowing
   it moves on to entering a city.
5. **Forecast**: cities with coordinates use [Open-Meteo](https://open-meteo.com) (true hourly,
   with UV); cities without coordinates, or all cities when the `open_meteo_enabled` Remote Config
   switch is off, use OpenWeatherMap's `data/2.5/forecast` (free tier, 3-hour slots). Each calendar
   day shows the slot closest to local noon, plus the daily high/low and rain chance derived from
   all slots; tapping a day opens its full hourly breakdown.
6. **Radar**: RainViewer radar frames (recent past plus short-range nowcast) are stitched over an
   OpenStreetMap base and animated, with a scrubber, legend, and OpenStreetMap / RainViewer credit
   caption. All frame times display in the selected city's timezone. RainViewer publishes a frame
   every 10 minutes, so a timeline that old is refetched on resume and every 10 minutes while the
   screen is resumed; the current loop stays on screen during the refresh, and a failed refresh
   keeps it.
7. **Settings**, top to bottom:
   - **My Pets**: *I have a dog* turns on the walk card, the Paw Check tile, the briefing's walk
     sentence and the Walk time notification ("Walk times, pavement checks and walk reminders").
     The Walk time notification channel is only created once there's a dog, and never deleted.
   - **My Locations**: *Manage Locations* to reorder and delete locations, with *Add location* under
     the list. A bell marks the location notifications are for: My Location, otherwise the top
     location. The city tabs on the main screen choose which city it shows.
   - **Notifications**: a row showing how many are on ("2 on", or "Off"), which opens its own
     screen: a link to the app's system settings while notifications aren't allowed, then a switch
     for each kind (*Morning briefing*, *Walk time*, *Rain alerts*). Walk time only shows with a
     dog, and a kind switched off in Remote Config is hidden. A hidden switch keeps its value.
     With a My Location saved, *Follow my location* (off) lets the background refresh and the
     8:30 run move My Location to where the phone is first, so notifications follow you even when
     the app is closed; a move made while the app is open shows at once. Otherwise My Location only
     moves while the app is open. Turning it on
     explains why (Play's required disclosure), then asks for "Allow all the time", which opens
     the app's location page in system settings; with that already granted it just turns on. It
     shows on only while that's granted. Off, the background runs don't read the location at all.
   - **Location**: permission status in Android's words ("Location allowed all the time" or "only
     while using the app"), or a link to the app's system settings.
   - **Units**: System default, Celsius (°C) or Fahrenheit (°F).
   - **Cache**: clear cached data.
   - **Privacy**: *Share usage statistics*.
   - **About**: the OpenWeather and Open-Meteo attributions, the
     [privacy policy](https://sunnyandmisty.com/privacy), and the version at the bottom ("Version 1.0.0" for build 1,
     with "· Debug build" in debug builds).
8. **Notifications**: WorkManager runs the 8:30 AM briefing (unless `daily_weather_notifications_enabled`
   is off), which also plans the one-off walk-time nudge, and refreshes
   the notification city's cached data (My Location when saved, otherwise the first city), and the city tab last viewed when it's another one, in the background every `weather_refresh_interval_minutes`; the
   schedule survives reboots.
9. **Error handling**: network failures, HTTP errors, empty API keys, and malformed payloads all
   surface as inline error messages with *Retry* where appropriate.

### Trying notifications

Debug builds only (`app/src/debug`), from a computer with the phone on adb, to see each kind without
waiting for 8:30 or the weather. Release builds don't include it, and only the shell can send it
(the receiver needs `android.permission.DUMP`). Notifications must be allowed in the app.

**Samples** post made-up weather for the real notification city (My Location, otherwise the top
city) through the real posting code, so the look, the pet picture, the channel and the tap are all
real. The text follows *I have a dog* the way the real ones do.

```bash
adb shell am broadcast -n com.fangjet.weather.debug/com.fangjet.weather.DebugNotifications --es mode sample --es kind briefing
adb shell am broadcast -n com.fangjet.weather.debug/com.fangjet.weather.DebugNotifications --es mode sample --es kind walk
adb shell am broadcast -n com.fangjet.weather.debug/com.fangjet.weather.DebugNotifications --es mode sample --es kind rain
adb shell am broadcast -n com.fangjet.weather.debug/com.fangjet.weather.DebugNotifications --es mode sample --es kind snow
```

**The real workers** run now, with live weather and the real rules, so they may post nothing: the
walk nudge only when it's a great time for a walk right now (and there's a dog), rain only when the
15-minute forecast has rain within the hour, between 7 AM and 9 PM.

```bash
adb shell am broadcast -n com.fangjet.weather.debug/com.fangjet.weather.DebugNotifications --es mode run --es kind briefing
adb shell am broadcast -n com.fangjet.weather.debug/com.fangjet.weather.DebugNotifications --es mode run --es kind walk
adb shell am broadcast -n com.fangjet.weather.debug/com.fangjet.weather.DebugNotifications --es mode run --es kind rain
```

`run briefing` is the 8:30 run, so it also plans that day's real Walk time nudge. With more than one
device connected, add `-s <serial>` after `adb`. Forcing the scheduled job with
`adb shell cmd jobscheduler run` doesn't work: WorkManager won't run work before its time.

## Analytics & privacy

Sunny & Misty reports a handful of **anonymous app-usage events**: nothing about where you are,
which cities you save, or what the weather is. The whole catalogue is four functions in
[`AnalyticsRepository`](app/src/main/java/com/fangjet/weather/analytics/AnalyticsRepository.kt),
so the privacy policy can be checked against the code line by line.

| Event | Parameters | When |
|---|---|---|
| `onboarding_complete` | None | First-run onboarding finished |
| `city_added` | None | A city was saved (never which one) |
| `settings_opened` | None | Settings opened from the weather screen |
| `weather_error` | `error_key` = `offline` / `rate_limited` / `server_error` / `city_not_found` / `empty_query` / `missing_api_key` / `bad_api_key` / `generic` | A current-weather or forecast load failed (the category only, never coordinates or a city) |

### Gates

An event is sent only if every gate passes:

1. **Remote Config kill switch** `analytics_enabled`: false drops every event, and turns Firebase
   Analytics collection off, on every install without an app update.
2. **The user's own opt-out**: Settings → About → *Share usage statistics* (on by default). It is
   enforced at the SDK level via `setAnalyticsCollectionEnabled`, at app start and whenever it
   changes, so Firebase's automatic events (`first_open`, sessions, screen views) stop too, not
   just ours. Flipping it is deliberately not itself tracked.
3. **Firebase configured**: without `google-services.json` there is no Firebase app and nothing is
   sent.

Debug builds do send, but to their own `com.fangjet.weather.debug` app stream, so development
never pollutes the production numbers. Every analytics call is wrapped so it can never crash the
app; a failure just drops the event.

**No advertising ID.** The app shows no ads, so the manifest strips the `AD_ID` and
`ACCESS_ADSERVICES_*` permissions that `firebase-analytics` merges in, and turns off ad-ID
collection and ad-personalisation signals. Analytics keeps working on its app-instance ID.

Crashlytics is enabled in release builds only (off in debug).

The privacy policy is at [sunnyandmisty.com/privacy](https://sunnyandmisty.com/privacy), linked from
Settings → About → *Privacy Policy*.

## Remote Config

Server-tunable values, so behaviour can change without shipping an update. The
app never blocks on a fetch: it starts from the in-app defaults in
[`remote_config_defaults.xml`](app/src/main/res/xml/remote_config_defaults.xml), reads the last
activated values on cold start, and refreshes in the background at most hourly. Values activated
while the app is open apply at once: the weather screen, Settings, onboarding and the update prompt
read Remote Config again on each activation. Every read is
validated: a missing or bad value falls back to the default rather than crashing.

| Key | Type | Default | What it does |
|---|---|---|---|
| `weather_refresh_interval_minutes` | number | `30` | Background weather refresh interval; values below the WorkManager minimum of 15 are raised to 15 |
| `radar_frame_interval_ms` | number | `850` | Delay between radar animation frames; outside 100–5000 falls back to 850 |
| `radar_enabled` | boolean | `true` | Radar kill switch: false hides the radar card (phone and tablet layouts) and skips the RainViewer timeline and tile requests, for when RainViewer or the OpenStreetMap tiles become unavailable |
| `walk_card_enabled` | boolean | `true` | Shows the "Best Walk Time" card; false hides it for everyone (phone and tablet layouts). Users can also turn it off in Settings |
| `hourly_forecast_enabled` | boolean | `true` | Shows the hourly strip; false hides it for everyone (phone and tablet layouts) |
| `next_rain_enabled` | boolean | `true` | Shows the "Rain starting around 3:15 PM" line from the 15-minute forecast; false hides it for everyone |
| `week_forecast_enabled` | boolean | `true` | Shows the rest of the week's day rows; false hides them for everyone (phone and tablet layouts) |
| `rain_outlook_enabled` | boolean | `true` | Shows the rain outlook card; false hides it for everyone, and the next-rain line shows as before |
| `rain_outlook_hours` | number | `10` | Hours the rain outlook charts, from the current one; whole number, valid 6–12 |
| `rain_outlook_min_chance` | number | `20` | The rain outlook only shows when some hour reaches this rain chance (%), which is also the chance from which an hour counts as wet ("Drying out after …"); whole number, valid 1–100 |
| `temperature_units_shown` | boolean | `false` | True shows the unit with temperatures ("89 °F"); false shows "89°" wherever a temperature carries its unit: the hero, week rows, day details, and the Feels Like and Paw Check tiles ("About 81°"). Temperatures already shown without one (the hero's high and low, the hourly strip, the walk card, notifications) stay as they are, and the units themselves don't change. See [Units](#units) |
| `open_meteo_enabled` | boolean | `true` | The current reading and forecasts for cities with coordinates come from Open-Meteo; false falls back to OpenWeatherMap's reading and 3-hourly forecast by lat/lon (foreground, background refresh, and notifications) |
| `air_quality_enabled` | boolean | `true` | Fetches hourly air quality alongside the Open-Meteo forecast; false hides the Air quality tile and the walk planner ignores the air |
| `openweather_api_key` | string | `""` | Replacement OpenWeather API key, used from the next app start; empty uses the key built into the app. Lets a leaked key be rotated without an update. Not secret: Remote Config values reach every device |
| `android_min_supported_version` | string | `""` | Below this version (e.g. `1.1`) the app shows only "Time for an update" until it's updated. Empty never asks. iOS has its own key |
| `android_min_recommended_version` | string | `""` | Below this version a banner under the city tabs suggests updating; "Not now" hides it for a week. Empty never asks |
| `daily_weather_notifications_enabled` | boolean | `true` | The 8:30 AM morning briefing; false skips it (the morning run still plans the walk-time nudge) and hides its switch in Settings |
| `walk_time_notifications_enabled` | boolean | `true` | The Walk time nudge; false stops planning and posting it and hides its switch in Settings |
| `rain_notifications_enabled` | boolean | `false` | Rain alerts; false stops them and hides their switch in Settings (keeping the user's choice), and the Rain alerts channel isn't made. Off for the first release |
| `foreground_refresh_min_minutes` | number | `0` | Skips the silent refresh on returning to the foreground if that city's weather (or forecast, tracked separately) was fetched within this many minutes in this session; outside 0–1440 falls back to 0, which always refreshes. Pull-to-refresh, retry and switching cities are never throttled |
| `walk_hot_pavement_c` | number | `46` | Pavement °C (46 is about 115 °F) at or above which the walk rating is *Paws hot* and that hour is not walkable; valid 40–70 |
| `walk_warm_pavement_c` | number | `40` | Pavement °C at or above which the Today Paw Check tile says *Getting hot* and that hour is not suggested for a walk (a walk now rates *Okay*); valid 30–65, and below `walk_hot_pavement_c` (otherwise 7 below it) |
| `walk_dangerous_pavement_c` | number | `57.2` | Pavement °C (57.2 is 135 °F) at or above which the Today Paw Check tile says *Dangerously hot*; valid 45–80, and above `walk_hot_pavement_c` (otherwise 5 above it) |
| `walk_icy_pavement_c` | number | `0` | Pavement °C below which the Today Paw Check tile says *Icy, watch for salt* after rain or snow in the last 12 hours or with snow on the ground, and *Below freezing* otherwise; valid -20–10 |
| `walk_max_rain_chance` | number | `30` | An hour is walkable only below this rain chance (%); whole number, valid 0–100 |
| `walk_comfort_min_c` | number | `3` | Coldest comfortable walking temperature (°C, feels-like); valid -20–20, and below `walk_comfort_max_c` |
| `walk_comfort_max_c` | number | `27` | Warmest comfortable walking temperature (°C, feels-like); valid 15–40, and above `walk_comfort_min_c` |
| `walk_hours_start` | number | `7` | First local hour a walk may be suggested (inclusive); whole number, valid 0–23, and before `walk_hours_end` |
| `walk_hours_end` | number | `20` | Last local hour a walk may be suggested (inclusive); whole number, valid 0–23, and after `walk_hours_start` |
| `pet_hot_threshold_c` | number | `30` | At or above this °C (feels-like) the pets look hot and the walk rating is *Paws hot*; valid 20–45, and above `pet_cold_threshold_c` |
| `pet_cold_threshold_c` | number | `0` | At or below this °C (feels-like) the pets look cold and the walk rating is *Too cold*; valid -20–10, and below `pet_hot_threshold_c` |
| `walk_bad_air_band` | number | `3` | Air quality band (0 good to 5 worst) from which a walk is potty breaks only: hours aren't suggested, the walk card and hero say why, and the Air Quality tile says "Potty breaks only today". 3 is US "Unhealthy" and Europe's "Poor"; the worst band (5) keeps pets inside whatever this is; valid 1–5 |
| `analytics_enabled` | boolean | `true` | Analytics kill switch; see [Gates](#gates) |
| `tile_feels_like_enabled`, `tile_wind_enabled`, `tile_humidity_enabled`, `tile_paw_check_enabled`, `tile_daylight_enabled`, `tile_sky_enabled` | boolean | `true` | One switch per Today tile (`tile_sky_enabled` is Misty's View, the sky and visibility tile); false hides that tile for everyone |
| `tile_uv_enabled` | boolean | `false` | The UV index tile; while off, UV stays in the Daylight tile's header (it still gets its own tile when there's no Daylight tile) |
| `seasonal_themes_enabled` | boolean | `true` | Seasonal themes (Halloween and so on); false switches them all off |
| `seasonal_themes` | string | the built-in list | The themes as a JSON list (format: `SeasonalTheme`), replacing the list built into the apps (`res/raw/seasonal_themes.json`) when valid; empty keeps it. A new theme for a fixed date, a date range or an nth weekday needs no app update |

The walk and pet-mood thresholds are always Celsius, whatever units the user picks. A value outside
its valid range (or a fractional value for a whole-number key) falls back to its default, and so
does any pair that isn't strictly ordered (comfort min/max, walk hours start/end, pet cold/hot):
both values of the pair revert together. Validation lives in
[`PetWeatherThresholds`](app/src/main/java/com/fangjet/weather/domain/PetWeatherThresholds.kt).

[`firebase/remoteconfig.template.json`](firebase/remoteconfig.template.json) is the source of truth
for the console parameters; keep it, `remote_config_defaults.xml`, and
[`RemoteConfigRepository`](app/src/main/java/com/fangjet/weather/data/RemoteConfigRepository.kt)
in step.

## Firebase setup

The app is wired to the Firebase project **`cats-dogs-weather`** (Analytics, Remote Config,
Crashlytics). To build against it, or your own:

1. Create a project at [console.firebase.google.com](https://console.firebase.google.com) **with
   Google Analytics enabled**, as events silently no-op if the project has no linked Analytics
   property.
2. Register **two** Android apps under it: `com.fangjet.weather` and `com.fangjet.weather.debug`.
   Debug builds use an `applicationIdSuffix`, and the `google-services` plugin matches package
   names exactly.
3. Download the merged `google-services.json` to `app/`. It is git-ignored; each developer
   supplies their own. CI restores it from the base64-encoded `GOOGLE_SERVICES_JSON` Actions secret
   when that is set, and builds without Firebase when it isn't.
4. Deploy the Remote Config parameters from this repo (the default project is set in
   [`.firebaserc`](.firebaserc)):
   ```bash
   firebase deploy --only remoteconfig
   ```
   `firebase remoteconfig:get` shows what is live. See [`firebase/README.md`](firebase/README.md).
5. In Analytics settings, set data retention to **14 months**.

## Unit tests

JVM unit tests (JUnit 4, MockK, and `kotlinx-coroutines-test`) live in `weather-api/src/test` and
`app/src/test`; `./gradlew test` runs both.

| Module | Test class | What it covers |
|---|---|---|
| `:weather-api` | `ForecastAggregatorTest` | Noon-slot selection, grouping by the city's local days (not UTC), daily min/max and rain chance, chronological hourly slots |
| `:weather-api` | `OpenMeteoForecastSourceTest` | Open-Meteo mapping: drops past hours, city-timezone grouping and labels (half-hour offsets, DST), weather codes, UV, pavement heat, each hour's rain amount |
| `:weather-api` | `HourlySlotTest` | A forecast cached before hourly rain amounts still decodes, and the amount is cached |
| `:weather-api` | `WeatherHelpersTest` | WMO weather-code mapping to conditions and day/night icons; pavement-heat estimate |
| `:weather-api` | `OpenWeatherParsingTest` | Kotlinx Serialization parsing of OpenWeatherMap current-weather and forecast payloads, including the UTC offset |
| `:weather-api` | `GeocodingDirectParsingTest` | Geocoding payload parsing when `state` is omitted |
| `:weather-api` | `GeocodingRepositoryTest` | City search: input trimming, label formatting (and the name, rest of the label and short label Add location shows), short-query and missing-key short-circuits, failure wrapping |
| `:weather-api` | `WeatherRepositoryTest` | Repository contract: coordinate routing (incl. `open_meteo_enabled` on/off and a failing `ForecastSourcePolicy`), Open-Meteo's reading with OpenWeather as the backup, My Location's name, city-query trimming, error mapping |
| `:weather-api` | `OpenMeteoCurrentTest` | Open-Meteo's current reading mapped for the hero, today's range holding the reading |
| `:weather-api` | `RadarRepositoryTest` | RainViewer timeline parsing, past/nowcast frame ordering, and tile URLs |
| `:weather-api` | `CurrentWeatherTest` | UTC-offset handling, including caches written before the offset existed |
| `:weather-api` | `WeatherUnitsTest` | Celsius conversion and locale-to-unit mapping (US and territories imperial, others metric) |
| `:app` | `WeatherForecastViewModelTest` | Cache-first loading, refresh races, background-refresh staleness guards, the `foreground_refresh_min_minutes` throttle, `walk_card_enabled`, the section switches, `temperature_units_shown`, and Remote Config pet thresholds |
| `:app` | `RadarViewModelTest` | Stale-timeline refresh (10 minutes, measured from the request), keeping the old timeline on a failed refresh, and `radar_enabled` gating |
| `:app` | `SettingsViewModelTest` | Unit-override selection, *Share usage statistics*, the walk card setting, and cache clearing |
| `:app` | `AnalyticsRepositoryTest` | Analytics gates: kill switch, user opt-out, both on, and never crashing |
| `:app` | `AnalyticsCollectionControllerTest` | SDK collection flag follows the opt-out and kill switch, at start and on change |
| `:app` | `PetWeatherThresholdsTest` | Remote Config threshold validation: ranges, non-finite values, and pair ordering fallbacks |
| `:app` | `PetMoodTest` | Pet mood selection by conditions, temperature, and night, with configurable hot/cold cut-offs; night captions by the city's local hour |
| `:app` | `WalkAdvisorTest` | Walk rating and best time: rain, comfort range, walking hours, daylight, raining now, pavement heat, imperial input, "tomorrow" in the city's calendar, 24-hour cap |
| `:app` | `WalkCheckTest` | The walk verdict's levels for wind (near gale to hurricane force), air (by `walk_bad_air_band`), pavement, heat, cold, storms, rain and the dark; the order of reasons; pollutant names; the walk card's captions and danger rating; the hero's reason captions and leaving out walk captions; the Air Quality tile following the walk rules |
| `:app` | `MoonPhaseTest` | The moon's phase from the date, checked against published phases |
| `:app` | `WeatherIconTest` | Icon for each condition by day and night, moon phases, and southern-hemisphere mirroring |
| `:app` | `CalmWindTest` | Calm wind reads as 0, and visibility at the 10 km cap reads as clear |
| `:app` | `DevicePlaceNamesTest` | My Location's name kept until it moves 1 km, failed lookups tried again |
| `:app` | `TemperatureTextTest` | Temperatures with and without their unit (`temperature_units_shown`): both unit systems, negatives, the same whole degrees either way, and inside "About …" and "Dew point …" |
| `:app` | `DistanceUnitsTest` | Visibility in miles or km: the unit choice, or the measurement system for "System default" |
| `:app` | `AppConfigModuleTest` | The OpenWeather key: a Remote Config replacement wins over the built-in key |
| `:app` | `CurrentSlotsTest` | `fromCurrentSlot`: drops past hourly and 3-hourly slots, keeps the one in progress |
| `:app` | `RainOutlookTest` | When the rain outlook shows (minimum chance, switch, window size), its headline (now or around, likely or a chance, the next-rain line's words), drying out, the total in mm or inches, and the bars |
| `:app` | `LocaleWeatherUnitsTest` | An explicit Celsius/Fahrenheit system preference beats the locale; unknown falls back to it |
| `:app` | `WelcomeViewModelTest` | Onboarding state and persistence |
| `:app` | `CityRepositoryTest` | Add, remove, reorder, active-city tracking, legacy single-city migration, and `city_added` firing |
| `:app` | `GeoLocationViewModelTest` | City autocomplete: short input, latest search wins, a failed search told apart from finding nothing |
| `:app` | `AddLocationRulesTest` | Add location's button: the first row named what was typed (accents ignored), the size check that stops "Nas" adding Nås, saved places (label or within 2 km, never My Location) gone to, and failed or empty searches |
| `:app` | `LocationPermissionViewModelTest` | Current-location lookup: permission denial, cached vs current location, failure, `LocationRepository`'s position reads, and permission answers: asking again after "Ask every time", an instant refusal opening the app's settings and the return from them, slow refusals, grants forgetting refusals |
| `:app` | `WeatherScreenStepsTest` | The weather screen's refreshes and My Location steps |
| `:app` | `NotificationWorkerLogicTest` | Briefing text, walk-time planning and nudge, rain alerts (lead, quiet hours, once per shower), city-timezone "today" matching, and the switches |
| `:app` | `UpdateWorkerLogicTest` | Background refresh result routing, caching, and resolved units |

Compose UI tests live in `app/src/androidTest` and run on an emulator in CI via a separate
`instrumented-tests` job:

| Test class | What it covers |
|---|---|
| `WelcomeScreenTest` | Welcome copy and the *Get started* callback |
| `OnboardingNotificationScreenTest` | Notification onboarding copy, both buttons, and *Not now* |
| `CurrentWeatherScreenTest` | Current-weather screen states: empty, error with retry, forecast error, upcoming day, hourly strip and rain chance, the rain outlook shown for a wet hour and hidden when `rain_outlook_enabled` is off, radar shown by default and hidden when `radar_enabled` is off |
| `LocationsScreenTest` | Manage Locations: tapping a row makes it active, Move up / Move down accessibility actions, delete buttons name their city |
| `AddLocationScreenTest` | Add location: the button names what it adds and the Search key does the same, tapping a row adds it, a far smaller match asks for a pick, a saved place is marked and gone to, and the X closes it |

## Text and translations

The app's own words, on screen and in notifications, live in `app/src/main/res/values/strings.xml`
(captions with several variants are `<string-array>`s there), in British English, word for word the
same as iOS. The exceptions are at the end of this section.

- Compose screens read it with `stringResource`. Rules that pick what to say, such as the walk card
  and the hero captions, return a resource id and its arguments (`WalkText`) instead of text, so they
  stay plain JVM code.
- Notifications are written by `worker/NotificationWorkerLogic.kt` through `util/Strings`: the
  workers pass `context.resources.asStrings()`, and the unit tests pass `StringsXml`, which reads
  strings.xml itself, so they check the real English wording without Robolectric.
- Each sentence is one string with numbered arguments (`%1$d° and %2$s in %3$s`), so a translation
  can reorder them. Code joins whole sentences, never pieces of one. Use `<plurals>` for anything that
  counts, and `translatable="false"` only for text that isn't language (URLs).

To add a language, add `app/src/main/res/values-<code>/strings.xml` (`values-fr`, `values-de`)
with the translated strings under the same names, keeping every `%1$s`-style argument and
escaping apostrophes (`\'`). Anything left out falls back to English; `./gradlew lint` lists what's
missing. For the language to show in Android 13's per-app language settings, also turn on
`androidResources { generateLocaleConfig = true }` with an `app/src/main/res/resources.properties`
naming the default (`unqualifiedResLocale=en-GB`). Some text isn't in strings.xml yet: weather
descriptions ("light rain") come from the API layer (`weather-api`'s `WmoWeatherCodes.kt`, and
OpenWeather in English), seasonal theme captions come from `seasonal_themes.json` or Remote Config,
and times and dates use fixed 12-hour patterns ("3:15 PM", "Mon, Jan 1").

## Units

Users choose their preferred units in Settings: **System default**, **Celsius (°C)** (metric), or
**Fahrenheit (°F)** (imperial). When set to System default, the app reads the Android 14+ temperature
preference where available, and otherwise falls back to a locale-based heuristic (the US and its
territories to Fahrenheit, otherwise Metric).

Temperatures are whole degrees with just the degree sign ("89°", "About 81°"), as other weather
apps show them. With Remote Config `temperature_units_shown` on they carry their unit ("89 °F",
"-3 °C"), with the same number and units; `formatTemperature` is the one place that decides. The hero's high and low, the
hourly strip, the walk card and notifications never carry the unit.

Visibility follows the same choice: km for Celsius, miles for Fahrenheit. For System default it
follows the system's measurement system, where the US and UK use miles (Android 9+; earlier
versions use the same locale heuristic).

## Accessibility

- TalkBack labels on every control. Settings' unit choices are single radio items, the radar slider
  reads as the frame's time, and Manage Locations rows offer Move up and Move down actions in place
  of dragging.
- Touch targets of at least 48 dp.
- Text meets WCAG AA contrast: the light theme's primary is `#01579B`, and in dark mode the paw and
  icons keep their contrast against the cards.
- Layouts reflow at the largest font sizes.
