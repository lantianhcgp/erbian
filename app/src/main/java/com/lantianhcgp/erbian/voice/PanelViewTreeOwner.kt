package com.lantianhcgp.erbian.voice

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * 给非 Activity 窗口（会话窗/悬浮窗）里的 Compose 视图补生命周期三件套。
 * 2026-10-15 实锤：SessionPanelView 在无 ViewTreeLifecycleOwner 的窗口里首帧崩溃
 * "ViewTreeLifecycleOwner not found" → 进程秒杀 → 用户只见入场白边（v1.7/v2.2/v2.3 全中此枪）。
 */
class PanelViewTreeOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val controller = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

    fun attachTo(view: android.view.View) {
        runCatching { controller.performRestore(null) }
        registry.currentState = Lifecycle.State.CREATED
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun detach() {
        registry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
