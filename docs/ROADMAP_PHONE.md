# Becoming a multi-purpose phone app: options

Jake wants pro to move away from depending on other devices, and to become a phone app with many uses, not only an AirPods app. **This page is a menu of ideas, not a plan, and none of it is built.** Each idea says what it would need, so Jake can pick. What already exists without headphones: the Dynamic Island for any sound, the phone's battery and clock, This phone, sound settings. See `REQUESTS.md` section 7.

How these were checked: against what the code already has and what Android allows without root. Anything that depends on how a particular phone behaves is marked "not verified".

| Idea | What it would add | What it needs | Notes |
|---|---|---|---|
| Notification moments on the island | When a notification arrives silently, the sender app's icon pops on the island | Notification access (already used for sounds) | Reuses `SoundSource`'s notification log. Needs a rule for which notifications are worth a pop (a message app yes, a download progress no). |
| Charging moment for the phone | A short pop when you plug in, with the percentage | Nothing new: `PhoneStatus` already reports charging | Small. |
| Call state on the island | Who is calling and for how long | Phone state permission (already declared for AirPods call control) | The island already shows a handset while a call's sound plays. Caller names would need more. |
| Timers and stopwatch | A countdown ring on the island | A timer inside pro (Android gives apps no way to read the Clock app's timer) | New feature with its own page. |
| Quick notes | A tiny note taken from the island or a tile | Storage inside pro | New page. Jake's message mentioned "phone notes"; the exact idea is not clear. |
| Per-app rules | Each app gets its own look or silence | The "Heard lately" list already has a switch per app | Extend to per-app slot choices. |
| Hide the island in chosen apps | Games, video apps | The app-on-screen clue (the accessibility switch) | Small. |
| A home page that isn't about headphones | Make This phone the first page when no headphones are connected | Navigation change | Changes what Jake sees at launch: ask first. |

Not possible: bending other apps' pixels with glass (Android never hands them over), reading what's inside a notification without notification access, knowing which app made a sound straight from Android (only clues; see `android/README.md`).

**Ask Jake which of these he wants next.** Don't build several at once.
