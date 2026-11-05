package com.godly.moonzoom

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Bộ điều khiển zoom mượt. Nội suy theo thang logarit (zoom cảm nhận đều),
 * kẹp cứng trong [1X, 100X]. Giá trị cuối cùng luôn là đúng giá trị đích (100f khi chạm trần).
 */
class SmoothZoomController(private val scope: CoroutineScope) {

    companion object {
        const val MIN = 1f
        const val MAX = 100f
        val PRESETS = listOf(1f, 2f, 5f, 10f, 30f, 50f, 100f)

        fun clamp(v: Float): Float = if (v.isNaN()) MIN else v.coerceIn(MIN, MAX)
    }

    private val _zoom = MutableStateFlow(MIN)
    val zoom: StateFlow<Float> = _zoom

    /** Giá trị đích hiện tại (đã kẹp). */
    var target: Float = MIN
        private set

    private var job: Job? = null

    fun setTarget(value: Float, immediate: Boolean = false) {
        target = clamp(value)
        if (immediate) {
            job?.cancel()
            _zoom.value = target
        } else {
            ensureLoop()
        }
    }

    private fun ensureLoop() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                val cur = _zoom.value
                val t = target
                if (abs(ln(t / cur)) < 0.002f) {
                    _zoom.value = t
                    break
                }
                _zoom.value = clamp(cur * exp(ln(t / cur) * 0.22f))
                delay(16)
            }
        }
    }
}
