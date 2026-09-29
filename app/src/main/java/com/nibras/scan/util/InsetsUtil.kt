package com.nibras.scan.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Apps targeting Android 15+ (API 35/36) are drawn edge-to-edge, i.e. behind the status
 * and navigation bars. This keeps content clear of the system bars.
 *
 * If [topTarget] is given (e.g. a coloured header), the status-bar inset is added to *its*
 * top padding so the header colour extends under the status bar.
 */
object InsetsUtil {
    fun apply(root: View, topTarget: View? = null) {
        val topStart = topTarget?.paddingTop ?: 0
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            if (topTarget != null) {
                topTarget.setPadding(
                    topTarget.paddingLeft, topStart + bars.top,
                    topTarget.paddingRight, topTarget.paddingBottom
                )
                v.setPadding(bars.left, 0, bars.right, bars.bottom)
            } else {
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            }
            insets
        }
    }
}
