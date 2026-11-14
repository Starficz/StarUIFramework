# Vanilla style crawl

Regenerates the counts in `starsector-starui/references/vanilla-styles.md` from the game's code, as the
`starsector-knowledge` skill holds it (unpacked: `api/`, `obf/`, `data/`). Run after a game update.

```sh
K=<starsector-knowledge folder>
python -I liveness.py "$K" live.json                          # reachable classes (dead code left out)
python -I crawl_ui.py "$K" crawl_live.json live.json live     # TooltipMakerAPI calls, colours, cuts, pads, fonts
python -I crawl_ui.py "$K" crawl_dead.json live.json dead     # the same over unreachable code, for comparison
python -I crawl_factory.py "$K" live.json                     # the engine's own buttons: overloads, colours, fonts
```

`crawl_factory.py` matches the engine's button factory by its obfuscated name in 0.98.5a-RC4 (`ui.Q.o00000`); after an
update, find the factory again by its shape (a static method taking `String, String, Alignment, CutStyle, listener`)
and change the pattern. Nothing here ships with the skill.
