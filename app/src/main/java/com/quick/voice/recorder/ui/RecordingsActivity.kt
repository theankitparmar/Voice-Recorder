// ui/RecordingsActivity.kt
package com.quick.voice.recorder.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.quick.voice.recorder.VoiceRecorderApplication
import com.quick.voice.recorder.databinding.ActivityRecordingsBinding
import com.quick.voice.recorder.ui.adapter.RecordingsAdapter
import com.quick.voice.recorder.viewmodel.RecordingsViewModel
import com.quick.voice.recorder.viewmodel.RecordingsViewModelFactory

class RecordingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordingsBinding
    private lateinit var adapter: RecordingsAdapter

    private val viewModel: RecordingsViewModel by viewModels {
        RecordingsViewModelFactory((application as VoiceRecorderApplication).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeRecordings()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Recordings"

        adapter = RecordingsAdapter { recording ->
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra("recording", recording)
            }
            startActivity(intent)
        }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@RecordingsActivity)
            adapter = this@RecordingsActivity.adapter
        }
    }

    private fun observeRecordings() {
        viewModel.recordings.observe(this) { recordings ->
            adapter.submitList(recordings)
            binding.tvEmptyState.visibility = if (recordings.isEmpty())
                View.VISIBLE else View.GONE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
