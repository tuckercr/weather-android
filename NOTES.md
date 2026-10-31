# Notes

Open ideas, decisions and loose ends for Sunny & Misty, both apps. The same file lives in both
repos (cats-dogs-iOS and cats-dogs); keep them in step. Dates are when the item came up (2026).
Finished items move to Done at the bottom.

## Open PRs (10-31)

- iOS: #35 air quality, the high/low fix, README and fresh screenshots. #34 and #36 are merged.
- Android: #94 tile gaps, then #95 air quality, the high/low fix, README and Play Store
  screenshots. #96 is merged.

## Do soon

- [ ] **App Store submission.** The listing is filled in (10-31). Before Add for Review: publish
      App Privacy (it asks you to confirm the answers), answer Content Rights once the licences
      below are settled, and pick a build on the version page. Release is set to manual.
- [ ] **Add Jessica to the TestFlight "Family" group** once she accepts the App Store Connect
      invitation (sent 10-31). (10-31)
- [ ] **Storms in the dog walking line, and maybe the hero text.** Warn about thunder in the walk
      card ("Storms from 4 PM, walk before 3"), and possibly the pets' hero line. Check that both
      forecast providers give thunderstorm codes hourly. Should storm hours also be dropped from
      suggested walks, like bad air? (10-30)
- [ ] **My Location: prefer the city name** over the neighbourhood OpenWeather reports
      ("Horinouchi" for central Tokyo). Needs a reverse-geocoding choice (Apple and Android's own
      geocoders, or OpenWeather's reverse lookup). (10-30)
- [ ] **Hourly card too tall when the rain chance is off screen.** Every hour keeps a line for
      the rain chance if any hour has one, so with rain only later in the day the hours on screen
      end in an empty band. With no rain at all it sizes correctly (checked 10-31). Better: the
      same height for every hour, with the chance under a slightly smaller icon in the icon's own
      space, so the card never changes height and never has a gap. Both apps. (10-31)
- [ ] **Android location: "Ask every time" never clears the permanently-denied flag**, so
      "Add My Location" stays hidden after a reset in system settings (review finding 3). (10-29)

## Air quality

- [ ] Review the copy without a dog ("Fresh air all round" … "Stay indoors") and with one
      ("Good air for a long walk" … "Stay in. Potty breaks only"). (10-30)
- [ ] The pollutant ("Mostly ozone") is only read out by VoiceOver and TalkBack: a fourth line made
      every tile in the grid taller. Show it somewhere else, such as a tap-through? (10-30)
- Decided: the scale follows the city (European index for cities on a European clock, US
  everywhere else); "Unhealthy for sensitive groups" is shortened to "Poor for some".
- Remote Config: `air_quality_enabled` (on) and `walk_bad_air_band` (3, valid 1–5) are live.

## More tiles to consider (10-30)

- [ ] **Wind:** gusts in the Wind tile ("14 mph, gusts 30"), and wind in the words too: the
      pets' captions, the walk card and the night "stroll" caption don't consider strong wind
      yet. (10-26, 10-31)
- [ ] Sunshine hours; snow depth feeding the paw check.
- Decided: no Storm watch tile (mocked up 10-30). Storms go in the walk line instead (Do soon).
- Decided: Pollen is a version 2 feature (see Version 2). (10-31)

## Notifications

- [ ] Captions like "Perfect walkies weather!" still show without a dog. You said to leave the dog
      walking strings for now. (10-28)
- Won't do: let people choose the morning briefing time; it stays at 8:30. (10-26, decided 10-31)

## Location

- [ ] Android Settings: the location row could ask once more in the app before sending people to
      system settings (Android allows two prompts). (10-28)

## Walk planner and pet safety

- [ ] Retune the pavement heat model after an outdoor check of your own. (10-27)

## Store and release

- [ ] Licences: RainViewer is personal and educational use only, and Open-Meteo's free tier is
      non-commercial. Emails were drafted on 10-24; no replies yet. Fallback: launch with
      `radar_enabled` / `open_meteo_enabled` off in Remote Config.
- [ ] Future radar: RainViewer's free feed has had no forecast frames since 1 Jan. A paid layer
      (Tomorrow.io, OpenWeather maps, Xweather, MapTiler) hasn't been researched. (10-28)
- [ ] OpenStreetMap tile policy: at scale, a commercial tile provider. (10-24)
- [ ] The OpenWeather key ships in the app: watch usage on the dashboard, rotate via Remote Config
      if abused. (10-24)
- [ ] Set `ios_app_store_url` in Remote Config to `https://apps.apple.com/app/id6827945995` once the
      app is live. Until then the iOS update screen has no Update button. (10-30)
- [ ] Mac: the iPad app isn't offered on Apple silicon Macs. Try it on a Mac, then tick Mac
      availability in Pricing and Availability. (10-31)
- [ ] Store screenshot captions in the pets' voice. (10-26)
- [ ] Plan for supporting the app after launch. (10-31)
- [ ] Website store buttons need the real App Store and Play links on launch day, ideally the
      official badges. (10-27)
- Debug builds can show either update prompt: launch iOS with `-updatePrompt required` (or
  `recommended`); on Android,
  `adb shell am start -n com.fangjet.weather.debug/com.fangjet.weather.MainActivity --es updatePrompt required`.

## Small fixes found while taking screenshots (10-30)

- [ ] On the day clocks fall back, the day detail lists "1 AM" twice. That's right, but the
      second could say so (for example "1 AM PST").

## Testing

- [ ] On an Android device: the full-width odd tile and the Air quality tile now read from the
      forecast (both only checked on iOS so far). (10-30)
- [ ] Reverse case: My Location showing, then location permission revoked. (10-28)
- [ ] "Open Settings" from iOS notification and location settings on a real iPhone. (10-28)
- [ ] Listen to new spoken text with VoiceOver and TalkBack. (10-28)
- [ ] Check on a device: the Remote Config switches hiding notification types, the Walk time
      channel only appearing once you have a dog, and the 15-minute My Location check. (10-29)
- [ ] The half-hourly refresh while the app stays open (an overnight test). (10-26)

## Infrastructure

- [ ] After the first release: rename the Xcode project and targets ("Cats&Dogs") and the Android
      folder (`cats-dogs`) in their own PRs. (10-28)
- [ ] Rename the "Furcast-Shots" and "Furcast-iPad-Shots" simulators. (10-28)

## Ideas

- [ ] Small touches: tap the pets (Sunny wags, Misty ignores you), a "Sunny's take / Misty's
      take" line in the day detail. (10-26)
- [ ] **More seasonal themes** (Thanksgiving, winter holidays, the Fourth of July...). A theme
      that reuses existing decorations is only a new entry in Remote Config's `seasonal_themes`
      JSON; new props (a turkey, a scarf with lights) need drawing in both apps' `PetScene` and a
      name in `SceneDecoration`. (10-31)

## Version 2

- [ ] Home screen widget: the pets reacting to the weather, plus the next walk time. (10-26)
- [ ] Kotlin Multiplatform, as a learning project. Low priority. (09-24)
- [x] Pollen: free from Open-Meteo for Europe only. The US needs a paid provider (Google Pollen,
      Tomorrow.io, Ambee). Moved to version 2. (10-30, decided 10-31)
- [x] Per-pet thresholds: small, short-haired and flat-faced dogs struggle sooner. Moved to
      version 2. (10-28, decided 10-31)

## Outside the apps

- [ ] Résumé PDF still links the old repo. (10-26)
- [ ] fangjet.com: the old WordPress install is no longer served (10-30), but its files and MySQL
      database may still sit in the IONOS webspace. Back up and delete them, leaving Rolo alone.
      Also tidy the Tailwind one-off values; the AI page is out of the navigation but still in the
      sitemap. (10-24)

## Done

- [x] Seasonal themes, with Halloween: JSON themes (fixed date, range or nth weekday), shipped
      in the apps and replaceable from Remote Config (`seasonal_themes`,
      `seasonal_themes_enabled`). Each sets the hero colours and caption, card tint, section and
      tile header colours for light and dark mode, and the pets' decorations. Picked by the city's
      own date, so it ends by itself. Not yet deployed to Firebase. (10-31)
- [x] Sunny and Misty drawn after the app icon: Misty's white blaze, pink nose, whiskers and inner
      ears; Sunny's eyebrows, open smile and a blue collar with a sun tag. (10-31)
- [x] UV tile: the index, today's peak while it's ahead ("Peak 8 at 1 PM"), then "Shade for pale
      noses" on a high day with a dog. Every Today tile now has a Remote Config switch
      (`tile_*_enabled`); UV starts off. (10-30, done 10-31)
- [x] Notification stacks on iOS say "3 more briefings" (a category summary per kind). (10-29,
      done 10-31)
- [x] Bad air in the notifications: the briefing adds "Smoky air today, so keep it short." (or
      "... so potty breaks only.") and the walk nudge "... so a short one."; ozone and other
      pollutants read "High ozone" and "Poor air". (10-30, done 10-31)
- [x] City search no longer shows a place twice. (10-30, done 10-31)
- [x] First TestFlight build: 1.0 (1) uploaded from `main` with `xcodebuild`, internal group
      "Family". The next upload needs build 2. (10-31)
- [x] App Store listing in App Store Connect: name "Sunny & Misty: Pet Weather", text, screenshots
      (6.3" and 6.9" iPhone, 13" iPad), age rating 4+, free in 174 countries (not China mainland),
      App Review contact and notes. (10-31)
- [x] The day detail's empty row under the header (two dividers in a row) is gone. (10-30)
- [x] iOS signing: FangJet LLC's team (`3V3NR8HP58`), your iPhone registered, and a Release archive
      that exports with App Store signing. (10-30)
- [x] Update prompts: `ios_`/`android_min_supported_version` block the app until it's updated,
      `ios_`/`android_min_recommended_version` show a banner ("Not now" hides it a week). The keys
      are live in Remote Config, all empty. (10-30)
- [x] Air quality tile, and bad air in the walk planner: hours at "Unhealthy" or worse are never
      suggested, and the walk card says "Bad air. Potty breaks only." Heat still outranks it. (10-30)
- [x] Air quality in one request: the forecast fetches each hour's full reading and the tile shows
      the hour in progress, so it's cached and shows offline. (10-30)
- [x] Remote Config: `air_quality_enabled` switch and `walk_bad_air_band` threshold. (10-30)
- [x] An odd tile out (Air quality, with Paw check showing) takes the full width. (10-30)
- [x] iOS "No cities added yet" fills the screen instead of showing as a box in dark mode. (10-30)
- [x] The Android release build stops at the start and lists what's missing when the signing key
      or weather API key isn't in `local.properties`. (10-27, done 10-30)
- [x] iOS test runs left hundreds of `CatsDogsTests-*.plist` files; tests now reuse 8. 60 old
      test-clone simulators (329 GB) deleted. (10-05, done 10-30)
- [x] Xcode 27.1 recommended settings, and the `nilIfBlank` isolation error. (10-30)
- [x] Old local and merged branches cleaned up. (10-26, done 10-30)
- [x] The hero's high and low came from OpenWeather's current reading (the spread across nearby
      stations right now) and today's forecast only covered the hours left. Both now show the
      whole day from the forecast. (10-30)
- [x] Fresh README, App Store and Play Store screenshots. (10-30)
