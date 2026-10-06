# docs/

Two kinds of pages live here.

## About pro (Jake's app)

| Page | What it is |
|---|---|
| [`REQUESTS.md`](REQUESTS.md) | **Start here.** Every request Jake has made, where it lives in the code, and how finished it is (done, needs the phone, not built, impossible). |
| [`ROADMAP_PHONE.md`](ROADMAP_PHONE.md) | Ideas for the move toward a multi-purpose phone app, with what each needs. Nothing in it is built. |
| [`../DECISIONS.md`](../DECISIONS.md) | Why the app is the way it is, in plain language, one numbered section per round of work. |
| [`../TESTING.md`](../TESTING.md) | What only Jake's phone can confirm, as short steps. |
| [`../POLISH_LEDGER.md`](../POLISH_LEDGER.md) | Running record of the polish passes: what was done, proof, what still needs the phone. |
| [`../CLAUDE.md`](../CLAUDE.md) | Standing rules from Jake and notes for sessions. |
| [`../android/README.md`](../android/README.md) | Map of the Android app: build and check, folders, how the Dynamic Island and the sound pipeline work, gotchas. Folder READMEs sit next to the code they describe. |

## About the AirPods protocol (from LibrePods)

`AAP Definitions.md`, `control_commands.md`, `device-info.md`, `opcodes.md`: notes on the Apple Accessory Protocol the AirPods speak. The app's decoders (`bluetooth/`, `data/Packets.kt`) follow them and `AirPodsProtocolTest` checks real message formats against them.

## Keeping these true

Whoever changes behaviour updates the page that describes it, in the same change. When Jake asks for something, add a row to `REQUESTS.md`. When something can't be checked in the cloud, say "not verified" and add the step to `TESTING.md`.
