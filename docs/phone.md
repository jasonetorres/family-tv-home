# Phone setup

The TV apps listen on [ntfy](https://ntfy.sh). The topic is the only shared secret: anyone who knows it can read and post. `scripts/configure.sh` prints a fresh topic and writes it to `config/family.json` (gitignored). Rebuild after you change it so every app embeds the same value.

Topics:

| Use | Topic |
|---|---|
| Notes and the home-screen note card | `YOUR_TOPIC` |
| Replies from the TV (stickers and "love it") | `YOUR_TOPIC-replies` |
| Travel commands | `YOUR_TOPIC-where` |

Open the notes app settings on the TV to see the topic. You can also set it without rebuilding:

```bash
adb shell am broadcast -a app.familytv.SET_TOPIC --es topic YOUR_TOPIC
```

That updates Notes, the travel app (it appends `-where`), and the launcher card.

## Android or any phone: ntfy app

1. Install ntfy from the store, or use the website.
2. Subscribe to `YOUR_TOPIC` and to `YOUR_TOPIC-replies`.
3. Publish a message to `YOUR_TOPIC`. The TV keeps the last 50 notes and shows a popup for about 20 seconds.
4. Commands, on their own line or as the whole message:
   - `/home 2026-12-24` sets the "home in N days" chip
   - `/clear` removes the notes
   - `/undo` removes the newest note
   - `/flight`, `/where`, `/trip`, and `/santa` are ignored by Notes. Send those to `YOUR_TOPIC-where`.

A message can have a title. An image attachment URL is shown on the corkboard.

## Travel commands

Publish these to `YOUR_TOPIC-where`.

```text
/where Paris
/where home
/flight UA100 2026-06-01
/trip Example trip
2026-06-01 9:00 UA100 PHL ORD 11:05
stay Chicago 2026-06-01 13:00 2026-06-04 15:00
2026-06-04 16:30 UA200 ORD PHL 19:10
```

Times are local to the airport. The airport table covers common IATA codes. Extra cities for stays include Chicago, Denver, Austin, Seattle, London, Paris, Rome, Tokyo, Toronto, and Honolulu. Live flight-page lookup is off until you enable it in the travel app. See the terms note in that screen and in the README.

## iOS Shortcut

1. Shortcuts → new shortcut → name it "Note to the TV".
2. Add **Ask for Input** (text), prompt "Note".
3. Add **Get Contents of URL**.
   - URL: `https://ntfy.sh/YOUR_TOPIC` (or your `ntfy.server`)
   - Method: POST
   - Request Body: Text, the input from step 2
4. Add the shortcut to the home screen or the share sheet.

For a trip file, set the request body to File and pick the text file. Post that shortcut to `YOUR_TOPIC-where`.

Subscribe to `YOUR_TOPIC-replies` in ntfy so sticker replies from the TV show up on the phone.

## Self-hosting ntfy

Set `ntfy.server` in `config/family.json` to your server, with no trailing path. An optional `ntfy.token` is sent as `Authorization: Bearer`. Rebuild so the apps pick it up.
