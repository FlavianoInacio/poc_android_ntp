package com.flaviano.pocntpserver

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.flaviano.pocntpserver.domain.usecase.GetTrueTimeNowUseCase
import com.flaviano.pocntpserver.presentation.main.viewmodel.MainViewModel
import com.instacart.library.truetime.TrueTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import java.io.IOException

class App : Application() {

    companion object {
        val NTP_BR_HOSTS = listOf(
            "200.160.7.186",
            "201.49.148.135",
            "200.186.125.195",
            "200.20.186.76",
            "200.160.0.8",
            "200.189.40.8",
            "200.192.232.8",
            "200.160.7.193"
        )
    }

    override fun onCreate() {
        super.onCreate()
        // start Koin!
        startKoin {
            // declare used Android context
            androidContext(this@App)
            // declare modules
            modules(modules = module {
                singleOf(::GetTrueTimeNowUseCase)
                viewModel { MainViewModel(get()) }
            })
        }
        // execute async
        ProcessLifecycleOwner.get().lifecycleScope.launch(Dispatchers.Default) {
            var position = 0
            while (!TrueTime.isInitialized()) {
                try {
                    TrueTime.build()
                        .withNtpHost(NTP_BR_HOSTS[position])
                        .withConnectionTimeout(3000)
                        .initialize()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                position++
            }
        }
    }
}