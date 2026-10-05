package com.akash.reelslimiter.detection

/**
 * Every Instagram-specific detection rule lives in this file, so a layout change
 * in an Instagram update is a one-file fix.
 */
object ReelsRules {
    const val INSTAGRAM_PACKAGE = "com.instagram.android"

    /** Bottom-bar tab labels for Reels (lowercase). Add localized labels here. */
    val reelsTabDescriptions = setOf("reels")

    /** View-id suffixes of the Reels tab; language independent. */
    val reelsTabIdSuffixes = listOf("/clips_tab")

    /** View-id fragments that only exist inside the Reels viewer (also used when a reel opens from a DM). */
    val reelsViewerIdMarkers = listOf("clips_viewer", "clips_video_container")

    /** Class-name fragments of the vertical pager that holds the reels. */
    val pagerClassMarkers = listOf("ViewPager", "RecyclerView")

    /** A pager counts as full-screen when it covers this much of the window. */
    const val MIN_PAGER_WIDTH_FRACTION = 0.9f
    const val MIN_PAGER_HEIGHT_FRACTION = 0.75f

    /** A reel is "the visible one" once its item fills this much of the pager; mid-swipe none does. */
    const val MIN_ITEM_VISIBLE_FRACTION = 0.9f

    /** Only descriptions that match a known tab label are kept in detection snapshots. */
    fun isRelevantDescription(description: String): Boolean =
        description.trim().lowercase() in reelsTabDescriptions
}

/**
 * Pure function: accessibility snapshot in, Reels / Not Reels (plus which reel) out.
 * When in doubt it answers NOT_REELS, so chat can never be blocked on a guess.
 */
object ReelsDetector {

    fun detect(root: UiNode?): Detection {
        if (root == null) return Detection(Screen.NOT_REELS, "no window")
        if (root.bounds.width <= 0 || root.bounds.height <= 0) {
            return Detection(Screen.NOT_REELS, "window has no size")
        }

        // The deepest full-screen pager is the reels pager, not an outer page container.
        var pager: UiNode? = null
        var pagerDepth = -1
        var reelsTabSelected = false
        var viewerMarker = false
        walk(root, depth = 0) { node, depth ->
            if (depth > pagerDepth && isFullScreenPager(node, root.bounds)) {
                pager = node
                pagerDepth = depth
            }
            if (!reelsTabSelected && isReelsTab(node) && isSelectedOrHasSelectedChild(node)) reelsTabSelected = true
            if (!viewerMarker && hasViewerMarker(node)) viewerMarker = true
        }

        val reelsPager = pager
        return when {
            reelsPager != null && reelsTabSelected ->
                Detection(Screen.REELS, "pager + Reels tab selected", visibleItemKey(reelsPager))
            reelsPager != null && viewerMarker ->
                Detection(Screen.REELS, "pager + reels viewer", visibleItemKey(reelsPager))
            reelsPager != null -> Detection(Screen.NOT_REELS, "pager without Reels signal (feed?)")
            reelsTabSelected || viewerMarker -> Detection(Screen.NOT_REELS, "Reels signal without pager")
            else -> Detection(Screen.NOT_REELS, "no Reels signals")
        }
    }

    private fun walk(node: UiNode, depth: Int, visit: (UiNode, Int) -> Unit) {
        visit(node, depth)
        node.children.forEach { walk(it, depth + 1, visit) }
    }

    private fun isFullScreenPager(node: UiNode, window: Bounds): Boolean {
        val className = node.className ?: return false
        if (!node.isScrollable) return false
        if (ReelsRules.pagerClassMarkers.none { className.contains(it) }) return false
        return node.bounds.width >= window.width * ReelsRules.MIN_PAGER_WIDTH_FRACTION &&
            node.bounds.height >= window.height * ReelsRules.MIN_PAGER_HEIGHT_FRACTION
    }

    /** The pager item that fills the pager, i.e. the reel being watched; null mid-swipe. */
    private fun visibleItemKey(pager: UiNode): Int? =
        pager.children.firstOrNull { item ->
            item.bounds.height >= pager.bounds.height * ReelsRules.MIN_ITEM_VISIBLE_FRACTION &&
                item.bounds.width >= pager.bounds.width * ReelsRules.MIN_ITEM_VISIBLE_FRACTION
        }?.key

    private fun isReelsTab(node: UiNode): Boolean {
        val byDescription = node.contentDescription?.trim()?.lowercase() in ReelsRules.reelsTabDescriptions
        val byId = node.viewId?.let { id -> ReelsRules.reelsTabIdSuffixes.any { id.endsWith(it) } } == true
        return byDescription || byId
    }

    private fun isSelectedOrHasSelectedChild(node: UiNode): Boolean =
        node.isSelected || node.children.any { it.isSelected }

    private fun hasViewerMarker(node: UiNode): Boolean {
        val id = node.viewId ?: return false
        return ReelsRules.reelsViewerIdMarkers.any { id.contains(it) }
    }
}
