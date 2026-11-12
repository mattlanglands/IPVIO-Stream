package com.ipvio.stream

import org.junit.Assert.assertEquals
import org.junit.Test

class GuideLogicTest {
    // Window 21:00-23:30 (150 min), times as minutes after 21:00 for readability
    private val ws = 75_600L
    private val span = 150 * 60L
    private fun at(min: Int) = ws + min * 60L
    private fun window(startMin: Int, endMin: Int, min: Long = 0L, max: Long = Long.MAX_VALUE) =
        GuideLogic.windowFor(ws, span, at(startMin), at(endMin), min, max)

    @Test fun visible_show_keeps_the_window() {
        assertEquals(ws, window(60, 120))     // 22:00-23:00
        assertEquals(ws, window(0, 120))      // 21:00-23:00
        assertEquals(ws, window(-60, 240))    // longer than the window, covers it
        assertEquals(ws, window(-60, 20))     // started earlier, 20 min still in view
        assertEquals(ws, window(130, 200))    // starts 20 min before the right edge
    }

    @Test fun show_barely_in_view_scrolls() {
        // Ended 10 min after the left edge: back so it is in view, rounded to the half hour
        assertEquals(at(-60), window(-60, 10))
        // Starts 10 min before the right edge: forward, it starts at the left edge
        assertEquals(at(120), window(140, 200))
    }

    @Test fun show_outside_scrolls() {
        assertEquals(at(180), window(195, 240))  // later: half hour before its start
        assertEquals(at(-90), window(-90, -30))  // earlier, short show: at its start
        // Earlier and long (6 h): its end in the middle of the window, not 6 h back
        assertEquals(at(-120), window(-480, -30))
    }

    @Test fun window_stays_in_bounds() {
        assertEquals(at(0), window(-90, -30, min = at(0)))
        assertEquals(at(30), window(195, 240, max = at(30)))
    }
}
