# Testing Glint on your Galaxy S25 FE

A short checklist. Tick things off, and for anything that looks wrong, take a screenshot and use **Settings > Report a Glint problem** (or just tell Claude what you saw).

## A. Install it

1. On your phone, open **https://github.com/Jbyjre/pro3/releases/latest** and tap **Glint.apk** (after the pull request is merged; until then, the newest test build is at https://github.com/Jbyjre/pro3/releases/tag/glint-preview as **Glint-preview.apk**).
2. When the download finishes, tap it to open it.
3. If Samsung says installation is blocked: go to **Settings > Security and privacy > Auto Blocker** and turn it **off**, then try again. You can turn it back on after installing. (Newer One UI versions can turn it back on for you automatically after 30 minutes.)
4. If asked, allow your browser or My Files to **install unknown apps** (a toggle on the screen that appears).
5. If Google Play Protect warns that the app is unrecognised, tap **More details > Install anyway**. That's expected for apps not from the Play Store.
6. Updating later: install a newer `Glint.apk` the same way; it installs over the old one and keeps your settings. If it ever says "App not installed" when updating, uninstall Glint first (see DECISIONS.md, section 2, for why this can happen once).

## B. First-run setup (about 2 minutes)

1. **Welcome**: tap to start (there's no agreement page).
2. **This phone**: read what it says about your phone.
   - "Should work" or "Ready": continue.
   - "Waiting for One UI 9": your phone doesn't have the Android 17 Bluetooth fix yet. You can still continue. Check **Settings > Software update**.
3. **Permissions**: tap **Grant all** and allow each pop-up as it appears (Nearby devices, notifications, phone). It finishes on the "Display over other apps" page: turn **Glint** on, then press back. Each row should turn highlighted once allowed. If you refused something earlier, tapping its row opens Glint's settings page so you can allow it there.
4. **Stay connected** (important on Samsung):
   1. **Link your AirPods**: tap **Link**, pick your AirPods in the pop-up, tap **Allow**. (Your AirPods must already be paired in Bluetooth settings.)
   2. **Allow unrestricted battery**: tap **Allow**, then **Allow** in the system pop-up.
   3. **Samsung: never sleep**: tap **Open settings**, then go to **Battery > Background usage limits > Never sleeping apps**, tap **+**, add **Glint**. While there, make sure Glint is **not** under **Deep sleeping apps**. Come back and tap **Done**.
5. Tap **Finish**.

You can redo these steps any time in **Settings > Stay connected & appearance**.

## C. Everyday features (with your AirPods Pro 3)

| Check | How | Expected |
|---|---|---|
| Connect | Open the case near the phone | The island (small orb) appears at the top once per connection. The bottom card no longer appears unless you turn on Settings > **Card when the case opens**; if you do, it shows once when you open the case, not again from Bluetooth hiccups |
| Main screen | Open Glint while connected | LibrePods' buds and case pictures with battery rings; the L/R and case marks and the charging bolt show as symbols, not empty boxes |
| Everything included | Look through Settings and the main screen | No "Locked" labels, no unlock or sponsor buttons; every switch works |
| Time left | Wear the buds and play music for 30+ minutes | Under the battery rings: "About … of listening left". Early on it says it's based on Apple's rating; after about half an hour it says "Measured from your recent listening". Put the buds in the case: it switches to "Full in about …" after a few minutes of charging |
| 3D viewer | Main screen > **View in 3D** | Drag sideways to turn, flick to spin (Earbuds spins all the way round; Case and Together turn end to end). The switcher at the bottom is glass and changes views smoothly |
| Battery | Look at the card, the main screen, the notification | Left, right and case percentages match |
| Listening modes | Switch modes in the app, then press-and-hold a stem | The app follows; a small island shows the mode you picked on the AirPods |
| Ear detection | Take one AirPod out while music plays, put it back | Music pauses, then resumes |
| Conversation awareness | Turn it on, start talking | Volume lowers while you talk |
| Press and hold | Settings > press and hold: change the action | The new action happens |
| Low battery | Use the buds until about 20% (or use Glint Lab) | A red/amber island appears once at 20% and once at 10% |
| Auto-reconnect | Turn Bluetooth off and on; put the buds in the case and take them out | The main screen shows "Connecting..." then your AirPods; no manual tapping |
| Disconnect | Tap Disconnect on the main screen | It stays disconnected until the AirPods reconnect |

## D. Staying alive

1. **Reboot** the phone, don't open Glint, then put your AirPods in. The notification should show battery within about a minute.
2. **Long idle**: leave the phone overnight, then put your AirPods in without opening Glint. Same result.
3. If either fails, open **Settings > Stay connected & appearance** and make sure all three steps show a check mark.

## E. Judging the visuals (Glint Lab)

1. Open **Settings**, scroll to **About**, and tap **Version code** seven times. **Glint Lab** opens.
2. The **Status** box says whether real window blur is available. Try it with **battery saver on and off**: the glass should switch between see-through blur and a clean frosted fill without looking broken.
3. Use the sliders and switches to make fake AirPods data, then tap each button: **Show connect card**, and the island's **Connected, In ear, Low battery, Listening mode, Moved to iPad, Taking over, Case charging, Problem**.
4. Try: tilt the phone while an overlay shows (the highlights should glide), tap the island (it grows into a card and the bubble melts back in), swipe the island up and the card down (both dismiss with a spring). The island's small turning AirPods should float on the dark glass with no black square around them.
5. **Main screen states**: preview "Bluetooth off", "Connecting", "Couldn't connect" and "Waiting".
6. Also try **dark mode** (the card's clip switches to its dark version), and **Settings > Accessibility > Visibility enhancements** options such as reduce animations (the clips become still pictures) and high-contrast text.

## F. Known limits (not bugs)

- Features marked **Needs root** don't work on a standard Samsung.
- Overlays don't appear on the lock screen (Android hides app overlays there).
- Widgets, the notification and the quick-settings tile are drawn by Samsung, so they can't be true glass.
