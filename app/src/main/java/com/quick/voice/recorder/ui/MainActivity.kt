package com.quick.voice.recorder.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.quick.voice.recorder.R
import com.quick.voice.recorder.VoiceRecorderApplication
import com.quick.voice.recorder.databinding.ActivityMainBinding
import com.quick.voice.recorder.service.RecordingService
import com.quick.voice.recorder.utils.FileUtils
import com.quick.voice.recorder.utils.formatDuration
import com.quick.voice.recorder.viewmodel.MainViewModel
import com.quick.voice.recorder.viewmodel.MainViewModelFactory

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory((application as VoiceRecorderApplication).repository)
    }

    private var recordingService: RecordingService? = null
    private var isServiceBound = false
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RecordingService.RecordingBinder
            recordingService = binder.getService()
            isServiceBound = true
            updateUIState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            recordingService = null
            isServiceBound = false
        }
    }

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

    // Call this in onCreate after setupUI()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        requestAllPermissions() // Add this line
    }

    private fun setupUI() {
        binding.apply {
            btnStartRecording.setOnClickListener {
                if (recordingService?.isCurrentlyRecording() == true) {
                    // Recording is active, show recording controls
                    return@setOnClickListener
                }
                checkPermissionsAndStartRecording()
            }

            btnPauseResume.setOnClickListener {
                togglePauseResume()
            }

            btnDiscard.setOnClickListener {
                discardRecording()
            }

            btnSave.setOnClickListener {
                saveRecording()
            }

            btnViewRecordings.setOnClickListener {
                startActivity(Intent(this@MainActivity, RecordingsActivity::class.java))
            }
        }

        updateUIState()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }
    }

    // Update the checkPermissionsAndStartRecording method in MainActivity.kt
    private fun checkPermissionsAndStartRecording() {
        val permissions = mutableListOf<String>()

        // Check microphone permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        // Check storage permission for older Android versions
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        // Check foreground service microphone permission for Android 14+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
            }
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            startRecording()
        }
    }

    // Add this to MainActivity.kt
    private fun requestAllPermissions() {
        val permissions = mutableListOf<String>()

        // Always needed permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        // Storage permission for older versions
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        // Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Foreground service microphone permission for Android 14+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
            }
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun startRecording() {
        val intent = Intent(this, RecordingService::class.java)

        // Use appropriate start method depending on API level
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        bindService(intent, serviceConnection, BIND_AUTO_CREATE)

        // Wait a moment for service to bind, then start recording
        handler.postDelayed({
            val outputFile = FileUtils.createAudioFile(this)
            if (recordingService?.startRecording(outputFile.absolutePath) == true) {
                startTimer()
                updateUIState()
            }
        }, 100)
    }


    private fun togglePauseResume() {
        recordingService?.let { service ->
            if (service.isCurrentlyPaused()) {
                service.resumeRecording()
            } else {
                service.pauseRecording()
            }
            updateUIState()
        }
    }

    private fun discardRecording() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Discard Recording")
            .setMessage("Are you sure you want to discard this recording?")
            .setPositiveButton("Discard") { _, _ ->
                recordingService?.discardRecording()
                stopTimer()
                unbindServiceIfBound()
                updateUIState()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveRecording() {
        recordingService?.let { service ->
            val filePath = service.stopRecording()
            if (filePath != null) {
                viewModel.saveRecording(filePath, service.getCurrentDuration())
                stopTimer()
                unbindServiceIfBound()
                updateUIState()
            }
        }
    }

    private fun startTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                recordingService?.let { service ->
                    val duration = service.getCurrentDuration()
                    binding.tvTimer.text = formatDuration(duration)
                    handler.postDelayed(this, 100)
                }
            }
        }
        handler.post(timerRunnable!!)
    }

    private fun stopTimer() {
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = null
        binding.tvTimer.text = "00:00"
    }

    private fun updateUIState() {
        val isRecording = recordingService?.isCurrentlyRecording() ?: false
        val isPaused = recordingService?.isCurrentlyPaused() ?: false

        binding.apply {
            // Show/hide recording controls
            layoutRecordingControls.visibility = if (isRecording)
                android.view.View.VISIBLE else android.view.View.GONE
            btnStartRecording.visibility = if (isRecording)
                android.view.View.GONE else android.view.View.VISIBLE

            // Update pause/resume button
            btnPauseResume.text = if (isPaused) "Resume" else "Pause"
            btnPauseResume.setIconResource(
                if (isPaused) R.drawable.ic_play else R.drawable.ic_pause
            )
        }
    }


    private fun unbindServiceIfBound() {
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }

    private fun showPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permissions Required")
            .setMessage("Audio recording permission is required to record voice notes.")
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
        unbindServiceIfBound()
    }
}