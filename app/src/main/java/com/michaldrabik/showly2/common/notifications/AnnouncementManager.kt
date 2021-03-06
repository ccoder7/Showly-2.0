package com.michaldrabik.showly2.common.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.michaldrabik.showly2.Config.TVDB_IMAGE_BASE_BANNERS_URL
import com.michaldrabik.showly2.R
import com.michaldrabik.showly2.common.images.ShowImagesProvider
import com.michaldrabik.showly2.common.notifications.AnnouncementWorker.Companion.DATA_CHANNEL
import com.michaldrabik.showly2.common.notifications.AnnouncementWorker.Companion.DATA_CONTENT
import com.michaldrabik.showly2.common.notifications.AnnouncementWorker.Companion.DATA_IMAGE_URL
import com.michaldrabik.showly2.common.notifications.AnnouncementWorker.Companion.DATA_TITLE
import com.michaldrabik.showly2.di.scope.AppScope
import com.michaldrabik.showly2.fcm.NotificationChannel
import com.michaldrabik.showly2.model.Image.Status.AVAILABLE
import com.michaldrabik.showly2.model.ImageType.FANART
import com.michaldrabik.showly2.model.ImageType.POSTER
import com.michaldrabik.showly2.model.NotificationDelay
import com.michaldrabik.showly2.model.mappers.Mappers
import com.michaldrabik.showly2.repository.settings.SettingsRepository
import com.michaldrabik.showly2.utilities.extensions.dateFromMillis
import com.michaldrabik.showly2.utilities.extensions.nowUtcMillis
import com.michaldrabik.showly2.utilities.extensions.toDisplayString
import com.michaldrabik.showly2.utilities.extensions.toMillis
import com.michaldrabik.storage.database.AppDatabase
import com.michaldrabik.storage.database.model.Episode
import com.michaldrabik.storage.database.model.Show
import timber.log.Timber
import java.util.concurrent.TimeUnit.MILLISECONDS
import javax.inject.Inject

@AppScope
class AnnouncementManager @Inject constructor(
  private val database: AppDatabase,
  private val settingsRepository: SettingsRepository,
  private val imagesProvider: ShowImagesProvider,
  private val mappers: Mappers
) {

  companion object {
    private const val ANNOUNCEMENT_WORK_TAG = "ANNOUNCEMENT_WORK_TAG"
    private const val ANNOUNCEMENT_STATIC_DELAY = 60000 // 1 min
  }

  suspend fun refreshEpisodesAnnouncements(context: Context) {
    Timber.i("Refreshing announcements")

    WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(ANNOUNCEMENT_WORK_TAG)

    val settings = settingsRepository.load()
    if (!settings.episodesNotificationsEnabled) {
      Timber.i("Episodes announcements are disabled. Exiting...")
      return
    }

    val myShows = database.myShowsDao().getAll()
    if (myShows.isEmpty()) {
      Timber.i("Nothing to process. Exiting...")
      return
    }

    val now = nowUtcMillis()
    val delay = settings.episodesNotificationsDelay
    myShows.forEach { show ->
      Timber.i("Processing ${show.title} (${show.idTrakt})")
      val episodes = database.episodesDao().getAllForShows(listOf(show.idTrakt))
      episodes
        .filter { it.firstAired != null && it.firstAired!!.toMillis() > now }
        .minBy { it.firstAired!!.toMillis() }
        ?.let {
          Timber.i("Next episode for ${show.title} (${show.idTrakt}) found. Setting notification...")
          scheduleAnnouncement(context.applicationContext, show, it, delay)
        }
    }
  }

  private suspend fun scheduleAnnouncement(
    context: Context,
    showDb: Show,
    episodeDb: Episode,
    delay: NotificationDelay
  ) {
    val show = mappers.show.fromDatabase(showDb)

    val data = Data.Builder().apply {
      putString(DATA_CHANNEL, NotificationChannel.EPISODES_ANNOUNCEMENTS.name)
      putString(DATA_TITLE, "${show.title} - Season ${episodeDb.seasonNumber}")

      val stringResId = when (episodeDb.episodeNumber) {
        1 -> if (delay.isBefore()) R.string.textNewSeasonAvailableSoon else R.string.textNewSeasonAvailable
        else -> if (delay.isBefore()) R.string.textNewEpisodeAvailableSoon else R.string.textNewEpisodeAvailable
      }
      putString(DATA_CONTENT, context.getString(stringResId))

      val posterImage = imagesProvider.findCachedImage(show, POSTER)
      if (posterImage.status == AVAILABLE) {
        putString(DATA_IMAGE_URL, "${TVDB_IMAGE_BASE_BANNERS_URL}${posterImage.fileUrl}")
      } else {
        val fanartImage = imagesProvider.findCachedImage(show, FANART)
        if (fanartImage.status == AVAILABLE) {
          putString(DATA_IMAGE_URL, "${TVDB_IMAGE_BASE_BANNERS_URL}${fanartImage.fileUrl}")
        }
      }
    }

    val delayed = (episodeDb.firstAired!!.toMillis() - nowUtcMillis()) + delay.delayMs + ANNOUNCEMENT_STATIC_DELAY
    val request = OneTimeWorkRequestBuilder<AnnouncementWorker>()
      .setInputData(data.build())
      .setInitialDelay(delayed, MILLISECONDS)
      .addTag(ANNOUNCEMENT_WORK_TAG)
      .build()

    WorkManager.getInstance(context.applicationContext).enqueue(request)

    val logTime = dateFromMillis(nowUtcMillis() + delayed)
    Timber.i("Notification set for: ${logTime.toDisplayString()} UTC")
  }
}
