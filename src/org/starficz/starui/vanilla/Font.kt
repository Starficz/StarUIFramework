package org.starficz.starui.vanilla

import com.fs.starfarer.api.Global

/** Font paths this framework has already made sure are loaded (SettingsAPI.loadFont re-parses on every call). */
private val loadedFontPaths = HashSet<String>()

/**
 * Starsector only preloads the fonts vanilla UI uses in each role. Using another .fnt as a paragraph font
 * (setParaFont, which measureTextSize and Text rely on) leaves the label with a null font and throws an NPE inside
 * the vanilla label's setText. So every path handed out by [path] is loaded once on first use.
 */
private fun ensureFontLoaded(path: String) {
    if (!loadedFontPaths.add(path)) return
    try {
        Global.getSettings().loadFont(path)
    } catch (e: Exception) {
        Global.getLogger(Font::class.java).error("StarUI: could not load font $path", e)
    }
}

/** Every font that ships with Starsector, by its file in `graphics/fonts`. */
enum class Font(file: String) {
    ARIAL_10("arial10.fnt"),
    ARIAL_12_BOLD("arial12bold.fnt"),
    ARIAL_16_BOLD("arial16bold.fnt"),

    INSIGNIA_15("insignia15LTaa.fnt"),
    INSIGNIA_16("insignia16.fnt"),
    INSIGNIA_16_A("insignia16a.fnt"),
    INSIGNIA_21("insignia21LTaa.fnt"),
    INSIGNIA_25("insignia25LTaa.fnt"),

    ORBITRON_10("orbitron10.fnt"),
    ORBITRON_12("orbitron12.fnt"),
    ORBITRON_12_CONDENSED("orbitron12condensed.fnt"),
    ORBITRON_20("orbitron20.fnt"),
    ORBITRON_20_AA("orbitron20aa.fnt"),
    ORBITRON_20_BOLD("orbitron20bold.fnt"),
    ORBITRON_20_AA_BOLD("orbitron20aabold.fnt"),
    ORBITRON_24("orbitron24aa.fnt"),
    ORBITRON_24_BOLD("orbitron24aabold.fnt"),

    SMALL_FONTS_8("small_fonts8.fnt"),

    VICTOR_10("victor10.fnt"),
    VICTOR_12("victor12.fnt"),
    VICTOR_14("victor14.fnt"),
    VICTOR_16("victor16.fnt"),
    VICTOR_21("victor21.fnt");

    internal val filePath = "graphics/fonts/$file"
}

/**
 * The fonts a vanilla button can be made with (TooltipMakerAPI has one setter for each and no other way). A separate
 * type, so a button cannot be given a font it does not support.
 */
enum class ButtonFont(val font: Font) {
    ORBITRON_12_CONDENSED(Font.ORBITRON_12_CONDENSED),
    ORBITRON_20(Font.ORBITRON_20),
    ORBITRON_20_BOLD(Font.ORBITRON_20_BOLD),
    ORBITRON_24(Font.ORBITRON_24),
    ORBITRON_24_BOLD(Font.ORBITRON_24_BOLD),
    VICTOR_10(Font.VICTOR_10),
    VICTOR_14(Font.VICTOR_14)
}

/** The font's file, for vanilla calls that take one (`setParaFont`, `addTextField`); loads the font if needed. */
val Font.path: String get() = filePath.also { ensureFontLoaded(it) }
