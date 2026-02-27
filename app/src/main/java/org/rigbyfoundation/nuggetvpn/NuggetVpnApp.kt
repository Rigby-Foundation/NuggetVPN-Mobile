package org.rigbyfoundation.nuggetvpn

import android.app.Application
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions
import java.io.File

class NuggetVpnApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initSingBox()
    }

    private fun initSingBox() {
        val baseDir = filesDir.absolutePath
        val workingDir = "$baseDir/sing-box"
        val tempDir = cacheDir.absolutePath
        File(workingDir).mkdirs()

        val options = SetupOptions()
        options.basePath = baseDir
        options.workingPath = workingDir
        options.tempPath = tempDir
        Libbox.setup(options)
    }
}
