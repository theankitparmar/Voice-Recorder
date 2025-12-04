package com.quick.voice.recorder.demo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.quick.voice.recorder.R
import java.io.File

class DummyActivity : AppCompatActivity() {

    private lateinit var waveformView: WaveformView
    private lateinit var btnRecord: Button
    private lateinit var btnLoadAudio: Button
    private lateinit var seekBarHeight: SeekBar

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRate = 60L
    private var recordedFilePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dummy)

        waveformView = findViewById(R.id.waveformView)
        btnRecord = findViewById(R.id.btnRecord)
        btnLoadAudio = findViewById(R.id.btnLoadAudio)
        seekBarHeight = findViewById(R.id.seekBarHeight)

        // Setup gradient colors
        waveformView.setGradientColors(
            Color.parseColor("#FF6B6B"),
            Color.parseColor("#4ECDC4")
        )
        waveformView.setGlowEffect(true)

        btnRecord.setOnClickListener {
            if (checkPermissions()) {
                if (isRecording) {
                    stopRecording()
                } else {
                    startRecording()
                }
            }
        }

        btnLoadAudio.setOnClickListener {
            recordedFilePath?.let { path ->
                loadAudioFile(path)
            } ?: run {
                Toast.makeText(this, "No recorded file available", Toast.LENGTH_SHORT).show()
            }
        }

        // Height adjustment control
        seekBarHeight.max = 150
        seekBarHeight.progress = 100
        seekBarHeight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val multiplier = progress / 100f
                waveformView.setWaveHeightMultiplier(multiplier)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun checkPermissions(): Boolean {
        return if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_CODE
            )
            false
        } else {
            true
        }
    }

    private fun startRecording() {
        try {
            waveformView.recreate() // Clear any previous audio file data

            val audioFile = File(filesDir, "recording_${System.currentTimeMillis()}.mp3")
            recordedFilePath = audioFile.absolutePath

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(320000)
                setAudioSamplingRate(48000)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            btnRecord.text = getString(R.string.stop_recording)
            btnLoadAudio.isEnabled = false
            animateWaveform()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to start recording", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            btnRecord.text = getString(R.string.start_recording)
            btnLoadAudio.isEnabled = true
            handler.removeCallbacksAndMessages(null)

            Toast.makeText(this, "Recording saved. Tap 'Load Audio' to view waveform", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun animateWaveform() {
        if (isRecording && mediaRecorder != null) {
            try {
                val amplitude = mediaRecorder?.maxAmplitude ?: 0
                waveformView.update(amplitude)

                handler.postDelayed({ animateWaveform() }, refreshRate)
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }
        }
    }

    private fun loadAudioFile(filePath: String) {
        waveformView.clearAudioFile()

        Toast.makeText(this, "Loading waveform...", Toast.LENGTH_SHORT).show()

        waveformView.loadAudioFile(filePath) { success ->
            if (success) {
                Toast.makeText(this, "Waveform loaded successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to load waveform", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) {
            stopRecording()
        }
    }

    companion object {
        private const val PERMISSION_CODE = 200
    }
}

