package com.nabhas.ai

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class NabhasAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        instance = this
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) {
            instance = null
        }
        return super.onUnbind(intent)
    }

    fun clickText(targetText: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val query = targetText.trim().lowercase(Locale.ROOT)
        if (query.isEmpty()) return false
        val match = findNode(root) { node ->
            val text = node.text?.toString()?.lowercase(Locale.ROOT).orEmpty()
            val desc = node.contentDescription?.toString()?.lowercase(Locale.ROOT).orEmpty()
            text.contains(query) || desc.contains(query)
        } ?: return false
        return performClickable(match)
    }

    fun scroll(direction: String, targetText: String = ""): Boolean {
        val root = rootInActiveWindow ?: return false
        val action = when (direction.trim().lowercase(Locale.ROOT)) {
            "up", "previous", "backward" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            else -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        if (targetText.isNotBlank()) {
            val query = targetText.trim().lowercase(Locale.ROOT)
            val targetNode = findNode(root) { node ->
                val text = node.text?.toString()?.lowercase(Locale.ROOT).orEmpty()
                val desc = node.contentDescription?.toString()?.lowercase(Locale.ROOT).orEmpty()
                node.isScrollable && (text.contains(query) || desc.contains(query))
            }
            if (targetNode != null && targetNode.performAction(action)) return true
        }
        val scrollable = findNode(root) { it.isScrollable } ?: return false
        return scrollable.performAction(action)
    }

    fun setText(text: String, targetField: String = ""): Boolean {
        val root = rootInActiveWindow ?: return false
        val query = targetField.trim().lowercase(Locale.ROOT)
        val inputNode = if (query.isNotEmpty()) {
            findNode(root) { node ->
                val nodeText = node.text?.toString()?.lowercase(Locale.ROOT).orEmpty()
                val desc = node.contentDescription?.toString()?.lowercase(Locale.ROOT).orEmpty()
                node.isEditable && (nodeText.contains(query) || desc.contains(query))
            }
        } else {
            root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findNode(root) { it.isEditable }
        } ?: return false

        inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    private fun performClickable(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            current = current.parent
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun findNode(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (predicate(current)) return current
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { queue.addLast(it) }
            }
        }
        return null
    }

    companion object {
        @Volatile
        private var instance: NabhasAccessibilityService? = null

        fun getInstance(): NabhasAccessibilityService? = instance
    }
}
