package app.kirimlayar.capture

import android.os.Handler
import android.os.Looper

enum class Phase { IDLE, STARTING, LIVE, RECORDING, ERROR }

data class UiState(
    val phase: Phase = Phase.IDLE,
    val message: String = "",
    val bitrateKbps: Long = 0,
    val recordPath: String? = null,
)

/** Jembatan status dari StreamService ke layar. Pemanggilan listener selalu di thread utama. */
object StreamStatus {
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    var current: UiState = UiState()
        private set

    @Volatile
    var listener: ((UiState) -> Unit)? = null

    fun publish(state: UiState) {
        current = state
        main.post { listener?.invoke(state) }
    }
}
