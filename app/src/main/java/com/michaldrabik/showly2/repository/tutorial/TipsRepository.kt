package com.michaldrabik.showly2.repository.tutorial

import android.content.SharedPreferences
import com.michaldrabik.showly2.di.scope.AppScope
import com.michaldrabik.showly2.model.Tip
import javax.inject.Inject
import javax.inject.Named

@AppScope
class TipsRepository @Inject constructor(
  @Named("tipsPreferences") private val sharedPreferences: SharedPreferences
) {

  fun isShown(tip: Tip) = sharedPreferences.getBoolean(tip.name, false)

  fun setShown(tip: Tip) {
    sharedPreferences.edit().putBoolean(tip.name, true).apply()
  }
}
