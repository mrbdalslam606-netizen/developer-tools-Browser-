package com.unixshells.devbrowser

import android.content.Context
import android.content.SharedPreferences
import android.os.Process
import android.util.Log

/**
 * Process-scoped runtime coordinator. It owns servers, never Activity or View
 * references, and makes start/stop idempotent across Activity recreation.
 */
object BrowserRuntime {
    private const val TAG = "BrowserRuntime"
    private const val DEFAULT_HTTP_PORT = 9222
    private const val DEFAULT_WS_PORT = 9223
    private const val DEVTOOLS_PORT = 9224

    @Volatile
    private var started = false
    private var cdpBridge: CDPBridge? = null
    private var devToolsServer: DevToolsServer? = null
    private var generation = 0L

    @Synchronized
    fun start(context: Context, prefs: SharedPreferences) {
        if (started) return

        val httpPort = prefs.getInt("cdp_http_port", DEFAULT_HTTP_PORT)
        val wsPort = prefs.getInt("cdp_ws_port", DEFAULT_WS_PORT)
        val bridge = CDPBridge(httpPort, wsPort)
        val server = DevToolsServer(context.applicationContext, DEVTOOLS_PORT)

        bridge.start(Process.myPid())
        server.start()
        cdpBridge = bridge
        devToolsServer = server
        generation++
        started = true
        Log.d(TAG, "Runtime started generation=$generation pid=${Process.myPid()}")
    }

    @Synchronized
    fun stop() {
        devToolsServer?.stop()
        cdpBridge?.stop()
        devToolsServer = null
        cdpBridge = null
        started = false
        Log.d(TAG, "Runtime stopped")
    }

    fun isStarted(): Boolean = started
    fun generation(): Long = generation
}
