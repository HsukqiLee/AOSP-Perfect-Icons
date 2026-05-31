package com.hsukqi.aospperfecticons

import android.app.Application
import com.google.android.material.color.DynamicColors

class AospPerfectIconsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}