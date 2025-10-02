// data/database/Recording.kt
package com.quick.voice.recorder.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "recordings")
data class Recording(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val filePath: String,
    val duration: Long, // in milliseconds
    val createdDate: Long,
    val fileSize: Long
) : Parcelable
