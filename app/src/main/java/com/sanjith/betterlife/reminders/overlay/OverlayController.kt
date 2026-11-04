package com.sanjith.betterlife.reminders.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.sanjith.betterlife.ui.theme.BetterLifeTheme

/** Why the overlay went away. */
public enum class OverlayDismissReason { DONE, SNOOZE, BACK, REMOVED }

/** Result of trying to add the overlay window. */
public sealed interface OverlayShowResult {
    public data object Shown : OverlayShowResult
    public data object AlreadyShowing : OverlayShowResult
    public data class Failed(val cause: String) : OverlayShowResult
}

/**
 * Shows one full-screen Compose window over every other app (TYPE_APPLICATION_OVERLAY).
 * Owns at most one window: a second [show] while one is visible is refused, never stacked.
 * The owner of this controller must call [remove] when it is destroyed so no window is left behind.
 */
public class OverlayController(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var onDismiss: ((OverlayDismissReason) -> Unit)? = null

    public val isShowing: Boolean
        get() = root != null

    public fun show(
        onDismiss: (OverlayDismissReason) -> Unit,
        content: @Composable (dismiss: (OverlayDismissReason) -> Unit) -> Unit,
    ): OverlayShowResult {
        if (root != null) return OverlayShowResult.AlreadyShowing
        // Re-checked at the moment of use: the permission can be revoked at any time.
        if (!Settings.canDrawOverlays(context)) return OverlayShowResult.Failed(CAUSE_PERMISSION)

        val owner = OverlayLifecycleOwner().apply { create() }
        val view = buildRoot(owner, content)
        return try {
            windowManager.addView(view, layoutParams())
            owner.resume()
            root = view
            lifecycleOwner = owner
            this.onDismiss = onDismiss
            OverlayShowResult.Shown
        } catch (e: WindowManager.BadTokenException) {
            owner.destroy()
            OverlayShowResult.Failed(e.javaClass.simpleName)
        } catch (e: SecurityException) {
            owner.destroy()
            OverlayShowResult.Failed(e.javaClass.simpleName)
        }
    }

    /** Removes the window if present. Safe to call more than once. */
    public fun remove() {
        dismiss(OverlayDismissReason.REMOVED)
    }

    private fun dismiss(reason: OverlayDismissReason) {
        val view = root ?: return
        root = null
        lifecycleOwner?.destroy()
        lifecycleOwner = null
        try {
            windowManager.removeViewImmediate(view)
        } catch (_: IllegalArgumentException) {
            // Already detached by the system (for example the permission was revoked): nothing left to remove.
        }
        onDismiss?.invoke(reason)
        onDismiss = null
    }

    private fun buildRoot(
        owner: OverlayLifecycleOwner,
        content: @Composable (dismiss: (OverlayDismissReason) -> Unit) -> Unit,
    ): View {
        val frame = BackAwareFrameLayout(context) { dismiss(OverlayDismissReason.BACK) }
        frame.setViewTreeLifecycleOwner(owner)
        frame.setViewTreeSavedStateRegistryOwner(owner)
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { BetterLifeTheme { content(::dismiss) } }
        }
        frame.addView(composeView)
        return frame
    }

    private fun layoutParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // Focusable (no FLAG_NOT_FOCUSABLE) so Back reaches us; drawn edge to edge under the system bars.
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        setFitInsetsTypes(0)
    }

    private companion object {
        const val CAUSE_PERMISSION = "draw-over-apps permission missing"
    }
}
