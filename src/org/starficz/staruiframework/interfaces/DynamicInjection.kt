package org.starficz.staruiframework.interfaces

import com.fs.starfarer.api.ui.UIPanelAPI

interface DynamicInjection {
    val id: String
    fun findTarget(): UIPanelAPI?
    fun UIPanelAPI.injectUI()
}