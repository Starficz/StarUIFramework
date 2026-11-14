package org.starficz.starui

/**
 * Marks StarUI's builder receivers, so a builder cannot call component functions on an outer builder's panel by
 * accident: inside `Column { Row { ... } }`, `Text(...)` goes into the Row, and reaching the Column needs an
 * explicit receiver.
 */
@DslMarker
@Target(AnnotationTarget.TYPE, AnnotationTarget.CLASS)
annotation class StarUIDsl
