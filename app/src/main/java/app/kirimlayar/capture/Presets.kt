package app.kirimlayar.capture

/** Logika murni (tanpa Android) supaya mudah diuji unit. */

enum class AudioMode { MIC, INTERNAL, MIX }

data class Quality(
    val label: String,
    val width: Int,
    val height: Int,
    val defaultBitrateKbps: Int,
)

object Presets {
    /** Lebar selalu lebih besar dari tinggi. Orientasi diatur lewat rotasi, bukan lewat tukar ukuran. */
    val qualities: List<Quality> = listOf(
        Quality("480p", 854, 480, 1500),
        Quality("720p", 1280, 720, 3000),
        Quality("1080p", 1920, 1080, 5000),
    )

    val fpsOptions: List<Int> = listOf(15, 30, 60)

    const val MIN_BITRATE_KBPS = 300
    const val MAX_BITRATE_KBPS = 20_000

    private val rtmpPattern = Regex("^rtmps?://[^/\\s]+/\\S+$", RegexOption.IGNORE_CASE)

    fun isValidRtmpUrl(url: String): Boolean = rtmpPattern.matches(url.trim())

    /** 90 untuk layar tegak (portrait), 0 untuk mendatar (landscape). */
    fun rotationFor(isPortrait: Boolean): Int = if (isPortrait) 90 else 0

    fun bitrateBps(kbps: Int): Int = kbps.coerceIn(MIN_BITRATE_KBPS, MAX_BITRATE_KBPS) * 1000
}
