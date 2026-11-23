package app.kirimlayar.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.sources.audio.AudioSource
import com.pedro.encoder.input.sources.audio.InternalAudioSource
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.audio.MixAudioSource
import com.pedro.encoder.input.sources.video.NoVideoSource
import com.pedro.encoder.input.sources.video.ScreenSource
import com.pedro.library.generic.GenericStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Service latar depan yang memegang MediaProjection dan encoder.
 * Alur: tangkap layar (ScreenSource) -> encode H.264/AAC (MediaCodec lewat RootEncoder) -> RTMP dan/atau file MP4.
 */
class StreamService : Service(), ConnectChecker {

    companion object {
        const val ACTION_START = "app.kirimlayar.capture.START"
        const val ACTION_STOP = "app.kirimlayar.capture.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_PROJECTION_DATA = "projection_data"
        const val EXTRA_URL = "url"
        const val EXTRA_WIDTH = "width"
        const val EXTRA_HEIGHT = "height"
        const val EXTRA_FPS = "fps"
        const val EXTRA_BITRATE_KBPS = "bitrate_kbps"
        const val EXTRA_ROTATION = "rotation"
        const val EXTRA_AUDIO_MODE = "audio_mode"
        const val EXTRA_RECORD = "record"

        private const val TAG = "StreamService"
        private const val CHANNEL_ID = "kirim_layar_live"
        private const val NOTIFICATION_ID = 1001
        private const val SAMPLE_RATE = 44100
        private const val AUDIO_BITRATE = 128 * 1000
    }

    private val main = Handler(Looper.getMainLooper())
    private var stream: GenericStream? = null
    private var mediaProjection: MediaProjection? = null
    private var recordPath: String? = null

    private val projectionManager: MediaProjectionManager by lazy {
        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> handleStop()
            else -> if (stream == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- mulai / henti

    private fun handleStart(intent: Intent) {
        if (stream != null) return

        // Wajib memanggil startForeground secepatnya setelah startForegroundService.
        try {
            enterForeground(getString(R.string.notification_starting))
        } catch (e: Exception) {
            Log.e(TAG, "startForeground gagal", e)
            StreamStatus.publish(UiState(Phase.ERROR, e.message ?: e.javaClass.simpleName))
            stopSelf()
            return
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val projectionData: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_PROJECTION_DATA)
        }
        if (resultCode != Activity.RESULT_OK || projectionData == null) {
            fail(getString(R.string.error_no_projection))
            return
        }

        val url = intent.getStringExtra(EXTRA_URL).orEmpty().trim()
        val width = intent.getIntExtra(EXTRA_WIDTH, 1280)
        val height = intent.getIntExtra(EXTRA_HEIGHT, 720)
        val fps = intent.getIntExtra(EXTRA_FPS, 30)
        val bitrateBps = Presets.bitrateBps(intent.getIntExtra(EXTRA_BITRATE_KBPS, 3000))
        val rotation = intent.getIntExtra(EXTRA_ROTATION, 90)
        val record = intent.getBooleanExtra(EXTRA_RECORD, false)
        val audioMode = runCatching {
            AudioMode.valueOf(intent.getStringExtra(EXTRA_AUDIO_MODE) ?: AudioMode.MIC.name)
        }.getOrDefault(AudioMode.MIC)

        StreamStatus.publish(UiState(Phase.STARTING))

        try {
            val projection = projectionManager.getMediaProjection(resultCode, projectionData)
                ?: throw IllegalStateException("MediaProjection tidak tersedia")
            mediaProjection = projection

            // Pengguna bisa menghentikan berbagi layar dari sistem; kita ikut berhenti.
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    main.post { handleStop() }
                }
            }, main)

            val audioSource: AudioSource = when {
                audioMode == AudioMode.INTERNAL && Build.VERSION.SDK_INT >= 29 -> InternalAudioSource(projection)
                audioMode == AudioMode.MIX && Build.VERSION.SDK_INT >= 29 -> MixAudioSource(projection)
                else -> MicrophoneSource(audioSource = MediaRecorder.AudioSource.MIC)
            }

            val s = GenericStream(baseContext, this, NoVideoSource(), audioSource).apply {
                // MediaProjection hanya menghasilkan frame saat layar berubah; paksa fps tetap.
                getGlInterface().setForceRender(true, fps)
            }

            val prepared = try {
                s.prepareVideo(width, height, bitrateBps, fps = fps, rotation = rotation) &&
                    s.prepareAudio(SAMPLE_RATE, true, AUDIO_BITRATE, echoCanceler = true, noiseSuppressor = true)
            } catch (e: IllegalArgumentException) {
                false
            }
            if (!prepared) {
                s.release()
                throw IllegalStateException(getString(R.string.error_encoder_prepare))
            }

            s.changeVideoSource(ScreenSource(applicationContext, projection))
            stream = s

            if (record) startLocalRecording(s)
            if (url.isNotEmpty()) {
                s.startStream(url)
                // Status LIVE ditetapkan di onConnectionSuccess.
            } else {
                StreamStatus.publish(UiState(Phase.RECORDING, recordPath = recordPath))
                updateNotification(getString(R.string.notification_recording))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memulai", e)
            fail(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun handleStop() {
        val path = recordPath
        teardown()
        StreamStatus.publish(UiState(Phase.IDLE, recordPath = path))
        stopSelf()
    }

    private fun fail(message: String) {
        teardown()
        StreamStatus.publish(UiState(Phase.ERROR, message))
        stopSelf()
    }

    private fun teardown() {
        try {
            stream?.let { s ->
                if (s.isRecording) s.stopRecord()
                if (s.isStreaming) s.stopStream()
                s.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Kesalahan saat membersihkan encoder", e)
        }
        stream = null
        try {
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Kesalahan saat menghentikan MediaProjection", e)
        }
        mediaProjection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun startLocalRecording(s: GenericStream) {
        val folder = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        if (!folder.exists()) folder.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(folder, "kirim-layar-$stamp.mp4")
        try {
            s.startRecord(file.absolutePath) { _ -> }
            recordPath = file.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "Tidak bisa merekam ke ${file.absolutePath}", e)
            recordPath = null
        }
    }

    // ---------------------------------------------------------------- notifikasi

    private fun enterForeground(text: String) {
        val type = when {
            Build.VERSION.SDK_INT >= 30 ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            Build.VERSION.SDK_INT >= 29 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            else -> 0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(text), type)
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, StreamService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_live)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, getString(R.string.action_stop), stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    // ---------------------------------------------------------------- ConnectChecker (thread bukan utama)

    override fun onConnectionStarted(url: String) {
        Log.i(TAG, "Menyambung ke server")
    }

    override fun onConnectionSuccess() {
        main.post {
            StreamStatus.publish(UiState(Phase.LIVE, recordPath = recordPath))
            updateNotification(getString(R.string.notification_live))
        }
    }

    override fun onNewBitrate(bitrate: Long) {
        val state = StreamStatus.current
        if (state.phase == Phase.LIVE) {
            StreamStatus.publish(state.copy(bitrateKbps = bitrate / 1000))
        }
    }

    override fun onConnectionFailed(reason: String) {
        main.post { fail(getString(R.string.error_connection, reason)) }
    }

    override fun onDisconnect() {
        Log.i(TAG, "Terputus dari server")
    }

    override fun onAuthError() {
        main.post { fail(getString(R.string.error_auth)) }
    }

    override fun onAuthSuccess() {
        Log.i(TAG, "Autentikasi berhasil")
    }
}
