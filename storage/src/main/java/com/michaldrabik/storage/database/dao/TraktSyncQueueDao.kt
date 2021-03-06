package com.michaldrabik.storage.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.michaldrabik.storage.database.model.TraktSyncQueue

@Dao
interface TraktSyncQueueDao : BaseDao<TraktSyncQueue> {

  @Query("SELECT * FROM trakt_sync_queue ORDER BY created_at ASC")
  suspend fun getAll(): List<TraktSyncQueue>

  @Query("DELETE FROM trakt_sync_queue WHERE id_trakt IN (:idsTrakt)")
  suspend fun deleteAll(idsTrakt: List<Long>)
}
