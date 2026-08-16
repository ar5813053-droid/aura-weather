package com.aura.weather.overlay

import android.graphics.Bitmap
import com.aura.weather.handtracking.HandTrackingResult
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * Shares preview frames + hand results from [com.aura.weather.input.HandDriveTrackingService]
 * with [HandDriveCameraOverlayService] without opening a second CameraX session.
 *
 * The tracking service publishes; the overlay (and any future UI) listens.
 * Bitmaps are owned by the hub — listeners must not recycle them.
 */
object HandDriveFrameHub {

    data class Snapshot(
        val bitmap: Bitmap?,
        val hands: HandTrackingResult?,
        val steeringEnabled: Boolean
    )

    private val listeners = CopyOnWriteArrayList<(Snapshot) -> Unit>()
    private val latest = AtomicReference(Snapshot(null, null, false))

    @Volatile
    var steeringEnabled: Boolean = false
        set(value) {
            field = value
            val cur = latest.get()
            val next = cur.copy(steeringEnabled = value)
            latest.set(next)
            notifyListeners(next)
        }

    fun snapshot(): Snapshot = latest.get()

    fun publishFrame(bitmap: Bitmap) {
        val prev = latest.get()
        val next = Snapshot(bitmap = bitmap, hands = prev.hands, steeringEnabled = steeringEnabled)
        latest.set(next)
        // Recycle previous after swap (if different instance).
        val old = prev.bitmap
        if (old != null && old !== bitmap && !old.isRecycled) {
            try {
                old.recycle()
            } catch (_: Throwable) {
            }
        }
        notifyListeners(next)
    }

    fun publishHands(result: HandTrackingResult) {
        val prev = latest.get()
        val next = Snapshot(bitmap = prev.bitmap, hands = result, steeringEnabled = steeringEnabled)
        latest.set(next)
        notifyListeners(next)
    }

    fun clear() {
        val prev = latest.getAndSet(Snapshot(null, null, false))
        prev.bitmap?.let {
            if (!it.isRecycled) {
                try {
                    it.recycle()
                } catch (_: Throwable) {
                }
            }
        }
        notifyListeners(Snapshot(null, null, false))
    }

    fun addListener(listener: (Snapshot) -> Unit) {
        listeners.add(listener)
        listener(latest.get())
    }

    fun removeListener(listener: (Snapshot) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners(snapshot: Snapshot) {
        for (l in listeners) {
            try {
                l(snapshot)
            } catch (_: Throwable) {
            }
        }
    }
}
