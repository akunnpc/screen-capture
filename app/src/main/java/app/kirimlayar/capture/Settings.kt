package app.kirimlayar.capture

import android.content.Context

/**
 * Penyimpanan setelan di perangkat. URL RTMP (yang memuat stream key) disimpan apa adanya
 * di penyimpanan privat app. allowBackup dimatikan di manifest supaya tidak ikut cadangan.
 */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("kirim_layar", Context.MODE_PRIVATE)

    var url: String
        get() = prefs.getString("url", "") ?: ""
        set(value) { prefs.edit().putString("url", value).apply() }

    var qualityIndex: Int
        get() = prefs.getInt("quality", 1)
        set(value) { prefs.edit().putInt("quality", value).apply() }

    var fpsIndex: Int
        get() = prefs.getInt("fps", 1)
        set(value) { prefs.edit().putInt("fps", value).apply() }

    /** 0 berarti otomatis (ikut kualitas). */
    var bitrateKbps: Int
        get() = prefs.getInt("bitrate", 0)
        set(value) { prefs.edit().putInt("bitrate", value).apply() }

    var audioMode: AudioMode
        get() = runCatching { AudioMode.valueOf(prefs.getString("audio", AudioMode.MIC.name) ?: "") }
            .getOrDefault(AudioMode.MIC)
        set(value) { prefs.edit().putString("audio", value.name).apply() }

    var recordLocal: Boolean
        get() = prefs.getBoolean("record", false)
        set(value) { prefs.edit().putBoolean("record", value).apply() }
}
