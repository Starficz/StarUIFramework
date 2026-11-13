package org.starficz.starui.core

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.core.ReflectionUtils.getConstructorsMatching
import org.starficz.starui.core.ReflectionUtils.invoke
import patchlib.api.PatchLib
import patchlib.api.query.ClassQuery
import patchlib.api.query.MethodQuery

/**
 * The vanilla classes StarUI constructs itself that have no API factory (ship previews, holo panels), found by their
 * readable methods and their constructors by shape. Looked up once at application load; re-check them after a game
 * update.
 */
internal object VanillaClasses {
    private val log = Global.getLogger(VanillaClasses::class.java)

    // ---- ship preview (ShipDisplay) ----
    var shipPreviewClass: Class<*>? = null
        private set
    /** (FleetMemberAPI) */
    var shipPreviewForMember: ReflectionUtils.ReflectedConstructor? = null
        private set
    /** (style enum, label colour) */
    var shipPreviewWithStyle: ReflectionUtils.ReflectedConstructor? = null
        private set
    /** The values of the style enum [shipPreviewWithStyle] takes (NONE, NORMAL, MINI). */
    var shipPreviewStyles: Array<*>? = null
        private set

    val hasShipPreview: Boolean get() = shipPreviewClass != null

    // ---- holo panel (Holo) ----
    var holoClass: Class<*>? = null
        private set
    /** (width, height, frame sprite set, content inset) */
    var holoConstructor: ReflectionUtils.ReflectedConstructor? = null
        private set

    fun findAtApplicationLoad() {
        findHolo()
        if (hasShipPreview) return
        // 0.98.5a-RC4 Windows: the ship preview the mission and refit screens use
        val query = ClassQuery.create()
            .subtype(UIPanelAPI::class.java)
            .hasMethod(MethodQuery.create().methodName("isSchematicMode").parameterCount(0))
            .hasMethod(MethodQuery.create().methodName("setScaleDownSmallerShipsMagnitude").parameterCount(1))
            .build()
        PatchLib.scan(query).firstOrNull()?.type?.let { useShipPreview(it) }
    }

    /** The fallback when the scan found no ship preview: the one in the title screen's mission list. */
    fun findShipPreviewOnTitleScreen(title: UIPanelAPI) {
        if (hasShipPreview) return
        val missionWidget = title.findChildWithMethod("getMissionList") as? UIPanelAPI ?: return
        val holographicBG = missionWidget.getChildrenCopy2()[1] // 2 of the same class in the tree here
        val missionDetail = holographicBG.invoke("getCurr") as? UIPanelAPI ?: return
        val missionShipPreview = missionDetail.findChildWithMethod("setVariant") as? UIPanelAPI ?: return
        val shipPreview = missionShipPreview.findChildWithMethod("isSchematicMode") ?: return
        useShipPreview(shipPreview.javaClass)
    }

    private fun useShipPreview(clazz: Class<*>) {
        val constructors = clazz.getConstructorsMatching()
        shipPreviewForMember = constructors.find { constructor ->
            constructor.parameterTypes.firstOrNull()?.let { FleetMemberAPI::class.java.isAssignableFrom(it) } ?: false
        }
        shipPreviewWithStyle = constructors.find { it.parameterTypes.size == 2 }
        shipPreviewStyles = shipPreviewWithStyle?.parameterTypes?.first()?.enumConstants
        shipPreviewClass = clazz
    }

    // 0.98.5a-RC4 Windows: matches com.fs.starfarer.E.I
    private fun findHolo() {
        if (holoClass != null) return
        val query = ClassQuery.create()
            .subtype(UIComponentAPI::class.java)
            .hasMethod(MethodQuery.create().methodName("foldOut").parameterCount(1))
            .hasMethod(MethodQuery.create().methodName("foldIn").parameterCount(1))
            .hasMethod(MethodQuery.create().methodName("setNoiseAlpha").parameterCount(1))
            .hasMethod(MethodQuery.create().methodName("getBorderThickness").parameterCount(0))
            .hasMethod(MethodQuery.create().methodName("getFanOut").parameterCount(0))
            .build()
        val found = PatchLib.scan(query).map { it.type }
        if (found.size != 1) {
            log.warn("StarUI: Holo needs vanilla's holo panel class; found ${found.size} candidates (${found.joinToString { it.name }}). Holo() will fail.")
            return
        }
        val clazz = found.single()
        val float = Float::class.javaPrimitiveType
        holoConstructor = clazz.getConstructorsMatching(4, arrayOf(float, float, String::class.java, float)).firstOrNull()
        holoClass = clazz
        if (holoConstructor == null) log.warn("StarUI: vanilla's holo panel has no (float, float, String, float) constructor. Holo() will fail.")
    }
}
