# Notes

Open ideas, decisions and loose ends for Sunny & Misty, both apps. The same file lives in both
repos (cats-dogs-iOS and cats-dogs); keep them in step. Dates are when the item came up (2026).
Finished items move to Done at the bottom.

## Do soon

- [ ] **App Store submission.** The listing is filled in (10-31). Before Add for Review: publish
      App Privacy (it asks you to confirm the answers), answer Content Rights once the licences
      below are settled, and pick a build on the version page. Release is set to manual. The
      App Store Connect version is still called 1.0: rename it to the build's version (build N is
      1.0.N from build 8 on; build 7 is 1.0.6) before submitting: a version page only offers builds
      with its own number. (11-03)
- [ ] **App Store review notes: what "Always" is for** (Settings › Notifications › Follow my
      location). The Play side, the background location declaration with its video, was filled in
      on 11-10 and goes to Google with the first review. (11-03)
- [ ] **Android: the city search in SEARCH.md**, assigned to a new developer. iOS has had it since
      11-06, so until it lands the apps differ: Open-Meteo prefix search with the OpenWeather
      fallback, filtering, county labels, sorting with the size guard, text after a comma, US ZIP
      codes, the six `city_search_*` Remote Config keys, `city_added`'s `source` and `provider`,
      the GeoNames credit, and the last "town or city" and "location" wording (onboarding and
      notification onboarding). SEARCH.md marks the changes building it on iOS made (11-06).
      When it's built, add the keys to `firebase/remoteconfig.template.json` with iOS's defaults;
      iOS reads them with in-app defaults, so nothing needs deploying first. (11-06)
- [ ] **Review analytics, and add parameters where they'd help.** Only four events today, three
      with no parameters. Worth knowing: which permissions are granted (notifications, location,
      "Allow all the time" for Follow my location) from onboarding and from Settings; how cities
      are added (search suggestion, typed by name, My Location; `city_added`'s `source` is already
      decided in SEARCH.md); and the dog answer and notification switches. Anonymous categories
      only, never a place or what was typed; keep the opt-out and kill switch gates. Each change
      updates README's analytics table, the privacy policy and the iOS app. Both apps. (11-05)

## Air quality

- [ ] Review the copy without a dog ("Fresh air all round" … "Stay indoors") and with one
      ("Good air for a long walk" … "Stay in. Potty breaks only"). (10-30)
- [ ] The pollutant ("Mostly ozone") is only read out by VoiceOver and TalkBack: a fourth line made
      every tile in the grid taller. Show it somewhere else, such as a tap-through? (10-30)
- Decided: the scale follows the city (European index for cities on a European clock, US
  everywhere else); "Unhealthy for sensitive groups" is shortened to "Poor for some".
- Remote Config: `air_quality_enabled` (on) and `walk_bad_air_band` (3, valid 1–5) are live.

## Design review (11-03)

- [ ] **Daylight tile.** What it does now (both apps): before sunrise, "Sunrise in 2 h 10 min"
      with the sun at the start of the arc; in the day, "4 h 29 min left" with the sun along the
      arc; after sunset, the arc filled with the sun down, "The sun has set", and that day's
      sunrise and sunset times until midnight. It doesn't look ahead: tomorrow's sunrise only
      shows after midnight, as "Sunrise in …". The UV index rides in the tile's header ("UV 1 ·
      Low") while the UV tile is off (`tile_uv_enabled`, off by default), and is hidden after dark.
      Consider: tomorrow's sunrise after sunset, and taking the UV off this tile.
- [ ] **Tile sizes.** Today tiles come in two widths now: half (side by side, as tall as the
      tallest in the row) and full. Consider four, adding 2/3 and 1/3: Sky and Daylight could be
      2/3 wide when they don't need the full width, and Feels Like and UV could have smaller 1/3
      versions, so a row holds a 2/3 and a 1/3 tile.
- [ ] **A colour scale for some tiles.** Green, blue, orange and red, for tiles whose value has a
      severity: Wind (red for storm force), and maybe UV (Air Quality has its own scale already,
      and Feels Like and Paw Check have one since 11-04). Define what each colour means per tile.
      Never colour alone: keep the words ("Gale", "Very hot"), and check contrast in both themes and
      on the seasonal card colours, which Feels Like's band word hasn't been checked on yet either.
- [ ] **Tile design looks too much like other weather apps.** Review the Today tiles as a whole:
      shape, layout, icons and type, and what would make them ours (the pets' voice, the brand
      font, the colour scale above, the tile sizes above).
- [ ] **Tile notes that don't fit on one line.** iOS shrinks them to fit (to 80% of the size at
      most); Android wraps them onto a second line, which makes that row of tiles taller: Wind's
      "From the NW, gusts 23" and Fluff Factor's "Misty's fur is behaving" on a Pixel 8a (11-06).
      Shrinking keeps the grid tidy but risks text too small to read, most of all for people who
      have already turned the text size up. Options: let both apps wrap to two lines; shorten the
      lines that run long; or shrink only a little, with a floor. Decide, then make both apps the
      same. (11-06)

## More tiles to consider (10-30)

- [ ] **Wind in the words:** the pets' captions and the night "stroll" caption don't consider
      strong wind yet. The Wind tile shows gusts (the windsock, 11-06), and the walk card warns
      about storms and hurricane force winds (11-09). (10-26, 10-31)
- [ ] Sunshine hours; snow depth feeding the paw check.
- Decided: no Storm watch tile (mocked up 10-30). Storms went in the walk line instead (11-09).
- Decided: Pollen is a version 2 feature (see Version 2). (10-31)

## Notifications

- [ ] **Briefing by push.** Each phone subscribes to a Firebase Cloud Messaging topic for its time
      zone; a scheduled Cloud Function sends a plain push to each zone at 8:30. On iOS a
      notification service extension fetches the weather and writes the briefing, falling back to
      "Tap to see today's weather.". The server only knows time zones, never locations. Always
      fresh and on time on both platforms, and it replaces the iOS 7-day queue and the Android
      8:30 job. Needs Firebase's pay-as-you-go plan for Cloud Functions and an APNs key in
      Firebase. (11-02)
- [ ] **Rain alerts by push.** The server keeps each phone's rough location, checks the 15-minute
      forecast every 15 minutes and pushes when rain is about to start. The only reliable way on
      both platforms (a visible push arrives even when the app was swiped away). Costs:
      approximate locations on a server (App Store privacy label and Play Data safety change),
      weather API calls per location, more server code. (11-02)
- [ ] **Without a server**, a partial fix. Android: an inexact alarm (`setAndAllowWhileIdle`, no
      permission or Play declaration) for 8:30 and for about 45 minutes before forecast rain, set
      again by each background refresh; it goes off on an idle phone, at worst an hour late. iOS:
      queue rain alerts further ahead from the hourly forecast ("Rain likely around 4 PM"), less
      precise. (11-02) Alarms are held back less than jobs when Android counts the app as rarely
      used (below); check that on the Pixel before relying on it. An alarm can't read the
      location (see Location). (11-03)
- [ ] **Android: late when the app counts as rarely used.** On 11-03 the Pixel's Adaptive
      Battery moved the app to the "rare" standby bucket at 1:53 am (not opened the day before).
      No job of ours ran from 11:49 pm until the phone restarted at 10:02, not even the
      half-hourly refresh, so the 8:30 briefing came at 10:03. The schedule was right (8:30, and
      8:30 again the next day). In the rare bucket Android may hold jobs for up to 24 hours. A
      home screen widget would avoid it, and the inexact alarm above might. (11-03)
- [ ] **Android walk time gaps.** With the briefing off, a best walk time less than 30 minutes
      after 8:30 gets no nudge at all. Turning Walk time on, or saying you have a dog, during the
      day plans nothing until the next morning. (11-02)
- Won't do: let people choose the morning briefing time; it stays at 8:30. (10-26, decided 10-31)

## From the 11-02 code review

Found reviewing the iOS changes since TestFlight build 1; where Android works the same way it's
noted. Checked against the code, not fixed yet.

- [ ] **Sunny's coat on the sunny sky.** The lighter coat (#E9B158) is hard to tell from the
      sunny sky (#FAC775), and the walk nudge's picture is always the sunny scene. It was lightened
      for the old Halloween card, which has since changed. Try a couple of coat colours side by
      side. Both apps.
- [ ] **Halloween contrast.** Tile labels (#C25E00 on #FCE9D2) are 3.6:1, under the 4.5:1 AA needs;
      the day pumpkin and Misty's grey fur barely show on the #B4500C hero, and one bat on the night
      hero. Fixable in Remote Config before next Halloween.
- [ ] **UV tile** (off by default). The peak only counts hours from the last fetch, so in the
      afternoon it says "At today's peak" (and loses "Shade for pale noses"). Turning the Daylight
      tile off also shows the UV tile, whatever `tile_uv_enabled` says, on purpose so the UV index
      isn't lost. Both apps.
- [ ] **Theme captions and the no-dog rule.** A theme's captions show whether or not there's a dog,
      so a future one about walks would reach people with Dog walking off (none mention walks yet).
      Both apps.
- [ ] **City search.** Two places with the same name, state and country (two "Richmond, England,
      GB") show as one row, so the second can't be picked. The search design in SEARCH.md (agreed
      11-05) fixes it: rows merge only within 2 km, and same-label places get their county. Fixed
      on iOS 11-06; Android with its search.

## Location

- [ ] **Re-check My Location on the half-hourly refresh too, while it's on screen.** A drive with
      the app open never returns to it, so without Follow my location My Location stays put until
      a pull to refresh (which reads the position since 11-06). Doing it on the 30-minute tick as
      well would catch up without a pull, at the cost of a location fix every half hour while the
      app is open. Both apps. (11-06)
- [ ] Android Settings: the location row could ask once more in the app before sending people to
      system settings (Android allows two prompts). (10-28)
- [ ] **My Location in the background, opt-in.** Built for the first release as the Follow my
      location switch in Notification settings (11-03). It asks for "Allow all the time" (Android,
      a second step on the app's location page in system settings) or "Always" (iOS, one prompt,
      then only the Settings app). With it on, Android's background refresh and 8:30 run read the
      phone's position first, and iOS wakes the app after a significant move. Before it, My
      Location only moved while the app was open, so a briefing could go to the last place the app
      was opened (11-03: a town 50 km away, where the app was last opened two days before).
      "While using the app" doesn't reach background work, and a service started from an alarm
      gets no location either. Still to design: a way in for people who never open Notification
      settings. Mocked up 11-03: a one-time card on the weather screen the first time My Location
      jumps after a briefing went to the old place ("This morning's briefing was for …", Not now /
      Keep it up to date), opening the same explanation; or an onboarding step after the location
      page, only when location and notifications were both allowed (recommended against: a fourth
      permission ask in a row, and iOS asks once). Android could later use a geofence a few
      kilometres round My Location (the Geofencing API, same permission), so it moves even while
      Android holds the app's jobs back. (11-03)

## Walk planner and pet safety

- [ ] Retune the pavement heat model after an outdoor check of your own. (10-27)
- [ ] **The Danger warning lives inside the walk card.** It borrows the card's icon, label and
      headline, and needs an exception to the card's own rule for when it shows (no dog,
      `walk_card_enabled` off). A danger to everyone isn't walk advice: a separate warning banner
      would be simpler. Both apps. (11-07)
- [ ] **Air quality switched off is handled in the rules.** `air_quality_enabled` is a field on
      `PetWeatherThresholds` for the walk check, and the Air Quality tile checks the switch
      separately. Dropping air quality from the forecast at the source (the repository, cached
      forecasts too) when it's off would let neither know about it. Both apps. (11-07)
- [ ] **"Dark" is a walk reason everyone skips.** It sits on the scale at Wait, but the hero's
      caption filter and the "Good until..." line both leave it out. Walking hours and daylight say
      when a walk can be, not what the weather is: a separate check would remove those exceptions.
      Both apps. (11-07)
- [ ] **Gusts count only at hurricane force.** A one-off rule: gusts should count at every level
      (for example two Beaufort steps below sustained wind) or not at all. Both apps. (11-07)
- [ ] **Walk verdict simplifications.** `WalkRating` repeats the verdict's level, reason and best
      time: the card could read the verdict, and the notifications only ask whether now is a good
      time. `WalkChange` ("Good until 6 PM, then rain") mirrors `WalkReason` one for one.
      `WalkAdvice` keeps the pavement temperature apart from the pavement reason, which could carry
      it. Both apps. (11-07)
- [ ] **One captions file for both apps.** The hero's captions are in Swift on iOS and strings.xml
      on Android, with the ones that suggest a walk marked differently on each. One file with tags
      (mood, time of night, dog, suggests a walk), like SeasonalThemes.json, would keep the wording
      identical by construction, and it's the shape translation would need. (11-07)
- [ ] **Shared walk scenarios for both test suites.** Every walk rule is written in Swift and Kotlin
      with separate tests. One file of scenarios (an hour of weather in; level, reason and caption
      out) that both test suites load would catch the apps drifting apart. (11-07)
- [ ] **Walk windows, the next steps.** From tester feedback (11-11): "why that time?" and "I
      can't always go then". The card now gives the best time's spell, a second spell and what
      clears by then (see Done). Still open:
      - The morning briefing and the walk nudge still give one time ("Sunny's picked 11 AM for a
        walk."). They could name both spells.
      - A strip of the day's hours shaded by the verdict was mocked up and set aside: 14 hour
        blocks across a card are too small to tap. A version without taps, or one hour per row in
        the day detail, could still work.
      - Free hours ("I can walk before 9 or after 5"), if the second spell isn't enough.
      Both apps. (11-12)

## Store and release

- [ ] **Terms of use: a lawyer's review before release.** The page is at sunnyandmisty.com/terms
      (11-09), linked from Settings › About in both apps, and says the walk advice and warnings are
      a guide from forecasts that can be wrong, not a severe weather warning service. Still to
      settle: a governing-law section (the state FangJet LLC is registered in), whether the
      no-warranty and liability paragraphs need capitals to stand out, and that the App Store and
      Play listings don't promise more than the terms do. (11-09)
- [ ] Licences: RainViewer is personal and educational use only, and Open-Meteo's free tier is
      non-commercial. Emails were drafted on 10-24; no replies yet. Fallback: launch with
      `radar_enabled` / `open_meteo_enabled` off in Remote Config. Plan (11-05): buy an Open-Meteo
      licence before release; the new city search (SEARCH.md) depends on it too.
- [ ] **Open-Meteo plan and API key.** Commercial use needs a paid plan (Standard 1M calls a
      month, Professional 5M; prices only in the customer portal). A request counts as variables
      divided by 10 once it asks for more than 10: the forecast is 1.7 calls, the current reading
      1.5, air quality 1 (it was 1.6 before the savings in Done, 11-10) and at most every 3 hours
      per city. Worked out on 11-10 for 200 active users, half iPhone and half Android:
      - Per user a day, one city: Android about 52 calls (about 12 of the 16 background runs every
        90 minutes, Doze holding the rest overnight, plus 3 opens), about 128 at most when the
        background run covers two cities; iPhone about 31 (a guessed 4 background refreshes and 3
        opens of about 1.3 cities).
      - A month: typical about 0.25M (0.9M before the savings); Android running every 90 minutes
        on time 0.29M (1.0M); every Android user on two cities 0.48M (1.6M); all 200 on Android
        with two cities 0.77M (2.8M).
      - So Standard holds the typical mix up to about 750 users, the two-city case about 400.
        Android's background refresh is still about three quarters of the calls: raising
        `weather_refresh_interval_minutes` is the quickest lever (a Remote Config deploy), at the
        cost of later rain alerts on Android. The iPhone refreshes and opens are guesses; the
        usage dashboard will show the real figure once there's a key.
      The paid plan's API key goes to customer-api.open-meteo.com: shipped in the apps it can be
      pulled out and spent, so a small proxy (a Cloud Function that adds the key) is worth it then,
      even without caching. A server cache saves little: the phones already cache, and each user's
      coordinates differ. Monthly limits aren't enforced yet; Open-Meteo emails at 80%, 90% and
      100%. (11-10)
- [ ] Future radar: RainViewer's free feed has had no forecast frames since 1 Jan. A paid layer
      (Tomorrow.io, OpenWeather maps, Xweather, MapTiler) hasn't been researched. (10-28)
- [ ] OpenStreetMap tile policy: at scale, a commercial tile provider. (10-24)
- [ ] The OpenWeather key ships in the app: watch usage on the dashboard, rotate via Remote Config
      if abused. (10-24)
- [ ] **OpenWeather stays as the fallback** (decided 11-06). Since 11-06 it's only a backup: cities
      saved by name only (no coordinates), the reading when Open-Meteo fails, everything when
      `open_meteo_enabled` is off, and the city search fallback (all of search on Android until
      SEARCH.md is built there). Removing it later would need Android's new search, a choice for
      name-only cities (stop saving typed names, or look them up on update and ask to re-add the
      ones not found), the Open-Meteo licence with nothing to switch to, and dropping the key, the
      iOS build check, the CI secrets, `openweather_api_key`, the `openweather` search provider,
      the Settings credit and the privacy policy mention. (11-06)
- [ ] App Store App Privacy: My Location's coordinates also go to the phone's place lookup
      (Apple's) to name it. The privacy policy on sunnyandmisty.com says so since 11-06, and Play
      Data safety (filled in 11-10) declares location for app functionality. (11-06)
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
- [ ] With `open_meteo_enabled` off, check nothing breaks on both phones: the hero and Today tiles
      (OpenWeather's reading, and My Location's name from it), the 3-hourly strip and days, the
      walk card, the air quality tile and rain outlook (missing rather than broken), city search on
      the OpenWeather fallback, and the 8:30 briefing. Only for your own phones: a Remote Config
      condition on their app instances, or a debug build with the default flipped. (11-06)

## Infrastructure

- [ ] After the first release: rename the Xcode project and targets ("Cats&Dogs") and the Android
      folder (`cats-dogs`) in their own PRs. (10-28)
- [ ] **Hardcoded numbers worth a Remote Config key.** Look for more like city search's 280 ms
      wait, now `city_search_delay_ms` (iOS 11-06; Android with its search): values we
      might want to tune after release without an update (timeouts, waits, limits, thresholds).
      List them first, then pick the few worth a key; each needs a default and validation in both
      apps. Both apps. (11-05)

## Architecture (MVVM review, 11-07)

The review's main items were done on 11-07 (see Done): the weather view model holds the screen's
clock and builds what the screen shows; the notifications share the screen's data layer and rules;
a screen view model runs the refresh and My Location steps; views go through their view models for
settings and permissions; and the domain rules are out of the UI layer. Still to do:

- [ ] **Architecture decisions to review (11-07).** From an MVVM audit of both apps after the
      refactors (iOS judged about 80-85% of the way; Android "MVVM with one unfinished seam").
      1, 2, 4, 5, 6 and 8 are done (see Done). Left:
  3. **Dependency wiring.** iOS view models fall back to `.shared` singletons in their
     initialisers (a service locator); `AppViewModels` could take one dependencies container.
     Android uses lambda constructors behind `@Inject`, which is consistent but unusual. M.
  7. **The clock moves only when told.** `tick()` is called from several hooks; a clock service
     publishing ticks would be harder to get wrong. S.
  9. **Weather cached in settings storage** (UserDefaults, DataStore preferences). Fine at today's
     size; look again before caching more.
  10. **Names don't match the layers.** Business rules in `Utilities`/`util`, domain code in
      `Services`, Android's `NotificationKind` in `worker/`. S.
  11. **Two native codebases kept in step by hand.** Shared rules in Kotlin Multiplatform
      (version 2 below) would remove most of the duplication.
- [ ] **Translation: what's still English only (11-08).** Both apps keep their text in
      resources now (see Done), but some isn't translatable yet: weather descriptions
      (OpenWeather is asked without `lang`, Open-Meteo's WMO names are English, and the geocoder
      asks for `language=en`), the fixed date patterns ("EEE, MMM d", "h:mm a"), seasonal theme
      captions (built-in JSON and Remote Config `seasonal_themes`), the debug sample notifications,
      lower-casing of sky words in notification titles, and joins between sentences (" · ", line
      breaks). iOS reuses keys with two meanings ("Moderate" for UV and air quality, "Calm" for
      wind); give them their own keys before translating. Do these when a first language is
      chosen. M.
- [ ] **Views still work things out (review of every view, 11-07).** Found by a pass over all
      views in both apps; none is a bug today, but each is logic a view model or the domain should
      own. The captions, the Today tiles, app start-up, the onboarding glue, the location flows, the
      radar and view models made in views are done (see Done). Left:
  - **Smaller rules in views.** The stale banner's text and the offline check (iOS
    `WeatherErrorViews`), "the last city can't be removed" and the "X added" toast
    (`LocationsView`), the notifications row's "Off / N on", and `uvLevelRes` and `moonPhaseAt`
    in Android's ui. Small each.

## Ideas

- [ ] Small touches: tap the pets (Sunny wags, Misty ignores you), a "Sunny's take / Misty's
      take" line in the day detail. (10-26)
- [ ] **Hero card layout.** Try other designs: the pets on the left with the text beside them, or
      a more interesting text layout than one long centred list (conditions, city, temperature,
      high and low, caption). (10-31)
- [ ] **More seasonal themes** (winter holidays, the Fourth of July...). A theme
      that reuses existing decorations is only a new entry in Remote Config's `seasonal_themes`
      JSON; new props (a turkey, a scarf with lights) need drawing in both apps' `PetScene` and a
      name in `SceneDecoration`. (10-31)

- [ ] **Sunny and Misty in a gale.** A hero scene for extreme wind (ears and fur blown back, Misty
      holding on), for a gale and worse, now that the walk verdict knows about them. (11-07)
- [ ] **A winter look for the hero in snow or extreme cold.** Like a seasonal theme, but set by the
      weather rather than the date: scarves and hats, frosty colours, Sunny's breath showing.
      (11-07)

## Version 2

- [ ] Home screen widget: the pets reacting to the weather, plus the next walk time. (10-26) On
      iOS it would also help the notifications: iOS reloads a widget on its own budget, even after
      the app is swiped away, and each reload could refresh the weather and re-queue the
      briefings and walk nudges (check that a widget extension may queue notifications). Only
      for people who add it, and no help to rain alerts. (11-02) On Android an app with a widget
      on the home screen never drops below the "active" standby bucket, so its background jobs
      aren't held back (see "late when the app counts as rarely used"). (11-03)
- [ ] Kotlin Multiplatform, as a learning project. Low priority. (09-24)
- [ ] **Translate the apps?** Both are English only. Since 11-08 both keep their text in resources
      (iOS a String Catalog, Android strings.xml; see Done), so a language is mostly a new
      translation, after the English-only leftovers under Architecture. Also the pets' voice in
      each language, the App Store and Play listings, the website and the privacy policy. Decide
      whether, and which languages, first. (11-06, 11-08)
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

- [x] **Walk windows** (11-12): the walk card gives the best time as its spell of good hours
      ("11 AM to 1 PM"), the next spell as a second option ("Or 5 to 8 PM.", title "Best Walk
      Times"; "Good again 5 to 8 PM." after a walk now that ends within 6 hours), and when now is
      only a wait, what's better by then ("The rain's gone by then."). Both apps.
- [x] **Fewer Open-Meteo calls** (11-10): the background refresh runs every 90 minutes
      (`weather_refresh_interval_minutes` 90, was 30); air quality asks only for the city's own
      scale (US or European, 10 variables or fewer, so 1 call instead of 1.6), comes after the
      forecast so its time zone picks the scale, and is reused for 3 hours; the background refresh
      skips a city whose reading and forecast are both under 30 minutes old. Both apps.
- [x] **Raindrops at night** (11-10): on a seasonal theme's dark night hero the raindrops are
      pale blue (#A9CFF2) instead of the umbrella's blue. Both apps.
- [x] **No UV left today** (11-10): the UV tile says "No more UV today" instead of "Peak 0" when
      the highest UV left today rounds to 0. Both apps.
- [x] **Simulators renamed** (11-10): "Furcast-Shots" and "Furcast-iPad-Shots" are now
      "SunnyMisty-Shots" and "SunnyMisty-iPad-Shots".
- [x] **Main screen horizontal margins** (11-10): 12 from the screen edge to the cards instead of
      16, on both (12 dp, 12 points), for more room in the hourly strip and tiles. The city tabs
      and the update banner moved in with them; onboarding keeps its 24.
- [x] **Thanksgiving theme** (11-09): on the fourth Thursday of November, an orange-brown hero
      with falling leaves and a pie, "Sunny is on crumb patrol under the table." by day and "Too
      full to move. Misty is napping on the coats." at night. Built in, and added to the Remote
      Config template, whose live value (Halloween only) replaces the built-in list until it's
      deployed. Unknown decorations in a Remote Config list are skipped.
- [x] **Seasonal theme at night, and costumes** (11-09): a theme's night look follows the clock,
      not the mood; the night caption shows only from noon while dark, so not at 3 AM. The
      umbrella is drawn over Misty's witch hat, and a costume replaces only its own pet's accessory
      in the same spot (Sunny keeps his sunglasses under the bandana).
- [x] **Clock reads in views** (11-10): the weather icons and the pet scene take the moon's phase
      from the weather screen's clock (`screenNow` in the environment on iOS, `LocalScreenNow` on
      Android), not the device's, so they match the rest of the screen. Outside the weather screen
      (onboarding, the update screen) they still use the device clock.
- [x] **A hint that the hourly strip scrolls** (11-09): the strip's edges fade (24 pt/dp) where
      there are more hours to scroll to, and not at either end or when every hour fits.
- [x] **Radar outside RainViewer's coverage** (11-09): RainViewer's coverage layer is transparent
      where it has radar and black where it doesn't (checked at London, Knoxville, Nairobi,
      Ulaanbaatar and the mid-Atlantic). The radar card dims the areas without radar, and says
      "No radar here" when the city itself has none, instead of an empty map that reads as no rain.
- [x] **The rain chance as the hourly strip shows it** (11-09): the walk rules (the walk limit and
      "then rain" against "then maybe rain") and the Rain outlook card (its bars, its minimum and
      "likely") read the chance rounded to the nearest 10%, hidden under 10%, as the strip shows it.
- [x] **Misty's View at night** (11-09): after dark her line is about the stars ("Misty can see the
      stars", "Misty can spot a few stars", "No stars for Misty tonight") or the streetlights ("The
      streetlights look fuzzy"); poor visibility keeps "Misty can't see past the garden".
- [x] **Walk reminders warn about hurricanes and storms** (11-09): a reminder followed within 3
      hours by hurricane force wind or likely storms ends "Hurricane force winds from 2 PM, so be
      back by then." or "Storms from 4 PM, so be back by then.", in place of the bad-air sentence.
      Android writes the reminder when it goes off, so its window moves if the worker is late; iOS
      plans it ahead.
- [x] **Cloudy captions** (11-09): partly cloudy has brighter captions of its own ("Bit of sun, bit
      of cloud. Good sniffing weather."), and "Nap weather, says Misty." only shows from noon.
- [x] **Terms of use page** (11-09): sunnyandmisty.com/terms, linked from every page's footer and
      from Settings › About in both apps. The legal review is still open (Store and release).
- [x] **Hurricane and storm warnings ahead** (11-09): hurricane force wind forecast in the next 6
      hours is a warning of its own on the walk card ("Hurricane force winds from 6 PM", "Get
      everyone inside before then.", for everyone, dog or not) instead of being treated as wind
      now, so the card, the hero and the Wind tile agree about now. Likely thunderstorms in the
      next 6 hours add "Storms from 4 PM." to the walk card's caption. The 8:30 briefing says
      either for the rest of the day, in place of the walk sentence for a hurricane. Storm hours
      were already left out of suggested walks. One difference: Android's briefing also counts
      hurricane force in the reading at 8:30, since it runs then; iOS plans briefings ahead from
      the forecast hours. Not done: the hero's line doesn't mention storms ahead, and walk nudges
      don't mention either warning.
- [x] **Manage Locations polish** (11-08): Android's rows wrap a long name at the delete button,
      not halfway across, and iOS's Settings row says "Manage Locations" like the screen and
      Android.
- [x] **Jessica added to the TestFlight "Family" group** (11-08).
- [x] **Radar label wrong for one moment after a refresh (Android)** (11-08): the label is worked
      out in the same step as playback and the frames, and a refresh resets playback where it
      stores the new timeline.
- [x] **Hourly card the same height whatever the rain** (11-08): every hour has a fixed icon space,
      with the full-size icon on its own or a smaller one with the rain chance under it, so the
      card never changes height and never ends in a gap. Both apps.
- [x] **Android: "Ask every time" offers My Location again** (11-08): the permanently-denied flag
      is a guess Android can't confirm, so "Add My Location" is offered whenever access is off, and
      the request itself decides: an answer back at once with no dialog opens the app's settings
      page; a dialog answered clears the flag. (review finding 3)
- [x] **Text in resources, ready to translate** (11-08): architecture item 4. iOS's text is in a
      String Catalog (`Localizable.xcstrings`, plus `InfoPlist.xcstrings` for the app name and the
      location prompts), with `String(localized:)` and whole sentences instead of glued fragments;
      the old stringsdict plurals live in the catalog. Android's notification text moved from
      Kotlin into strings.xml, read through a small `Strings` interface that tests fill from
      strings.xml. English wording unchanged. Both READMEs say how to add a language. What's
      still English only is under Architecture.
- [x] **iOS notification scheduler testable** (11-08): architecture item 5. The picture's mood per
      kind, the walk-nudge and rain-alert records and the southern-hemisphere check are the
      planner's (or `SavedLocation`'s), with tests; the scheduler takes its notification centre,
      preferences, Remote Config, clock and picture renderer, so it has its own tests with a fake
      centre; the pets' picture comes through a renderer instead of the scheduler calling a view.
- [x] **Screen state kept, and Remote Config followed while open** (11-08): architecture item 6.
      iOS's weather view model keeps `WeatherScreen` and rebuilds it when an input changes, not
      on every redraw; Remote Config posts when a fetch activates and the screen rebuilds. On
      Android the weather screen, Settings' switches, onboarding and the radar follow
      `activationCount`, so a value deployed while the app is open (like the paw bands) applies
      without a restart.
- [x] **Paw Check bands lowered** (11-08): 115 °F pavement showed the orange "Getting hot" paw,
      though that's already too hot for paws. `walk_hot_pavement_c` 52 → 46 (about 115 °F) and
      `walk_warm_pavement_c` 45 → 40 (about 104 °F), in both apps, the template and the READMEs.
      Dangerous (57.2, 135 °F) and icy (0) are unchanged. Hours with pavement at 40 °C or more are
      no longer suggested for walks, so hot sunny afternoons offer fewer walk times. Remote Config
      deployed with 46 and 40 on 11-08.
- [x] **Privacy policy for `city_added`'s `source` and `provider`** (11-08): already live since
      11-06 (sunny-misty-web #3); the store privacy answers are still in Store and release.
- [x] **Location and cities in the data layer, one weather screen view model** (11-08):
      architecture items 1, 2 and 8. `LocationRepository` owns the location manager (iOS) or Play
      services' client (Android), the permission checks and the refusal bookkeeping;
      `LocationPermissionViewModel`, Settings, the weather screen and (Android) Follow my location
      go through it. `CityRepository` holds the saved cities and the active one, one copy for every
      screen; `CityListViewModel` is a thin view model over it. `CurrentWeatherViewModel` is gone:
      its steps are `WeatherForecastViewModel`'s, which reads both repositories, so no view model
      holds another. iOS Settings' "a grant adds My Location" rule moved into `SettingsViewModel`.
      Left as they are: iOS's Follow my location keeps its own location manager (it watches
      significant moves in the background); Android's weather view model refetches when the city on
      screen changes by itself, iOS's view still says so (`activeCityChanged`).
- [x] **Location flows, radar playback and view model creation out of the views** (11-07):
      "Use my location" and "Add My Location" go through `LocationPermissionViewModel`
      (`useMyLocation`, iOS `canOfferMyLocation`; Android `myLocationOffered` and `addMyLocation`
      with one-off added and failed events instead of the screen's flag and `consumeLocated`). The
      radar's playback (frame, play/pause, the loop, scrubbing, the "now" frame), the tile maths,
      the frame label and the recheck loop are `RadarViewModel`'s; Android's "Now ·" and
      "Forecast ·" moved to strings.xml. iOS's RootView makes `CurrentWeatherViewModel` once and Add
      location opens its view model on appearing; Android's weather route no longer makes a
      `SettingsViewModel` (the weather view model gives it the distance units). All unit tested.
- [x] **Captions, Today tiles, start-up and onboarding out of the views** (11-07): the screen state
      carries the walk card's wording (iOS `WalkCardContent`; Android `WalkCardContent` of string
      resources), Android's hero caption choice (`HeroCaption`: the walk's reason, the theme's, or
      the mood's captions with whether to leave out walk suggestions and which day's pick), and the
      Today tiles (`TodayTilesState`: which tiles show, in order, with each one's values, bands and
      lines, and the air tile's tone by the walk rules); the views only say it and colour it. iOS's
      launch work moved to `AppStartup` and Android's notification channels to
      `NotificationChannels`. `WelcomeViewModel` says which onboarding step is next and, on iOS,
      hands the dog answer and the location on; on Android it gives the notification screen its
      dog answer and Remote Config switches. All unit tested.
- [x] **The last Android workers have wiring tests, and bad air is named from strings.xml**
      (11-07): `WalkTimeWorkerTest` and `WeatherUpdateWorkerTest` check which city each fetches,
      in which units, and that it's cached. The briefing and walk nudge take bad air's name from
      strings.xml through `airNameOrPoorRes` (now in `util/AirNames.kt`), as the hero does,
      instead of spelling it out in Kotlin.
- [x] **Domain rules out of the UI layer** (11-07): the pets' moods, the walk advisor and check,
      the thresholds and the seasonal themes live in iOS's `Domain/` and Android's `domain/`, with
      no UI in them; their colours are view extensions (iOS `PetMood+Colors`,
      `SeasonalTheme+Colors`; Android's were already in `PetScene.kt`), and only iOS's `Views/`
      imports SwiftUI. "Is the walk card shown" is a domain rule (`WalkAdvice.showsCard`,
      `walkCardShown`), Android's shared formatting moved to `util/Formatting.kt`, and the
      notification picture is drawn by the pets' view on request (`PetSceneIcon.png`,
      `petSceneIcon`), the one place notification code touches the view layer.
- [x] **Views go through their view models** (11-07): on iOS, notification onboarding's text and
      "Allow notifications" are `WelcomeViewModel`'s; Follow my location (with its explanation),
      the debug samples and the link from iOS Settings are `SettingsViewModel`'s; `RootView` owns
      the one `RadarViewModel` and hands it down in the environment instead of a singleton. On
      Android, Settings and Notifications read what's allowed from `SettingsViewModel`
      (`AppPermissions`, re-read on resume) instead of checking in the composables, and Follow my
      location's explanation and what to ask for next (`FollowMyLocationRequest`) are the view
      model's, with the refusal count behind `AppPermissions`. All unit tested.
- [x] **Refresh and location steps in a view model** (11-07): `CurrentWeatherViewModel` in both
      apps refreshes the city on screen when it changes, on returning to the app and every half
      hour, reads My Location first on pull to refresh, moves My Location at most every 15 minutes
      or adds it once location access turns up (not back after it was removed on purpose), and on
      iOS refreshes the notification city when that changes. The screens only say what happened.
      The phone's position comes through `DevicePositioning`, so the rules are unit tested with a
      fake. Android's `rememberAddMyLocation` is back to the Add location screen's button.
- [x] **The notifications share the screen's data and rules** (11-07): `LocationWeatherRepository`
      in both apps is the one way to a saved location's weather, its cache and refreshes that
      fetch in the user's units and cache what comes back. The weather view model, the background
      refresh and the notifications all go through it; before, fetch-then-cache and the units were
      written out in the view model, iOS's background refresh (twice) and Android's three workers,
      and the morning and walk-time runs on Android didn't cache what they fetched. The briefing
      finds today by the city's date (`isDay(of:in:)`, `isDayOf`; Android's had matched labels, and
      its "today" came from the device clock), gives the hero's high and low (taking in the
      temperature now, or at 8:30 for iOS's planned briefings), and Android's briefing and walk
      nudge show the pets by the clock's day or night, as the hero does (`petMoodAt`, iOS
      `PetMood.forReading`; iOS's briefing picture is the 8:30 forecast hour, `PetMood.forHour`).
      iOS replans the notifications from the weather view model when new weather is in, not from
      the view. Android's rain alert moved out of `WeatherUpdateWorker` into tested `RainAlertLogic`.
- [x] **One clock and the screen state in the view model** (11-07): the weather view model holds
      the screen's clock (`now`, moved on by `tick()` on refreshes, returning to the app, the
      half-hour timer and the stale recheck) and builds a `WeatherScreen` from it: loading, an
      error, or the state the screen draws (the reading or the forecast hour standing in for an
      old one, today, the hero's mood, theme and caption, the high and low, the hourly strip, next
      rain and the rain outlook, the walk verdict and card, the tiles' inputs, and which sections
      show). The views only draw it, and it's unit tested in both apps. Today is found by its
      hours' dates in the city's time zone, so Android no longer loses it after midnight. On
      Android the hero caption's words still come from string resources, so the state holds the
      choice and the screen picks the words; `petCaption`, `heroWalkCaption` and the moon take the
      screen's clock, and the rain outlook takes the next rain rather than its words.
- [x] **Data work in the data layer** (11-07): fetching a saved location (its coordinates, its name
      to fall back on, and an empty label for My Location so the phone names it) is one rule in the
      data layer, iOS's `WeatherRepository.fetchCurrentWeather(for:units:)` and Android's
      `SavedLocation.weatherRequest`, which the screen, the background refresh and the notifications
      all use. Android's background update had passed My Location's own label, so it cached a
      reading named "My Location". Android's cache is encoded in `PreferencesRepository`, not in the
      view model and the worker. The radar's map and overlay tiles load in the data layer
      (`RadarTileLoader`) and the images live in `RadarViewModel`, in both apps. iOS's Add location
      takes its choices from `GeoLocationViewModel`.
- [x] **One walk verdict** (11-07): `WalkConditions.check` judges an hour once, and the hero's
      caption, the walk card, the Air Quality tile and the notifications all read it, on a scale
      from Great through Fine, Wait, Potty breaks only and Stay in to Danger, with the reason's
      detail (the pollutant, the wind's force, the pavement). It settles two items from the 11-07
      screenshots: the hero no longer says something cheery about walks when now isn't the time
      (bad air gets "Smoky air today. Quick potty breaks, then back on the sofa."), and the paw
      note says it's about now ("Paw check: about 49° now. Getting hot.") and never stands in for
      rain. A gale is potty breaks only, storm force keeps pets inside, and hurricane force (or
      hurricane force gusts, or hurricane force wind forecast in the next 6 hours) shows a Danger
      warning even without a dog. With `air_quality_enabled` off the walk now leaves the air out,
      even from a cached forecast, which settles "Air quality switched off still reaches the walk"
      from the 11-02 code review.
- [x] **Temperatures without the unit ("89°")** (11-06): `temperature_units_shown` is off in
      Remote Config (version 13) and by default in both apps, so every temperature reads "89°", as
      Apple and Google Weather show them. Turning it on brings the unit back without an update.
- [x] **A bar for dew point** (11-06): Fluff Factor shows the dew point instead, with Misty's fur
      going from sleek to full floof as it gets muggy.
- [x] **The hero's reading from Open-Meteo** (11-06): in Knoxville the hero said 70°F and the
      hourly strip 62 to 64°F. Both were working as built: the hero was OpenWeather's reading,
      which leans on the nearest weather station (the airport, 12 km away, had 69.8°F), and the
      strip is Open-Meteo's forecast for the exact spot, which matched it there. The hero, Feels
      Like and the other Today tiles now take Open-Meteo's current reading for the spot, so they
      agree with the strip; OpenWeather's reading is the backup when Open-Meteo fails or is
      switched off. Open-Meteo gives no place name, so My Location is named by the phone's own
      lookup (the town, else the county, else the state), kept until My Location moves 1 km, and
      "My Location" when the lookup fails. That also settles "prefer the city name" over
      OpenWeather's neighbourhood ("Horinouchi" for central Tokyo; Tokyo not checked yet). iOS
      `Services/OpenMeteoCurrent.swift` and `PlaceNames.swift`, Android `OpenMeteoCurrent.kt` and
      `PlaceNames.kt`.
- [x] **Add location, a full screen of its own** (11-06): the search results sat under the
      keyboard in a sheet below the saved list. Manage Locations' Add location button and the
      empty weather screen now open a full screen with the results right under the field, the
      weather screen's + and the sheet are gone, and the button above the keyboard names what it
      does ("Add Malmö, SE", "Go to London, GB", or "Pick a town or city from the list"). Details
      in SEARCH.md, "The Add location screen". The new search itself is still to come.
- [x] **Paw Check tile design** (11-04): five paws from icy to dangerous under the number, the
      pavement's own full size and colour (blue, green, orange, red, maroon), and the icy and
      below freezing lines in blue. A badge with when it changes later today ("Cooler after
      6 PM") was mocked up too and left for later.
- [x] **Sunrise and sunset in the hourly strip** (11-04): a slim marker between the hours they
      fall between, with the time ("6:50"), a sun on the horizon with an arrow, and a thin line.
      The times come from Open-Meteo's daily sunrise and sunset, so the evening's strip has
      tomorrow's sunrise; cities on the OpenWeatherMap fallback only have today's. Shading the
      night hours in the strip was mocked up too and left for later.
- [x] **Feels Like tile design** (11-04): the band beside the number (Cold, Chilly, Mild, Warm or
      Hot, from the pets' thresholds and the walk comfort range), a five-colour bar like Air
      Quality's, and how far it is from the actual temperature ("8° warmer than it is"). Its 1/3
      width version waits for the tile sizes item.
- [x] From the 11-02 review, fixed:
      - Walk reminders survive a replan after 8:30. Opening the app or tapping the briefing used
        to delete that day's (and usually tomorrow's); there's still at most one a day. iOS.
      - Queued notifications are dated by when the forecast was fetched, not the current
        reading's time, so tapping the briefing no longer costs tomorrow's its weather. iOS.
      - A Remote Config theme can leave out its day or night captions; iOS used to drop the whole
        list.
      - The walk card no longer says "Now is a great time" after sunset, and a reading from an
        earlier day uses today's sunrise and sunset in the Daylight tile and walk card. Both apps.
      - "Today's forecast" briefings use the city's day, and a walk reminder's bad-air note looks
        from the walk on, as on Android. iOS.
      - The listing and READMEs say rain alerts are off, and the READMEs list the tile and theme
        switches.
      - One Remote Config theme this version can't read is skipped instead of dropping the whole
        list. Both apps.
      - Tidy-ups: the walk rules live in one place (both apps); on iOS the theme list is decoded
        once per Remote Config value, each tile's default is written once, the briefing's walk and
        bad-air checks share one "rest of the day", theme colours reuse `Color(hex:)`, and
        `SeasonalThemeTests` reads dates the same on any calendar. (11-02)
- [x] Rain alerts off for the first release: `rain_notifications_enabled` false in Remote Config
      (deployed 11-02) and in both apps' built-in defaults, so the Settings switch and the
      onboarding line stay hidden from the first launch; Android doesn't make the channel. The
      website's home, privacy and support pages no longer mention them. (11-02)
- [x] iOS: what queued notifications say follows how old the weather is, since they go off
      whether or not iOS has refreshed. Within a day of the fetch a briefing gives the hour's
      weather and the walk, and walk nudges are queued; up to 3 days, "Today's forecast for
      Knoxville" with the high and low; later, "Tap to see today's weather.". The briefing's
      picture is the forecast at 8:30, not the weather when it was planned (often the night
      scene). (11-02)
- [x] Android: the morning briefing is back on 8:30 every day. Each run was due a day after the
      last one finished, so every held-back run made the rest later (11:46 AM on the Pixel after
      four days). Each run now sets the next for the following 8:30. (11-01)
- [x] Captions without a dog: with Dog walking off, no-dog lists swap the walks and paw care for
      things Misty would do ("No sun, no problem. The birds are still out, says Misty."). (11-01)
- [x] Seasonal themes, with Halloween: JSON themes (fixed date, range or nth weekday), shipped
      in the apps and replaceable from Remote Config (`seasonal_themes`,
      `seasonal_themes_enabled`). Each sets the hero colours and caption, card tint, section and
      tile header colours for light and dark mode, and the pets' decorations. Picked by the city's
      own date, so it ends by itself. Not yet deployed to Firebase. (10-31)
- [x] UV tile: the index, today's peak while it's ahead ("Peak 8 at 1 PM"), then "Shade for pale
      noses" on a high day with a dog. Every Today tile now has a Remote Config switch
      (`tile_*_enabled`); UV starts off. (10-30, done 10-31)
- [x] First TestFlight build: 1.0 (1) uploaded from `main` with `xcodebuild`, internal group
      "Family". The next upload needs build 2. (10-31)
- [x] App Store listing in App Store Connect: name "Sunny & Misty: Pet Weather", text, screenshots
      (6.3" and 6.9" iPhone, 13" iPad), age rating 4+, free in 174 countries (not China mainland),
      App Review contact and notes. (10-31)
- [x] iOS signing: FangJet LLC's team (`3V3NR8HP58`), your iPhone registered, and a Release archive
      that exports with App Store signing. (10-30)
- [x] Update prompts: `ios_`/`android_min_supported_version` block the app until it's updated,
      `ios_`/`android_min_recommended_version` show a banner ("Not now" hides it a week). The keys
      are live in Remote Config, all empty. (10-30)
- [x] The hero's high and low came from OpenWeather's current reading (the spread across nearby
      stations right now) and today's forecast only covered the hours left. Both now show the
      whole day from the forecast. (10-30)
- [x] Also done 10-24 to 10-31, with the details in git history and the READMEs: the pets redrawn
      after the app icon; iOS notification stacks ("3 more briefings"); bad air in the
      notifications; city search without duplicates; the day detail's double divider; the Air
      quality tile in one forecast request, with its Remote Config keys and bad air kept out of
      suggested walks; the full-width odd tile; iOS "No cities added yet" in dark mode; Android's
      release build listing missing keys; iOS tests reusing 8 preference files (and 60 old test
      simulators, 329 GB, deleted); Xcode 27.1 settings; old branches cleaned up; fresh README and
      store screenshots. (10-30, 10-31)
