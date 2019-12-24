package com.michaldrabik.showly2.ui.trakt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import com.michaldrabik.network.Config
import com.michaldrabik.showly2.R
import com.michaldrabik.showly2.appComponent
import com.michaldrabik.showly2.common.trakt.TraktImportService
import com.michaldrabik.showly2.common.trakt.TraktImportService.Companion.ACTION_IMPORT_AUTH_ERROR
import com.michaldrabik.showly2.common.trakt.TraktImportService.Companion.ACTION_IMPORT_COMPLETE_ERROR
import com.michaldrabik.showly2.common.trakt.TraktImportService.Companion.ACTION_IMPORT_COMPLETE_SUCCESS
import com.michaldrabik.showly2.common.trakt.TraktImportService.Companion.ACTION_IMPORT_PROGRESS
import com.michaldrabik.showly2.common.trakt.TraktImportService.Companion.ACTION_IMPORT_START
import com.michaldrabik.showly2.ui.common.OnTraktAuthorizeListener
import com.michaldrabik.showly2.ui.common.base.BaseFragment
import com.michaldrabik.showly2.utilities.extensions.onClick
import com.michaldrabik.showly2.utilities.extensions.visibleIf
import kotlinx.android.synthetic.main.fragment_trakt_import.*

class TraktImportFragment : BaseFragment<TraktImportViewModel>(), OnTraktAuthorizeListener {

  override val layoutResId = R.layout.fragment_trakt_import

  override fun onCreate(savedInstanceState: Bundle?) {
    appComponent().inject(this)
    super.onCreate(savedInstanceState)
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    val filter = IntentFilter().apply {
      addAction(ACTION_IMPORT_COMPLETE_SUCCESS)
      addAction(ACTION_IMPORT_COMPLETE_ERROR)
      addAction(ACTION_IMPORT_START)
      addAction(ACTION_IMPORT_PROGRESS)
      addAction(ACTION_IMPORT_AUTH_ERROR)
    }
    LocalBroadcastManager.getInstance(requireContext().applicationContext).registerReceiver(broadcastReceiver, filter)
    return super.onCreateView(inflater, container, savedInstanceState)
  }

  override fun onDestroyView() {
    LocalBroadcastManager.getInstance(requireContext().applicationContext).unregisterReceiver(broadcastReceiver)
    super.onDestroyView()
  }

  override fun createViewModel(provider: ViewModelProvider) =
    provider.get(TraktImportViewModel::class.java)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setupView()
    viewModel.run {
      uiStream.observe(viewLifecycleOwner, Observer { render(it!!) })
      messageStream.observe(viewLifecycleOwner, Observer { showInfoSnackbar(it!!) })
      errorStream.observe(viewLifecycleOwner, Observer { showErrorSnackbar(it!!) })
      invalidate()
    }
  }

  private fun setupView() {
    traktImportToolbar.setNavigationOnClickListener { activity?.onBackPressed() }
  }

  override fun onResume() {
    super.onResume()
    handleBackPressed()
  }

  private fun startImport() {
    val context = requireContext().applicationContext
    Intent(context, TraktImportService::class.java).run {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(this)
      } else {
        context.startService(this)
      }
    }
  }

  private fun startAuthorization() {
    Intent(Intent.ACTION_VIEW).run {
      data = Uri.parse(Config.TRAKT_AUTHORIZE_URL)
      startActivity(this)
    }
  }

  override fun onAuthorizationResult(authData: Uri?) = viewModel.authorizeTrakt(authData)

  private fun render(uiModel: TraktImportUiModel) {
    uiModel.run {
      isProgress?.let {
        traktImportButton.visibleIf(!it, false)
        traktImportProgress.visibleIf(it)
      }
      authError?.let { findNavController().popBackStack() }
      isAuthorized?.let {
        when {
          it -> {
            traktImportButton.text = getString(R.string.textTraktImportStart)
            traktImportButton.onClick { startImport() }
          }
          else -> {
            traktImportButton.text = getString(R.string.textSettingsTraktAuthorizeTitle)
            traktImportButton.onClick { startAuthorization() }
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

  override fun getSnackbarHost(): ViewGroup = traktImportRoot

  private val broadcastReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      viewModel.onBroadcastAction(intent?.action)
    }
  }
}
