# Motion

When and how UI moves. Starsector UI moves very little: buttons glow on hover, panels fade in, a changed value
flashes. Anything beyond that must be short, must not move the layout the player is aiming at, and must be
optional. This file holds what was learned building animated buttons. In StarUI, per-frame work goes in
`Modifier.onAdvance { amount -> }` and drawing in `Modifier.onRender { alpha -> }`; vanilla components keep their
own faders (`fader`, `mouseoverHighlightFader` in the vanilla toolkit).

## When to animate

| Animate | Do not animate |
|---|---|
| Hover feedback on a custom drawn control | Layout of a management panel (rows, columns, strips) |
| A tile that unfolds to show its name | Numbers counting up |
| The "selected" mark of a custom tile, when the user asked for a lively look | Anything that delays a click |
| A panel or overlay appearing | Scroll position |
| A bar that fills while the player watches | A rebuild after a click: it is instant |

Stock widgets (`Button`, `AreaCheckbox`, tooltips, `Holo`) bring their own motion. Do not add to it.

## Rules

1. **Time based, never frame based.** Advance by the seconds passed to `advance(amount)`, so the speed is the
   same at any frame rate. For effects that must run while the game is paused (the campaign pauses in most UI),
   take the time from `System.nanoTime()`.
2. **Short.** 0.1 s is the default for hover and unfold. The stock button fader is 0.05 s in, 0.25 s out. Above
   0.3 s the UI feels slow.
3. **Optional.** Give the player a setting: duration from 0 to 1 s where 0 means instant, and a mode choice
   (for example On hover / On click / Never). Some players want no hover motion at all, and asked for it.
4. **The first frame snaps.** When a panel opens, everything is drawn in its final state. Animate only changes
   that happen while the player looks.
5. **Keep the total size constant.** When one tile of a row grows, the others shrink by the same amount, so
   the row's outer edges and everything around it stay where they are. The player's next target must not run
   away from the cursor.
6. **Several things animate at once.** One tile folds while another unfolds. Keep one progress value per
   element, not one global animation.
7. **Keep the hover target while the cursor is in a gap** between two tiles or has just left the row.
   Otherwise the row flickers between states when the cursor crosses a 4 px gap.
8. **Reveal, do not pop.** Text that appears with an unfolding tile is clipped to the tile's current width
   (a container with `overflow = Overflow.Clip` whose width animates), so it is uncovered as the tile grows. Fading text in or
   letting it appear at the end looks cheap.
9. **Linear is fine** at 0.1 s. Do not add easing curves or overshoot.
10. **Settings are read once per build and cached** for the per frame render. Reading a settings store every
    frame costs time on every screen.

## A hover value per element

```kotlin
// 0 = folded, 1 = unfolded; duration 0 snaps. In Modifier.onAdvance { amount -> ... }
val step = if (duration <= 0f) 1f else amount / duration
progress = if (hovered) min(1f, progress + step) else max(0f, progress - step)
val width = (foldedW + (unfoldedW - foldedW) * progress).roundToInt().toFloat()
```

An animated size changes every frame, so do not drive it through a `UIState` read by a builder (that rebuilds the
container every frame). Draw the animated part in `onRender`, or set the native size in `onAdvance`
(`component.setSize(w, h)` from the toolkit) and call `component.requestLayout()`.

Round the animated size to whole pixels, and guard quads against zero width: a quad that has just reached
zero or gone negative still draws a 1 px line.

## A moving highlight on a selected tile

Used on custom tiles when "selected" should read at a glance without a fill:

- Static form: three 1 px rings following the cut corners: black, the owner's colour at full saturation and
  value, white tinted 10% toward it, drawn in `onRender`.
- Animated form: the black rings stay (alpha 1 and 0.5); four short highlights in the saturated colour run
  around the ring clockwise, each fading out over 8% of a lap, one lap per 4.4 s, timed with
  `System.nanoTime()` so it runs while paused.
- Offer both plus Off as a setting. The colour is the owner's (faction, market), not yellow.

## State and lifetime

- Keep animation state (progress per tile, hovered tile) in a small object owned by the panel, not in static
  fields.
- Hold UI children that the game owns through weak references in anything that lives longer than the screen,
  or the old screen is never freed.
- Put the layout maths of an animated row into a pure class without game types (widths in, rectangles out) and
  test it headless over many widths and counts (for example widths 450 to 1900, 1 to 12 tiles): total width
  constant, no overlap, no negative sizes. Animated layouts have many in between states and a screenshot shows
  only one.

## Preview before building

Motion cannot be approved from a still board. Show it as a short strip of frames (folded, 25%, 50%, 75%,
unfolded) on a mockup board, or as two or three "fake render" variants side by side, and let the user pick
before implementing. Users ask for this ("pre-rendered options", "preview") because a wrong guess on motion
costs a full build and test round.
