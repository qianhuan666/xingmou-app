package com.xingmou.core.perception

enum class DetectPhase { IDLE, AWAIT_PERMISSION, DETECTING, RETRYING, MANUAL_FALLBACK, HIT }

data class DetectSessionState(
    val questionId: String,
    val isCamera: Boolean,
    val phase: DetectPhase,
    val remainingMs: Long,
    val timeoutMs: Long
)

/**
 * 观察题自动检测会话状态机。纯 Kotlin，时间由调用方注入，便于单测。
 *
 * 生命周期：start → AWAIT_PERMISSION → onPermissionReady → begin →
 * DETECTING ──命中──▶ HIT(触发 onAutoAnswer)
 *           ──超时──▶ RETRYING(触发 onEncourage) ──再超时──▶ MANUAL_FALLBACK
 * MANUAL_FALLBACK 下检测器不停，仍可 onHit 自动作答。
 */
class DetectSessionController(private val timeoutMs: Long = 10_000L) {

    var session: DetectSessionState? = null
        private set

    var onAutoAnswer: ((String, String?) -> Unit)? = null
    var onEncourage: ((Boolean) -> Unit)? = null

    private var phaseStartMs = 0L

    @Synchronized
    fun start(questionId: String, isCamera: Boolean) {
        session = DetectSessionState(questionId, isCamera, DetectPhase.AWAIT_PERMISSION, timeoutMs, timeoutMs)
    }

    @Synchronized
    fun onPermissionReady(available: Boolean) {
        val s = session ?: return
        if (s.phase != DetectPhase.AWAIT_PERMISSION) return
        session = if (available) {
            s.copy(phase = DetectPhase.DETECTING, remainingMs = timeoutMs)
        } else {
            s.copy(phase = DetectPhase.MANUAL_FALLBACK, remainingMs = 0L)
        }
    }

    @Synchronized
    fun chooseManual() {
        val s = session ?: return
        if (s.phase == DetectPhase.AWAIT_PERMISSION) {
            session = s.copy(phase = DetectPhase.MANUAL_FALLBACK, remainingMs = 0L)
        }
    }

    @Synchronized
    fun onHit(meta: String?, nowMs: Long) {
        val s = session ?: return
        if (s.phase != DetectPhase.DETECTING &&
            s.phase != DetectPhase.RETRYING &&
            s.phase != DetectPhase.MANUAL_FALLBACK) return
        session = s.copy(phase = DetectPhase.HIT, remainingMs = 0L)
        onAutoAnswer?.invoke(s.questionId, meta)
    }

    /** 硬件不可用（模拟器 liteMode、识别服务缺失）时强制转手动兜底。 */
    @Synchronized
    fun forceManual() {
        val s = session ?: return
        if (s.phase == DetectPhase.DETECTING || s.phase == DetectPhase.RETRYING) {
            session = s.copy(phase = DetectPhase.MANUAL_FALLBACK, remainingMs = 0L)
        }
    }

    @Synchronized
    fun begin(nowMs: Long) {
        // ViewModel 在权限就绪并真正开始接收数据时调用，锚定计时起点
        phaseStartMs = nowMs
    }

    @Synchronized
    fun tick(nowMs: Long) {
        val s = session ?: return
        when (s.phase) {
            DetectPhase.DETECTING -> {
                val remaining = (timeoutMs - (nowMs - phaseStartMs)).coerceAtLeast(0L)
                if (remaining <= 0L) {
                    phaseStartMs = nowMs
                    session = s.copy(phase = DetectPhase.RETRYING, remainingMs = timeoutMs)
                    onEncourage?.invoke(s.isCamera)
                } else {
                    session = s.copy(remainingMs = remaining)
                }
            }
            DetectPhase.RETRYING -> {
                val remaining = (timeoutMs - (nowMs - phaseStartMs)).coerceAtLeast(0L)
                if (remaining <= 0L) {
                    session = s.copy(phase = DetectPhase.MANUAL_FALLBACK, remainingMs = 0L)
                } else {
                    session = s.copy(remainingMs = remaining)
                }
            }
            else -> Unit
        }
    }

    @Synchronized
    fun stop() {
        session = null
    }
}
