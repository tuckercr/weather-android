# City search

How adding a city by name works today, and what could make the results more sensible. Written
11-05 after "Nas" (on the way to "Nashville") gave strange results and "Nashvill" gave none.

The same file is in both repos, like NOTES.md: keep the two in step. Its code references are to
the Android app.

## The current flow

1. **The sheet.** `AddCitySheetContent`
   ([CurrentWeatherScreen.kt:713](app/src/main/java/com/fangjet/weather/ui/CurrentWeatherScreen.kt:713))
   has one text field; every keystroke calls `GeoLocationViewModel.onCityInputChange`
   ([:805](app/src/main/java/com/fangjet/weather/ui/CurrentWeatherScreen.kt:805), wired at
   [:429](app/src/main/java/com/fangjet/weather/ui/CurrentWeatherScreen.kt:429)). The keyboard's
   Search key only hides the keyboard (:809-810). A progress bar shows while a search runs (:813).
   Suggestions are listed in the order they arrive, in a card capped at 220 dp (:815-842); nothing
   is highlighted or grouped.
2. **Minimum length.** Under 2 characters (after trimming) clears the list and searches nothing
   ([GeoLocationViewModel.kt:59-64](app/src/main/java/com/fangjet/weather/model/GeoLocationViewModel.kt:59)).
   The repository checks the same again
   ([GeocodingRepository.kt:22-25](weather-api/src/main/java/com/fangjet/weather/data/GeocodingRepository.kt:22)).
3. **Debounce.** Each keystroke cancels the previous search job (:58), then waits 280 ms (:67).
4. **Latest search wins.** After the wait and again after the response, the job drops its result if
   the field no longer holds the query it searched for (:68-71, :73-76). A failed search shows an
   empty list, with no message (:78).
5. **The request.** `searchCities` returns a failure if the OpenWeather key is blank
   ([GeocodingRepository.kt:18-21](weather-api/src/main/java/com/fangjet/weather/data/GeocodingRepository.kt:18)),
   otherwise calls `GET geo/1.0/direct?q=<typed text>&limit=8&appid=…` (:29;
   [GeocodingApi.kt:9-14](weather-api/src/main/java/com/fangjet/weather/data/remote/GeocodingApi.kt:9)) on
   `https://api.openweathermap.org/`
   ([WeatherNetworkModule.kt:24](weather-api/src/main/java/com/fangjet/weather/di/WeatherNetworkModule.kt:24)).
   The text goes as typed: no country, no state, no language.
6. **Parsing.** `GeocodingDirectDto` keeps `name`, `lat`, `lon`, `country` and an optional `state`
   ([GeocodingDirectDto.kt:9-15](weather-api/src/main/java/com/fangjet/weather/data/remote/dto/GeocodingDirectDto.kt:9));
   `local_names` is ignored.
7. **Labels.** "name, state, country", leaving out a blank state: "Nashville, Tennessee, US",
   "Paris, FR" ([GeocodingRepository.kt:33-46](weather-api/src/main/java/com/fangjet/weather/data/GeocodingRepository.kt:33)).
   The country is the ISO code, never a country name.
8. **De-duplication.** `distinctBy { it.label }` keeps the first of rows with the same label (:29).
   NOTES.md records the side effect: two different places with the same name, state and country
   show as one row.
9. **Ordering.** None of our own: the list is the API's order, after de-duplication.
10. **Limit.** We ask for 8, but OpenWeather's documentation says "up to 5 results can be returned",
    and live queries never return more, so 5 is the real ceiling, and de-duplication can leave
    fewer.
11. **What Add city adds.** The picked suggestion, **otherwise the top suggestion showing**,
    otherwise the typed name without coordinates
    ([GeoLocationViewModel.kt:42-53](app/src/main/java/com/fangjet/weather/model/GeoLocationViewModel.kt:42)).
    So typing "Paris" and tapping Add takes whatever the API put first. A name-only city is geocoded
    later by taking the API's first result too
    ([CityListViewModel.kt:90](app/src/main/java/com/fangjet/weather/model/CityListViewModel.kt:90),
    [:119-122](app/src/main/java/com/fangjet/weather/model/CityListViewModel.kt:119)). Ordering
    therefore matters beyond the dropdown: it decides which city is added.

iOS has its own copy of this flow; anything changed here should change there too.

## Ordering and matching on OpenWeather

Live queries on 11-05, with the app's request (`limit=8`), rows exactly as returned:

| Query | Rows (name, state, country) |
|---|---|
| Na | NA, Jahra KW; NA, Jahra KW (another point); Nau, Sughd TJ; Nur, Mazandaran IR; Na, Bagamati NP |
| Nas | Nasu JP; Nas, Xinjiang CN; \`Anas, Al Hudaydah YE; Nås SE; Nas, Auvergne-Rhône-Alpes FR |
| Nash | Nash TX; Nash, England GB; Nash OK; Nash, Gilan IR; Nash WI |
| Nashv, Nashvill | Nothing |
| Nashville | Nashville TN, KS, GA, AR, IL |
| Paris | Paris, Ile-de-France FR (three times, a few hundred metres apart); Paris TX; Paris KY |
| Springfield | Springfield IL, MA, MO, OH, OR |
| Springfield,IL,US | Springfield IL only |
| Paris,US | Paris TX, KY, TN, IL, ME |

What this shows:

- **5 rows at most**, whatever `limit` says, as documented.
- **It matches whole names, not prefixes.** A row comes back when its name, or one of its
  `local_names`, equals the query, loosely: accents and some punctuation are ignored (Nås, \`Anas)
  and short queries match near names (Nasu, Nau, Nur). That explains both reports:
  - "Nas" never means "the start of Nashville". It finds five small places called something like
    "Nas", in Japan, China, Yemen, Sweden and France, and none of them is what anyone typing
    Nashville wants. "Nash" finds villages called Nash.
  - "Nashv" through "Nashvill" match nothing. The list only fills in at the full "Nashville".
- **Ordering is not documented**, but for whole names the big place comes first (Nashville TN,
  Paris FR, Springfield IL), so it looks like some importance ranking. We don't re-sort.
- **Duplicates eat the 5 rows.** "Paris" spends three of its five on central Paris; after our
  de-duplication the user sees only 3 rows. "Na" shows 4.
- **The 5-row cap hides real places.** "Springfield" has no room for Springfield TN, KY or VA.
  "Paris,US" helps a US user, but not one in Tennessee (Paris TN is third).

To run these again without printing the key (from the Android repo's root):

```bash
KEY=$(grep '^OWM_API_KEY=' local.properties | cut -d= -f2-); for q in Nas Nash Nashvill Nashville Paris Springfield; do echo "== $q"; curl -s "https://api.openweathermap.org/geo/1.0/direct?q=$q&limit=5&appid=$KEY" | python3 -c 'import json,sys; [print(" ", r["name"], r.get("state"), r["country"]) for r in json.load(sys.stdin)]'; done
```

## What OpenWeather's geocoding supports

| Feature | Used? | Notes |
|---|---|---|
| `q` as `city,state,country` | Partly: the city only | State codes are for the US only; country is an ISO 3166 code. "Springfield,IL,US" narrows to one. |
| `limit` | Yes, 8 | The documented maximum is 5. |
| `appid` | Yes | The bundled key, or Remote Config `openweather_api_key`. |
| `local_names` in the response | No | Names in other languages; could label a city in the user's language. |
| Zip/post code (`/geo/1.0/zip?zip=E14,GB`) | No | Could take a post code typed into the same field. |
| Reverse (`/geo/1.0/reverse`) | No | Not search; NOTES.md mentions it for naming My Location. |
| Prefix, fuzzy or ranked search | Not offered | No parameter for it, and no `lang`. |

## Options

### 1. Smaller wins on the current API

- **Fix `limit` to 5.** No change in results; just honest.
- **Pass a country.** When the text has no comma, append the device's region ("Paris,US" on a US
  phone)? This **hides** results elsewhere, so it can't be the only search: better as a second,
  parallel request whose rows go first. Doubles requests on the shared key.
- **Let people type "Springfield, IL".** Already works (the text goes to `q` as is); a hint in the
  field ("City, state or country") would tell people.
- **Rank exact names first.** On a whole-name API every row is already an exact match, so this does
  little here. It matters with a prefix search (option 3).
- **Sort by country match.** Rows in the device's country first, the rest in API order. Cheap,
  local, no new data. Helps "Paris" on a US phone (and hurts the US user who wanted France).
- **Cost and privacy:** none beyond OpenWeather's free calls. **Effort:** an hour or two each, plus
  tests and the iOS copy. **Doesn't fix** "Nas" or "Nashvill".

### 2. Ordering by the user's rough location

- **Device region** (`Locale.getDefault().country`, or the SIM / network country from
  `TelephonyManager`). No permission, never leaves the phone when used only for sorting. Wrong for
  travellers and people with a foreign locale.
- **Last known location**, only when location permission is already granted (most users grant it in
  onboarding for My Location). Sort rows by distance from it, on the phone. A saved My Location's
  coordinates are already stored, so no new location read is needed at all. Sending it to a server
  as a bias would be new, but sorting locally sends nothing extra.
- **IP geolocation** (a third-party lookup). Works without permission, but sends the IP address to
  another company, which needs a line in the privacy policy and the Data safety form (approximate
  location shared with a third party), and many such services forbid commercial use on free plans.
  Not worth it when the region and My Location are already on the phone.
- **Play Data safety:** sorting on the phone with data the app already has needs no change (data
  processed only on the device isn't "collected"). Sending location to a geocoder as a bias would be
  approximate location shared with that provider, for app functionality; the forecasts already send
  coordinates to OpenWeather and Open-Meteo, so check what the form says today before adding a
  provider.
- **Effort:** a small sort function in `:weather-api` (distance, then country) with JVM tests; half
  a day with iOS. Pairs with any provider, but only re-orders the 5 rows OpenWeather returns.

### 3. Prefix and fuzzy matching

**Open-Meteo geocoding** (`geocoding-api.open-meteo.com/v1/search?name=…&count=…&countryCode=…`),
the same provider as our forecasts. Its [documentation](https://open-meteo.com/en/docs/geocoding-api):
under 2 characters returns nothing, 2 characters match an exact name, 3 or more use prefix
matching (ignoring case and accents), up to 100 results, `countryCode` filters, and "Paris, FR"
or "Springfield, Illinois" qualifiers after a comma match exactly. Responses carry `population`,
`feature_code`, `admin1` (state) and the country name. No key for non-commercial use. Ordering is
not documented. Results from live queries (11-05, `count=8&language=en`):

| Query | First results |
|---|---|
| Nas | Nashik IN (1.5M), **Nashville TN**, Nassau BS, Nasiriyah IQ, Ahvāz IR, Grozny RU, Nasimshahr IR, Nås SE |
| Nas, `countryCode=US` | **Nashville TN**, Nash TX, Nash OK, Nashua NH, Nash TX, Nash ND |
| Nash | Nashik IN, **Nashville TN**, then six small places called Nash |
| Nashvill | **Nashville TN**, then Nashville NC, GA, AR, IL, IN, MI, OH |
| Nashville | Same as Nashvill |
| Nashvile (typo) | Nothing: prefix only, no fuzzy matching |
| Paris | **Paris FR**, then Paris TX, TN, KY, IL, ME, MO, ID |
| Springfield | Springfield MO, IL, MA, OH, TN, KY, GA, CO |
| Nashville,TN | Nashville TN, then a water park, an airport and a recreation area |
| Pari,FR | Nothing: with a qualifier, the name doesn't prefix-match either |

Compared with OpenWeather's rows above, it fixes the "Nashvill" case outright, puts Nashville
second for "Nas" (OpenWeather never shows it), and has room for all the Springfields. Ordering looks like
population, mostly (big cities first), but not strictly (Springfield MA, 154k, comes after IL,
114k), and alternate names can match ("Nas" finds Ahvāz and Grozny). With a qualifier it returns
airports and parks, which we'd filter by `feature_code` (keep `PPL*`). A country bias from the
device region brings Nashville to the top for "Nas" on a US phone.

- **Licensing:** the open question in NOTES.md applies here too: Open-Meteo's free tier is
  non-commercial, the licence emails sent 10-24 have no reply, and the fallback is to turn
  `open_meteo_enabled` off. Search would need its own switch (or fall back to OpenWeather with the
  same one), and the commercial plan, if bought, covers geocoding. It also needs the attribution
  "Location data based on GeoNames" in About.
- **Privacy:** the typed text goes to Open-Meteo instead of OpenWeather; Open-Meteo already gets
  every city's coordinates. No Data safety change beyond naming the provider if the form lists them.
- **Cost:** free now; covered by the commercial plan if one is bought. More calls than OpenWeather
  today, because a prefix search makes the dropdown useful on every keystroke (the 280 ms debounce
  stays).
- **Effort:** a new Retrofit API and DTOs in `:weather-api` (it already talks to Open-Meteo), a
  label built from `name, admin1, country_code` so labels look as they do now, a Remote Config
  switch, tests, and the iOS copy. About a day per platform.

**Android's `Geocoder`** (`getFromLocationName`, with an async listener from API 33). Free and needs
no key, but it is a whole-address geocoder, not autocomplete: no promise of prefix matching, results
vary by device and Play services (`Geocoder.isPresent()` can be false), and iOS's `CLGeocoder`
behaves differently, so the two apps would disagree. Not recommended as the main search. Google's
Places Autocomplete does this well but is paid per request and needs a billed Google Cloud key.

**Client-side handling on OpenWeather**, if we stay with it:

- **Retry with the last character dropped** when a search returns nothing. Fixes "Nashvill" only
  when the user is one letter short, and turns every miss into two requests; "Nas" still finds the
  wrong places.
- **Keep the last good list** while a longer query returns nothing. Doesn't help here: with
  whole-name matching, "Nashvi" and "Nashvil" had nothing to keep either.
- **Wait for the Search key** instead of searching as you type, which is honest about whole-name
  matching but feels worse.
- **A bundled city list** (GeoNames cities15000, about 25k places, a few MB) searched on the phone:
  instant prefix search, offline, sorted by population, no calls. CC BY licence (attribution).
  Larger APK, a list to refresh now and then, and both apps need it. Worth keeping in mind if
  Open-Meteo's licence falls through.

## Decisions

Reviewed and agreed 11-05; not built yet. The details are in the sections below.

1. **Open-Meteo for suggestions** (`city_search_provider`, default `open_meteo`), OpenWeather as
   the fallback when it fails, and both off Open-Meteo when `open_meteo_enabled` is. A commercial
   Open-Meteo licence will be bought before release (NOTES.md).
2. **Filtering:** populated places except historical, abandoned and destroyed ones; no minimum
   population; rows merged only when the label matches and they're within 2 km, with the county
   added to tell same-label places apart.
3. **Sorting on the phone:** the phone's country first, then same-name places nearest to My
   Location first, with a size guard (`city_search_size_ratio`, 20) so Paris, France stays above
   Paris, Texas. No sorting on the OpenWeather fallback.
4. **Add with nothing tapped** takes the first row whose name matches what was typed, otherwise
   asks for a pick. **Already saved** is a label match or within 2 km, My Location aside.
5. **US ZIP codes** work; other postcodes search nothing, but town names with a number in them
   (100 Mile House) still search.
6. **Copy:** "town or city" to search, "location" for what's saved; the strings are approved.
7. **Analytics:** `source` and `provider` on `city_added`.
8. **Remote Config keys** and their defaults, all approved.

The suggestion list's layout is left to the full-screen Add location screen (NOTES.md).

## Remote Config design

Agreed 11-05, defaults included; not built. Everything about search that we might want to change after release is a
Remote Config key, read on every search (like `open_meteo_enabled` on every forecast fetch), with an
in-app default and validation, so a bad console value falls back instead of breaking search.

### Keys

| Key | Type | Default | What it does |
|---|---|---|---|
| `city_search_provider` | string | `open_meteo` | Where suggestions come from: `open_meteo` (prefix search) or `openweather` (today's whole-name search). Anything else uses the default. `open_meteo_enabled` false forces `openweather`, so the one licence switch turns off every Open-Meteo call |
| `city_search_results` | number | `8` | Most suggestions shown; whole number, valid 3–10. OpenWeather never returns more than 5 |
| `city_search_country_first` | boolean | `true` | Suggestions in the phone's country go first, otherwise in the provider's order |
| `city_search_nearest_first` | boolean | `true` | Places with the same name are listed nearest first, measured from My Location when it's saved (no location is read for this) |
| `city_search_size_ratio` | number | `20` | Neither rule above can lift a place above one this many times its population, so a US phone still gets Paris, France before Paris, Texas (86×). Whole number, valid 2–1000; `0` turns the guard off. Anything else uses the default |
| `city_search_delay_ms` | number | `280` | Wait after the last keystroke before searching; whole number, valid 100–1000. Raising it cuts calls if usage gets close to a quota. New key: today the 280 is fixed in code ([GeoLocationViewModel.kt:67](app/src/main/java/com/fangjet/weather/model/GeoLocationViewModel.kt:67)) |

Left out on purpose: the minimum query length (2 is right for both providers: Open-Meteo matches
exact names at 2 and prefixes from 3), the filtering rules (fixed in code, see
[Filtering](#filtering)), and falling back on error (always on, below). Each
could become a key later if a reason turns up.

### Behaviour

1. **Choose the provider.** `open_meteo` when `city_search_provider` says so **and**
   `open_meteo_enabled` is true; otherwise `openweather`.
2. **Ask for more than we show.** Open-Meteo: `count=20`, so filtering and sorting have room;
   OpenWeather: `limit=5`, its maximum. No `countryCode` filter: it would hide the Paris in France
   from a US phone. The country only sorts.
3. **Fall back on failure.** If Open-Meteo fails (network error, timeout, HTTP error), search
   OpenWeather with the same text. An empty answer is an answer, not a failure: "Nashvile" (typo)
   has no prefix match anywhere, and OpenWeather wouldn't find it either.
4. **Filter** (decided 11-05; see [Filtering](#filtering)). Open-Meteo: keep populated places
   (`feature_code` starting `PPL`) except historical, abandoned and destroyed ones. Both: merge
   rows that are the same place (same label, within 2 km).
5. **Sort**, keeping the provider's order otherwise (a stable sort):
   - `city_search_country_first`: rows whose country is the phone's region first
     (`Locale.getDefault().country`; iOS `Locale.current.region`). No permission, nothing sent.
     Decided 11-05 over My Location's country: Add city is likely to be used most by people who
     don't share their location, and the phone's region works for them. (My Location's country
     would also need new code: it's saved as coordinates labelled "My Location", with no country.
     OpenWeather's current-weather response has one we don't parse, if travellers ever need it.)
   - `city_search_nearest_first`: rows with the same name move together to where the first of them
     was, nearest first. "Springfield" on a phone whose My Location is in Tennessee shows
     Springfield, TN first; "Nas" in Texas still shows Nashville above Nash, TX, because the
     different names keep the provider's order. Pure distance sorting was rejected for exactly
     that: it would put every nearby village above the city being typed.
   - **Size guard** (decided 11-05): a row the two rules would lift moves up only past rows less
     than `city_search_size_ratio` times its population. A row with no population can't pass a
     row that has one; a row with no population blocks nothing. Without it, both rules put
     Paris, Texas above Paris, France for any US user (country-first on every US phone,
     nearest-first from any US My Location).
   - **On the OpenWeather fallback, no sorting.** It has no populations, so the guard can't work,
     and its own order already puts the big place first (Paris, France). Both rules are skipped.
6. **Trim** to `city_search_results`.

The guard with the default 20, against live Open-Meteo results (11-05):

| Search | Who | First rows | Why |
|---|---|---|---|
| Paris | US phone | Paris FR, Paris TX | Texas is 86× smaller |
| Paris | My Location in Tennessee | Paris FR, Paris TN, Paris TX | Tennessee passes Texas, not France |
| Nas | US phone | Nashville TN, then Nashik IN | Nashik is only 2.2× bigger |
| Springfield | My Location in Tennessee | Springfield TN first | Missouri is 10× bigger |
| Portland | My Location in Maine | Portland ME first | Oregon is 9.8× bigger |
| Birmingham | US phone | Birmingham AL first | England's is 5.9× bigger |
| Sydney | Canadian phone | Sydney AU, then Sydney NS | Nova Scotia's is 53× smaller |
| London | Canadian phone | London GB, then London ON | 21×, just over the line; at 25, Ontario first |

Considered and rejected: never passing a capital (fixes Paris and London, not Sydney, which isn't
one); country-first only for unfinished names (nearest-first still does it to Paris); country-first
off (loses Nashville for "Nas").

Example, Open-Meteo (`count=20`, checked live), a US phone, "Nas": Nashville TN, Nashik IN,
Nassau BS, Nasiriyah IQ, Ahvāz IR, Grozny RU, Nasimshahr IR, Nash TX. Nashville (689k) passes
Nashik (2.2× bigger) to the top. Nash TX (3k) passes only Nås (205) and stops under Nasimshahr
(64× bigger), and Nash OK (204) ends up ninth, below the eight shown. Today the same phone gets
Nasu JP, Nas CN, \`Anas YE, Nås SE, Nas FR. The 20 only hold three US rows (the tail is villages
called Näs in Sweden), so country-first works with what the provider ranks highly; it can't find a
small local town the provider ranks low, and the guard keeps one below the big cities anyway. A
second request with `countryCode` would, at twice the calls; not proposed for now.

### Filtering

Decided 11-05. All fixed in code, the same in both apps; no Remote Config key (a list of place codes
in the console would be more to get wrong than it's worth).

- **Kinds of place (Open-Meteo).** Keep `feature_code` starting `PPL`, except `PPLH` (historical),
  `PPLQ` (abandoned) and `PPLW` (destroyed): nobody wants the weather for an abandoned town. This
  keeps towns, state and county seats, capitals and parts of cities (`PPLX`, so "Shoreditch" and
  "Williamsburg" still work), and drops airports, parks and the like. OpenWeather has no feature
  code, so this applies to Open-Meteo only.
- **No minimum population.** Many real towns have none listed (Nash, Wales), so a threshold would
  hide them. Population is left to the provider's ranking.
- **Same place, or two places?** Two rows are merged (the first kept) only when their labels match
  **and** they're within 2 km of each other. Today's label-only rule merges different towns: the
  two "Richmond, England, GB" in NOTES.md. Labels that match but are farther apart are both kept.
  This also stops OpenWeather spending three of its five rows on central Paris.
- **Telling same-label places apart.** When two kept rows would still read the same, Open-Meteo's
  rows add the county (`admin2`): "Richmond, North Yorkshire, England, GB" and "Richmond, Greater
  London, England, GB". Other rows keep the short label. OpenWeather has no county, so on the
  fallback the two rows look identical; accepted, since the fallback only runs when Open-Meteo
  fails.

### Postcodes

Decided 11-05: US ZIP codes only. Open-Meteo also searches by postcode. Checked live: "90210"
finds Beverly Hills and "37203" Nashville, but British postcodes go wrong ("E14" and "M1" find
places in Abu Dhabi, "SW1A" finds Westminster Abbey).

- **Five digits** (the text before any comma): search as usual, keeping only US rows.
- **A postcode** (decided 11-05): search nothing and show no list when a word mixes letters and
  digits (E14, SW1A 1AA, K1A 0B1, D02 X285), unless it's an ordinal (1st, 10th), or when the text
  is only digits, spaces and hyphens but not five digits (ZIP+4 "90210-1234", a typo). Every
  British outward code mixes them in one word, while real towns keep a number as a word of its
  own (100 Mile House, 23 August in Romania, 10th of Ramadan City in Egypt), so those still
  search. A Dutch postcode ("1012 AB") is a number word and a letter word, so it still searches
  and finds nothing useful; accepted.
- **On the OpenWeather fallback:** no ZIP search (its direct search doesn't take postcodes); the
  list stays empty.
- Add with nothing tapped can't take "90210", since the row's name (Beverly Hills) doesn't match
  what was typed: the user taps the row (see below).
- The copy doesn't mention postcodes; ZIP codes are a quiet bonus for US users.

### Labels

Open-Meteo labels are built the same way as today: `name, admin1, country_code` ("Nashville,
Tennessee, US"), with `admin2` added only to tell two same-label places apart (above).

### Already saved

Decided 11-05. `addLocation` switches to a saved location instead of adding it again when the
labels match (today's check) **or** the two are within 2 km. Labels alone miss the same place:
the providers spell some states differently ("Île-de-France Region" against "Ile-de-France"), and
the county is now added to some labels. Within 2 km the forecasts are effectively the same anyway.

- Name-only locations have no coordinates, so for them only the label counts.
- My Location is left out of the check: it moves, and saving the town you're in as its own
  location is reasonable.
- When it switches, the app says "Already in your locations" (new string, `location_already_saved`)
  instead of switching silently.
- Two places really within 2 km (Shoreditch and the City of London) can't both be saved; accepted.

### Add with nothing picked

Decided 11-05. With prefix search, the top row can be a different place from what was typed ("Nas"
puts Nashville first), so Add stops guessing from a fragment:

- **A list is showing:** Add takes the first row whose name matches what was typed, ignoring case,
  accents and anything after a comma (that part is matched as in [Copy](#copy)). "Paris" adds the
  first Paris in the sorted list (France, thanks to the size guard). "Nas" matches no row, so Add
  shows "Pick a town or city from the list" (new string, `add_pick_from_list`) and adds nothing.
- **No list** (offline, or the search failed): the typed name is saved without coordinates, as
  today.
- **Filling in coordinates later** (`CityListViewModel.addLocation` and
  `fillInMissingCoordinates`, which take the first result today): the same rule, the same
  filtering and sorting, and the phone's region. No matching row leaves the location name-only,
  on OpenWeather's forecast by name, as today.
- A tapped suggestion is added as it is, as today.

### Where the code goes

- `:weather-api` stays free of Firebase, as with `ForecastSourcePolicy`: a `CitySearchPolicy`
  interface (provider, results, country-first, nearest-first, size ratio) supplied by the app from Remote
  Config in `AppConfigModule`.
- `:weather-api`: `OpenMeteoGeocodingApi` and its DTO (on Open-Meteo's geocoding host, a second
  Retrofit base URL), `GeocodingRepository.searchCities(query, origin)` choosing the provider and
  falling back, and a pure `CitySearchRanking` (filter, sort, trim) with JVM tests for each rule
  above. `origin` is the phone's region plus My Location's coordinates, both optional.
- `:app`: `GeoLocationViewModel` reads `city_search_delay_ms` for its wait and passes the origin from
  `PreferencesRepository`. `CityListViewModel`'s name-only lookups pass the same origin, so "Add
  city" with a typed "Paris" also gets the sorted first row.
- **Every place a default lives** (CLAUDE.md): `firebase/remoteconfig.template.json`,
  `remote_config_defaults.xml`, `RemoteConfigRepository.kt`, iOS `RemoteConfigRepository.swift`,
  and README's Remote Config table. The iOS app needs the same provider, filter and sort.
- About: add "Location data based on GeoNames" next to the Open-Meteo credit (always, simpler than
  following the provider).

### Rollout

Ship with the defaults above. If the Open-Meteo licence answer is no, setting `open_meteo_enabled`
false (already the plan) puts search back on OpenWeather with the new sorting still on. If the new
search misbehaves on its own, `city_search_provider` = `openweather` alone reverts it, leaving the
forecasts on Open-Meteo. Deploying the new keys to the console follows the usual fetch, compare,
deploy, and is asked for first.

## Copy

Decided 11-05, the same in both apps:

- **The search heading** in the add-city sheet changes from "ADD A NEW CITY" to "SEARCH FOR A
  TOWN OR CITY" (Android `search_city_section`). One wording for every region: no "state" (wrong for
  British testers) or "county" (wrong for American ones), and no country (nobody adds France).
- **No hint about narrowing with a comma.** People who type "Richmond, North Yorkshire" or "Paris,
  TX" still get it: the text after the comma is matched on the phone against the state, county
  and country of each row, by the start of a word ("London" finds "Greater London"), and only the
  part before the comma goes to the provider. Open-Meteo's own qualifier only understands a
  country or state ("Richmond, North Yorkshire" and "Richmond, London" find nothing, checked
  11-05), which is why this is done on the phone. The OpenWeather fallback can't match a county.
- **"Town or city" to search, "location" for what's saved** (decided 11-05). "City" alone
  doesn't fit a town or a village, and Settings already says My Locations and Manage Locations.
  You search for a town or city, and it becomes one of your locations: "town or city" wherever
  someone types a name (the heading, the field, manual entry, not-found messages), "location" for
  the list and its entries (My Location isn't a town). It also keeps "location" from meaning both
  where the phone is and a saved place in one message. Every user-facing "city" changes; resource names, code and Remote Config keys (`city_search_*`) keep "city", since users
  never see them. Android strings, approved 11-05 (iOS has the same text):

  | String | Today | New |
  |---|---|---|
  | `add_city_sheet_title` | Manage cities | Manage Locations (as in Settings) |
  | `saved_cities_section` | SAVED CITIES | SAVED LOCATIONS |
  | `search_city_section` | ADD A NEW CITY | SEARCH FOR A TOWN OR CITY |
  | `field_city_label` | City | Town or city |
  | `action_add_city` | Add city | Add location |
  | `cd_add_city` | Add a city | Add a location |
  | `cd_remove_city` | Remove city | Remove location |
  | `empty_cities_title` | No cities added yet | No locations added yet |
  | `empty_cities_body` | Tap the + button to add your first city and get started. | Tap the + button to add your first location and get started. |
  | `add_pick_from_list` (new) | | Pick a town or city from the list |
  | `location_already_saved` (new) | | Already in your locations |
  | `error_city_not_found` | We couldn't find that city. Try a different search. | We couldn't find that town or city. Try a different search. |
  | `location_skip_button` | Enter a city manually | Enter a town or city manually |
  | `add_my_location_failed` | Couldn't find your location. Try again, or add a city by name. | Couldn't find your location. Try again, or search for a town or city. |
  | `location_fetch_failed` | Could not determine your location. You can still add cities manually. | Could not determine your location. You can still add locations manually. |
  | `locations_notification_footnote` | Notifications are for My Location, otherwise the top city. | Notifications are for My Location, otherwise the top location. |
  | `notif_onboarding_body` | …a weather briefing for your city. | …a weather briefing for your location. |
  | `notif_onboarding_body_dog` | …a weather briefing for your city, and Sunny will… | …a weather briefing for your location, and Sunny will… |

  Also to change with it: README's descriptions of these screens, the Play and App Store listings
  and screenshot captions if they say "city", and the iOS strings.
- **About:** "Location data based on GeoNames" next to the Open-Meteo credit (Open-Meteo's
  required wording).

## Analytics

Decided 11-05. `city_added` gets two parameters, both anonymous categories, never which place:

| Parameter | Values | Tells us |
|---|---|---|
| `source` | `suggestion` (a row was tapped), `typed_match` (Add took the matching row), `typed_name` (saved by name with no list, e.g. offline), `my_location` | Whether people find their town in the list; many `typed_name` means it isn't helping |
| `provider` | `open_meteo`, `openweather` | How often the fallback runs (`my_location` and `typed_name` send the provider the search would have used) |

No event for searches that find nothing: it would invite sending what was typed, which the privacy
policy promises never to send. To change with it: README's analytics table (`city_added` says no
parameters), the privacy policy at sunnyandmisty.com/privacy, and the iOS app.
