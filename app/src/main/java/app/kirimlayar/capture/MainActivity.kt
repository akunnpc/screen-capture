package app.kirimlayar.capture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var settings: Settings
    private lateinit var urlInput: EditText
    private lateinit var showUrl: CheckBox
    private lateinit var qualitySpinner: Spinner
    private lateinit var fpsSpinner: Spinner
    private lateinit var bitrateInput: EditText
    private lateinit var audioSpinner: Spinner
    private lateinit var recordCheck: CheckBox
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    /** Mode audio yang ditawarkan. Audio sistem dan campuran butuh Android 10 ke atas. */
    private val audioModes: List<AudioMode> =
        if (Build.VERSION.SDK_INT >= 29) AudioMode.values().toList() else listOf(AudioMode.MIC)

    private val projectionManager: MediaProjectionManager by lazy {
        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasMicPermission()) {
                projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
            } else {
                toast(R.string.error_mic_permission)
            }
        }

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                launchService(result.resultCode, data)
            } else {
                toast(R.string.error_projection_denied)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = Settings(this)

        urlInput = findViewById(R.id.input_url)
        showUrl = findViewById(R.id.check_show_url)
        qualitySpinner = findViewById(R.id.spinner_quality)
        fpsSpinner = findViewById(R.id.spinner_fps)
        bitrateInput = findViewById(R.id.input_bitrate)
        audioSpinner = findViewById(R.id.spinner_audio)
        recordCheck = findViewById(R.id.check_record)
        statusText = findViewById(R.id.text_status)
        startButton = findViewById(R.id.button_start)
        stopButton = findViewById(R.id.button_stop)

        bindSpinner(qualitySpinner, Presets.qualities.map { it.label })
        bindSpinner(fpsSpinner, Presets.fpsOptions.map { "$it fps" })
        bindSpinner(audioSpinner, audioModes.map { getString(audioLabel(it)) })

        urlInput.setText(settings.url)
        qualitySpinner.setSelection(settings.qualityIndex.coerceIn(0, Presets.qualities.lastIndex))
        fpsSpinner.setSelection(settings.fpsIndex.coerceIn(0, Presets.fpsOptions.lastIndex))
        if (settings.bitrateKbps > 0) bitrateInput.setText(settings.bitrateKbps.toString())
        audioSpinner.setSelection(audioModes.indexOf(settings.audioMode).coerceAtLeast(0))
        recordCheck.isChecked = settings.recordLocal

        maskUrl(true)
        showUrl.setOnCheckedChangeListener { _, checked -> maskUrl(!checked) }

        startButton.setOnClickListener { onStartClicked() }
        stopButton.setOnClickListener {
            startService(Intent(this, StreamService::class.java).setAction(StreamService.ACTION_STOP))
        }
    }

    override fun onStart() {
        super.onStart()
        StreamStatus.listener = { render(it) }
        render(StreamStatus.current)
    }

    override fun onStop() {
        StreamStatus.listener = null
        super.onStop()
    }

    // ---------------------------------------------------------------- aksi

    private fun onStartClicked() {
        val url = urlInput.text.toString().trim()
        val record = recordCheck.isChecked

        if (url.isEmpty() && !record) {
            toast(R.string.error_need_target)
            return
        }
        if (url.isNotEmpty() && !Presets.isValidRtmpUrl(url)) {
            urlInput.error = getString(R.string.error_invalid_url)
            return
        }
        saveSettings(url)

        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun saveSettings(url: String) {
        settings.url = url
        settings.qualityIndex = qualitySpinner.selectedItemPosition
        settings.fpsIndex = fpsSpinner.selectedItemPosition
        settings.bitrateKbps = bitrateInput.text.toString().toIntOrNull() ?: 0
        settings.audioMode = audioModes[audioSpinner.selectedItemPosition.coerceIn(0, audioModes.lastIndex)]
        settings.recordLocal = recordCheck.isChecked
    }

    private fun launchService(resultCode: Int, data: Intent) {
        val quality = Presets.qualities[qualitySpinner.selectedItemPosition.coerceIn(0, Presets.qualities.lastIndex)]
        val fps = Presets.fpsOptions[fpsSpinner.selectedItemPosition.coerceIn(0, Presets.fpsOptions.lastIndex)]
        val kbps = bitrateInput.text.toString().toIntOrNull() ?: quality.defaultBitrateKbps
        val portrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

        val intent = Intent(this, StreamService::class.java).apply {
            action = StreamService.ACTION_START
            putExtra(StreamService.EXTRA_RESULT_CODE, resultCode)
            putExtra(StreamService.EXTRA_PROJECTION_DATA, data)
            putExtra(StreamService.EXTRA_URL, urlInput.text.toString().trim())
            putExtra(StreamService.EXTRA_WIDTH, quality.width)
            putExtra(StreamService.EXTRA_HEIGHT, quality.height)
            putExtra(StreamService.EXTRA_FPS, fps)
            putExtra(StreamService.EXTRA_BITRATE_KBPS, kbps)
            putExtra(StreamService.EXTRA_ROTATION, Presets.rotationFor(portrait))
            putExtra(
                StreamService.EXTRA_AUDIO_MODE,
                audioModes[audioSpinner.selectedItemPosition.coerceIn(0, audioModes.lastIndex)].name,
            )
            putExtra(StreamService.EXTRA_RECORD, recordCheck.isChecked)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    // ---------------------------------------------------------------- tampilan

    private fun render(state: UiState) {
        val running = state.phase == Phase.STARTING || state.phase == Phase.LIVE || state.phase == Phase.RECORDING
        startButton.isEnabled = !running
        stopButton.isEnabled = running

        val main = when (state.phase) {
            Phase.IDLE -> getString(R.string.status_idle)
            Phase.STARTING -> getString(R.string.status_starting)
            Phase.LIVE -> getString(R.string.status_live, state.bitrateKbps)
            Phase.RECORDING -> getString(R.string.status_recording)
            Phase.ERROR -> getString(R.string.status_error, state.message)
        }
        val path = state.recordPath
        statusText.text = if (path != null) main + "\n" + getString(R.string.status_record_path, path) else main
    }

    private fun maskUrl(masked: Boolean) {
        val variation = if (masked) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        urlInput.inputType = InputType.TYPE_CLASS_TEXT or variation
        urlInput.setSelection(urlInput.text.length)
    }

    private fun bindSpinner(spinner: Spinner, items: List<String>) {
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
    }

    private fun audioLabel(mode: AudioMode): Int = when (mode) {
        AudioMode.MIC -> R.string.audio_mic
        AudioMode.INTERNAL -> R.string.audio_internal
        AudioMode.MIX -> R.string.audio_mix
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_LONG).show()
}
