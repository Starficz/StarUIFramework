package org.starficz.starui.vanilla

import com.fs.starfarer.api.Global

/**
 * Plays a UI sound, as vanilla's buttons and panels do.
 *
 * @param id the sound's id in `sounds.json`
 * @param volume the volume, 1 for the sound's own
 * @param pitch the pitch, 1 for the sound's own
 */
fun playSound(id: String, volume: Float = 1f, pitch: Float = 1f) {
    Global.getSoundPlayer().playUISound(id, pitch, volume)
}
