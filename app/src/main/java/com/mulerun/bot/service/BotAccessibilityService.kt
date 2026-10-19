package com.mulerun.bot.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.Display
import android.os.Bundle
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import android.view.accessibility.AccessibilityNodeInfo
import com.mulerun.bot.data.BotSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow

class BotAccessibilityService : AccessibilityService() {
    private lateinit var store: BotSettingsStore

    override fun onServiceConnected() {
        store = BotSettingsStore(this)
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    suspend fun captureScreenshotBase64(): String? = suspendCancellableCoroutine { continuation ->
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                try {
                    val hardwareBuffer = screenshot.hardwareBuffer
                    val hardwareBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
                    val bitmap = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                    hardwareBuffer.close()
                    if (bitmap == null) {
                        if (continuation.isActive) continuation.resume(null)
                        return
                    }
                    val bytes = ByteArrayOutputStream().use { output ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 78, output)
                        output.toByteArray()
                    }
                    bitmap.recycle()
                    if (continuation.isActive) continuation.resume(Base64.encodeToString(bytes, Base64.NO_WRAP))
                } catch (_: Throwable) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
            override fun onFailure(errorCode: Int) {
                if (continuation.isActive) continuation.resume(null)
            }
        })
    }

    fun clickVisibleText(target: String): Boolean {
        val roots = windows.asSequence().mapNotNull { it.root }.toList()
        for (root in roots) {
            if (clickMatchingNode(root, target)) return true
        }
        return false
    }

    private fun clickMatchingNode(node: AccessibilityNodeInfo, target: String): Boolean {
        if (node.isVisibleToUser && !node.isPassword) {
            val text = node.text?.toString().orEmpty()
            val description = node.contentDescription?.toString().orEmpty()
            if (text.contains(target, ignoreCase = true) || description.contains(target, ignoreCase = true)) {
                if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                node.parent?.let { parent ->
                    if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                }
            }
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val clicked = clickMatchingNode(child, target)
            child.recycle()
            if (clicked) return true
        }
        return false
    }

    fun typeIntoFocusedField(text: String): Boolean {
        if (text.length > 4_000) return false
        for (window in windows) {
            val root = window.root ?: continue
            if (setTextNode(root, text)) return true
        }
        return false
    }

    private fun setTextNode(node: AccessibilityNodeInfo, text: String): Boolean {
        if (node.isVisibleToUser && !node.isPassword && node.isEditable && node.isFocused) {
            val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val done = setTextNode(child, text)
            child.recycle()
            if (done) return true
        }
        return false
    }

    fun pressBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun scroll(direction: String): Boolean {
        val action = if (direction.equals("up", true) || direction.equals("backward", true)) {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        for (window in windows) {
            val root = window.root ?: continue
            if (scrollNode(root, action)) return true
        }
        return false
    }

    private fun scrollNode(node: AccessibilityNodeInfo, action: Int): Boolean {
        if (node.isVisibleToUser && node.isScrollable && node.performAction(action)) return true
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val done = scrollNode(child, action)
            child.recycle()
            if (done) return true
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::store.isInitialized || !store.settings().screenAwarenessEnabled) {
            visibleScreenText.value = ""
            return
        }
        val lines = linkedSetOf<String>()
        windows.asSequence()
            .mapNotNull { it.root }
            .forEach { collectVisibleText(it, lines) }
        visibleScreenText.value = lines.joinToString("\n").take(8_000)
    }

    private fun collectVisibleText(node: AccessibilityNodeInfo, output: MutableSet<String>) {
        if (!node.isVisibleToUser || node.isPassword) return
        node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(output::add)
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(output::add)
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { collectVisibleText(it, output) }
        }
    }

    override fun onInterrupt() = Unit

    companion object {
        @Volatile var instance: BotAccessibilityService? = null
            private set
        val visibleScreenText = MutableStateFlow("")
    }
}
