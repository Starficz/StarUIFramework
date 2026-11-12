package org.starficz.starui.compose.runtime

import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CutStyle
import org.starficz.starui.compose.Alignment
import org.starficz.starui.compose.BoundedSize
import org.starficz.starui.compose.ContentScale
import org.starficz.starui.compose.ImageSize
import org.starficz.starui.compose.Size
import org.starficz.starui.compose.TextAlign
import org.starficz.starui.compose.modifier.PaddingValues
import org.starficz.starui.core.BoxedUIShipPreview
import org.starficz.starui.core.ButtonFont
import org.starficz.starui.core.Font
import java.awt.Color

// The defaults every component function takes its optional arguments from. `Theme(theme) { ... }` swaps them for the
// calls inside. A null colour means "vanilla's own": the player faction's UI colours, read when the component is made.

data class TextTheme(
    val size: Size = Size.wrapContent(),
    val font: Font? = null,
    val color: Color? = null,
    val textAlign: TextAlign = TextAlign.Left
)

data class TextFieldTheme(
    val size: Size = Size.fillMaxWidth(),
    /** Space between the field's edge and its text. */
    val contentPadding: PaddingValues = PaddingValues.symmetric(vertical = 5f),
    val font: Font = Font.INSIGNIA_15
)

data class ButtonTheme(
    val size: Size = Size.wrapContent(),
    /** Space between the button's edge and its label. */
    val contentPadding: PaddingValues = PaddingValues.symmetric(horizontal = 10f, vertical = 4f),
    val color: Color? = null,            // null = the player faction's base UI colour
    val backgroundColor: Color? = null,  // null = the player faction's dark UI colour
    val font: ButtonFont = ButtonFont.ORBITRON_12_CONDENSED,
    val textAlign: TextAlign = TextAlign.Center,
    val cutStyle: CutStyle = CutStyle.TL_BR
)

data class AreaCheckboxTheme(
    val size: Size = Size.wrapContent(),
    val contentPadding: PaddingValues = PaddingValues.symmetric(horizontal = 10f, vertical = 4f),
    val baseColor: Color? = null,   // null = the player faction's base UI colour
    val darkColor: Color? = null,   // null = the player faction's dark UI colour
    val brightColor: Color? = null, // null = the player faction's bright UI colour
    val font: Font? = null,
    val leftAlign: Boolean = false
)

data class CheckboxTheme(
    val size: Size = Size.wrapContent(),
    val contentPadding: PaddingValues = PaddingValues.symmetric(horizontal = 8f, vertical = 4f),
    val checkboxSize: ButtonAPI.UICheckboxSize = ButtonAPI.UICheckboxSize.SMALL,
    val font: Font = Font.INSIGNIA_15,
    val color: Color? = null // null = the player faction's base UI colour
)

data class SliderTheme(
    val size: BoundedSize = Size.exact(200f, 25f),
    val barColor: Color? = null,    // null = vanilla's
    val widgetColor: Color? = null  // null = vanilla's
)

data class ProgressBarTheme(
    val size: BoundedSize = Size.exact(200f, 12f),
    val barColor: Color? = null     // null = vanilla's
)

/** The vanilla scroll panel behind every container with a scrolling `overflow`. */
data class ScrollTheme(
    val isLeftScrollbar: Boolean = false,
    val renderShadows: Boolean = true,
    val maxShadowHeight: Float? = null,
    val shadowColor: Color? = null, // The dark gradient color
    val scrollWidgetColor: Color? = null,
    val scrollRailColor: Color? = null,
    val showScrollbars: Boolean = true,
    val useMouseWheel: Boolean = true
)

data class DividerTheme(
    val thickness: Float = 2f,
    val color: Color = Color.DARK_GRAY
)

data class SectionHeadingTheme(
    val size: Size = Size.fillMaxWidth(),
    val color: Color? = null,            // null = the player faction's base UI colour
    val backgroundColor: Color? = null,  // null = the player faction's dark UI colour
    val textAlign: TextAlign = TextAlign.Center
)

data class SectionTheme(
    val size: Size = Size.fillMaxWidth(),
    /** Space between the section's border and its content, below the heading. */
    val contentPadding: PaddingValues = PaddingValues.symmetric(horizontal = 10f, vertical = 8f),
    val spacing: Float = 6f,
    val backgroundColor: Color? = null,  // null = VanillaColors.panel
    val borderColor: Color? = null       // null = the player faction's dark UI colour
)

data class TableTheme(
    val size: Size = Size.wrapContent(),
    /** The height of every row. */
    val itemHeight: Float = 20f,
    val withHeader: Boolean = true,
    val withBorder: Boolean = true,
    val baseColor: Color? = null,   // null = the player faction's base UI colour
    val darkColor: Color? = null,   // null = its dark UI colour
    val brightColor: Color? = null  // null = its bright UI colour
)

data class ShipDisplayTheme(
    val size: BoundedSize = Size.exact(100f, 100f),
    val vanillaStyle: BoxedUIShipPreview.Style = BoxedUIShipPreview.Style.NORMAL,
    val labelColor: Color? = null, // null defaults to Global.getSettings().getColor("textFriendColor")
    val showFighters: Boolean = false,
    val showDestroyedFighters: Boolean = false,
    val showBorder: Boolean = false,
    val newBorderStyle: Boolean = true,
    val scaleSmallerShips: Boolean = false,
    val scanlines: Float = 0f
)

data class ImageTheme(
    val size: ImageSize = ImageSize.native(),
    val contentScale: ContentScale = ContentScale.Fit,
    val withOutline: Boolean = false,
    val renderSchematic: Boolean = false,
    val showBorder: Boolean = false,
    val borderColor: Color? = null, // null defaults to a vanilla internal color (color_20)
    val textureClamp: Boolean = false,
    val forceNoRounding: Boolean = false
)

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
        val Default = StarUITheme()
        val current: StarUITheme
            get() = StarUIContext.currentTheme
    }
}
