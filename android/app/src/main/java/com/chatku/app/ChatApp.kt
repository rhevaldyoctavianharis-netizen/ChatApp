package com.chatku.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.chatku.app.data.Net
import com.chatku.app.data.Appearance
import com.chatku.app.data.Session
import com.chatku.app.util.Notifier

class ChatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.app = this
        Session.init(this)
        Appearance.init(this)
        Net.configure(Session.baseUrl)
        Notifier.createChannel(this)

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                val repo = ServiceLocator.repo
                repo.inForeground = true
                repo.openChatId?.let { repo.markRead(it) }
            }

            override fun onStop(owner: LifecycleOwner) {
                ServiceLocator.repo.inForeground = false
            }
        })
    }
}
