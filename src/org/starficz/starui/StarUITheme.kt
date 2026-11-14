package org.starficz.starui

import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CutStyle
import org.starficz.starui.modifier.PaddingValues
import org.starficz.starui.runtime.StarUIContext
import org.starficz.starui.vanilla.BoxedUIShipPreview
import org.starficz.starui.vanilla.ButtonFont
import org.starficz.starui.vanilla.Font
import java.awt.Color

/**
 * The defaults of [Text][org.starficz.starui.components.Text]: each one the value of the argument of the same name when
 * a call leaves it out.
 *
 * @property size the default size. By default the text's own size
 * @property font the default font. `null` is vanilla's paragraph font
 * @property color the default colour. `null` is vanilla's text colour
 * @property textAlign the default alignment of the text within its lines
 */
data class TextTheme(
    val size: Size = Size.wrapContent(),
    val font: Font? = null,
    val color: Color? = null,
    val textAlign: TextAlign = TextAlign.Left
)

/**
 * The defaults of [TextField][org.starficz.starui.components.TextField]: each one the value of the argument of the same
 * name when a call leaves it out.
 *
 * @property size the default size. By default the field fills the width
 * @property contentPadding the default space between the field's edge and its text
 * @property font the default font
 */
data class TextFieldTheme(
    val size: Size = Size.fillMaxWidth(),
    val contentPadding: PaddingValues = PaddingValues.symmetric(vertical = 5f),
    val font: Font = Font.INSIGNIA_15
)

/**
 * The defaults of [Button][org.starficz.starui.components.Button]: each one the value of the argument of the same name
 * when a call leaves it out.
 *
 * @property size the default size. By default the text's size plus [contentPadding]
 * @property contentPadding the default space between the button's edge and its text
 * @property color the default colour of the text and border. `null` is the player faction's base UI colour, read when
 *   the component is made
 * @property backgroundColor the default background colour. `null` is the player faction's dark UI colour, read when the
 *   component is made
 * @property font the default font
 * @property textAlign the default alignment of the text within the button
 * @property cutStyle the default corners that are cut off
 */
data class ButtonTheme(
    val size: Size = Size.wrapContent(),
    val contentPadding: PaddingValues = PaddingValues.symmetric(horizontal = 10f, vertical = 4f),
    val color: Color? = null,
    val backgroundColor: Color? = null,
    val font: ButtonFont = ButtonFont.ORBITRON_12_CONDENSED,
    val textAlign: TextAlign = TextAlign.Center,
    val cutStyle: CutStyle = CutStyle.TL_BR
)

/**
 * The defaults of [AreaCheckbox][org.starficz.starui.components.AreaCheckbox]: each one the value of the argument of
 * the same name when a call leaves it out.
 *
 * @property size the default size. By default the text's size plus [contentPadding]
 * @property contentPadding the default space between the button's edge and its text
 * @property baseColor the default colour of the glow under the cursor. `null` is the player faction's base UI
 *   colour, read when the component is made
 * @property darkColor the default colour of the border, and of the fill while on. `null` is the player faction's
 *   dark UI colour, read when the component is made
 * @property brightColor the default colour of the text. `null` is the player faction's bright UI colour, read when
 *   the component is made
 * @property font the default font. `null` is vanilla's area checkbox font
 * @property leftAlign whether the text is aligned to the left by default
 */
data class AreaCheckboxTheme(
    val size: Size = Size.wrapContent(),
    val contentPadding: PaddingValues = PaddingValues.symmetric(horizontal = 10f, vertical = 4f),
    val baseColor: Color? = null,
    val darkColor: Color? = null,
    val brightColor: Color? = null,
    val font: Font? = null,
    val leftAlign: Boolean = false
)

/**
 * The defaults of [Checkbox][org.starficz.starui.components.Checkbox]: each one the value of the argument of the same
 * name when a call leaves it out.
 *
 * @property size the default size. By default the box and label plus [contentPadding]
 * @property contentPadding the default space between the checkbox's edge and its content
 * @property checkboxSize the default size of the box
 * @property font the default font of the label
 * @property color the default colour of the box and label. `null` is the player faction's base UI colour, read when the
 *   component is made
 */
data class CheckboxTheme(
    val size: Size = Size.wrapContent(),
    val contentPadding: PaddingValues = PaddingValues.symmetric(horizontal = 8f, vertical = 4f),
    val checkboxSize: ButtonAPI.UICheckboxSize = ButtonAPI.UICheckboxSize.SMALL,
    val font: Font = Font.INSIGNIA_15,
    val color: Color? = null
)

/**
 * The defaults of [Slider][org.starficz.starui.components.Slider]: each one the value of the argument of the same name
 * when a call leaves it out.
 *
 * @property size the default size
 * @property barColor the default colour of the filled part of the bar. `null` keeps vanilla's
 * @property widgetColor the default colour of the handle. `null` keeps vanilla's
 */
data class SliderTheme(
    val size: BoundedSize = Size.exact(200f, 25f),
    val barColor: Color? = null,
    val widgetColor: Color? = null
)

/**
 * The defaults of [ProgressBar][org.starficz.starui.components.ProgressBar]: each one the value of the argument of the
 * same name when a call leaves it out.
 *
 * @property size the default size
 * @property barColor the default colour of the filled part of the bar. `null` keeps vanilla's
 */
data class ProgressBarTheme(
    val size: BoundedSize = Size.exact(200f, 12f),
    val barColor: Color? = null
)

/**
 * The look and behaviour of the vanilla scroll panel behind every container with a scrolling `overflow`. A `null`
 * colour keeps vanilla's.
 *
 * @property isLeftScrollbar whether the vertical scrollbar is on the left instead of the right
 * @property renderShadows whether dark shadows are drawn at the scrolling edges, where content is cut off
 * @property maxShadowHeight the largest height of the edge shadows, or `null` for vanilla's
 * @property shadowColor the colour of the edge shadows
 * @property scrollWidgetColor the colour of the scrollbar's handle
 * @property scrollRailColor the colour of the scrollbar's rail
 * @property showScrollbars whether the scrollbars are shown. The content still scrolls without them
 * @property useMouseWheel whether the mouse wheel scrolls the content
 */
data class ScrollTheme(
    val isLeftScrollbar: Boolean = false,
    val renderShadows: Boolean = true,
    val maxShadowHeight: Float? = null,
    val shadowColor: Color? = null,
    val scrollWidgetColor: Color? = null,
    val scrollRailColor: Color? = null,
    val showScrollbars: Boolean = true,
    val useMouseWheel: Boolean = true
)

/**
 * The defaults of [Divider][org.starficz.starui.components.Divider]: each one the value of the argument of the same
 * name when a call leaves it out.
 *
 * @property thickness the default thickness of the line, in pixels
 * @property color the default colour of the line. `null` is the player faction's dark UI colour, read when the
 *   component is made
 */
data class DividerTheme(
    val thickness: Float = 2f,
    val color: Color? = null
)

/**
 * The defaults of [SectionHeading][org.starficz.starui.components.SectionHeading]: each one the value of the argument
 * of the same name when a call leaves it out.
 *
 * @property size the default size. By default the heading fills the width
 * @property color the default colour of the text. `null` is the player faction's base UI colour, read when the
 *   component is made
 * @property backgroundColor the default colour of the bar. `null` is the player faction's dark UI colour, read when the
 *   component is made
 * @property textAlign the default alignment of the text on the bar
 */
data class SectionHeadingTheme(
    val size: Size = Size.fillMaxWidth(),
    val color: Color? = null,
    val backgroundColor: Color? = null,
    val textAlign: TextAlign = TextAlign.Center
)

/**
 * The defaults of [Section][org.starficz.starui.components.Section]: each one the value of the argument of the same
 * name when a call leaves it out.
 *
 * @property size the default size. By default the section fills the width and wraps its content in height
 * @property contentPadding the default space between the border and the content, below the heading
 * @property spacing the default gap between two neighbouring children of the content
 * @property backgroundColor the default background colour. `null` is [VanillaColors.panel]
 * @property borderColor the default colour of the border. `null` is the player faction's dark UI colour, read when the
 *   component is made
 */
data class SectionTheme(
    val size: Size = Size.fillMaxWidth(),
    val contentPadding: PaddingValues = PaddingValues.symmetric(horizontal = 10f, vertical = 8f),
    val spacing: Float = 6f,
    val backgroundColor: Color? = null,
    val borderColor: Color? = null
)

/**
 * The defaults of [Table][org.starficz.starui.components.Table]: each one the value of the argument of the same name
 * when a call leaves it out.
 *
 * @property size the default size. By default the table wraps its columns and rows
 * @property itemHeight the default height of a row
 * @property withHeader whether the row of column titles is shown by default
 * @property withBorder whether vanilla's frame is drawn by default
 * @property baseColor the default colour of the column titles, scrollbar handle, empty text and plain cells. `null`
 *   is the player faction's base UI colour, read when the component is made
 * @property darkColor the default colour of the frame, title borders, scrollbar rail and row glow. `null` is the
 *   player faction's dark UI colour, read when the component is made
 * @property sortIconColor the default colour of the sort arrow on sortable columns' headers. `null` is the player
 *   faction's bright UI colour, read when the component is made
 */
data class TableTheme(
    val size: Size = Size.wrapContent(),
    val itemHeight: Float = 20f,
    val withHeader: Boolean = true,
    val withBorder: Boolean = true,
    val baseColor: Color? = null,
    val darkColor: Color? = null,
    val sortIconColor: Color? = null
)

/**
 * The defaults of [ShipDisplay][org.starficz.starui.components.ShipDisplay]: each one the value of the argument of the
 * same name when a call leaves it out.
 *
 * @property size the default size
 * @property style the default label style
 * @property labelColor the default colour of the labels. `null` is vanilla's friendly text colour
 * @property showFighters whether the ship's fighters are drawn by default
 * @property showDestroyedFighters whether destroyed fighters are drawn by default
 * @property showBorder whether a border is drawn by default
 * @property newBorderStyle whether the border is a 1 pixel line by default, instead of the `ui_border4` sprite frame
 * @property scaleSmallerShips whether smaller hulls are shrunk by vanilla's table by default
 * @property scanlines the default darkness of the scanline overlay, from 0 (none) to 1 (solid black)
 */
data class ShipDisplayTheme(
    val size: BoundedSize = Size.exact(100f, 100f),
    val style: BoxedUIShipPreview.Style = BoxedUIShipPreview.Style.NORMAL,
    val labelColor: Color? = null,
    val showFighters: Boolean = false,
    val showDestroyedFighters: Boolean = false,
    val showBorder: Boolean = false,
    val newBorderStyle: Boolean = true,
    val scaleSmallerShips: Boolean = false,
    val scanlines: Float = 0f
)

/**
 * The defaults of [Image][org.starficz.starui.components.Image]: each one the value of the argument of the same name
 * when a call leaves it out.
 *
 * @property size the default size. By default the sprite's own size
 * @property contentScale the default rule for filling bounds of another shape than the sprite
 * @property withOutline whether a black outline is drawn around the sprite by default
 * @property renderSchematic whether vanilla's schematic effect (a yellow grid and glow) is drawn by default
 * @property showBorder whether a border is drawn around the image's bounds by default
 * @property borderColor the default colour of the border. `null` keeps vanilla's
 * @property textureClamp whether the sprite's texture is clamped at its edges by default
 * @property forceNoRounding whether the sprite is drawn at its exact position by default, instead of whole pixels
 */
data class ImageTheme(
    val size: ImageSize = ImageSize.native(),
    val contentScale: ContentScale = ContentScale.Fit,
    val withOutline: Boolean = false,
    val renderSchematic: Boolean = false,
    val showBorder: Boolean = false,
    val borderColor: Color? = null,
    val textureClamp: Boolean = false,
    val forceNoRounding: Boolean = false
)

/**
 * The defaults every component takes its optional arguments from, one class per component. Components such as
 * [Button][org.starficz.starui.components.Button] and [Checkbox][org.starficz.starui.components.Checkbox] use
 * values provided here when retrieving default values.
 *
 * Provide a theme for part of a UI with [Theme][org.starficz.starui.components.Theme]. Any values that are not set
 * keep the defaults: copy [current] and override only the parts of the theme that need to change.
 *
 * ```kotlin
 * val compact = StarUITheme.current.copy(button = StarUITheme.current.button.copy(contentPadding = PaddingValues.Zero))
 * Theme(compact) { Button("OK") }
 * ```
 *
 * A `null` colour in a theme means vanilla's own: the player faction's UI colours, read when the component is made.
 */
data class StarUITheme(
    val text: TextTheme = TextTheme(),
    val textField: TextFieldTheme = TextFieldTheme(),
    val button: ButtonTheme = ButtonTheme(),
    val areaCheckbox: AreaCheckboxTheme = AreaCheckboxTheme(),
    val checkbox: CheckboxTheme = CheckboxTheme(),
    val slider: SliderTheme = SliderTheme(),
    val progressBar: ProgressBarTheme = ProgressBarTheme(),
    val scroll: ScrollTheme = ScrollTheme(),
    val divider: DividerTheme = DividerTheme(),
    val sectionHeading: SectionHeadingTheme = SectionHeadingTheme(),
    val section: SectionTheme = SectionTheme(),
    val shipDisplay: ShipDisplayTheme = ShipDisplayTheme(),
    val image: ImageTheme = ImageTheme(),
    val table: TableTheme = TableTheme()
) {
    companion object {
        /** The theme StarUI uses when no [Theme][org.starficz.starui.components.Theme] is provided. */
        val Default = StarUITheme()
        /** The theme provided by the nearest enclosing [Theme][org.starficz.starui.components.Theme], or [Default]. */
        val current: StarUITheme
            get() = StarUIContext.currentTheme
    }
}
