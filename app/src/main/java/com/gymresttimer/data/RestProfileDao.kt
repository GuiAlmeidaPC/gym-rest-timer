package com.gymresttimer.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RestProfileDao {
    @Query("SELECT * FROM rest_profiles ORDER BY restDurationSeconds ASC")
    fun observeProfiles(): Flow<List<RestProfile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: RestProfile): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(profiles: List<RestProfile>)

    @Update
    suspend fun update(profile: RestProfile)

    @Delete
    suspend fun delete(profile: RestProfile)
}
