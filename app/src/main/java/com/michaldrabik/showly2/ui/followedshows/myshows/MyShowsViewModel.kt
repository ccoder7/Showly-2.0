package com.michaldrabik.showly2.ui.followedshows.myshows

import androidx.lifecycle.viewModelScope
import com.michaldrabik.showly2.model.Image
import com.michaldrabik.showly2.model.ImageType
import com.michaldrabik.showly2.model.ImageType.POSTER
import com.michaldrabik.showly2.model.MyShowsSection
import com.michaldrabik.showly2.model.MyShowsSection.ALL
import com.michaldrabik.showly2.model.MyShowsSection.FINISHED
import com.michaldrabik.showly2.model.MyShowsSection.RECENTS
import com.michaldrabik.showly2.model.MyShowsSection.UPCOMING
import com.michaldrabik.showly2.model.MyShowsSection.WATCHING
import com.michaldrabik.showly2.model.Show
import com.michaldrabik.showly2.model.SortOrder
import com.michaldrabik.showly2.ui.common.base.BaseViewModel
import com.michaldrabik.showly2.ui.followedshows.myshows.recycler.MyShowsItem
import com.michaldrabik.showly2.ui.followedshows.myshows.recycler.MyShowsItem.Type.ALL_SHOWS_ITEM
import com.michaldrabik.showly2.ui.followedshows.myshows.recycler.MyShowsItem.Type.RECENT_SHOWS
import com.michaldrabik.showly2.utilities.extensions.findReplace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import javax.inject.Inject

class MyShowsViewModel @Inject constructor(
  private val interactor: MyShowsInteractor
) : BaseViewModel<MyShowsUiModel>() {

  fun loadShows(notifyListsUpdate: Boolean = false) {
    viewModelScope.launch {
      val settings = interactor.loadSettings()
      val shows = interactor.loadAllShows().map { toListItemAsync(ALL_SHOWS_ITEM, it) }.awaitAll()
      val seasons = interactor.loadSeasonsForShows(shows.map { it.show.traktId })

      val allShows = interactor.filterSectionShows(shows, seasons, ALL)

      val runningShows =
        if (settings.myShowsRunningIsEnabled) interactor.filterSectionShows(shows, seasons, WATCHING)
        else emptyList()

      val endedShows =
        if (settings.myShowsEndedIsEnabled) interactor.filterSectionShows(shows, seasons, FINISHED)
        else emptyList()

      val incomingShows =
        if (settings.myShowsIncomingIsEnabled) interactor.filterSectionShows(shows, seasons, UPCOMING)
        else emptyList()

      val recentShows = if (settings.myShowsRecentIsEnabled) {
        interactor.loadRecentShows().map { toListItemAsync(RECENT_SHOWS, it, ImageType.FANART) }.awaitAll()
      } else {
        emptyList()
      }

      val listItems = mutableListOf<MyShowsItem>()
      listItems.run {
        if (recentShows.isNotEmpty()) {
          add(MyShowsItem.createHeader(RECENTS, recentShows.count(), null))
          add(MyShowsItem.createRecentsSection(recentShows))
        }
        if (runningShows.isNotEmpty()) {
          add(MyShowsItem.createHeader(WATCHING, runningShows.count(), interactor.loadSortOrder(WATCHING)))
          add(MyShowsItem.createHorizontalSection(WATCHING, runningShows))
        }
        if (incomingShows.isNotEmpty()) {
          add(MyShowsItem.createHeader(UPCOMING, incomingShows.count(), interactor.loadSortOrder(UPCOMING)))
          add(MyShowsItem.createHorizontalSection(UPCOMING, incomingShows))
        }
        if (endedShows.isNotEmpty()) {
          add(MyShowsItem.createHeader(FINISHED, endedShows.count(), interactor.loadSortOrder(FINISHED)))
          add(MyShowsItem.createHorizontalSection(FINISHED, endedShows))
        }
        if (allShows.isNotEmpty()) {
          add(MyShowsItem.createHeader(ALL, allShows.count(), interactor.loadSortOrder(ALL)))
          addAll(allShows)
        }
      }

      uiState = MyShowsUiModel(listItems = listItems, notifyListsUpdate = notifyListsUpdate)
    }
  }

  fun loadSortedSection(section: MyShowsSection, order: SortOrder) {
    viewModelScope.launch {
      interactor.setSectionSortOrder(section, order)
      loadShows(notifyListsUpdate = true)
    }
  }

  fun loadMissingImage(item: MyShowsItem, force: Boolean) {

    fun updateItem(new: MyShowsItem) {
      val items = uiState?.listItems?.toMutableList() ?: mutableListOf()
      items.findReplace(new) { it.isSameAs(new) }
      uiState = uiState?.copy(listItems = items)
    }

    viewModelScope.launch {
      updateItem(item.copy(isLoading = true))
      try {
        val image = interactor.loadMissingImage(item.show, item.image.type, force)
        updateItem(item.copy(isLoading = false, image = image))
      } catch (t: Throwable) {
        updateItem(item.copy(isLoading = false, image = Image.createUnavailable(item.image.type)))
      }
    }
  }

  fun loadSectionMissingItem(item: MyShowsItem, itemSection: MyShowsItem.HorizontalSection, force: Boolean) {

    fun updateItem(newItem: MyShowsItem, newSection: MyShowsItem.HorizontalSection) {
      val items = uiState?.listItems?.toMutableList() ?: mutableListOf()
      val section = items.find { it.horizontalSection?.section == newSection.section }?.horizontalSection

      val sectionItems = section?.items?.toMutableList() ?: mutableListOf()
      sectionItems.findReplace(newItem) { it.isSameAs(newItem) }

      val newSecWithItems = section?.copy(items = sectionItems)
      items.findReplace(newItem.copy(horizontalSection = newSecWithItems)) { it.horizontalSection?.section == newSection.section }

      uiState = uiState?.copy(listItems = items)
    }

    viewModelScope.launch {
      updateItem(item.copy(isLoading = true), itemSection)
      try {
        val image = interactor.loadMissingImage(item.show, item.image.type, force)
        updateItem(item.copy(isLoading = false, image = image), itemSection)
      } catch (t: Throwable) {
        updateItem(item.copy(isLoading = false, image = Image.createUnavailable(item.image.type)), itemSection)
      }
    }
  }

  private fun CoroutineScope.toListItemAsync(
    itemType: MyShowsItem.Type,
    show: Show,
    type: ImageType = POSTER
  ) = async {
    val image = interactor.findCachedImage(show, type)
    MyShowsItem(itemType, null, null, null, show, image, false)
  }
}
