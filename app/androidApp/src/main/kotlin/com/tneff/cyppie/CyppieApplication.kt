package com.tneff.cyppie

import android.app.Application
import com.tneff.cyppie.storage.AndroidStoragePaths

/**
 * App Application — initializes `:storage`'s app-private dir once, before any entry point/process
 * restart reaches the Activity (KAN-89/95 L1), so `defaultSeedFilePath()` always resolves.
 */
class CyppieApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidStoragePaths.init(filesDir.path)
    }
}
