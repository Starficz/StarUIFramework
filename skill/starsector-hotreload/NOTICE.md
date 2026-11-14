# Notice

These parts of this skill are adapted from **starsector-native-smoke 2.0.2**, used with permission:

- `scripts/launch-game.sh` (slimmed: Windows only, no slot system), `scripts/stage-mod.sh`,
  `scripts/make-modset.sh`, `scripts/find-mod.sh`, `scripts/game-proc.sh`, `scripts/wait-log.sh`,
  `scripts/new-game.sh` (from `new-game-1600x900.sh`), parts of `scripts/lib.sh` and `scripts/build-tools.sh`;
- `tools/gamectl.cs`, `tools/SmokePrefs.java`, `tools/CursorGuard.java`, unchanged.

A hot game does not take a starsector-native-smoke slot: when both skills run games on one machine, they do not know
about each other's games.
