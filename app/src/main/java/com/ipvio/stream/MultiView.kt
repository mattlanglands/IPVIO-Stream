package com.ipvio.stream

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/** Layouts of the multi view, without Android, testable. */
object MultiViewLogic {
    const val TWO = 0      // two tiles of equal size side by side
    const val BIG = 1      // one large tile, one small one
    const val FOUR = 2     // 2 × 2
    const val ONE3 = 3     // one large tile, three small ones below it

    /** Short labels of the layout buttons (numbers, not translated). */
    val LAYOUT_LABELS = listOf("2", "1+1", "4", "1+3")

    /** Most tiles of any layout: more streams overload most boxes and many providers allow few connections. */
    const val SLOTS = 4

    fun slots(layout: Int): Int = if (layout == FOUR || layout == ONE3) 4 else 2

    /** Start channels: the first two distinct live channels from favorites, then history, then the list. */
    fun startChannels(live: List<Item>, favs: List<String>, hist: List<String>): List<Item> {
        val byId = live.associateBy { it.id }
        val out = LinkedHashMap<String, Item>()
        for (id in favs + hist) byId[id]?.let { out.putIfAbsent(it.id, it) }
        for (i in live) { if (out.size >= 2) break; out.putIfAbsent(i.id, i) }
        return out.values.take(2)
    }
}

/** The multi view keeps its layout and channels while the app runs (closing and opening again restores them). */
private object MultiMemory {
    var layout = MultiViewLogic.TWO
    var ids: List<String?> = emptyList()
    var audio = 0
}

/**
 * Multi view: up to four live streams at once. Left: layouts, full screen, mute, reset. Right: the tiles; only one has
 * sound (marked "Ton"). OK on a tile = sound to it, long OK = choose another channel (an empty tile asks at once).
 * Many providers allow only one connection per account; failing tiles say so. Opened full screen from the Live TV list.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MultiViewScreen(live: List<Item>, onClose: () -> Unit) {
    val slots = remember {
        mutableStateListOf<Item?>().also { l ->
            val byId = live.associateBy { it.id }
            val saved = MultiMemory.ids.map { id -> id?.let { byId[it] } }
            val start = if (saved.any { it != null }) saved else MultiViewLogic.startChannels(live, Library.favs.toList(), History.ids.toList())
            repeat(MultiViewLogic.SLOTS) { i -> l.add(start.getOrNull(i)) }
        }
    }
    var layout by remember { mutableIntStateOf(MultiMemory.layout) }
    var audio by remember { mutableIntStateOf(MultiMemory.audio) }
    var muted by remember { mutableStateOf(false) }
    var full by remember { mutableIntStateOf(-1) }
    var focused by remember { mutableIntStateOf(0) }
    var picking by remember { mutableIntStateOf(-1) }
    val n = MultiViewLogic.slots(layout)
    fun saveMemory() { MultiMemory.layout = layout; MultiMemory.ids = slots.map { it?.id }; MultiMemory.audio = audio }
    val firstTile = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { firstTile.requestFocus() } }

    Dialog(
        // Back: leave full screen first, then close
        onDismissRequest = { if (full >= 0) full = -1 else { saveMemory(); onClose() } },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        @Composable
        fun tile(i: Int, m: Modifier, req: FocusRequester? = null) {
            MultiTile(
                slots.getOrNull(i), audio == i && !muted, audio == i, m.then(if (req != null) Modifier.focusRequester(req) else Modifier),
                onFocus = { focused = i },
                onClick = { if (slots.getOrNull(i) == null) picking = i else { audio = i; muted = false } },
                onLong = { picking = i }
            )
        }
        if (full >= 0 && slots.getOrNull(full) != null) {
            // Full screen: only this stream plays (the others stop, so the box decodes one picture)
            Box(Modifier.fillMaxSize().background(Color.Black)) { tile(full, Modifier.fillMaxSize(), firstTile) }
            LaunchedEffect(full) { delay(60); runCatching { firstTile.requestFocus() } }
        } else Row(Modifier.fillMaxSize().background(Bg).padding(dpx(20)), horizontalArrangement = Arrangement.spacedBy(dpx(18))) {
            // Left: controls
            Column(Modifier.width(dpx(230)).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(dpx(10))) {
                Text("Mehrfachansicht", color = Tok.TextHi, fontSize = spx(26), fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text("${slots.take(n).count { it != null }} von $n Streams", color = Muted, fontSize = spx(14))
                Spacer(Modifier.height(dpx(4)))
                Text("Fenster", color = Muted, fontSize = spx(12), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(dpx(8))) {
                    MultiViewLogic.LAYOUT_LABELS.forEachIndexed { li, label ->
                        LayoutButton(label, li, li == layout, Modifier.weight(1f)) { layout = li; if (audio >= MultiViewLogic.slots(li)) audio = 0; saveMemory() }
                    }
                }
                Spacer(Modifier.height(dpx(4)))
                PanelButton(AIcon.Grid, "Vollbild", "Ausgewähltes Fenster füllt den Bildschirm") { if (slots.getOrNull(focused) != null) full = focused }
                PanelButton(AIcon.Live, if (muted) "Ton an" else "Ton aus", "Ton aller Fenster") { muted = !muted }
                PanelButton(AIcon.Refresh, "Zurücksetzen", "Alle Fenster leeren") {
                    for (i in slots.indices) slots[i] = null
                    audio = 0; saveMemory()
                }
                Spacer(Modifier.weight(1f))
                Text("OK = Ton hierher, OK lange = Sender wechseln", color = Muted, fontSize = spx(13))
                PanelButton(AIcon.Close, "Schließen", null) { saveMemory(); onClose() }
            }
            // Right: tiles in 16:9, centred in the free space
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                val gap = dpx(12)
                when (layout) {
                    MultiViewLogic.TWO -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        tile(0, Modifier.weight(1f).aspectRatio(16f / 9f), firstTile)
                        tile(1, Modifier.weight(1f).aspectRatio(16f / 9f))
                    }
                    MultiViewLogic.BIG -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                        tile(0, Modifier.weight(2f).aspectRatio(16f / 9f), firstTile)
                        tile(1, Modifier.weight(1f).aspectRatio(16f / 9f))
                    }
                    MultiViewLogic.FOUR -> {
                        // As large as fits: two rows of two 16:9 tiles
                        val w = minOf((maxWidth - gap) / 2, ((maxHeight - gap) / 2) * (16f / 9f))
                        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) { tile(0, Modifier.width(w).aspectRatio(16f / 9f), firstTile); tile(1, Modifier.width(w).aspectRatio(16f / 9f)) }
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) { tile(2, Modifier.width(w).aspectRatio(16f / 9f)); tile(3, Modifier.width(w).aspectRatio(16f / 9f)) }
                        }
                    }
                    else -> {
                        // One large on top, three small below: total height = big + small + gap
                        val small = minOf((maxWidth - gap * 2) / 3, ((maxHeight - gap) * (16f / 9f) - gap * 2 * (1f)) / 4)
                        val big = small * 3 + gap * 2
                        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                            tile(0, Modifier.width(big).aspectRatio(16f / 9f), firstTile)
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                tile(1, Modifier.width(small).aspectRatio(16f / 9f)); tile(2, Modifier.width(small).aspectRatio(16f / 9f)); tile(3, Modifier.width(small).aspectRatio(16f / 9f))
                            }
                        }
                    }
                }
            }
        }
        if (picking >= 0) GuideSearchDialog(live, onPick = { ch ->
            val p = picking
            if (p in slots.indices) slots[p] = ch
            if (slots.count { it != null } == 1) audio = p
            picking = -1
            saveMemory()
        }, onClose = { picking = -1 })
    }
}

/** One tile: stream (without its own frame), LIVE and sound marks, channel and current show at the bottom; empty = "+". */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MultiTile(item: Item?, audioOn: Boolean, hasAudio: Boolean, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit, onLong: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(DS.RadiusM)
    Box(
        modifier.clip(shape).background(Tok.Card)
            .border(if (f) 3.dp else 1.dp, if (f) Accent2 else Tok.Line, shape)
            .onFocusChanged { f = it.isFocused; if (it.isFocused) onFocus() }
            .combinedClickable(onClick = onClick, onLongClick = onLong)
    ) {
        if (item == null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(dpx(6))) {
                Box(Modifier.size(dpx(44)).clip(RoundedCornerShape(50)).background(if (f) Accent2 else Color(0x22FFFFFF)), contentAlignment = Alignment.Center) {
                    Text(raw = true, text = "+", color = if (f) Color.Black else Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
                Text("Sender hinzufügen", color = Tok.TextMid, fontSize = spx(14))
            }
            return@Box
        }
        key(item.id) { SecondStream(item, split = false, audioOn = audioOn, modifier = Modifier.fillMaxSize(), small = true, chrome = false) }
        // Marks on top
        Row(Modifier.fillMaxWidth().padding(dpx(8)), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(DS.RadiusXs)).background(PT.Live).padding(horizontal = dpx(6), vertical = dpx(1))) {
                Text("LIVE", color = Color.White, fontSize = spx(11), fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.weight(1f))
            if (hasAudio) Row(
                Modifier.clip(RoundedCornerShape(50)).background(if (audioOn) Accent2 else Color(0x99000000)).padding(horizontal = dpx(8), vertical = dpx(2)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(raw = true, text = (if (audioOn) "♪ " else "✕ ") + Lang.tr("Ton"), color = if (audioOn) Color.Black else Color.White, fontSize = spx(11), fontWeight = FontWeight.Bold)
            }
        }
        // Channel and show at the bottom, on a soft dark gradient
        val prog by produceState<EpgEntry?>(null, item.id) {
            while (true) {
                value = Epg.current(EpgFull.get(item))
                delay(60_000)
            }
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                .padding(start = dpx(10), end = dpx(10), top = dpx(18), bottom = dpx(8))
        ) {
            Text(raw = true, text = item.title, color = Color.White, fontSize = spx(15), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val p = prog
            if (p != null) Text(raw = true, text = p.title + " · " + hhmm(p.start) + " – " + hhmm(p.end), color = Color(0xFFD0D4E0), fontSize = spx(12), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Layout button: a small drawing of the tiles and the label. */
@Composable
private fun LayoutButton(label: String, layout: Int, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(DS.RadiusS)
    Column(
        modifier.height(dpx(58)).clip(shape)
            .background(if (f) RowFocus else if (on) Accent2.copy(alpha = 0.16f) else Tok.Card)
            .border(if (f || on) 2.dp else 1.dp, if (f) Accent2 else if (on) Accent2.copy(alpha = 0.6f) else Tok.Line, shape)
            .onFocusChanged { f = it.isFocused }
            .trackFocus()
            .combinedClickableNoLong(onClick)
            .padding(dpx(6)),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(dpx(4), Alignment.CenterVertically)
    ) {
        LayoutIcon(layout, if (on || f) Accent2 else Tok.TextMid, dpx(26))
        Text(raw = true, text = label, color = if (on) Accent2 else Tok.TextHi, fontSize = spx(12), fontWeight = FontWeight.Bold)
    }
}

/** Tiny drawing of a layout (boxes), so the buttons read without words. */
@Composable
private fun LayoutIcon(layout: Int, c: Color, w: Dp) {
    val h = w * 0.62f
    val g = 2.dp
    @Composable fun cell(m: Modifier) = Box(m.clip(RoundedCornerShape(1.5.dp)).background(c))
    Box(Modifier.width(w).height(h)) {
        when (layout) {
            MultiViewLogic.TWO -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(g)) { cell(Modifier.weight(1f).fillMaxHeight()); cell(Modifier.weight(1f).fillMaxHeight()) }
            MultiViewLogic.BIG -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(g)) { cell(Modifier.weight(2f).fillMaxHeight()); cell(Modifier.weight(1f).fillMaxHeight(0.5f).align(Alignment.CenterVertically)) }
            MultiViewLogic.FOUR -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(g)) {
                repeat(2) { Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(g)) { cell(Modifier.weight(1f).fillMaxHeight()); cell(Modifier.weight(1f).fillMaxHeight()) } }
            }
            else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(g)) {
                cell(Modifier.weight(2f).fillMaxWidth())
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(g)) { repeat(3) { cell(Modifier.weight(1f).fillMaxHeight()) } }
            }
        }
    }
}

/** Control button of the left panel: icon, title and a short description. */
@Composable
private fun ColumnScope.PanelButton(icon: AIcon, title: String, desc: String?, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(DS.RadiusS)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (f) RowFocus else Tok.Card)
            .border(if (f) 2.dp else 1.dp, if (f) Accent2 else Tok.Line, shape)
            .onFocusChanged { f = it.isFocused }
            .trackFocus()
            .combinedClickableNoLong(onClick)
            .padding(horizontal = dpx(12), vertical = dpx(9)),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dpx(10))
    ) {
        AppIcon(icon, if (f) Accent2 else Tok.TextMid, size = dpx(18))
        Column(Modifier.weight(1f)) {
            Text(title, color = Tok.TextHi, fontSize = spx(15), fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (desc != null) Text(desc, color = Muted, fontSize = spx(12), maxLines = 2)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableNoLong(onClick: () -> Unit): Modifier = combinedClickable(onClick = onClick)
