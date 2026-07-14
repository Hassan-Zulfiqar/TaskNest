package com.hassan.tasknest

import android.app.Application
import com.hassan.tasknest.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/** Application entry point; initialises Koin with app-level dependencies. */
class TodoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@TodoApplication)
            modules(appModule)
        }
    }
}
