package com.shopfinance.app

import android.app.Application

/** Process entry point: builds the [AppContainer] before any activity starts. */
class ShopFinanceApplication : Application() {

    /** The app's shared objects. Set in [onCreate], so it is ready for every activity. */
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
