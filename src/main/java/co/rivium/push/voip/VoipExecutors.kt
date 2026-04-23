package co.rivium.push.voip

import android.os.Handler
import android.os.Looper

/**
 * Centralized thread management for Rivium Push VoIP SDK.
 *
 * This class provides a cached main handler to avoid creating
 * Handler instances repeatedly which can cause memory pressure.
 */
internal object VoipExecutors {

    /**
     * Main thread handler for posting UI updates.
     * Cached to avoid creating new Handler instances.
     */
    val mainHandler: Handler by lazy {
        Handler(Looper.getMainLooper())
    }

    /**
     * Execute a task on the main thread.
     */
    fun executeMain(task: Runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run()
        } else {
            mainHandler.post(task)
        }
    }

    /**
     * Execute a task on the main thread with a delay.
     */
    fun executeMainDelayed(delayMs: Long, task: Runnable) {
        mainHandler.postDelayed(task, delayMs)
    }

    /**
     * Remove pending callbacks
     */
    fun removeCallbacks(task: Runnable) {
        mainHandler.removeCallbacks(task)
    }
}
