# Recipes

Component functions for parts StarUI has no component for, each compiled and checked in the in-game suite (group
`skill-recipes`). Copy and rename freely. Imports: `org.starficz.starui.*`, `.components.*`, `.modifier.*`,
`org.lwjgl.opengl.GL11`, and `withAlpha`, `Font`, `VanillaColors` from `.vanilla`.

Contents: [Change row](#change-row) · [Guarded button](#guarded-button) · [Cost button](#cost-button) ·
[Tabs](#tabs) · [Toast content](#toast-content)

Now components: cut text is `Text(..., overflow = TextOverflow.Ellipsis)`, modes are `AreaCheckbox`es in a
`RadioGroup` ([components.md](components.md#content-and-controls)), panels and confirmations are a `Dialog`
([setup.md](setup.md#a-dialog)).

## Change row

```kotlin
/** "Label ........ old -> new": the old value gray and struck through, the new one in highlight. */
fun UIPanelAPI.ChangeRow(label: String, old: String, new: String) {
    Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        FlexSpacer()
        Text(old, color = VanillaColors.gray, modifier = Modifier.onRender { alpha ->
            // the strike line: text colour, 1 px, 2 px longer than the digits on each side; y up, from the bottom edge
            val lineY = (y + height / 2f).toInt().toFloat()
            val c = VanillaColors.text
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glColor4f(c.red / 255f, c.green / 255f, c.blue / 255f, alpha)
            GL11.glRectf(x - 2f, lineY, x + width + 2f, lineY + 1f)
            GL11.glEnable(GL11.GL_TEXTURE_2D)
        })
        Text(" -> ", color = VanillaColors.gray)
        Text(new, color = VanillaColors.highlight)
    }
}
```

Fills its width: call it in a container with a known width.

## Guarded button

A blocked button stays in place, grey, its red reason on a line reserved even while enabled
([buttons.md](buttons.md#states)).

```kotlin
fun UIPanelAPI.GuardedButton(
    text: String, reason: String?, size: Size = Size.exact(120f, 20f), backgroundColor: Color = VanillaColors.dark,
    tooltip: String? = null, onClick: () -> Unit
): ButtonAPI {
    lateinit var button: ButtonAPI
    Column(spacing = 3f) {
        button = Button(text, size, backgroundColor = backgroundColor,
            modifier = if (tooltip != null) Modifier.tooltip(tooltip) else Modifier) { onClick() }
        button.isEnabled = reason == null
        // its own width (the reason may be wider than the button), a fixed 20 px height
        Box(Size.mixed(Axis.WrapContent, Axis.Exact(20f))) {
            if (reason != null) Text(reason, color = VanillaColors.negative, modifier = Modifier.align(Alignment.CenterLeft))
        }
    }
    return button
}

// the reason comes from state read in the builder: "Needs 40 more Supplies", or null
GuardedButton("Train", reason = shortBy.value?.let { "Needs $it more Supplies" }) { train() }
```

Checked: greys out, the reason stays on one line, same height with and without it. A long reason widens the block;
leave room in a Row.

## Cost button

A single cost on its button: icon, label, amount (red when short)
([buttons.md](buttons.md#icons-and-second-text-on-a-button)).

```kotlin
fun UIPanelAPI.CostButton(
    label: String, iconSprite: String, amount: Int, affordable: Boolean, width: Float = 140f,
    cutStyle: CutStyle = CutStyle.ALL, backgroundColor: Color = VanillaColors.dark, onClick: () -> Unit
): ButtonAPI {
    lateinit var button: ButtonAPI
    Box(Size.exact(width, 20f)) {
        button = Button("", Size.fillMaxSize(), backgroundColor = backgroundColor, cutStyle = cutStyle) { onClick() }
        button.isEnabled = affordable
        Row(modifier = Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, spacing = 4f) {
            Image(iconSprite, Size.exact(16f, 16f))
            Text(label, font = Font.ORBITRON_12_CONDENSED, color = if (affordable) VanillaColors.base else VanillaColors.gray)
            Text("$amount", color = if (affordable) VanillaColors.highlight else VanillaColors.negative)
        }
    }
    return button
}

CostButton("Send", Global.getSettings().getCommoditySpec("supplies").iconName, 40, affordable = supplies >= 40) { send() }
```

Checked: clicks on the face reach the button; the face does not glow on hover (accepted). Add cost against stock
as its `Modifier.tooltip` (shows while disabled). Cut of its line: `ALL` alone on a strip, `TL_BR` in the bottom row.

## Tabs

`TOP` buttons in a Row, the selected one bright on opaque dark ([buttons.md](buttons.md#tabs)); a tab change remakes
the button (colours are fixed at creation).

```kotlin
fun UIPanelAPI.Tabs(labels: List<String>, selected: UIState<Int>, width: Float = 130f) {
    Row(spacing = 2f) {
        val current = selected.value
        labels.forEachIndexed { i, label ->
            key(label) {
                val on = i == current
                Button(label, Size.exact(width, 20f), cutStyle = CutStyle.TOP,
                    color = if (on) VanillaColors.bright else VanillaColors.base,
                    backgroundColor = if (on) VanillaColors.dark.withAlpha(255) else VanillaColors.dark.withAlpha(153)) {
                    selected.value = i
                }
            }
        }
    }
}
```

Checked. Tabs and counters: one `Row(Size.fillMaxWidth())` with a `FlexSpacer()` between, a `Divider(thickness = 1f)`
under it.

## Toast content

Content for a toast with more presence: `showToast(duration = 5f) { IntelToast("Charter renewed", "Halberd survey team: 30 more days.", icon) }`;
the toast's panel, slide and click-to-dismiss stay around it.

```kotlin
/** A toast like vanilla's intel notices: an icon, the title in the faction colour, the text in gray. */
fun UIPanelAPI.IntelToast(title: String, text: String, icon: String) {
    Row(spacing = 8f, verticalAlignment = Alignment.CenterVertically) {
        Image(icon, ImageSize.native(maxWidth = 32f, maxHeight = 32f))
        Column(spacing = 2f) {
            Text(title, font = Font.INSIGNIA_21, color = VanillaColors.base)
            Text(text, color = VanillaColors.gray)
        }
    }
}

/** A toast with a thin bar in [accent] down its left edge, the title in highlight, the text in gray. */
fun UIPanelAPI.AccentToast(title: String, text: String, accent: Color = VanillaColors.base) {
    Column(modifier = Modifier.padding(left = 11f).onRenderBelow { alpha ->
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glColor4f(accent.red / 255f, accent.green / 255f, accent.blue / 255f, alpha)
        GL11.glRectf(left, bottom, left + 3f, top)
        GL11.glEnable(GL11.GL_TEXTURE_2D)
    }, spacing = 2f) {
        Text(title, color = VanillaColors.highlight)
        Text(text, color = VanillaColors.gray)
    }
}

/** A toast like a small intel panel: vanilla's section heading bar over the text, [width] wide. */
fun UIPanelAPI.HeadingToast(title: String, text: String, width: Float = 280f) {
    Column(spacing = 4f) {
        SectionHeading(title, Size.exact(width, 20f))
        Text(text, Size.mixed(Axis.Exact(width), Axis.WrapContent))
    }
}
```

`IntelToast`: like the campaign's message list, for news about a thing with its icon. `AccentToast`: lightest
(`accent = VanillaColors.negative` for failures). `HeadingToast`: a small intel panel for more than a line. Checked
alone and inside a toast.
