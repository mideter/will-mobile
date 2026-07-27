package com.will.app

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager


object SystemBarInsets {

    fun applyToHeader(activity: Activity, headerId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.window.attributes = activity.window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
        }

        val headerWrap = activity.findViewById<View>(headerId)
        headerWrap.setOnApplyWindowInsetsListener { view, insets ->
            val statusBarTop = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.statusBars()).top
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsets.top
            }
            view.setPadding(
                view.paddingLeft,
                statusBarTop,
                view.paddingRight,
                view.paddingBottom,
            )
            insets
        }
        headerWrap.requestApplyInsets()
    }
}
