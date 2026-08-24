package it.mattia.glucoseglyph.glyph

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy

/**
 * Boilerplate for a Glyph Matrix "Toy" service, adapted from Nothing's official
 * GlyphMatrix-Example-Project (MIT licensed): binds to the system's Glyph service,
 * registers this device, and forwards touch/AOD events as plain callbacks.
 */
abstract class GlyphMatrixServiceBase(private val tag: String) : Service() {

    private val eventHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) {
                super.handleMessage(msg)
                return
            }
            val data = msg.data ?: return
            if (!data.containsKey(KEY_DATA)) return
            when (data.getString(KEY_DATA)) {
                GlyphToy.EVENT_ACTION_DOWN -> onTouchPointPressed()
                GlyphToy.EVENT_ACTION_UP -> onTouchPointReleased()
                GlyphToy.EVENT_CHANGE -> onTouchPointLongPress()
                GlyphToy.EVENT_AOD -> onAodTick()
            }
        }
    }

    private val serviceMessenger = Messenger(eventHandler)

    var glyphMatrixManager: GlyphMatrixManager? = null
        private set

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(componentName: ComponentName?) {
            glyphMatrixManager?.let { manager ->
                manager.register(Glyph.DEVICE_23112)
                performOnServiceConnected(applicationContext, manager)
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {}
    }

    final override fun onBind(intent: Intent?): IBinder {
        GlyphMatrixManager.getInstance(applicationContext)?.let { manager ->
            glyphMatrixManager = manager
            manager.init(callback)
        }
        return serviceMessenger.binder
    }

    final override fun onUnbind(intent: Intent?): Boolean {
        glyphMatrixManager?.let { performOnServiceDisconnected(applicationContext) }
        glyphMatrixManager?.turnOff()
        glyphMatrixManager?.unInit()
        glyphMatrixManager = null
        return false
    }

    open fun performOnServiceConnected(context: Context, glyphMatrixManager: GlyphMatrixManager) {}
    open fun performOnServiceDisconnected(context: Context) {}

    open fun onTouchPointPressed() {}
    open fun onTouchPointLongPress() {}
    open fun onTouchPointReleased() {}
    open fun onAodTick() {}

    private companion object {
        const val KEY_DATA = "data"
    }
}
