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
      below are settled, and pick a build on the version page. Release is set to manual. The
      App Store Connect version is still called 1.0: rename it to the build's version (build N is
      1.0.(N-1), so 1.0.1 for build 2) before submitting: a version page only offers builds
      with its own number. (11-03)
- [ ] **App Store Connect description** still says "a heads-up before rain"; rain alerts are off
      for the first release. The suggested description in listing-review.html is updated. (11-02)
- [ ] **Play Console: background location declaration** for Follow my location, before the first
      release goes to review: the permissions declaration form (what the feature is, why it needs
      "Allow all the time"), a short video of turning it on and a briefing following the phone,
      and the in-app explanation, which the switch already shows. Google reviews it. App Store
      Connect: say in the review notes what "Always" is for (Settings › Notifications › Follow my
      location). (11-03)
- [ ] **Add Jessica to the TestFlight "Family" group** once she accepts the App Store Connect
      invitation (sent 10-31). (10-31)
- [ ] **Storms in the dog walking line, and maybe the hero text.** Warn about thunder in the walk
      card ("Storms from 4 PM, walk before 3"), and possibly the pets' hero line. Check that both
      forecast providers give thunderstorm codes hourly. Should storm hours also be dropped from
      suggested walks, like bad air? (10-30)
- [ ] **Hourly card too tall when the rain chance is off screen.** Every hour keeps a line for
      the rain chance if any hour has one, so with rain only later in the day the hours on screen
      end in an empty band. With no rain at all it sizes correctly (checked 10-31). Better: the
      same height for every hour, with the chance under a slightly smaller icon in the icon's own
      space, so the card never changes height and never has a gap. Both apps. (10-31)
- [ ] **Manage Locations polish.** Android's rows wrap a long name at half the row's width
      ("Paris," above "Ile-de-France, FR"), because the name and the space after it share the row
      equally. iOS's Settings row says "Manage locations" where Android's says "Manage Locations",
      the screen's title on both. (11-06)
- [ ] **Android: the city search in SEARCH.md**, assigned to a new developer. iOS has had it since
      11-06, so until it lands the apps differ: Open-Meteo prefix search with the OpenWeather
      fallback, filtering, county labels, sorting with the size guard, text after a comma, US ZIP
      codes, the six `city_search_*` Remote Config keys, `city_added`'s `source` and `provider`,
      the GeoNames credit, and the last "town or city" and "location" wording (onboarding and
      notification onboarding). SEARCH.md marks the changes building it on iOS made (11-06).
      When it's built, add the keys to `firebase/remoteconfig.template.json` with iOS's defaults;
      iOS reads them with in-app defaults, so nothing needs deploying first. (11-06)
- [ ] **Privacy policy: `city_added` now has `source` and `provider`** on iOS (anonymous
      categories, never a place or what was typed). Update sunnyandmisty.com/privacy before the
      release that ships it, and the App Store privacy answers if they list events. (11-06)
- [ ] **Review analytics, and add parameters where they'd help.** Only four events today, three
      with no parameters. Worth knowing: which permissions are granted (notifications, location,
      "Allow all the time" for Follow my location) from onboarding and from Settings; how cities
      are added (search suggestion, typed by name, My Location; `city_added`'s `source` is already
      decided in SEARCH.md); and the dog answer and notification switches. Anonymous categories
      only, never a place or what was typed; keep the opt-out and kill switch gates. Each change
      updates README's analytics table, the privacy policy and the iOS app. Both apps. (11-05)
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

## Design review (11-03)

- [ ] **Daylight tile.** What it does now (both apps): before sunrise, "Sunrise in 2 h 10 min"
      with the sun at the start of the arc; in the day, "4 h 29 min left" with the sun along the
      arc; after sunset, the arc filled with the sun down, "The sun has set", and that day's
      sunrise and sunset times until midnight. It doesn't look ahead: tomorrow's sunrise only
      shows after midnight, as "Sunrise in …". The UV index rides in the tile's header ("UV 1 ·
      Low") while the UV tile is off (`tile_uv_enabled`, off by default), and is hidden after dark.
      Consider: tomorrow's sunrise after sunset, and taking the UV off this tile.
- [ ] **Main screen horizontal margins.** 16 from the screen edge to the cards on both: 16 dp on
      Android (`padding(horizontal = 16.dp)` on the weather column) and 16 points on iOS
      (`.padding(.horizontal, 16)` on the weather content). A point is iOS's density-independent
      unit, like dp: 3 pixels on a 3x iPhone such as the 14 Pro. Cards add their own padding
      inside (12 to 16). Consider reducing it, for example to 12, for more room in the hourly
      strip and tiles; check the iPad and landscape layouts, and leave the onboarding screens' own
      margins (24) alone.
- [ ] **Tile sizes.** Today tiles come in two widths now: half (side by side, as tall as the
      tallest in the row) and full. Consider four, adding 2/3 and 1/3: Sky and Daylight could be
      2/3 wide when they don't need the full width, and Feels Like and UV could have smaller 1/3
      versions, so a row holds a 2/3 and a 1/3 tile.
- [ ] **A colour scale for some tiles.** Green, blue, orange and red, for tiles whose value has a
      severity: Wind (red for storm force), and maybe UV (Air Quality has its own scale already,
      and Feels Like and Paw Check have one since 11-04). Define what each colour means per tile.
      Never colour alone: keep the words ("Gale", "Very hot"), and check contrast in both themes and
      on the seasonal card colours, which Feels Like's band word hasn't been checked on yet either.
- [ ] **A hint that the hourly strip scrolls.** Nothing shows there's more past the edge unless an
      hour is cut off there by luck. Consider fading the edges, letting the last hour on screen
      always peek out half cut, or a small arrow; check it at large text sizes and on iPad, where
      more hours fit.
- [ ] **Radar outside RainViewer's coverage.** Test a city whose coordinates are outside it
      (RainViewer has no radar for much of Africa, central Asia and the oceans): do the tiles come
      back empty (an empty map that reads as "no rain"), as a placeholder, or as errors? If it can
      be told apart (RainViewer may offer a coverage layer; check its API), say "No radar here" or
      hide the card rather than show an empty map.
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

- [ ] **Wind:** gusts in the Wind tile ("14 mph, gusts 30"), and wind in the words too: the
      pets' captions, the walk card and the night "stroll" caption don't consider strong wind
      yet. (10-26, 10-31)
- [ ] Sunshine hours; snow depth feeding the paw check.
- Decided: no Storm watch tile (mocked up 10-30). Storms go in the walk line instead (Do soon).
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

- [ ] **Danger in the notifications.** At hurricane force the app shows a Danger warning, but the
      8:30 briefing and the walk nudges only find no walking hours ("Not a good day for
      walkies."). A forecast of it could be said in the briefing, or get a warning of its own.
      Both apps. (11-07)

## From the 11-02 code review

Found reviewing the iOS changes since TestFlight build 1; where Android works the same way it's
noted. Checked against the code, not fixed yet.

- [ ] **Sunny's coat on the sunny sky.** The lighter coat (#E9B158) is hard to tell from the
      sunny sky (#FAC775), and the walk nudge's picture is always the sunny scene. It was lightened
      for the old Halloween card, which has since changed. Try a couple of coat colours side by
      side. Both apps.
- [ ] **Seasonal theme at night.** The night look (colours, caption, costumes off, lit pumpkin)
      follows the night mood, so on a rainy or freezing Halloween evening the day look shows; go by
      the clock instead. The night caption also shows from midnight on the 31st ("Too many
      doorbells" at 3 AM). Both apps.
- [ ] **Witch hat over the umbrella.** On a rainy Halloween Misty's hat is drawn over the umbrella;
      and a theme with only the hat would take Sunny's sunglasses or scarf off too. Both apps.
- [ ] **Halloween contrast.** Tile labels (#C25E00 on #FCE9D2) are 3.6:1, under the 4.5:1 AA needs;
      the day pumpkin and Misty's grey fur barely show on the #B4500C hero, and one bat on the night
      hero. Fixable in Remote Config before next Halloween.
- [ ] **UV tile** (off by default). The peak only counts hours from the last fetch, so in the
      afternoon it says "At today's peak" (and loses "Shade for pale noses"); a day of all-zero UV
      reads "Peak 0". Turning the Daylight tile off also shows the UV tile, whatever
      `tile_uv_enabled` says. Both apps.
- [ ] **Theme captions and the no-dog rule.** A theme's captions show whether or not there's a dog,
      so a future one about walks would reach people with Dog walking off (none mention walks yet).
      Both apps.
- [ ] **Wording.** "Then rain" or "then maybe rain" goes by the raw rain chance while the hourly
      strip rounds it (55% shows 60% but says "maybe rain"). Both apps. (Since 11-07 dust is
      "Dusty air today", and the walk card and the Air tile follow `walk_bad_air_band` together.)
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

## Store and release

- [ ] Licences: RainViewer is personal and educational use only, and Open-Meteo's free tier is
      non-commercial. Emails were drafted on 10-24; no replies yet. Fallback: launch with
      `radar_enabled` / `open_meteo_enabled` off in Remote Config. Plan (11-05): buy an Open-Meteo
      licence before release; the new city search (SEARCH.md) depends on it too.
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
- [ ] Store privacy answers (App Store App Privacy, Play Data safety): My Location's coordinates
      now also go to the phone's place lookup (Apple's on iPhone, Google's on Android) to name it.
      The privacy policy on sunnyandmisty.com says so since 11-06. (11-06)
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
- [ ] Rename the "Furcast-Shots" and "Furcast-iPad-Shots" simulators. (10-28)
- [ ] **Hardcoded numbers worth a Remote Config key.** Look for more like city search's 280 ms
      wait, now `city_search_delay_ms` (iOS 11-06; Android with its search): values we
      might want to tune after release without an update (timeouts, waits, limits, thresholds).
      List them first, then pick the few worth a key; each needs a default and validation in both
      apps. Both apps. (11-05)

## Ideas

- [ ] Small touches: tap the pets (Sunny wags, Misty ignores you), a "Sunny's take / Misty's
      take" line in the day detail. (10-26)
- [ ] **Hero card layout.** Try other designs: the pets on the left with the text beside them, or
      a more interesting text layout than one long centred list (conditions, city, temperature,
      high and low, caption). (10-31)
- [ ] **More seasonal themes** (Thanksgiving, winter holidays, the Fourth of July...). A theme
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
- [ ] **Translate the apps?** Both are English only. Android's copy is already in `strings.xml`
      (lint expects it), so translating there is mostly adding a `values-xx` file per language.
      iOS writes every string in the code, with no String Catalog, and SwiftUI's `Text` only
      translates literals and `LocalizedStringKey`, not strings passed in as `String`: tile labels
      (`Tile(label:)`), `windNote`, `fluffLine`, the pets' captions and the notification text.
      iOS would need a String Catalog, those parameters changed to `LocalizedStringKey` (or
      `String(localized:)`), and the strings built in code moved: hundreds of strings. Also the
      pets' voice in each language, the App Store and Play listings, the website and the privacy
      policy. Decide whether, and which languages, first. (11-06)
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
