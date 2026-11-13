package org.starficz.starui.vanilla

import com.fs.starfarer.api.Global

/** Plays a UI sound (a sound id from `sounds.json`), as vanilla's buttons and panels do. */
fun playSound(id: String, volume: Float = 1f, pitch: Float = 1f) {
    Global.getSoundPlayer().playUISound(id, pitch, volume)
}
