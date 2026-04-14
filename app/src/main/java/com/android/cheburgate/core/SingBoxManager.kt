package com.android.cheburgate.core

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File

object SingBoxManager {

    private const val MAX_LOG_LINES = 200
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // replay=0: новые подписчики получают только свежие строки, не всю историю
    private val _logs = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = MAX_LOG_LINES)
    val logs: Flow<String> = _logs.asSharedFlow()

    private val logBuffer = ArrayDeque<String>(MAX_LOG_LINES)

    @Volatile
    private var process: Process? = null

    /**
     * Бинарник находится в nativeLibraryDir как libsing-box.so.
     * Android устанавливает его с правами на выполнение автоматически.
     * Требует: файл app/src/main/jniLibs/<abi>/libsing-box.so
     *          + android:extractNativeLibs="true" в манифесте
     */
    private fun getBinaryFile(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, "libsing-box.so")

    private fun emit(msg: String) {
        scope.launch { _logs.emit(msg) }
        synchronized(logBuffer) {
            if (logBuffer.size >= MAX_LOG_LINES) logBuffer.removeFirst()
            logBuffer.addLast(msg)
        }
    }

    fun clearLogs() {
        synchronized(logBuffer) { logBuffer.clear() }
    }

    fun start(context: Context, configJson: String): Boolean {
        stop()
        clearLogs()
        return try {
            val configDir = File(context.filesDir, "singbox")
            configDir.mkdirs()
            val configFile = File(configDir, "config.json")
            configFile.writeText(configJson)
            // Логируем полный конфиг по частям (SharedFlow буферизует)
            configJson.chunked(500).forEachIndexed { i, chunk ->
                emit("[config:$i] $chunk")
            }

            val binary = getBinaryFile(context)
            emit("[sing-box] binary: ${binary.absolutePath}")
            emit("[sing-box] exists=${binary.exists()} canExecute=${binary.canExecute()}")

            if (!binary.exists()) {
                emit("[sing-box] ERROR: binary not found in nativeLibraryDir")
                emit("[sing-box] Переместите бинарник: jniLibs/<abi>/libsing-box.so")
                return false
            }

            emit("[sing-box] starting...")
            val pb = ProcessBuilder(binary.absolutePath, "run", "-c", configFile.absolutePath)
            pb.directory(configDir)
            pb.redirectErrorStream(true)
            process = pb.start()

            scope.launch {
                process?.inputStream?.bufferedReader()?.use { reader ->
                    reader.lineSequence().forEach { line -> emit(line) }
                }
            }
            emit("[sing-box] process launched")
            // Ждём немного, чтобы поймать мгновенный FATAL (например, unknown transport type)
            Thread.sleep(400)
            if (process?.isAlive != true) {
                emit("[sing-box] ERROR: process exited immediately (invalid config or unsupported feature)")
                process = null
                return false
            }
            true
        } catch (e: Exception) {
            emit("[sing-box] EXCEPTION ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    fun stop() {
        val p = process ?: return
        process = null
        emit("[sing-box] stopping...")
        p.destroy()
        scope.launch {
            kotlinx.coroutines.delay(500)
            try { p.destroyForcibly() } catch (_: Exception) {}
        }
    }

    fun isRunning(): Boolean = process?.isAlive == true

    fun getLogSnapshot(): List<String> = synchronized(logBuffer) { logBuffer.toList() }

    fun getSingBoxVersion(context: Context): String {
        return try {
            val binary = getBinaryFile(context)
            if (!binary.exists()) return "binary not found: ${binary.absolutePath}"
            if (!binary.canExecute()) return "no execute permission: ${binary.absolutePath}"
            val pb = ProcessBuilder(binary.absolutePath, "version")
            pb.redirectErrorStream(true)
            val p = pb.start()
            val output = p.inputStream.bufferedReader().readLines().joinToString(" | ")
            val exitCode = p.waitFor()
            if (output.isBlank()) "exit=$exitCode, no output" else output
        } catch (e: Exception) {
            "${e.javaClass.simpleName}: ${e.message}"
        }
    }
}
