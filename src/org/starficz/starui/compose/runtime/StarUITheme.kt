package org.starficz.starui.compose.runtime

import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CutStyle
import org.starficz.starui.compose.ImageSize
import org.starficz.starui.compose.PanelSize
import org.starficz.starui.compose.Size
import org.starficz.starui.compose.components.ContentScale
import org.starficz.starui.compose.components.TextAlign
import org.starficz.starui.compose.layout.XAlign
import org.starficz.starui.compose.layout.YAlign
import org.starficz.starui.core.BoxedUIShipPreview
import org.starficz.starui.core.Font
import java.awt.Color

data class TextTheme(
    val size: Size = Size.wrap(),
    val font: Font? = null,
    val color: Color? = null,
    val textAlign: TextAlign = TextAlign.Left,
    val xAlign: XAlign = XAlign.Left,
    val yAlign: YAlign = YAlign.Top
)

data class TextFieldTheme(
    val size: Size = Size.fillX(yPadding = 5f),
    val font: Font = Font.INSIGNIA_15
)

data class ButtonTheme(
    val size: Size = Size.wrap(xPadding = 10f, yPadding = 4f),
    val baseColor: Color? = null, // null = Global.getSettings().basePlayerColor
    val bgColor: Color? = null,   // null = Global.getSettings().darkPlayerColor
    val font: Font = Font.ORBITRON_12_CONDENSED,
    val align: Alignment = Alignment.MID,
    val cutStyle: CutStyle = CutStyle.TL_BR
)

data class AreaCheckboxTheme(
    val size: Size = Size.wrap(xPadding = 10f, yPadding = 4f),
    val baseColor: Color? = null,   // null = Global.getSettings().basePlayerColor
    val bgColor: Color? = null,     // null = Global.getSettings().darkPlayerColor
    val brightColor: Color? = null, // null = Global.getSettings().brightPlayerColor
    val font: Font? = null,
    val leftAlign: Boolean = false
)

data class CheckboxTheme(
    val size: Size = Size.wrap(xPadding = 8f, yPadding = 4f),
    val checkboxSize: ButtonAPI.UICheckboxSize = ButtonAPI.UICheckboxSize.SMALL,
    val font: Font = Font.INSIGNIA_15,
    val color: Color? = null // null = Global.getSettings().basePlayerColor
)

data class ScrollPanelTheme(
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

data class ShipDisplayTheme(
    val size: PanelSize = PanelSize.exact(100f, 100f),
    val vanillaStyle: BoxedUIShipPreview.Style = BoxedUIShipPreview.Style.NORMAL,
    val color: Color? = null, // null defaults to Global.getSettings().getColor("textFriendColor")
    val showFighters: Boolean = false,
    val showDestroyedFighters: Boolean = false,
    val showBorder: Boolean = false,
    val newBorderStyle: Boolean = true,
    val scaleSmallerShips: Float = 1f
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
    val scrollPanel: ScrollPanelTheme = ScrollPanelTheme(),
    val divider: DividerTheme = DividerTheme(),
    val shipDisplay: ShipDisplayTheme = ShipDisplayTheme(),
    val image: ImageTheme = ImageTheme()
) {
    companion object {
        val Default = StarUITheme()
        val current: StarUITheme
            get() = StarUIContext.currentTheme
    }
}
