package com.hellovixora.replyvault

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

/**
 * Lightweight launcher activity that shows the system SplashScreen (API 31+)
 * or its androidx compat rendering (API 24-30) and then immediately hands off
 * to [MainActivity]. Kept separate from MainActivity so the (potentially
 * heavier) WebView is never created just to show a splash frame, which keeps
 * cold start fast.
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate() and before setContentView().
        installSplashScreen()

        super.onCreate(savedInstanceState)

        startActivity(Intent(this, MainActivity::class.java))
        finish()
        overridePendingTransition(0, 0)
    }
}
