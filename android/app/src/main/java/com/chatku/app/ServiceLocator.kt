package com.chatku.app

import android.app.Application
import com.chatku.app.data.Repo

object ServiceLocator {
    lateinit var app: Application
    val repo: Repo by lazy { Repo(app) }
}
