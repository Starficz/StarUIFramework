package org.starficz.starui

/** Marks StarUI builder receivers, so a builder cannot call component functions on an outer builder's panel by accident. */
@DslMarker
@Target(AnnotationTarget.TYPE, AnnotationTarget.CLASS)
annotation class StarUIDsl
