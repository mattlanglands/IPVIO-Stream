package com.ipvio.stream

/** TV guide time window, without Android, testable. */
object GuideLogic {
    /**
     * Start of the time window after a show gets the focus (all values in seconds).
     *
     * The window stays where it is while the show can be seen in it, like on a cable or satellite box: moving Up/Down to
     * another channel keeps the time axis still. It only scrolls when the show lies (almost) outside the window: forward so
     * the show starts at the left edge, back so it is in view (a long show with its end in the middle). The new start is
     * rounded down to the half hour, so the time marks stay in place.
     */
    fun windowFor(winStart: Long, span: Long, start: Long, end: Long, min: Long, max: Long): Long {
        val winEnd = winStart + span
        // At least this much of the show has to be in the window (15 min, less for a very narrow window)
        val margin = minOf(span / 6, 900L)
        val inside = start >= winStart && end <= winEnd
        val visible = inside || (start < winEnd - margin && end > winStart + margin)
        if (visible) return winStart.coerceIn(min, maxOf(min, max))
        val target = if (start < winStart) maxOf(start, end - span / 2) else start
        return (target - Math.floorMod(target, 1800L)).coerceIn(min, maxOf(min, max))
    }
}
