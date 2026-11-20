# Donut Gamble Tracker (Fabric, Minecraft 1.21.11, client-side)

Build: install JDK 21 + Gradle, then run `gradle build` in this folder.
Jar lands in build/libs/ (use the one without -sources). Drop it in your mods folder with Fabric API.

## Setup (important)
The chat patterns are guesses. In-game:
1. Run `/gamble debug`, play a round, and read the raw chat lines it echoes.
2. Edit `.minecraft/config/gambletracker.json` -> `winRegex` / `lossRegex`
   (group 1 must capture the amount), then `/gamble reload`.
Until then, `/gamble win 5k` and `/gamble loss 2.5m` add results manually.

## Commands
/gamble hud | debug | win <amt> | loss <amt> | pos <x> <y> | reload | resetsession | reset

## Hotkeys (rebind in Options > Controls > Gamble Tracker)
H = show/hide HUD, J = cycle full/compact/minimal, K = reset session, "Reset ALL" is unbound by default.
