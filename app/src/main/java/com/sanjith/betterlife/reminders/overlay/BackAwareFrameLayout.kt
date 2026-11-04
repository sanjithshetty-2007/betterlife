package com.sanjith.betterlife.reminders.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.KeyEvent
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

/**
 * Root of the overlay window. Android 16 delivers Back to opted-in apps through OnBackInvokedCallback
 * (predictive back) instead of a key event, so both routes call [onBack].
 */
@SuppressLint("ViewConstructor")
internal class BackAwareFrameLayout(
    context: Context,
    private val onBack: () -> Unit,
) : FrameLayout(context) {

    private val backCallback = OnBackInvokedCallback { onBack() }
    private var registeredDispatcher: OnBackInvokedDispatcher? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        registeredDispatcher = findOnBackInvokedDispatcher()?.also {
            it.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback)
        }
    }

    override fun onDetachedFromWindow() {
        registeredDispatcher?.unregisterOnBackInvokedCallback(backCallback)
        registeredDispatcher = null
        super.onDetachedFromWindow()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) onBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
