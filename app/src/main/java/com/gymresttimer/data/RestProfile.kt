package com.gymresttimer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rest_profiles")
data class RestProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val profileName: String,
    val restDurationSeconds: Int,
)
