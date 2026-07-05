package com.hellovixora.replyvault

import android.app.Application

/**
 * Application entry point for ReplyVault.
 *
 * ReplyVault is a fully offline, ad-free study companion. It has no analytics,
 * no network calls, and no third-party SDKs — everything the user needs lives
 * inside the bundled [MainActivity] WebView and the device's local storage.
 */
class ReplyVaultApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Intentionally minimal. All app data lives inside the WebView's
        // local storage (WebStorage / localStorage), which Android persists
        // per-app automatically — no extra initialization required.
    }
}
