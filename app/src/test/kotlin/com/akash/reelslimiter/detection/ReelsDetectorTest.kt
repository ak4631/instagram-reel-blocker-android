package com.akash.reelslimiter.detection

import org.junit.Assert.assertEquals
import org.junit.Test

class ReelsDetectorTest {

    private val window = Bounds(0, 0, 1080, 2400)

    private fun screen(vararg children: UiNode) =
        UiNode(className = "android.widget.FrameLayout", bounds = window, children = children.toList())

    private fun pager(
        className: String = "androidx.viewpager.widget.ViewPager",
        bounds: Bounds = Bounds(0, 0, 1080, 2200),
        viewId: String? = null,
    ) = UiNode(className = className, viewId = viewId, isScrollable = true, bounds = bounds)

    private fun tab(description: String?, selected: Boolean, viewId: String? = null) =
        UiNode(
            className = "android.widget.FrameLayout",
            viewId = viewId,
            contentDescription = description,
            isSelected = selected,
            bounds = Bounds(0, 2200, 216, 2400),
        )

    private fun bottomBar(vararg tabs: UiNode) =
        UiNode(className = "android.widget.LinearLayout", bounds = Bounds(0, 2200, 1080, 2400), children = tabs.toList())

    @Test
    fun `reels tab selected with full-screen pager is Reels`() {
        val root = screen(pager(), bottomBar(tab("Home", false), tab("Reels", true)))
        assertEquals(Screen.REELS, ReelsDetector.detect(root).screen)
    }

    @Test
    fun `reels tab matched by id works for any language`() {
        val root = screen(pager(), bottomBar(tab("Réels", true, viewId = "com.instagram.android:id/clips_tab")))
        assertEquals(Screen.REELS, ReelsDetector.detect(root).screen)
    }

    @Test
    fun `selection on a child of the reels tab still counts`() {
        val reelsTab = tab("Reels", false).copy(children = listOf(UiNode(className = "android.widget.ImageView", isSelected = true)))
        val root = screen(pager(), bottomBar(reelsTab))
        assertEquals(Screen.REELS, ReelsDetector.detect(root).screen)
    }

    @Test
    fun `reel opened from a DM is Reels via the viewer marker`() {
        val root = screen(pager(viewId = "com.instagram.android:id/clips_viewer_view_pager", bounds = window))
        assertEquals(Screen.REELS, ReelsDetector.detect(root).screen)
    }

    @Test
    fun `home feed with a full-screen list is not Reels`() {
        val feed = pager(className = "androidx.recyclerview.widget.RecyclerView")
        val root = screen(feed, bottomBar(tab("Home", true), tab("Reels", false)))
        assertEquals(Screen.NOT_REELS, ReelsDetector.detect(root).screen)
    }

    @Test
    fun `DM thread is not Reels`() {
        val thread = pager(className = "androidx.recyclerview.widget.RecyclerView", bounds = Bounds(0, 200, 1080, 2100))
        assertEquals(Screen.NOT_REELS, ReelsDetector.detect(screen(thread)).screen)
    }

    @Test
    fun `reels tab selected but no full-screen pager is not Reels`() {
        val small = pager(bounds = Bounds(0, 0, 1080, 800))
        val root = screen(small, bottomBar(tab("Reels", true)))
        assertEquals(Screen.NOT_REELS, ReelsDetector.detect(root).screen)
    }

    @Test
    fun `non-scrollable pager is not Reels`() {
        val frozen = pager().copy(isScrollable = false)
        val root = screen(frozen, bottomBar(tab("Reels", true)))
        assertEquals(Screen.NOT_REELS, ReelsDetector.detect(root).screen)
    }

    @Test
    fun `viewer marker without pager is not Reels`() {
        val root = screen(UiNode(viewId = "com.instagram.android:id/clips_viewer_container", bounds = window))
        assertEquals(Screen.NOT_REELS, ReelsDetector.detect(root).screen)
    }

    @Test
    fun `missing or empty window is not Reels`() {
        assertEquals(Screen.NOT_REELS, ReelsDetector.detect(null).screen)
        assertEquals(Screen.NOT_REELS, ReelsDetector.detect(UiNode()).screen)
    }

    private fun reelItem(key: Int, top: Int = 0) =
        UiNode(className = "android.widget.FrameLayout", bounds = Bounds(0, top, 1080, minOf(top + 2200, 2200)), key = key)

    @Test
    fun `reel key is the pager item that fills the pager`() {
        val root = screen(pager().copy(children = listOf(reelItem(key = 42))), bottomBar(tab("Reels", true)))
        assertEquals(42, ReelsDetector.detect(root).reelKey)
    }

    @Test
    fun `mid-swipe has no reel key`() {
        val half = pager().copy(children = listOf(reelItem(key = 1, top = 0).copy(bounds = Bounds(0, 0, 1080, 1100)), reelItem(key = 2, top = 1100)))
        val root = screen(half, bottomBar(tab("Reels", true)))
        val detection = ReelsDetector.detect(root)
        assertEquals(Screen.REELS, detection.screen)
        assertEquals(null, detection.reelKey)
    }

    @Test
    fun `reel key comes from the innermost full-screen pager`() {
        val inner = pager(className = "androidx.recyclerview.widget.RecyclerView").copy(children = listOf(reelItem(key = 7)))
        val outer = pager(bounds = window).copy(children = listOf(UiNode(bounds = window, key = 99, children = listOf(inner))))
        val root = screen(outer, bottomBar(tab("Reels", true)))
        assertEquals(7, ReelsDetector.detect(root).reelKey)
    }

    @Test
    fun `not Reels has no reel key`() {
        val feed = pager(className = "androidx.recyclerview.widget.RecyclerView").copy(children = listOf(reelItem(key = 5)))
        assertEquals(null, ReelsDetector.detect(screen(feed, bottomBar(tab("Home", true)))).reelKey)
    }

    @Test
    fun `only tab labels are relevant descriptions`() {
        assertEquals(true, ReelsRules.isRelevantDescription(" Reels "))
        assertEquals(false, ReelsRules.isRelevantDescription("someone's profile picture"))
    }
}
