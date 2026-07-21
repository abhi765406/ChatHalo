package com.example

import android.app.Application
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HologramApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val file = File(getExternalFilesDir(null), "crash_log.txt")
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val stackTrace = Log.getStackTraceString(throwable)
                
                FileWriter(file, true).use { writer ->
                    writer.append("\n\n--- Crash at $timestamp ---\n")
                    writer.append(stackTrace)
                }
            } catch (e: Exception) {
                // Ignore if we can't write the crash log
                Log.e("HologramApplication", "Error writing crash log", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
