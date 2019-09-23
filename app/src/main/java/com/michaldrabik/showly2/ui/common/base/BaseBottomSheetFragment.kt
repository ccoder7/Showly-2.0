package com.michaldrabik.showly2.ui.common.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.michaldrabik.showly2.ui.ViewModelFactory
import javax.inject.Inject

abstract class BaseBottomSheetFragment<T : BaseViewModel> : BottomSheetDialogFragment() {

  @Inject lateinit var viewModelFactory: ViewModelFactory
  protected lateinit var viewModel: T

  protected abstract val layoutResId: Int

  protected abstract fun createViewModel(): T

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    viewModel = createViewModel()
  }

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View = inflater.inflate(layoutResId, container, false)
}
