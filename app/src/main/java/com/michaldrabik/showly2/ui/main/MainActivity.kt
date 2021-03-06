package com.michaldrabik.showly2.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import androidx.activity.addCallback
import androidx.annotation.IdRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.michaldrabik.showly2.R
import com.michaldrabik.showly2.appComponent
import com.michaldrabik.showly2.common.events.Event
import com.michaldrabik.showly2.common.events.EventObserver
import com.michaldrabik.showly2.common.events.ShowsSyncComplete
import com.michaldrabik.showly2.common.events.TraktQuickSyncSuccess
import com.michaldrabik.showly2.common.events.TraktSyncProgress
import com.michaldrabik.showly2.di.DaggerViewModelFactory
import com.michaldrabik.showly2.di.component.FragmentComponent
import com.michaldrabik.showly2.model.Tip
import com.michaldrabik.showly2.model.Tip.MENU_DISCOVER
import com.michaldrabik.showly2.model.Tip.MENU_MY_SHOWS
import com.michaldrabik.showly2.ui.NotificationActivity
import com.michaldrabik.showly2.ui.common.OnEpisodesSyncedListener
import com.michaldrabik.showly2.ui.common.OnTabReselectedListener
import com.michaldrabik.showly2.ui.common.OnTraktSyncListener
import com.michaldrabik.showly2.ui.common.views.WhatsNewView
import com.michaldrabik.showly2.utilities.extensions.dimenToPx
import com.michaldrabik.showly2.utilities.extensions.fadeOut
import com.michaldrabik.showly2.utilities.extensions.gone
import com.michaldrabik.showly2.utilities.extensions.onClick
import com.michaldrabik.showly2.utilities.extensions.showShortInfoSnackbar
import com.michaldrabik.showly2.utilities.extensions.visibleIf
import com.michaldrabik.showly2.utilities.network.NetworkObserver
import kotlinx.android.synthetic.main.activity_main.*
import javax.inject.Inject

class MainActivity : NotificationActivity(), EventObserver, NetworkObserver {

  companion object {
    private const val NAVIGATION_TRANSITION_DURATION_MS = 350L
    private const val ARG_NAVIGATION_VISIBLE = "ARG_NAVIGATION_VISIBLE"
  }

  lateinit var fragmentComponent: FragmentComponent

  @Inject
  lateinit var viewModelFactory: DaggerViewModelFactory
  private lateinit var viewModel: MainViewModel

  private val navigationHeight by lazy { dimenToPx(R.dimen.bottomNavigationHeightPadded) }
  private val decelerateInterpolator by lazy { DecelerateInterpolator(2F) }
  private val tips by lazy {
    mapOf(
      MENU_DISCOVER to tutorialTipDiscover,
      MENU_MY_SHOWS to tutorialTipMyShows
    )
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    appComponent().inject(this)
    fragmentComponent = appComponent().fragmentComponent().create()

    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    setupViewModel()
    setupNavigation()
    setupNavigationBackHandler()
    setupTips()

    restoreState(savedInstanceState)
    onNewIntent(intent)
  }

  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    handleAppShortcut(intent)
    handleNotification(intent?.extras) { hideNavigation(false) }
    handleTraktAuthorization(intent?.data)
  }

  override fun onStop() {
    viewModel.clearUp()
    super.onStop()
  }

  private fun setupViewModel() {
    viewModel = ViewModelProvider(this, viewModelFactory).get(MainViewModel::class.java)
    viewModel.run {
      uiLiveData.observe(this@MainActivity, Observer { render(it!!) })
      initSettings()
      refreshTraktSyncSchedule(applicationContext)
    }
  }

  private fun setupNavigation() {
    bottomNavigationView.setOnNavigationItemSelectedListener { item ->
      if (bottomNavigationView.selectedItemId == item.itemId) {
        doForFragments { (it as? OnTabReselectedListener)?.onTabReselected() }
        return@setOnNavigationItemSelectedListener true
      }

      val target = when (item.itemId) {
        R.id.menuWatchlist -> R.id.actionNavigateWatchlistFragment
        R.id.menuDiscover -> R.id.actionNavigateDiscoverFragment
        R.id.menuShows -> R.id.actionNavigateFollowedShowsFragment
        else -> throw IllegalStateException("Invalid menu item.")
      }

      clearUiCache()
      navigationHost.findNavController().navigate(target)
      showNavigation()
      return@setOnNavigationItemSelectedListener true
    }
  }

  private fun setupNavigationBackHandler() {
    onBackPressedDispatcher.addCallback(this) {
      if (tutorialView.isVisible) {
        tutorialView.fadeOut()
        return@addCallback
      }

      navigationHost.findNavController().run {
        if (currentDestination?.id == R.id.watchlistFragment) {
          remove()
          super.onBackPressed()
        }
        when (currentDestination?.id) {
          R.id.discoverFragment, R.id.followedShowsFragment -> {
            bottomNavigationView.selectedItemId = R.id.menuWatchlist
          }
        }
      }
    }
  }

  private fun setupTips() {
    tips.entries.forEach { (tip, view) ->
      view.visibleIf(!isTipShown(tip))
      view.onClick {
        it.gone()
        showTip(tip)
      }
    }
  }

  fun showTip(tip: Tip) {
    tutorialView.showTip(tip)
    viewModel.setTipShown(tip)
  }

  fun isTipShown(tip: Tip) = viewModel.isTipShown(tip)

  fun hideNavigation(animate: Boolean = true) {
    bottomNavigationView.run {
      isEnabled = false
      isClickable = false
    }
    tips.values.forEach { it.gone() }
    bottomNavigationWrapper.animate()
      .translationYBy(navigationHeight.toFloat())
      .setDuration(if (animate) NAVIGATION_TRANSITION_DURATION_MS else 0)
      .setInterpolator(decelerateInterpolator)
      .start()
  }

  fun showNavigation(animate: Boolean = true) {
    bottomNavigationView.run {
      isEnabled = true
      isClickable = true
    }
    tips.entries.forEach { (tip, view) -> view.visibleIf(!isTipShown(tip)) }
    bottomNavigationWrapper.animate()
      .translationY(0F)
      .setDuration(if (animate) NAVIGATION_TRANSITION_DURATION_MS else 0)
      .setInterpolator(decelerateInterpolator)
      .start()
  }

  fun openTab(@IdRes navigationId: Int) {
    bottomNavigationView.selectedItemId = navigationId
  }

  private fun render(uiModel: MainUiModel) {
    uiModel.run {
      isInitialRun?.let { if (it) openTab(R.id.menuDiscover) }
      showWhatsNew?.let { if (it) showWhatsNew() }
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    outState.putBoolean(ARG_NAVIGATION_VISIBLE, bottomNavigationWrapper.translationY == 0F)
    super.onSaveInstanceState(outState)
  }

  private fun restoreState(savedInstanceState: Bundle?) {
    val isNavigationVisible = savedInstanceState?.getBoolean(ARG_NAVIGATION_VISIBLE, true) ?: true
    if (!isNavigationVisible) hideNavigation()
  }

  private fun doForFragments(action: (Fragment) -> Unit) {
    navigationHost.findNavController().currentDestination?.id?.let {
      val navHost = supportFragmentManager.findFragmentById(R.id.navigationHost)
      navHost?.childFragmentManager?.primaryNavigationFragment?.let {
        action(it)
      }
    }
  }

  override fun onNetworkAvailableListener(isAvailable: Boolean) =
    runOnUiThread {
      statusView.visibleIf(!isAvailable)
      statusView.text = getString(R.string.errorNoInternetConnection)
    }

  override fun onNewEvent(event: Event) {
    runOnUiThread {
      when (event) {
        is ShowsSyncComplete -> {
          doForFragments { (it as? OnEpisodesSyncedListener)?.onEpisodesSyncFinished() }
          viewModel.refreshAnnouncements(applicationContext)
        }
        is TraktSyncProgress -> {
          doForFragments { (it as? OnTraktSyncListener)?.onTraktSyncProgress() }
        }
        is TraktQuickSyncSuccess -> {
          val text = resources.getQuantityString(R.plurals.textTraktQuickSyncComplete, event.count, event.count)
          snackBarHost.showShortInfoSnackbar(text)
        }
      }
    }
  }

  private fun handleAppShortcut(intent: Intent?) {
    when {
      intent == null -> return
      intent.extras?.containsKey("extraShortcutWatchlist") == true ->
        bottomNavigationView.selectedItemId = R.id.menuWatchlist
      intent.extras?.containsKey("extraShortcutDiscover") == true ->
        bottomNavigationView.selectedItemId = R.id.menuDiscover
      intent.extras?.containsKey("extraShortcutMyShows") == true ->
        bottomNavigationView.selectedItemId = R.id.menuShows
      intent.extras?.containsKey("extraShortcutSearch") == true -> {
        bottomNavigationView.selectedItemId = R.id.menuDiscover
        navigationHost.findNavController().navigate(R.id.actionDiscoverFragmentToSearchFragment)
      }
    }
  }

  private fun showWhatsNew() {
    MaterialAlertDialogBuilder(this, R.style.AlertDialog)
      .setBackground(ContextCompat.getDrawable(this, R.drawable.bg_dialog))
      .setView(WhatsNewView(this))
      .setCancelable(false)
      .setPositiveButton(R.string.textClose) { _, _ -> }
      .show()
  }
}
