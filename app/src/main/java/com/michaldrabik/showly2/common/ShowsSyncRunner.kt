package com.michaldrabik.showly2.common

import com.michaldrabik.network.Cloud
import com.michaldrabik.showly2.Config
import com.michaldrabik.showly2.di.scope.AppScope
import com.michaldrabik.showly2.model.ShowStatus.CANCELED
import com.michaldrabik.showly2.model.ShowStatus.ENDED
import com.michaldrabik.showly2.model.mappers.Mappers
import com.michaldrabik.showly2.repository.shows.ShowsRepository
import com.michaldrabik.showly2.ui.show.seasons.episodes.EpisodesManager
import com.michaldrabik.showly2.utilities.extensions.nowUtcMillis
import com.michaldrabik.storage.database.AppDatabase
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject

/**
 * This class is responsible for fetching and syncing missing/updated episodes data for current watchlist items.
 */
@AppScope
class ShowsSyncRunner @Inject constructor(
  private val cloud: Cloud,
  private val database: AppDatabase,
  private val mappers: Mappers,
  private val episodesManager: EpisodesManager,
  private val showsRepository: ShowsRepository
) {

  companion object {
    private const val DELAY_MS = 250L
  }

  suspend fun run(): Int {
    Timber.i("Sync initialized.")

    val showsToSync = showsRepository.myShows.loadAll()
      .filter { it.status !in arrayOf(ENDED, CANCELED) }

    if (showsToSync.isEmpty()) {
      Timber.i("Nothing to process. Exiting...")
      return 0
    }
    Timber.i("Shows to sync: ${showsToSync.size}.")

    var syncCount = 0
    val syncLog = database.episodesSyncLogDao().getAll()
    showsToSync.forEach { show ->

      val lastSync = syncLog.find { it.idTrakt == show.ids.trakt.id }?.syncedAt ?: 0
      if (nowUtcMillis() - lastSync < Config.SHOW_SYNC_COOLDOWN) {
        Timber.i("${show.title} is on cooldown. No need to sync.")
        return@forEach
      }

      try {
        Timber.i("Syncing ${show.title}(${show.ids.trakt}) show...")
        showsRepository.detailsShow.load(show.ids.trakt, force = true)
        syncCount++

        Timber.i("${show.title}(${show.ids.trakt}) show synced.")
      } catch (t: Throwable) {
        Timber.e("${show.title}(${show.ids.trakt}) show sync error. Skipping... \n$t")
      }

      try {
        Timber.i("Syncing ${show.title}(${show.ids.trakt}) episodes...")

        val remoteEpisodes = cloud.traktApi.fetchSeasons(show.ids.trakt.id)
          .map { mappers.season.fromNetwork(it) }
        episodesManager.invalidateEpisodes(show, remoteEpisodes)
        syncCount++

        Timber.i("${show.title}(${show.ids.trakt}) episodes synced.")
      } catch (t: Throwable) {
        Timber.e("${show.title}(${show.ids.trakt}) episodes sync error. Skipping... \n$t")
      } finally {
        delay(DELAY_MS)
      }
    }

    return syncCount
  }
}
