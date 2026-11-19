# Ratings prompt

Both apps ask for a store rating with the system prompt, at a happy moment, for people who keep
coming back. Both stores had no ratings at launch, against 15 to 65,000 for the rival pet weather
apps, and both rank by ratings. Built the same way on each app, with every setting in Remote
Config, and **off until `review_prompt_enabled` is turned on**. The same file lives in both repos.

Decided 11-19: as recommended below, with no "Rate" row in Settings, and Remote Config off by
default to start with. iOS: `Domain/ReviewPrompt.swift`, `ViewModels/ReviewPromptViewModel.swift`.
Android: `domain/ReviewPrompt.kt` and its view model.

## What the stores allow

| | App Store | Google Play |
|---|---|---|
| The prompt | StoreKit's system prompt (`requestReview` in SwiftUI). Apple's guidelines don't allow review prompts of our own. | The Play In-App Review API's system sheet. |
| How often it shows | At most 3 times in 365 days, and never if the person turned prompts off or already reviewed. The system decides; we can't tell if it showed. | A quota Google doesn't publish ("under a month" apart may not show). We can't tell if it showed. |
| Rules | Ask after real use, not at launch or mid-task. Never from a button tap (it may not appear). Don't ask twice for the same version. | Ask after enough use to give useful feedback. Never ask a question before or during it ("Do you like the app?", "Would you give us 5 stars?"). Never from a button. Don't change or cover the sheet. |
| Testing | Always shows in Debug builds; **never** in TestFlight. | Quota off on the internal test track; `FakeReviewManager` for unit tests. |

Sources: [Apple, ratings and reviews](https://developer.apple.com/app-store/ratings-and-reviews/),
[Google Play In-App Review](https://developer.android.com/guide/playcore/in-app-review).

## Decisions

**1. The system prompt only, with no "Enjoying Sunny & Misty?" question first.** Recommended.
A question first (and sending unhappy people to feedback instead, "review gating") is common in
older apps, but Google forbids it, Apple's guidelines don't allow custom review prompts, and it can
get an app rejected or its ratings removed. The system prompt is also the one with the highest
response.

**2. Who gets asked: installed for at least 7 days, and opened on at least 5 different days.**
Recommended. That's someone who keeps coming back, so they know the app well enough to rate it, and
a weather app's natural rhythm is a quick look most days. Days are the phone's calendar days on
which the weather screen was shown. Both numbers are Remote Config keys; lower them (say 3 and 3)
if you want ratings sooner after launch. Active days count from the update; days
installed count from the real install (decision 11).

**3. When: at a happy moment, a few seconds after the screen settles.** Recommended moments, each
switchable in Remote Config:
- `great_walk`: the walk card says "Now is a great time". The best moment for dog owners: the app
  just told them something useful and good.
- `pleasant_weather`: the pets are in their sunny or fair mood by day. Covers people without a dog,
  who never see the walk card.

Both only when the weather on screen is fresh. Then wait 3 seconds, and only ask if the person is
still on the weather screen with nothing else open.

**4. Never ask when:** Recommended, all of these:
- During onboarding, or on the first day.
- The weather on screen is old, a refresh failed, or the phone is offline.
- The update banner or the "Time for an update" screen is showing.
- The app asked for a permission (notifications, location) in this visit.
- Settings, city search, a day's detail or any other sheet is open.
- The walk card is a "Danger" or "Stay in" warning, or storms are under way.
- The app was opened from a notification (they came for one thing; Remote Config can allow it).

**5. How often: once per app version, and at least 120 days apart, up to 3 times in all.**
Recommended. Apple already stops at 3 a year and Google has its own quota, so asking more often
only wastes the moments. "Once per version" follows Apple's advice; 120 days keeps it to about 3
a year even with frequent releases. All three are Remote Config keys.

**6. Remote Config defaults: off for now, in the app and in the template.** Decided 11-19. The
project keeps each key's default the same in the template and both apps. It goes on from the
console (and later as the default) once it's been tried on a phone.

**7. One set of keys for both apps,** with the same names and defaults, as with every other key.
Recommended. A Remote Config condition can still give one platform different values if needed.

**8. Analytics:** `review_prompt_requested` with `moment` (`great_walk`, `pleasant_weather`) and
`request_number` (1, 2, 3), behind `analytics_enabled` like the rest. The stores never say whether
the prompt showed or what was given, so this counts asks, and the stores' own ratings show the
result.

**9. Built as a pure, unit-tested rule, like the notification planner.** Recommended. One
function (`ReviewPromptPolicy` in `Domain/` on iOS, `domain/` on Android) takes the saved counts,
the Remote Config values and what's on screen, and answers "ask now?". Tests cover every rule
above. The view only calls the system prompt when the view model says so. Debug builds get a
launch argument (`-reviewPromptForce` on iOS, an intent extra on Android) to try it, since Debug
iOS builds always show the prompt and TestFlight never does.

**10. Ship in the next release of both apps,** with the README's Remote Config table, the Android
template and defaults, and NOTES.md updated.

**11. The install date.** Neither app stored one. Android reads the real one from the system
(`firstInstallTime`). iOS saves it on first use, taken from when iOS made the app's Documents
folder, so people updating count from their real install rather than from the update.

## Remote Config keys

Every value is validated with a fallback, like the other keys, so a bad console value can't make
the app ask too often.

| Key | Default (allowed) | What it does |
|---|---|---|
| `review_prompt_enabled` | false | Master switch for the prompt. Off: never asks. |
| `review_prompt_min_days_installed` | 7 (1–365) | Days since install before asking; never on the day of install. |
| `review_prompt_min_active_days` | 5 (1–100) | Different days the weather screen was shown before asking. |
| `review_prompt_moments` | `great_walk,pleasant_weather` | The happy moments that can ask, comma-separated. Unknown names are ignored; empty never asks. |
| `review_prompt_delay_seconds` | 3 (0–30) | Wait after the moment, with the screen unchanged, before asking. |
| `review_prompt_once_per_version` | true | Never ask twice for the same app version. |
| `review_prompt_cooldown_days` | 120 (30–365) | Days between asks. |
| `review_prompt_max_requests` | 3 (0–20; 0 means no limit) | Asks in all, ever. |
| `review_prompt_on_notification_open` | false | Allows asking in a visit that started from a notification. |

## What gets saved on the phone

The days the weather screen was shown (a count and the last day), the number of asks, the date of
the last ask and the app version it was in, and on iOS the install date. Nothing leaves the phone
except the analytics event. Uninstalling clears it.

## Trying it

iOS Debug builds always show Apple's prompt, and TestFlight never does: launch a Debug build with
`-reviewPromptForce YES` to ask once, a few seconds after the weather shows. On Android, Play only
shows its sheet for an app installed from Play; the internal test track has no quota. Debug builds
take the `reviewPromptForce` intent extra.

## Later

- A third moment, such as the morning briefing being opened (decision 4 leaves notification
  visits out by default).
