package com.akash.reelslimiter.detection

/** Screen rectangle in pixels, decoupled from android.graphics.Rect so the detector stays pure. */
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Structure-only snapshot of one accessibility node.
 * Never carries on-screen text; [contentDescription] is only filled when the
 * snapshot builder decides it is safe (see ReelsRules.isRelevantDescription).
 * [key] is the node's identity (same on-screen view → same key), used to tell reels apart.
 */
data class UiNode(
    val className: String? = null,
    val viewId: String? = null,
    val contentDescription: String? = null,
    val isSelected: Boolean = false,
    val isScrollable: Boolean = false,
    val bounds: Bounds = Bounds(0, 0, 0, 0),
    val children: List<UiNode> = emptyList(),
    val key: Int = 0,
) {
    fun nodeCount(): Int = 1 + children.sumOf { it.nodeCount() }
}

enum class Screen { REELS, NOT_REELS }

/**
 * Detector verdict plus a short human-readable reason for the debug screen and logs.
 * [reelKey] identifies the reel filling the pager; null when not on Reels or mid-swipe.
 */
data class Detection(val screen: Screen, val reason: String, val reelKey: Int? = null)
