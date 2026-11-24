package com.example.service

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MarcoAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "MarcoAccessibilityService connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Deliberately passive: Marco never secretly records or transmits screen content.
        // Screen inspection happens ONLY when Sanni explicitly invokes a screen command.
    }

    override fun onInterrupt() {
        Log.w(TAG, "MarcoAccessibilityService interrupted.")
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    fun readVisibleScreenSummary(): String {
        val root = rootInActiveWindow ?: return "Screen content currently unavailable."
        return try {
            val pkg = root.packageName?.toString().orEmpty()
            val texts = LinkedHashSet<String>()
            val buttons = LinkedHashSet<String>()
            val inputs = LinkedHashSet<String>()

            traverseNode(root) { node ->
                val text = node.text?.toString()?.trim().orEmpty()
                val desc = node.contentDescription?.toString()?.trim().orEmpty()
                val hint = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    node.hintText?.toString()?.trim().orEmpty()
                } else ""
                val label = listOf(text, desc).firstOrNull { it.isNotBlank() }.orEmpty()

                if (node.isEditable) {
                    inputs += (hint.ifBlank { label.ifBlank { "Text field" } })
                } else if (node.isClickable && label.isNotBlank()) {
                    buttons += label
                } else if (label.isNotBlank()) {
                    texts += label
                }
            }

            buildString {
                appendLine("Active app: ${pkg.ifBlank { "unknown" }}")
                if (buttons.isNotEmpty()) {
                    appendLine("Clickable buttons: ${buttons.take(12).joinToString(", ")}")
                }
                if (inputs.isNotEmpty()) {
                    appendLine("Input fields: ${inputs.take(6).joinToString(", ")}")
                }
                if (texts.isNotEmpty()) {
                    append("Visible text: ${texts.take(18).joinToString(" | ")}")
                }
            }.trim()
        } finally {
            root.recycle()
        }
    }

    fun tapElementByText(query: String): Boolean {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return false
        val root = rootInActiveWindow ?: return false
        return try {
            var matchedNode: AccessibilityNodeInfo? = null
            traverseNode(root) { node ->
                if (matchedNode != null) return@traverseNode
                val text = node.text?.toString()?.trim().orEmpty()
                val desc = node.contentDescription?.toString()?.trim().orEmpty()
                if (text.contains(cleanQuery, ignoreCase = true) ||
                    desc.contains(cleanQuery, ignoreCase = true)
                ) {
                    matchedNode = node
                }
            }
            var target = matchedNode
            while (target != null && !target.isClickable) {
                target = target.parent
            }
            target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        } finally {
            root.recycle()
        }
    }

    fun scrollScreen(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            var scrollableNode: AccessibilityNodeInfo? = null
            traverseNode(root) { node ->
                if (scrollableNode == null && node.isScrollable) {
                    scrollableNode = node
                }
            }
            val action = if (forward) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
            scrollableNode?.performAction(action) ?: false
        } finally {
            root.recycle()
        }
    }

    fun enterTextIntoField(targetHint: String, textToEnter: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            val editableNodes = mutableListOf<AccessibilityNodeInfo>()
            traverseNode(root) { node ->
                if (node.isEditable) editableNodes += node
            }
            val chosen = if (targetHint.isBlank()) {
                editableNodes.firstOrNull { it.isFocused } ?: editableNodes.firstOrNull()
            } else {
                editableNodes.firstOrNull { node ->
                    val hint = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        node.hintText?.toString().orEmpty()
                    } else ""
                    val existing = node.text?.toString().orEmpty()
                    val desc = node.contentDescription?.toString().orEmpty()
                    hint.contains(targetHint, ignoreCase = true) ||
                        existing.contains(targetHint, ignoreCase = true) ||
                        desc.contains(targetHint, ignoreCase = true)
                } ?: editableNodes.firstOrNull { it.isFocused } ?: editableNodes.firstOrNull()
            }

            if (chosen != null) {
                chosen.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val args = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        textToEnter
                    )
                }
                chosen.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            } else {
                false
            }
        } finally {
            root.recycle()
        }
    }

    fun performNavigation(actionName: String): Boolean {
        val globalAction = when (actionName.lowercase()) {
            "back", "piche", "wapas" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents", "recent", "apps" -> GLOBAL_ACTION_RECENTS
            "notifications", "notification" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings", "quicksettings" -> GLOBAL_ACTION_QUICK_SETTINGS
            else -> return false
        }
        return performGlobalAction(globalAction)
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo,
        visitor: (AccessibilityNodeInfo) -> Unit
    ) {
        visitor(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseNode(child, visitor)
        }
    }

    companion object {
        private const val TAG = "MarcoAccessibility"

        @Volatile
        var instance: MarcoAccessibilityService? = null
            private set
    }
}
