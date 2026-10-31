# Firebase assets

Server-side configuration for Sunny & Misty (Firebase project `cats-dogs-weather`), kept in
the repo so it's versioned alongside the code that depends on it.

| File | What it is |
|------|------------|
| `remoteconfig.template.json` | Remote Config parameters — the 38 server-tunable values. Keys mirror [`RemoteConfigRepository`](../app/src/main/java/com/fangjet/weather/data/RemoteConfigRepository.kt) and the in-app defaults in [`remote_config_defaults.xml`](../app/src/main/res/xml/remote_config_defaults.xml). |

## Deploying Remote Config

With the Firebase CLI set up (`firebase login`; the default project comes from
[`.firebaserc`](../.firebaserc)), from the repo root:

```bash
firebase deploy --only remoteconfig
```

[`firebase.json`](../firebase.json) points the CLI at the template. `firebase remoteconfig:get`
shows what is live.

Or, without the CLI: Firebase console → **Remote Config** → **⋮** → *Publish from file* →
pick `remoteconfig.template.json`.

## How the app uses it

The app never blocks on a fetch. It starts from the in-app defaults, reads the last
activated values on cold start, and refreshes in the background (at most hourly). Every
value is validated, so a bad console entry can never break the app — it just falls back to
the in-app default. The parameters are described in the
[main README](../README.md#remote-config). When adding a parameter, add it in all three places:
this template, `remote_config_defaults.xml`, and `RemoteConfigRepository`, and add a row to the
README table.
