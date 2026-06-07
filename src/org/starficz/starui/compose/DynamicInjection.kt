package org.starficz.starui.compose

import com.fs.starfarer.api.ui.UIPanelAPI

interface DynamicInjection {
    val id: String
    fun findTarget(): UIPanelAPI?
    fun UIPanelAPI.injectUI()
}