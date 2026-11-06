package org.starficz.starui.compose.runtime

import com.fs.starfarer.api.ui.UIPanelAPI

interface DynamicInjection {
    val id: String
    fun findTarget(): UIPanelAPI?
    fun UIPanelAPI.injectUI()
}