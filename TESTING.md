# Testing pro on your Galaxy S25 FE

A short checklist. Tick things off, and for anything that looks wrong, take a screenshot and use **Settings > Report a pro problem** (or just tell Claude what you saw).

## A. Install it

1. On your phone, open **https://github.com/Jbyjre/pro3/releases/latest** and tap **pro.apk** (after the pull request is merged; until then, the newest test build is at https://github.com/Jbyjre/pro3/releases/tag/glint-preview as **pro-preview.apk**).
2. When the download finishes, tap it to open it.
3. If Samsung says installation is blocked: go to **Settings > Security and privacy > Auto Blocker** and turn it **off**, then try again. You can turn it back on after installing. (Newer One UI versions can turn it back on for you automatically after 30 minutes.)
4. If asked, allow your browser or My Files to **install unknown apps** (a toggle on the screen that appears).
5. If Google Play Protect warns that the app is unrecognised, tap **More details > Install anyway**. That's expected for apps not from the Play Store.
6. Updating later: install a newer `pro.apk` the same way; it installs over the old one and keeps your settings. If it ever says "App not installed" when updating, uninstall pro first (see DECISIONS.md, section 2, for why this can happen once).

## B. First-run setup (about 2 minutes)

1. **Welcome**: tap to start (there's no agreement page).
2. **This phone**: read what it says about your phone.
   - "Should work" or "Ready": continue.
   - "Waiting for One UI 9": your phone doesn't have the Android 17 Bluetooth fix yet. You can still continue. Check **Settings > Software update**.
3. **Permissions**: tap **Grant all** and allow each pop-up as it appears (Nearby devices, notifications, phone). It finishes on the "Display over other apps" page: turn **pro** on, then press back. Each row should turn highlighted once allowed. If you refused something earlier, tapping its row opens pro's settings page so you can allow it there.
4. **Stay connected** (important on Samsung):
   1. **Link your AirPods**: tap **Link**, pick your AirPods in the pop-up, tap **Allow**. (Your AirPods must already be paired in Bluetooth settings.)
   2. **Allow unrestricted battery**: tap **Allow**, then **Allow** in the system pop-up.
   3. **Samsung: never sleep**: tap **Open settings**, then go to **Battery > Background usage limits > Never sleeping apps**, tap **+**, add **pro**. While there, make sure pro is **not** under **Deep sleeping apps**. Come back and tap **Done**.
5. Tap **Finish**.

You can redo these steps any time in **Settings > Stay connected & appearance**.

## C. Everyday features (with your AirPods Pro 3)

| Check | How | Expected |
|---|---|---|
| Connect | Open the case near the phone | The island (small orb) appears at the top once per connection. The bottom card no longer appears unless you turn on Settings > **Card when the case opens**; if you do, it shows once when you open the case, not again from Bluetooth hiccups |
| Main screen | Open pro while connected | The buds and case pictures with battery rings; the L/R and case marks and the charging bolt show as symbols, not empty boxes |
| Everything included | Look through Settings and the main screen | No "Locked" labels, no unlock or sponsor buttons; every switch works |
| Time left | Wear the buds and play music for 30+ minutes | Under the battery rings: "About … of listening left". Early on it says it's based on Apple's rating; after about half an hour it says "Measured from your recent listening". Put the buds in the case: it switches to "Full in about …" after a few minutes of charging |
| Island | Turn Bluetooth off and on, or put the AirPods in | The island appears once when they connect, with the earbuds turning. Tap it: it grows into a card with batteries and time left. Hold it to open pro. The earbuds turn smoothly |
| Change feedback | Tap Adaptive on the main screen | A glass notice says "Switching to Adaptive…" then "Adaptive is on". If the AirPods don't answer, it says so and offers Reconnect |
| Heart rate | Main screen > **Heart rate** > Start measuring (both buds in) | A number within a few seconds, a heart beating at that rate, and a line building up. Take a bud out: it says there's no reading |
| Share heart rate | Heart rate > **Share live heart rate**: turn on Bluetooth sensor, start measuring, then in a fitness app or watch search for a heart-rate sensor | Your phone appears and shows the same BPM. Web address: paste an https webhook and tap Send a test: "It worked" |
| Heart insights | Measure at least 3 minutes sitting, then exercise and stop | "Resting, est." appears; a minute after your peak, "1-min recovery". Export readings opens the share sheet with a CSV |
| Background heart rate | Heart rate > turn on **Measure whenever worn**, close pro, wear the buds 5+ minutes, take them out, wait a minute | No notification appears; reopening pro shows the stretch under Earlier |
| Glass tilt | Open the main screen and slowly tilt the phone left and right | The glass edges' shine drifts a little; it settles straight when the phone is upright |
| Recorder | Main screen > **Recorder (experimental)** | Tap the red button, speak, stop. A recording appears you can play. If it says no audio is arriving, that firmware doesn't support it |
| Head gestures | Main screen > Head gestures | The dot moves as you turn and tilt your head; Test Head Gestures reacts to a nod or shake |
| Light/dark | Settings > Appearance: Light, then Dark | The app, its status bar and the island all switch |
| Notification | Connect the AirPods | Nothing stays in the notification shade. Settings > Stay connected & appearance > Status in notifications brings the battery notification back |
| Battery | Look at the card, the main screen, the notification | Left, right and case percentages match |
| Listening modes | Switch modes in the app, then press-and-hold a stem | The app follows; a small island shows the mode you picked on the AirPods |
| Ear detection | Take one AirPod out while music plays, put it back | Music pauses, then resumes |
| Conversation awareness | Turn it on, start talking | Volume lowers while you talk |
| Press and hold | Settings > press and hold: change the action | The new action happens |
| Low battery | Use the buds until about 20% (or use pro Lab) | A red/amber island appears once at 20% and once at 10% |
| Auto-reconnect | Turn Bluetooth off and on; put the buds in the case and take them out | The main screen shows "Connecting..." then your AirPods; no manual tapping |
| Disconnect | Tap Disconnect on the main screen | It stays disconnected until the AirPods reconnect |

## D. Staying alive

1. **Reboot** the phone, don't open pro, then put your AirPods in. The notification should show battery within about a minute.
2. **Long idle**: leave the phone overnight, then put your AirPods in without opening pro. Same result.
3. If either fails, open **Settings > Stay connected & appearance** and make sure all three steps show a check mark.

## E. Judging the visuals (pro Lab)

1. Open **Settings**, scroll to **About**, and tap **Version code** seven times. **pro Lab** opens.
2. The **Status** box says whether real window blur is available. Try it with **battery saver on and off**: the glass should switch between see-through blur and a clean frosted fill without looking broken.
3. Use the sliders and switches to make fake AirPods data, then tap each button: **Show connect card**, and the island's **Connected, In ear, Low battery, Listening mode, Moved to iPad, Taking over, Case charging, Problem**.
4. Try: tilt the phone while an overlay shows (the highlights should glide), tap the island (it grows into a card and the bubble melts back in), swipe the island up and the card down (both dismiss with a spring). The island's small turning AirPods should float on the dark glass with no black square around them.
5. **Main screen states**: preview "Bluetooth off", "Connecting", "Couldn't connect" and "Waiting".
6. Also try **dark mode** (the card's clip switches to its dark version), and **Settings > Accessibility > Visibility enhancements** options such as reduce animations (the clips become still pictures) and high-contrast text.

## F. Heart rate, history and backup (round 16)

1. **Warm-up:** tap Start measuring. The first number should appear after a few seconds and should *not* start high and then drop (the first 3 seconds are thrown away).
2. **Background + island:** turn on Measure whenever worn, close pro, put the AirPods in. Within about 20 seconds the island should show "Heart rate · NN BPM" with a beating heart.
3. **Pace:** pick Balanced, take the buds out and back in. The Heart rate screen should alternate between "Live" and "Last reading · next in about N min".
4. **History:** Heart rate > History. Every session of a minute or more is listed by day; tap one to see its graph.
5. **GitHub backup (into pro3):** first make pro3 private on github.com (pro3 > Settings > scroll to Danger Zone > Change visibility > Private). Then History > Back up to GitHub > Set up > Open GitHub. The token page opens already filled in: under **Repository access** pick **Only select repositories** and choose **pro3**, tap **Generate token**, copy it, paste it in the app, tap Connect. On github.com, open pro3 and switch the branch menu (top left, says "main") to **heart-backup**: you should see a `heart` folder. End a session: a new file should appear there within a minute. If pro3 is still public, the app says so and won't upload.
   - Offline test: turn on airplane mode, end a session (the line says "Waiting for internet"), turn airplane mode off: it should back up by itself within a few seconds.
6. **Restore:** (optional) delete a session on the phone, then tap Restore. It comes back.
7. **Hearing Protection:** main screen > Hearing Protection. Turn Workspace Use on and off; the switch should stay where you put it. With the AirPods disconnected the switches should be greyed out with "Connect your AirPods to change this".
8. **Only in Adaptive:** main screen > Audio > Only in Adaptive on. Switch to Transparency: Conversational Awareness turns off; switch to Adaptive: it turns on.

## G. Round 17

1. **Icon:** the home screen shows the blue-aqua icon with the white ring around a glass orb. With themed icons on (Samsung: Home screen settings > Themed icons, if your One UI has it), it shows the outline version.
2. **Light/dark:** Settings > Appearance, tap Dark then Light. The new look should spread out in a circle from your finger, and no row should stay white in dark mode.
3. **Volume limit:** main screen > Hearing Protection > Limit media volume on, pick 60%. Play music on the AirPods and press volume up: it should stop at about 60% and the screen should say when it turned it down.
4. **Heart:** with your age set, the session graph is green/amber/orange/red by effort; History shows Resting heart rate and Personal bests.

## H. Round 19: icon, island, music, heart

1. **Icon:** Settings > App icon: tap White, then Graphite, then Black. The home-screen icon changes within a few seconds. If it vanishes from the home screen, drag pro back from the app list.
2. **AirPod out:** play music, take one AirPod out. The island shows "One AirPod out · Music paused" with a play button. Tap the button: the music plays again. Put the bud back: "Both AirPods in".
3. **Music starts:** with pro closed, start a song in Spotify. After about 1.5 s the island shows "Now playing" (or the song name, see 5) with a pause button. Tap it: Spotify pauses.
4. **Opened island:** tap the island. It's a compact card: the AirPods, three battery rings marked L, R and a case symbol (with the % under each, and a small green bolt when charging), and one round play/pause button in the bottom-right corner that pauses and plays Spotify. There's no time-left line.
5. **Song names:** Settings > Island > Show song names > Allow, turn pro on. If Android says "Restricted setting", tap App info, the ⋮ menu, Allow restricted settings, then Allow again. Start a song: the island shows its name, artist and cover.
6. **Heart:** start measuring heart rate, then tap the island open. A small chip at the bottom left shows a beating heart and your number, in the island's own white (dark mode) or graphite (light mode), not red. Tap it: the island grows smoothly to the explanation page (a large heart in the same colours, a small coloured dot for what the number means, and the scale). Tap the back arrow: it shrinks back.
7. **Settings > Island:** turn off "An AirPod comes out" and take a bud out: no island. Try Short and Long under "Stays on screen for". Use the Try it buttons.

## I. Known limits (not bugs)

- Features marked **Needs root** don't work on a standard Samsung.
- Overlays don't appear on the lock screen (Android hides app overlays there).
- Widgets, the notification and the quick-settings tile are drawn by Samsung, so they can't be true glass.

## G. Round 22: new name, icons, feel

1. **Name:** the home-screen label and the app's own screens say **pro**. It installs over the old Glint build and keeps your settings (the app's internal ID didn't change).
2. **Icons:** Settings and the main screen show a small picture next to each row (heart, bell, ear, shield and so on). On Heart rate, the round glass heart starts/stops measuring when tapped, the button under the line is a play/stop symbol, and the session card has share and reset symbols. **Press and hold** any round symbol button to see its name.
3. **Feel:** switching screens should glide and settle softly; the screen behind sinks back a little. Tapping cards, listening modes and symbol buttons gives a light tick and a squish.
4. **Glass on Battery Saver:** turn Battery Saver on with the app open. The glass should stay smooth and look nearly the same (slightly less blur, no rainbow edge); turn it off and it goes back.

## H. Round 23: mini island, name in Settings

1. **Name:** the main page no longer has the Name row. Settings shows it at the top; tap it to rename.
2. **Mini island:** play music (any app). A small black pill should appear around the front camera: cover on the left, moving bars on the right. Check it sits evenly around the camera.
3. **Skip:** swipe the pill left: the next song plays and the pill widens with its name for about 3 seconds. Swipe right: previous song.
4. **Tap / hold:** tap opens the full island; hold opens pro.
5. **Pause:** the cover greys and the bars become dots; about 30 seconds later it shrinks into the camera.
6. **Hides:** turn the phone sideways, play a full-screen video, lock the phone. It should get out of the way each time and come back after.
7. **Settings > Island > Mini island:** each switch works; Try it > Mini island shows a 7-second sample.

## I. Round 24: heart chip from the play button

1. Open the island (tap it). About a second later, a bubble should come out from under the play/pause button and glide left into the heart chip.
2. Not measuring: it reads **-- BPM**; tapping it opens pro. Measuring: it shows your number and beats; tapping it opens the explanation.

## J. Round 27: Dynamic Island names, idle look, Conversation Awareness

1. Connect the AirPods with no music: the Dynamic Island appears around the camera in dark graphite (slightly lighter than black) with the battery ring and mode symbol. Tap it: it expands. Play music: it turns pure black with the cover and bars.
2. Turn on Conversation Awareness, play music, start talking: the volume drops and three dots show on the Dynamic Island. Talk back and forth: the volume should stay down the whole time, not bounce. Stop talking: about 2 seconds later it swells back. Change the volume yourself while talking: afterwards it stays where you put it.

## K. Round 28

1. Connect the AirPods: the Dynamic Island stays around the camera. Turn the phone sideways and lock it: it should stay (on the lock screen only if your phone allows it). Play a full-screen video: it steps aside, and comes back after.
2. Tap it once: it expands below the clock (the clock and icons must not cover it). Twice: play/pause. Three times: next song.
3. Trigger a pop-up (Settings > Islands > Try it): it grows out of the Dynamic Island, which stays and goes softly blurry, then sharpens with a small bounce when the pop-up goes back in.
4. Open the island: after a second, back and skip come out of the play button and the heart chip glides left.
5. Settings > Islands: every switch changes and works straight away.
6. Main page: Hearing Protection is below the audio settings.
