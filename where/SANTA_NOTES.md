# Santa mode

Santa is a second traveler, available only in December. The route fixture in `app/src/main/assets/santa_route_fallback.json` is trimmed from Google's Santa Tracker Android test resource (`santa.json`), Apache-2.0, Copyright Google LLC.

The app loads that fixture first, then tries `https://santa-api.appspot.com/info?client=web` and the route URL it returns. Live route files are not bundled because their license is not the Apache fixture.

Times in the fixture are from a past year. The app shifts them so the first departure matches this year's takeoff (or the takeoff in the live info response).
