package com.akash.reelslimiter.service

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.akash.reelslimiter.detection.Bounds
import com.akash.reelslimiter.detection.UiNode

/** Caps the walk so a huge tree can't blow the per-event time budget. */
private const val MAX_NODES = 1500
private const val MAX_DEPTH = 40

/**
 * Copies the visible part of an accessibility tree into a [UiNode] snapshot.
 * Never reads node text; a content description is kept only when [keepDescription] allows it.
 */
fun AccessibilityNodeInfo.toUiNode(keepDescription: (String) -> Boolean): UiNode =
    snapshot(this, keepDescription, depth = 0, budget = intArrayOf(MAX_NODES))

private fun snapshot(
    node: AccessibilityNodeInfo,
    keepDescription: (String) -> Boolean,
    depth: Int,
    budget: IntArray,
): UiNode {
    budget[0]--
    val rect = Rect().also { node.getBoundsInScreen(it) }
    val children = mutableListOf<UiNode>()
    if (depth < MAX_DEPTH) {
        for (i in 0 until node.childCount) {
            if (budget[0] <= 0) break
            val child = node.getChild(i) ?: continue
            if (child.isVisibleToUser) children += snapshot(child, keepDescription, depth + 1, budget)
            recycleCompat(child)
        }
    }
    return UiNode(
        className = node.className?.toString(),
        viewId = node.viewIdResourceName,
        contentDescription = node.contentDescription?.toString()?.takeIf(keepDescription),
        isSelected = node.isSelected,
        isScrollable = node.isScrollable,
        bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom),
        children = children,
        // Derived from the source view id and window id: stable for the same on-screen view.
        key = node.hashCode(),
    )
}

private fun recycleCompat(node: AccessibilityNodeInfo) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        @Suppress("DEPRECATION")
        node.recycle()
    }
}

/** Indented one-line-per-node dump for the debug screen. */
fun UiNode.dump(): String = buildString { appendNode(this@dump, 0) }

private fun StringBuilder.appendNode(node: UiNode, depth: Int) {
    append("  ".repeat(depth))
    append(node.className?.substringAfterLast('.') ?: "?")
    node.viewId?.let { append(" #").append(it.substringAfter(":id/")) }
    node.contentDescription?.let { append(" \"").append(it.take(40)).append('"') }
    if (node.isSelected) append(" [selected]")
    if (node.isScrollable) append(" [scroll]")
    with(node.bounds) { append(" ${width}x$height@$left,$top") }
    append('\n')
    node.children.forEach { appendNode(it, depth + 1) }
}
