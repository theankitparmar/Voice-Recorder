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
    private lateinit var seekBarHeight: SeekBar

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRate = 60L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dummy)

        waveformView = findViewById(R.id.waveformView)
        btnRecord = findViewById(R.id.btnRecord)
        seekBarHeight = findViewById(R.id.seekBarHeight)

        // Setup gradient colors
        waveformView.setGradientColors(
            Color.parseColor("#FD1D64"),
            Color.parseColor("#F5BA62")
        )
//        waveformView.setGlowEffect(true)

        btnRecord.setOnClickListener {
            if (checkPermissions()) {
                if (isRecording) {
                    stopRecording()
                } else {
                    startRecording()
                }
            }
        }

        // Height adjustment control
        seekBarHeight.max = 200
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
            val audioFile = File(cacheDir, "demo_audio.mp3")

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            btnRecord.text = getString(R.string.stop_recording)
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
            handler.removeCallbacksAndMessages(null)
            waveformView.recreate()

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

