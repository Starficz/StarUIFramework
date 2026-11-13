// ImageSize.aspectWrap(): as large as the space allows at the sprite's aspect ratio, in a Box, a Column and a Row.
// ImageSize.native() stays the sprite's own size. Uses the wolf hull sprite, 52 x 96.

import com.fs.starfarer.api.Global
import org.starficz.starui.*
import org.starficz.starui.Alignment
import org.starficz.starui.components.Box
import org.starficz.starui.components.Column
import org.starficz.starui.components.Image
import org.starficz.starui.components.Row
import org.starficz.starui.modifier.align
import org.starficz.starui.modifier.anchor
import org.starficz.starui.modifier.Modifier
import org.starficz.starui.modifier.padding
import org.starficz.starui.vanilla.BoxedUIImage

private const val WOLF = "graphics/ships/wolf/wolf_base.png"
private fun imageAt(x: Float, y: Float) = Modifier.anchor(Anchor.inside.topLeft.ofParent(x, y))

fun registerImageSizeTests() {
    StarUITestSuite.group("image-aspect-wrap") {
        if (Global.getSettings().getSprite(WOLF).width == 0f) runCatching { Global.getSettings().loadTexture(WOLF) }
        val sprite = Global.getSettings().getSprite(WOLF)
        expect(sprite.width == 52f && sprite.height == 96f, "the wolf sprite is loaded at 52 x 96 (${sprite.width} x ${sprite.height})")

        lateinit var inBox: BoxedUIImage
        lateinit var inPaddedBox: BoxedUIImage
        lateinit var inColumn: BoxedUIImage
        lateinit var inRow: BoxedUIImage
        lateinit var native: BoxedUIImage
        root.Box(Size.exact(90f, 60f), imageAt(40f, 40f)) {
            inBox = Image(WOLF, ImageSize.aspectWrap(), Modifier.align(Alignment.Center))
        }
        root.Box(Size.exact(90f, 60f), imageAt(160f, 40f).padding(10f)) {
            inPaddedBox = Image(WOLF, ImageSize.aspectWrap())
        }
        root.Column(Size.mixed(Axis.Exact(200f), Axis.WrapContent), imageAt(280f, 40f)) {
            inColumn = Image(WOLF, ImageSize.aspectWrap())
        }
        root.Row(Size.mixed(Axis.WrapContent, Axis.Exact(48f)), imageAt(500f, 40f)) {
            inRow = Image(WOLF, ImageSize.aspectWrap())
        }
        root.Box(Size.exact(200f, 200f), imageAt(560f, 40f)) {
            native = Image(WOLF, ImageSize.native())
        }
        frame()

        near(inBox.h, 60f, "in a 90 x 60 Box a tall sprite takes the full height")
        near(inBox.w, 60f * 52f / 96f, "at its own aspect ratio")
        near(inPaddedBox.h, 40f, "a Box's padding is taken off the room")
        near(inPaddedBox.w, 40f * 52f / 96f, "at the same ratio")
        near(inColumn.w, 200f, "in a Column 200 wide it takes the width", tolerance = 1f)
        near(inColumn.h, 200f * 96f / 52f, "and the height follows", tolerance = 1f)
        near(inRow.h, 48f, "in a Row 48 tall it takes the height")
        near(inRow.w, 48f * 52f / 96f, "and the width follows")
        near(native.w, 52f, "ImageSize.native() stays the sprite's own width in a larger Box")
        near(native.h, 96f, "and height")
        screenshot("final")
    }
}
