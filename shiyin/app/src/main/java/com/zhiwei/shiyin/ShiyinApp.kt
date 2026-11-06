package com.zhiwei.shiyin

import android.app.Application
import android.content.Context
import com.zhiwei.shiyin.data.PresetRepository
import com.zhiwei.shiyin.data.RecordingRepository
import java.io.File

class ShiyinApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    private val movies = File(
        context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES),
        "shiyin",
    )
    val recordings = RecordingRepository(movies)
    val presets = PresetRepository(context.getSharedPreferences("shiyin", Context.MODE_PRIVATE))
}
