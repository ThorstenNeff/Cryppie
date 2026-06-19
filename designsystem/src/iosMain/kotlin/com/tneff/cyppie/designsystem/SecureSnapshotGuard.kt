package com.tneff.cyppie.designsystem

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIVisualEffectView
import platform.UIKit.UIWindow
import platform.darwin.NSObjectProtocol

/**
 * iOS app-switcher snapshot guard (KAN-74, ADR-0005). iOS has no `FLAG_SECURE`; instead, while any
 * secure screen is on-screen we cover the key window with a blur the instant the app resigns active
 * (`willResignActive` fires **before** the system snapshots for the app switcher / Recents), and we
 * remove it on `didBecomeActive`. So the seed / password screens never appear in the switcher snapshot.
 *
 * Shared by every `SecureScreenEffect` actual (onboarding seed + unlock password, wallet Send) so a
 * single ref-count spans cross-module secure→secure navigation — the cover is installed while ≥1
 * secure screen is composed and removed only when the last leaves. Main-thread only (Compose effects).
 */
@OptIn(ExperimentalForeignApi::class)
object SecureSnapshotGuard {
    private var count = 0
    private var resignObserver: NSObjectProtocol? = null
    private var activeObserver: NSObjectProtocol? = null
    private var cover: UIVisualEffectView? = null

    /** A secure screen entered composition. */
    fun acquire() {
        count++
        if (count == 1) installObservers()
    }

    /** A secure screen left composition. */
    fun release() {
        if (count > 0) count--
        if (count == 0) {
            removeObservers()
            removeCover()
        }
    }

    private fun installObservers() {
        val center = NSNotificationCenter.defaultCenter
        // queue = null → the block runs SYNCHRONOUSLY on the posting thread (Apple docs). The system
        // snapshots for the app switcher right after willResignActive, so the cover MUST be up before
        // this returns — an async mainQueue hop could land the blur after the snapshot (review M1). The
        // resign notification is posted on the main thread, so showCover() stays main-thread-safe.
        resignObserver = center.addObserverForName(UIApplicationWillResignActiveNotification, null, null) { _ -> showCover() }
        // Removal isn't timing-critical → main queue is fine.
        activeObserver = center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) { _ -> removeCover() }
    }

    private fun removeObservers() {
        val center = NSNotificationCenter.defaultCenter
        resignObserver?.let { center.removeObserver(it) }
        activeObserver?.let { center.removeObserver(it) }
        resignObserver = null
        activeObserver = null
    }

    private fun showCover() {
        if (cover != null) return
        val window = keyWindow() ?: return
        val view = UIVisualEffectView(effect = UIBlurEffect.effectWithStyle(UIBlurEffectStyle.UIBlurEffectStyleSystemChromeMaterial))
        view.setFrame(window.bounds)
        view.setAutoresizingMask(UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight)
        window.addSubview(view)
        cover = view
    }

    private fun removeCover() {
        cover?.removeFromSuperview()
        cover = null
    }

    private fun keyWindow(): UIWindow? {
        val windows = UIApplication.sharedApplication.windows
        return windows.firstOrNull { (it as? UIWindow)?.isKeyWindow() == true } as? UIWindow
            ?: windows.firstOrNull() as? UIWindow
    }
}
