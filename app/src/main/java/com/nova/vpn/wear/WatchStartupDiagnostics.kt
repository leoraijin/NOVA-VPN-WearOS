package com.nova.vpn.wear

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.AtomicFile
import java.io.File

/** Fixed phase names only. Never store exception text, VPN config, hostnames or native traces. */
internal object WatchStartupDiagnostics {
    private val phases = setOf("service-created", "foreground", "permission-check", "core-setup", "config-adapt", "config-check", "core-start", "tun-open", "running", "startup-error", "stopped")
    fun mark(context: Context, phase: String) {
        require(phase in phases)
        val atomic = AtomicFile(File(context.filesDir, "watch-startup-phase.txt"))
        runCatching {
            val output = atomic.startWrite()
            try { output.write(phase.toByteArray()); atomic.finishWrite(output) }
            catch (error: Exception) { atomic.failWrite(output); throw error }
        }
    }
    fun describe(context: Context): String {
        val phase = runCatching { AtomicFile(File(context.filesDir, "watch-startup-phase.txt")).readFully().toString(Charsets.UTF_8) }
            .getOrNull()?.takeIf { it in phases } ?: "неизвестно"
        val reason = if (Build.VERSION.SDK_INT >= 30) runCatching {
            val manager = context.getSystemService(ActivityManager::class.java)
            val exit = manager.getHistoricalProcessExitReasons(context.packageName, 0, 8)
                .firstOrNull { it.processName == context.packageName + ":vpn" && it.timestamp >= System.currentTimeMillis() - 60000 }
            when (exit?.reason) {
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "сбой native-ядра"
                ApplicationExitInfo.REASON_CRASH -> "ошибка процесса"
                ApplicationExitInfo.REASON_LOW_MEMORY -> "нехватка памяти"
                ApplicationExitInfo.REASON_ANR -> "процесс не отвечал"
                else -> "процесс VPN завершился"
            }
        }.getOrDefault("процесс VPN завершился") else "процесс VPN завершился"
        return "VPN остановился: $reason. Этап: $phase. Версия ${BuildConfig.VERSION_NAME}; ABI: ${Build.SUPPORTED_ABIS.joinToString()}. Профиль сохранён."
    }
    fun startupError(context: Context, error: Throwable): String {
        val phase = runCatching { AtomicFile(File(context.filesDir, "watch-startup-phase.txt")).readFully().toString(Charsets.UTF_8) }
            .getOrNull()?.takeIf { it in phases } ?: "неизвестно"
        val category = error.javaClass.simpleName.takeIf { Regex("[A-Za-z][A-Za-z0-9_]{0,80}").matches(it) } ?: "ошибка ядра"
        return "VPN не запущен ($category). Этап: $phase. Версия ${BuildConfig.VERSION_NAME}. Профиль сохранён."
    }
}
