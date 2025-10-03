package com.quick.voice.recorder.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.quick.voice.recorder.R
import com.quick.voice.recorder.data.database.Recording
import com.quick.voice.recorder.databinding.ItemRecordingBinding
import com.quick.voice.recorder.utils.FileUtils.formatDuration
import java.text.SimpleDateFormat
import java.util.*

class RecordingsAdapter(
    private val onItemClick: (Recording) -> Unit,
    private val onItemLongClick: (Recording) -> Unit = { _ -> }
) : ListAdapter<Recording, RecordingsAdapter.RecordingViewHolder>(RecordingDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordingViewHolder {
        val binding = ItemRecordingBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RecordingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecordingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RecordingViewHolder(
        private val binding: ItemRecordingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(recording: Recording) {
            binding.apply {
                tvFileName.text = recording.fileName
                tvDuration.text = formatDuration(recording.duration)
                tvFileSize.text = recording.fileSize.toString()
                tvDate.text = recording.formattedCreatedDate

                // Set appropriate icon based on file type
                val iconRes = when {
                        recording.fileName.endsWith(".m4a") -> R.drawable.ic_audio_m4a
                        recording.fileName.endsWith(".mp3") -> R.drawable.ic_audio_mp3
                        recording.fileName.endsWith(".wav") -> R.drawable.ic_audio_wav
                    else -> R.drawable.ic_audio_file
                }
                ivFileIcon.setImageResource(iconRes)

                // Set background based on position for better visual separation
                val backgroundColor = if (adapterPosition % 2 == 0) {
                    ContextCompat.getColor(root.context, R.color.item_background_even)
                } else {
                    ContextCompat.getColor(root.context, R.color.item_background_odd)
                }
                root.setBackgroundColor(backgroundColor)

                root.setOnClickListener {
                    onItemClick(recording)
                }

                root.setOnLongClickListener {
                    onItemLongClick(recording)
                    true
                }

                // Add ripple effect
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    root.foreground = ContextCompat.getDrawable(
                        root.context,
                        R.drawable.ripple_effect
                    )
                }
            }
        }
    }

    class RecordingDiffCallback : DiffUtil.ItemCallback<Recording>() {
        override fun areItemsTheSame(oldItem: Recording, newItem: Recording): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Recording, newItem: Recording): Boolean {
            return oldItem == newItem
        }
    }
}

// Extension property for formatted date
val Recording.formattedCreatedDate: String
    get() = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
        .format(Date(createdDate))