package com.github.foxray

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.PowerManager
import androidx.core.content.getSystemService
import go.Seq
import com.github.foxray.bg.AppChangeReceiver
import com.github.foxray.bg.UpdateProfileWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import com.github.foxray.Application as BoxApplication

class Application : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        application = this
    }

    override fun onCreate() {
        super.onCreate()

        Seq.setContext(this)

        GlobalScope.launch(Dispatchers.IO) {
            UpdateProfileWork.reconfigureUpdater()
        }

        registerReceiver(AppChangeReceiver(), IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addDataScheme("package")
        })
    }

    companion object {
        lateinit var application: BoxApplication
        val notification by lazy { application.getSystemService<NotificationManager>()!! }
        val connectivity by lazy { application.getSystemService<ConnectivityManager>()!! }
        val packageManager by lazy { application.packageManager }
        val powerManager by lazy { application.getSystemService<PowerManager>()!! }
    }

}