package com.quick.voice.recorder.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.quick.voice.recorder.R
import com.quick.voice.recorder.VoiceRecorderApplication
import com.quick.voice.recorder.databinding.ActivityRecordingsBinding
import com.quick.voice.recorder.ui.adapter.RecordingsAdapter
import com.quick.voice.recorder.viewmodel.RecordingsViewModel
import com.quick.voice.recorder.viewmodel.RecordingsViewModelFactory
import kotlinx.coroutines.launch

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
        setupSwipeToRefresh()
    }

    private fun setupUI() {
        // Uncomment these if you have a toolbar in your layout
        // setSupportActionBar(binding.toolbar)
        // supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // supportActionBar?.title = getString(R.string.recordings)

        adapter = RecordingsAdapter(
            onItemClick = { recording ->
                startActivity(PlayerActivity.createIntent(this, recording))
            },
            onItemLongClick = { recording ->
                showRecordingOptions(recording)
                true
            }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@RecordingsActivity)
            adapter = this@RecordingsActivity.adapter
            addItemDecoration(
                DividerItemDecoration(
                    this@RecordingsActivity,
                    LinearLayoutManager.VERTICAL
                )
            )

            // Add scroll listener to hide/show FAB
            addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dy > 0 && binding.fabDeleteAll.isShown) {
                        binding.fabDeleteAll.hide()
                    } else if (dy < 0 && !binding.fabDeleteAll.isShown) {
                        binding.fabDeleteAll.show()
                    }
                }
            })
        }

        binding.fabDeleteAll.setOnClickListener {
            showDeleteAllConfirmation()
        }
    }

    private fun observeRecordings() {
        // Observe recordings using StateFlow
        lifecycleScope.launch {
            viewModel.recordings.collect { recordings ->
                adapter.submitList(recordings)
                updateEmptyState(recordings.isEmpty())
                updateDeleteAllButton(recordings.isNotEmpty())
            }
        }

        // Observe loading state
        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.swipeRefresh.isRefreshing = isLoading
            }
        }

        // Observe error messages
        lifecycleScope.launch {
            viewModel.errorMessage.collect { errorMessage ->
                errorMessage?.let {
                    com.google.android.material.snackbar.Snackbar.make(
                        binding.root,
                        it,
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    ).show()
                    // Clear error after showing
                    viewModel.clearErrorMessage()
                }
            }
        }
    }

    private fun setupSwipeToRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshRecordings()
        }

        binding.swipeRefresh.setColorSchemeResources(
            R.color.colorPrimary,
            R.color.colorAccent,
            R.color.colorPrimaryDark
        )
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.apply {
            tvEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
            recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
            fabDeleteAll.visibility = if (isEmpty) View.GONE else View.VISIBLE

            if (isEmpty) {
                tvEmptyState.text = getString(R.string.no_recordings_found)
            }
        }
    }

    private fun updateDeleteAllButton(hasRecordings: Boolean) {
        if (hasRecordings) {
            binding.fabDeleteAll.show()
        } else {
            binding.fabDeleteAll.hide()
        }
    }

    private fun showRecordingOptions(recording: com.quick.voice.recorder.data.database.Recording) {
        val options = arrayOf(
            getString(R.string.play),
            getString(R.string.share),
            getString(R.string.rename),
            getString(R.string.delete)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(recording.fileName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startActivity(PlayerActivity.createIntent(this, recording))
                    1 -> shareRecording(recording)
                    2 -> renameRecording(recording)
                    3 -> deleteRecording(recording)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun shareRecording(recording: com.quick.voice.recorder.data.database.Recording) {
        try {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(
                    this@RecordingsActivity,
                    "${packageName}.provider",
                    java.io.File(recording.filePath)
                ))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_recording)))
        } catch (e: Exception) {
            com.google.android.material.snackbar.Snackbar.make(
                binding.root,
                getString(R.string.share_error),
                com.google.android.material.snackbar.Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun renameRecording(recording: com.quick.voice.recorder.data.database.Recording) {
        val input = android.widget.EditText(this).apply {
            setText(recording.fileName.replace(".m4a", "").replace(".mp3", "").replace(".wav", ""))
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.rename_recording))
            .setView(input)
            .setPositiveButton(getString(R.string.rename)) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    viewModel.renameRecording(recording, newName)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun deleteRecording(recording: com.quick.voice.recorder.data.database.Recording) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_recording))
            .setMessage(getString(R.string.delete_recording_confirmation, recording.fileName))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewModel.deleteRecording(recording)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showDeleteAllConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_all_recordings))
            .setMessage(getString(R.string.delete_all_recordings_confirmation))
            .setPositiveButton(getString(R.string.delete_all)) { _, _ ->
                viewModel.deleteAllRecordings()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}