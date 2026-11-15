# Motion

Starsector UI barely moves: buttons glow on hover, panels fade in, changed values flash. Anything more is short, never
moves the layout the player aims at, and is optional. Per-frame work: `Modifier.onAdvance { amount -> }`; drawing:
`Modifier.onRender { alpha -> }`; vanilla faders: `fader`, `mouseoverHighlightFader` (toolkit).

## When to animate

| Animate | Do not animate |
|---|---|
| Hover feedback on a custom drawn control | Layout of a management panel |
| A tile unfolding to show its name | Numbers counting up |
| A custom tile's selected mark, when a lively look was asked for | Anything that delays a click |
| A panel or overlay appearing | Scroll position |
| A bar filling while watched | Rebuilds after a click (instant) |

Stock widgets (`Button`, `AreaCheckbox`, tooltips, `Holo`) bring their own motion: add nothing.

## Rules

1. **Time based**: advance by `amount` seconds; while paused (most campaign UI) use `System.nanoTime()`.
2. **Short**: 0.1 s for hover and unfold (stock fader 0.05 in, 0.25 out); over 0.3 s feels slow.
3. **Optional**: a setting for duration (0 to 1 s, 0 instant) and mode (On hover / On click / Never); players asked.
4. **The first frame snaps** to the final state; animate only changes while watched.
5. **Constant total size**: when one tile grows, the others shrink by the same, so the row's edges stay put.
6. **One progress value per element** (one folds while another unfolds).
7. **Keep the hover target in gaps** between tiles or just outside the row, or it flickers across a 4 px gap.
8. **Reveal, don't pop**: text in an unfolding tile is clipped to its current width (`overflow = Overflow.Clip`
   container with animated width); fades or end-pops look cheap.
9. **Linear** at 0.1 s; no easing or overshoot.
10. **Settings read once per build**, cached for rendering.

## A hover value per element

For a plain on and off look (a colour, a border, a text), `Modifier.hoverState(hovered)` and a builder that reads
`hovered.value` are enough. The value below is for a look that eases in and out.

```kotlin
// 0 = folded, 1 = unfolded; duration 0 snaps. In Modifier.onAdvance { amount -> ... }
val step = if (duration <= 0f) 1f else amount / duration
progress = if (hovered) min(1f, progress + step) else max(0f, progress - step)
val width = (foldedW + (unfoldedW - foldedW) * progress).roundToInt().toFloat()
```

Never drive a per-frame size through a `UIState` read by a builder (rebuilds every frame): draw it in `onRender`, or
`component.setSize(w, h)` in `onAdvance` plus `component.requestLayout()`. Round to whole pixels; guard zero-width
quads.

## A moving highlight on a selected tile

For custom tiles where "selected" should read without a fill:

- Static: three 1 px rings along the cuts: black, the owner's colour at full saturation and value, white tinted 10%
  toward it (`onRender`).
- Animated: black rings stay (alpha 1 and 0.5); four short saturated highlights run clockwise, each fading over 8% of
  a lap, a lap per 4.4 s (`System.nanoTime()`, runs while paused).
- Offer both plus Off as a setting; owner's colour (faction, market), not yellow.

## State and lifetime

- Animation state (progress per tile, hovered tile) in a small object owned by the panel, not statics.
- Game-owned UI children held by longer-lived objects: weak references, or the old screen leaks.
- Animated row maths in a pure class (widths in, rectangles out), tested headless over many widths and counts (450 to
  1900, 1 to 12 tiles): constant total, no overlap, no negative sizes.

## Preview before building

Stills can't approve motion: show a frame strip (folded, 25, 50, 75%, unfolded) on a board, or two or three fake
renders side by side, and let the user pick first; a wrong motion guess costs a build and test round.
