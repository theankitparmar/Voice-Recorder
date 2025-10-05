package com.quick.voice.recorder.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.quick.voice.recorder.R
import com.quick.voice.recorder.VoiceRecorderApplication
import com.quick.voice.recorder.databinding.ActivityDemoBinding
import com.quick.voice.recorder.utils.FileUtils
import com.quick.voice.recorder.utils.WaveformView
import com.quick.voice.recorder.viewmodel.MainViewModel
import com.quick.voice.recorder.viewmodel.MainViewModelFactory
import java.io.File

class DemoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDemoBinding
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory((application as VoiceRecorderApplication).repository)
    }

    private var mediaRecorder: MediaRecorder? = null
    private val handler = Handler(Looper.getMainLooper())
    private var waveformUpdateRunnable: Runnable? = null
    private var timerRunnable: Runnable? = null
    private var isRecording = false
    private var isPaused = false
    private var currentOutputFile: File? = null
    private var recordingStartTime: Long = 0L
    private var recordingDuration: Long = 0L

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startRecording()
        } else {
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWaveform()
        setupUI()
        requestAllPermissions()
    }

    private fun setupWaveform() {
        binding.waveformView.apply {
            setScrollingMode(true)
            setAlignment(WaveformView2.WaveformAlignment.CENTER)
            setSpikeStyle(
                width = context.dpToPx(3),
                padding = context.dpToPx(2),
                radius = context.dpToPx(4)
            )
            setDimOverlay(enabled = true, height = context.dpToPx(25))
        }
    }

    private fun setupUI() {
        binding.btnStartRecording.setOnClickListener {
            if (!isRecording) {
                checkPermissionsAndStartRecording()
            }
        }

        binding.btnPauseResume.setOnClickListener {
            togglePauseResume()
        }

        binding.btnStop.setOnClickListener {
            stopRecording()
        }
    }

    private fun startRecording() {
        try {
            currentOutputFile = FileUtils.createAudioFile(this)

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(currentOutputFile?.absolutePath)

                prepare()
                start()
            }

            isRecording = true
            isPaused = false
            recordingStartTime = System.currentTimeMillis()
            recordingDuration = 0L

            binding.waveformView.recreate()

            startWaveformUpdates()
            startTimer()
            updateUIState()

        } catch (e: Exception) {
            android.util.Log.e("DemoActivity", "Failed to start recording: ${e.message}")
            showErrorDialog("Failed to start recording: ${e.message}")
        }
    }

    private fun togglePauseResume() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                if (isPaused) {
                    // Resume recording
                    mediaRecorder?.resume()
                    isPaused = false
                    recordingStartTime = System.currentTimeMillis() - recordingDuration
                    startWaveformUpdates()
                    startTimer()
                } else {
                    // Pause recording
                    mediaRecorder?.pause()
                    isPaused = true
                    recordingDuration = System.currentTimeMillis() - recordingStartTime
                    stopWaveformUpdates()
                    stopTimer()
                }
                updateUIState()
            } catch (e: Exception) {
                android.util.Log.e("DemoActivity", "Failed to pause/resume: ${e.message}")
            }
        } else {
            showErrorDialog("Pause/Resume requires Android 7.0 or higher")
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            stopWaveformUpdates()
            stopTimer()

            val finalDuration = if (isPaused) {
                recordingDuration
            } else {
                System.currentTimeMillis() - recordingStartTime
            }

            currentOutputFile?.let { file ->
                if (file.exists()) {
                    viewModel.saveRecording(file.absolutePath, finalDuration)

                    com.google.android.material.snackbar.Snackbar.make(
                        binding.root,
                        "Recording saved successfully",
                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                    ).show()
                }
            }

            isRecording = false
            isPaused = false
            recordingDuration = 0L
            binding.waveformView.recreate()
            binding.tvRecordingTime.text = "00:00"
            updateUIState()

        } catch (e: Exception) {
            android.util.Log.e("DemoActivity", "Failed to stop recording: ${e.message}")
            showErrorDialog("Failed to stop recording: ${e.message}")
        }
    }

    private fun startWaveformUpdates() {
        stopWaveformUpdates()

        waveformUpdateRunnable = object : Runnable {
            override fun run() {
                mediaRecorder?.let { recorder ->
                    try {
                        val amplitude = recorder.maxAmplitude
                        binding.waveformView.update(amplitude)
                        handler.postDelayed(this, 50)

                    } catch (e: Exception) {
                        android.util.Log.e("DemoActivity", "Error getting amplitude: ${e.message}")
                    }
                }
            }
        }
        handler.post(waveformUpdateRunnable!!)
    }

    private fun stopWaveformUpdates() {
        waveformUpdateRunnable?.let { handler.removeCallbacks(it) }
        waveformUpdateRunnable = null
    }

    private fun startTimer() {
        stopTimer()

        timerRunnable = object : Runnable {
            override fun run() {
                if (isRecording && !isPaused) {
                    val elapsed = System.currentTimeMillis() - recordingStartTime
                    binding.tvRecordingTime.text = formatTime(elapsed)
                    handler.postDelayed(this, 100)
                }
            }
        }
        handler.post(timerRunnable!!)
    }

    private fun stopTimer() {
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = null
    }

    private fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60))

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun updateUIState() {
        binding.apply {
            btnStartRecording.isEnabled = !isRecording
            btnPauseResume.isEnabled = isRecording
            btnStop.isEnabled = isRecording

            btnPauseResume.text = if (isPaused) "Resume" else "Pause"
            btnPauseResume.setIconResource(
                if (isPaused) R.drawable.ic_play else R.drawable.ic_pause
            )

            tvStatus.text = when {
                isPaused -> "⏸️ Recording Paused"
                isRecording -> "🔴 Recording..."
                else -> "Ready to Record"
            }
        }
    }

    private fun checkPermissionsAndStartRecording() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            startRecording()
        }
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun showPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.permissions_required))
            .setMessage(getString(R.string.audio_permission_required))
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    private fun showErrorDialog(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopWaveformUpdates()
        stopTimer()

        mediaRecorder?.apply {
            try {
                stop()
            } catch (e: Exception) {
                // Ignore if already stopped
            }
            release()
        }
        mediaRecorder = null
    }
}
