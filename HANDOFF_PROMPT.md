# Prompt for the next Claude session

**1. The project.** You're picking up work on **Glint**, Jake's personal Android app for using his AirPods Pro 3 with his Samsung Galaxy S25 FE.
- It's a fork of the open-source LibrePods app (GPLv3).
- Repo: `Jbyjre/pro3`, with the app in `android/`.
- Branch: `claude/pensive-franklin-lvce7c`. Open pull request: https://github.com/Jbyjre/pro3/pull/1.
- `CLAUDE.md`, `DECISIONS.md`, `TESTING.md` and `HANDOFF.md` at the repo root hold background notes. Use them as reference, not as a script. How you meet Jake's goals is up to your own judgement.

**2. Jake's standing rules.** These are the only firm instructions in this prompt:
- **Never subscribe to PR or merge updates, and never schedule check-ins or CI polling**, unless he asks in your session.
- **He has no coding experience.** Explain things plainly and never hand him terminal commands; you do the technical work.
- **Verify facts**, and say "not verified" when something isn't checked.
- **Ask before spending money** or doing anything hard to undo.
- **Don't use subagents.**
- **Never commit signing keys.**
- **Be supportive and never condescending.**

**3. Where things stand.** Everything is pushed to the branch. The latest round of changes compiled, and its setup screenshots rendered, but the full tests, lint and release build weren't run on it before the last session paused. Treat that round as unproven.

**4. Jake's goal, in his words, summarised.** He wants this to be *his own* app:
- no leftover Kavishdevar/LibrePods branding he has to look at
- no "agree and continue" step
- no features locked behind sponsoring or paying
- Liquid Glass throughout the whole app, not just parts of it
- every button and feature working smoothly
- accuracy and thoroughness

**5. Branding: what's been done.**
- The previous session removed the LibrePods row, the unlock button, the billing banner and the release-notes link from Settings.
- Some traces remained in less visible places, e.g. strings mentioning kavishdevar or kavish.xyz, and screens that are no longer reachable.
- The GPL requires the licence and source to stay available. The internal package name `me.kavishdevar.librepods` is invisible to Jake, and changing it would be a large change.
- Whether an in-app credit is legally required was not verified.

**6. Paywall and agreement: what's been done.**
- The agreement (privacy) page was deleted.
- The foss build now reports every feature as included.
- Jake wants to actually *have* every feature, working the way it should, not just unlocked on paper.

**7. Liquid Glass.**
- Jake felt the setup flow didn't feel like Liquid Glass. It was rebuilt as a glass sheet over a studio backdrop using the Kyant backdrop library. That library does real blur and refraction on the phone; Robolectric screenshots don't show it.
- Jake wants the glass feel applied throughout the app. He asked for the Liquid Glass skill to be used; the frontend-design skill was also used before.

**8. The overlays.** Glint has two overlays:
- **The island:** a small orb/pill at the top of the screen.
- **The card:** a pop-up at the bottom.

Jake said they looked good, but he disliked that they felt "modular", like separate pieces stuck together. The last session merged the island's battery bubble into its pill and removed the card's inner box.

**9. Jake's newest feedback on the overlays.**
- He says the top orb now "looks incredible".
- The bottom card ("your AirPods are connected") "appears kind of randomly" and isn't in sync.
- He'd honestly be fine without the bottom card. What he wants to look good is the smooth, small orb.

**10. A lead on why the card is random.** This was found by reading the code and not confirmed on a device. The card is triggered by the case's lid state in Bluetooth adverts (`BLEManager.kt` → `AirPodsService.showPopup`).
- The code assumes the lid closed if adverts stop for 2.5 seconds.
- Samsung throttles background scanning, so that may re-arm and re-show the card.

**11. The 3D viewer request.** Jake wants to see the 3D AirPods inside the app. He wants to be able to:
- switch between the 3D representations
- move them, spin them around and look at the AirPods

**12. What 3D assets exist.** There's no real 3D model in the repo. There are two rendered video clips from LibrePods, which Jake chose over drawn artwork:
- `res/raw/island.mp4`: 418×418, 25 fps, 6 s, black background. The earbuds turn through several angles.
- `res/raw/connected.mp4`: 1050×354, 30 fps, 6 s, with a dark version in `res/raw-night`. The earbuds and case turn through front, side and back views.

A quick look suggested neither clip is a clean continuous 360° turn. There are also still pictures (`airpods_pro_2*.png`).

**13. If you consider true 3D.** A true 3D model is a bigger step:
- It needs a new library.
- Cloud sessions build offline, so the dependency snapshot needs refreshing.
- It needs a model whose licence allows a public GPL repo; none was found.
- It would change the look Jake chose.

He likes being asked before big or hard-to-undo choices.

**14. The battery request.** Jake wants the app to estimate how much time he has left on his AirPods battery, and he stressed being very accurate.

**15. Battery data facts.**
- When connected over the AirPods' own protocol (AACP), battery for left, right and case arrives in 1% steps, with charging status (`data/Packets.kt`).
- Bluetooth adverts only give 10% steps.
- Apple's AirPods Pro 3 specs page (checked 2026-09-30) says:
  - up to 8 h listening with ANC
  - 7.5 h with Spatial Audio and head tracking
  - 6.5 h with workout heart-rate sensing
  - up to 10 h in Transparency with the Hearing Aid feature
  - up to 24 h total with the case
  - 5 minutes of charging gives about 1 hour of listening

**16. Jake's overall standard.** He wants it thorough, accurate and precise. Everything should work smoothly and look polished, not "AI-made". The work should fulfil all of his requests, old and new, "all the way". He also asked for context efficiency.

**17. Open questions Jake hasn't decided.**
- Glint only trusts Bluetooth adverts after getting the AirPods' identity key over a successful connection. Should nearby unverified adverts be used before that?
- Should the app keep a small "Based on LibrePods" credit?
- The AirPods Pro 3 Bluetooth product ID was never verified; sources conflict.

**18. Building in the cloud.**
- Cloud sessions can't reach Google's Maven servers. `CLAUDE.md` explains the offline route:
  - a `ci-deps-snapshot` release asset
  - the Android SDK from the `cimg/android` Docker image
  - NDK/CMake overrides
  - a pre-downloaded Robolectric jar
- The dark Robolectric qualifier must be ordered `w412dp-h915dp-night-xxhdpi`.
- `AppTourScreenshots` renders every screen to `build/screenshots/tour`.

**19. Delivery.**
- Jake installs from the repo's Releases page. CI builds `Glint-preview.apk` for branches and `Glint.apk` when he merges into main with the green Merge button.
- The cloud container is temporary, so unpushed work is lost.
- The docs (`DECISIONS.md`, `TESTING.md`) still describe the old sponsor unlock and privacy page.

**20. How to reply to Jake.** When you report back:
- Say plainly what changed, what you verified and what still needs his phone and AirPods to confirm.
- Say it in words someone without coding experience understands.
- Beyond Jake's requests and rules above, decide the approach, priorities and design yourself.
