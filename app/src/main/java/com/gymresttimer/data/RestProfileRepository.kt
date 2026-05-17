package com.gymresttimer.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface RestProfileRepository {
    fun observeProfiles(): Flow<List<RestProfile>>
    suspend fun insert(profile: RestProfile)
    suspend fun update(profile: RestProfile)
    suspend fun delete(profile: RestProfile)
}

@Singleton
class RoomRestProfileRepository @Inject constructor(
    private val dao: RestProfileDao,
) : RestProfileRepository {
    override fun observeProfiles(): Flow<List<RestProfile>> = dao.observeProfiles()
    override suspend fun insert(profile: RestProfile) { dao.insert(profile) }
    override suspend fun update(profile: RestProfile) { dao.update(profile) }
    override suspend fun delete(profile: RestProfile) { dao.delete(profile) }
}
