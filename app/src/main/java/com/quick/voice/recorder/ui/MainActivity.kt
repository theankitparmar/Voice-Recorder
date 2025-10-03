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
import com.quick.voice.recorder.utils.FileUtils.formatDuration
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
    private var isRecording = false
    private var isPaused = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RecordingService.RecordingBinder
            recordingService = binder.getService()
            isServiceBound = true

            // Update UI based on current service state
            updateUIFromServiceState()
            if (recordingService?.isCurrentlyRecording() == true) {
                startTimer()
            }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        bindRecordingService()
        requestAllPermissions()
    }

    private fun setupUI() {
        binding.apply {
            btnStartRecording.setOnClickListener {
                if (isRecording) {
                    // If already recording, show controls
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

    private fun bindRecordingService() {
        val intent = Intent(this, RecordingService::class.java)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    private fun updateUIFromServiceState() {
        recordingService?.let { service ->
            isRecording = service.isCurrentlyRecording()
            isPaused = service.isCurrentlyPaused()

            if (isRecording && timerRunnable == null) {
                startTimer()
            }
            updateUIState()
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
        val outputFile = FileUtils.createAudioFile(this)
        val intent = Intent(this, RecordingService::class.java).apply {
            putExtra(RecordingService.EXTRA_OUTPUT_FILE, outputFile.absolutePath)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        // Wait a moment for service to start, then update UI
        handler.postDelayed({
            updateUIFromServiceState()
        }, 100)
    }

    private fun togglePauseResume() {
        recordingService?.let { service ->
            if (service.isCurrentlyPaused()) {
                service.resumeRecording()
                isPaused = false
            } else {
                service.pauseRecording()
                isPaused = true
            }
            updateUIState()
        }
    }

    private fun discardRecording() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.discard_recording))
            .setMessage(getString(R.string.discard_confirmation))
            .setPositiveButton(getString(R.string.discard)) { _, _ ->
                recordingService?.discardRecording()
                stopTimer()
                isRecording = false
                isPaused = false
                updateUIState()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun saveRecording() {
        recordingService?.let { service ->
            val filePath = service.stopRecording()
            if (filePath != null) {
                viewModel.saveRecording(filePath, service.getCurrentDuration())
                stopTimer()
                isRecording = false
                isPaused = false
                updateUIState()

                // Show success message
                com.google.android.material.snackbar.Snackbar.make(
                    binding.root,
                    getString(R.string.recording_saved),
                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun startTimer() {
        stopTimer() // Ensure no existing timer
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
        binding.apply {
            // Show/hide recording controls
            layoutRecordingControls.visibility = if (isRecording) View.VISIBLE else View.GONE
            btnStartRecording.visibility = if (isRecording) View.GONE else View.VISIBLE

            // Update pause/resume button
            val pauseResumeText = if (isPaused) getString(R.string.resume) else getString(R.string.pause)
            val pauseResumeIcon = if (isPaused) R.drawable.ic_play else R.drawable.ic_pause

            btnPauseResume.text = pauseResumeText
            btnPauseResume.setIconResource(pauseResumeIcon)

            // Update recording status text
            val statusText = when {
                isPaused -> getString(R.string.recording_paused)
                isRecording -> getString(R.string.recording_in_progress)
                else -> getString(R.string.ready_to_record)
            }
            tvRecordingStatus.text = statusText
        }
    }

    private fun showPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.permissions_required))
            .setMessage(getString(R.string.audio_permission_required))
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Update UI state when returning to the app
        updateUIFromServiceState()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }
}