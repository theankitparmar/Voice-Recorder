// ui/adapter/RecordingsAdapter.kt
package com.quick.voice.recorder.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.quick.voice.recorder.data.database.Recording
import com.quick.voice.recorder.databinding.ItemRecordingBinding
import com.quick.voice.recorder.utils.formatDuration
import java.text.SimpleDateFormat
import java.util.*

class RecordingsAdapter(
    private val onItemClick: (Recording) -> Unit
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
                tvDate.text = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
                    .format(Date(recording.createdDate))
                
                root.setOnClickListener { onItemClick(recording) }
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
