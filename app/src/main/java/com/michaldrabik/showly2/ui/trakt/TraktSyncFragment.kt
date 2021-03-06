package com.michaldrabik.showly2.ui.trakt

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.michaldrabik.network.Config
import com.michaldrabik.showly2.R
import com.michaldrabik.showly2.common.events.Event
import com.michaldrabik.showly2.common.events.EventObserver
import com.michaldrabik.showly2.common.trakt.TraktSyncService
import com.michaldrabik.showly2.fragmentComponent
import com.michaldrabik.showly2.model.TraktSyncSchedule
import com.michaldrabik.showly2.requireAppContext
import com.michaldrabik.showly2.ui.common.OnTraktAuthorizeListener
import com.michaldrabik.showly2.ui.common.base.BaseFragment
import com.michaldrabik.showly2.utilities.MessageEvent
import com.michaldrabik.showly2.utilities.extensions.dateFromMillis
import com.michaldrabik.showly2.utilities.extensions.doOnApplyWindowInsets
import com.michaldrabik.showly2.utilities.extensions.gone
import com.michaldrabik.showly2.utilities.extensions.onClick
import com.michaldrabik.showly2.utilities.extensions.toDisplayString
import com.michaldrabik.showly2.utilities.extensions.toLocalTimeZone
import com.michaldrabik.showly2.utilities.extensions.visibleIf
import kotlinx.android.synthetic.main.fragment_trakt_sync.*

class TraktSyncFragment : BaseFragment<TraktSyncViewModel>(R.layout.fragment_trakt_sync),
  OnTraktAuthorizeListener,
  EventObserver {

  override val viewModel by viewModels<TraktSyncViewModel> { viewModelFactory }

  override fun onCreate(savedInstanceState: Bundle?) {
    fragmentComponent().inject(this)
    super.onCreate(savedInstanceState)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setupView()
    setupStatusBar()

    viewModel.run {
      uiLiveData.observe(viewLifecycleOwner, Observer { render(it!!) })
      messageLiveData.observe(viewLifecycleOwner, Observer { showSnack(it) })
      invalidate()
    }
  }

  private fun setupView() {
    traktSyncToolbar.setNavigationOnClickListener { activity?.onBackPressed() }
    traktSyncImportCheckbox.setOnCheckedChangeListener { _, isChecked ->
      traktSyncButton.isEnabled = (isChecked || traktSyncExportCheckbox.isChecked)
    }
    traktSyncExportCheckbox.setOnCheckedChangeListener { _, isChecked ->
      traktSyncButton.isEnabled = (isChecked || traktSyncImportCheckbox.isChecked)
    }
  }

  private fun setupStatusBar() {
    traktSyncRoot.doOnApplyWindowInsets { view, insets, _, _ ->
      view.updatePadding(top = insets.systemWindowInsetTop)
    }
  }

  override fun onResume() {
    super.onResume()
    handleBackPressed()
  }

  private fun startImport() {
    val context = requireAppContext()
    TraktSyncService.createIntent(
      context,
      isImport = traktSyncImportCheckbox.isChecked,
      isExport = traktSyncExportCheckbox.isChecked
    ).run {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(this)
      } else {
        context.startService(this)
      }
    }
  }

  private fun checkScheduleImport(currentSchedule: TraktSyncSchedule?, quickSyncEnabled: Boolean?) {
    if (quickSyncEnabled == true && currentSchedule == TraktSyncSchedule.OFF) {
      MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialog)
        .setTitle(R.string.textSettingsScheduleImportConfirmationTitle)
        .setMessage(R.string.textSettingsScheduleImportConfirmationMessage)
        .setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_dialog))
        .setPositiveButton(R.string.textYes) { _, _ -> scheduleImport(currentSchedule) }
        .setNegativeButton(R.string.textCancel) { _, _ -> }
        .show()
    } else {
      scheduleImport(currentSchedule)
    }
  }

  private fun scheduleImport(currentSchedule: TraktSyncSchedule?) {
    val options = TraktSyncSchedule.values()
    val optionsStrings = options.map { getString(it.stringRes) }.toTypedArray()
    MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialog)
      .setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_dialog))
      .setSingleChoiceItems(optionsStrings, options.indexOf(currentSchedule)) { dialog, index ->
        val schedule = options[index]
        viewModel.saveTraktSyncSchedule(schedule, requireAppContext())
        showSnack(MessageEvent.info(schedule.confirmationStringRes))
        dialog.dismiss()
      }
      .show()
  }

  private fun startAuthorization() {
    Intent(Intent.ACTION_VIEW).run {
      data = Uri.parse(Config.TRAKT_AUTHORIZE_URL)
      startActivity(this)
    }
  }

  override fun onAuthorizationResult(authData: Uri?) = viewModel.authorizeTrakt(authData)

  private fun render(uiModel: TraktSyncUiModel) {
    uiModel.run {
      isProgress?.let {
        traktSyncButton.visibleIf(!it, false)
        traktSyncProgress.visibleIf(it)
        traktSyncImportCheckbox.visibleIf(!it)
        traktSyncExportCheckbox.visibleIf(!it)
        traktSyncScheduleButton.visibleIf(!it)
        traktLastSyncTimestamp.visibleIf(!it)
      }
      progressStatus?.let { traktSyncStatus.text = it }
      authError?.let { findNavController().popBackStack() }
      traktSyncSchedule?.let { traktSyncScheduleButton.setText(it.buttonStringRes) }
      lastTraktSyncTimestamp?.let {
        if (it != 0L) {
          val date = dateFromMillis(it).toLocalTimeZone().toDisplayString()
          traktLastSyncTimestamp.text = getString(R.string.textTraktSyncLastTimestamp, date)
        }
      }
      isAuthorized?.let {
        when {
          it -> {
            traktSyncButton.text = getString(R.string.textTraktSyncStart)
            traktSyncButton.onClick { startImport() }
            traktSyncScheduleButton.onClick { checkScheduleImport(traktSyncSchedule, quickSyncEnabled) }
          }
          else -> {
            traktSyncButton.text = getString(R.string.textSettingsTraktAuthorizeTitle)
            traktSyncButton.onClick { startAuthorization() }
            traktSyncScheduleButton.gone()
          }
        }
      }
    }
  }

  private fun handleBackPressed() {
    val dispatcher = requireActivity().onBackPressedDispatcher
    dispatcher.addCallback(viewLifecycleOwner) {
      remove()
      findNavController().popBackStack()
    }
  }

  override fun getSnackbarHost(): ViewGroup = traktSyncRoot

  override fun onNewEvent(event: Event) = viewModel.handleEvent(event)
}
