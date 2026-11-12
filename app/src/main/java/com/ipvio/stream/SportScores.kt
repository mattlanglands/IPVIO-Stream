package com.ipvio.stream

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Scores page and featured hero of the Sports Hub (rework of the 1.3 Live and Schedule tabs into one page).

/**
 * Scores: one page for live games, today, earlier and later days. Day switch and sport filter on top; on the left
 * "Live only", "My channels only" and the competitions to jump to; on the right one compact row per game
 * (status | home – score – away | channel ▶). [liveStart] = opened from "Live now" (starts with "Live only").
 */
@Composable
internal fun HubScores(feed: SportFeed.State, hub: HubCtx, sport: String?, liveStart: Boolean, onSport: (String?) -> Unit) {
    var day by remember { mutableIntStateOf(if (liveStart) 0 else HubMemory.day) }
    var liveOnly by remember { mutableStateOf(liveStart) }
    var mineOnly by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val leaguesKey = SportLeagues.off.joinToString("|")
    // Today comes from the shared live feed (refreshes by itself); other days are loaded once
    val other by produceState<List<SportData.Game>?>(null, day, leaguesKey, retry) {
        if (day == 0) { value = emptyList(); return@produceState }
        value = null
        val ymd = SportData.ymd(day, General.zone())
        val r = coroutineScope {
            SportLeagues.enabled().map { l -> async { SportData.fetch(l, ymd, 300_000) } }.awaitAll()
        }.flatten().distinctBy { it.key }
        SportFeed.put(r)
        value = r
    }
    val list = current(if (day == 0) feed.games else other.orEmpty())
    val loading = if (day == 0) feed.loading else other == null
    // Channel per game (team names in the TV guide), off the main thread
    val chanOf by produceState(emptyMap<String, Item>(), list) {
        value = withContext(Dispatchers.Default) { list.mapNotNull { g -> hub.chans(g).firstOrNull()?.let { g.key to it } }.toMap() }
    }
    val inSport = list.filter { sport == null || SportData.sportOf(it) == sport }
    val liveN = inSport.count { it.state == 1 }
    val counts = list.groupingBy { SportData.sportOf(it) }.eachCount()
    val shown = inSport.filter { (!liveOnly || it.state == 1) && (!mineOnly || it.key in chanOf) }
        .sortedWith(compareBy({ if (it.state == 1) 0 else 1 }, { popularity(it) }, { it.groupName }, { it.start }))
    // Flat list for the right column: competition headers and games; the header index is the jump target
    val flat = ArrayList<Any>()
    val headAt = LinkedHashMap<String, Int>()
    shown.groupBy { it.groupName }.forEach { (name, games) ->
        headAt[name] = flat.size
        flat.add(name)
        flat.addAll(games)
    }
    val rightLs = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        DaySwitch(day, -7..14) { day = it; HubMemory.day = it }
        Spacer(Modifier.height(dpx(6)))
        SportFilter(sport, counts) { onSport(it) }
        Row(Modifier.fillMaxSize().padding(start = DS.PageX, end = DS.PageX, top = dpx(8))) {
            // Left: filters and competitions
            LazyColumn(
                Modifier.width(dpx(250)).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(dpx(6)), contentPadding = PaddingValues(bottom = dpx(16))
            ) {
                item(key = "live") { ToggleRow("Nur live", liveOnly, if (day == 0) liveN else null, dot = true) { liveOnly = !liveOnly } }
                item(key = "mine") { ToggleRow("Nur meine Sender", mineOnly, null) { mineOnly = !mineOnly } }
                if (headAt.size > 1) {
                    item(key = "jt") {
                        Text("Springe zu", color = Muted, fontSize = spx(13), fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = dpx(12), top = dpx(12), bottom = dpx(2)))
                    }
                    headAt.forEach { (name, at) ->
                        item(key = "c$name") {
                            val games = shown.filter { it.groupName == name }
                            FRow({ scope.launch { rightLs.animateScrollToItem(at) } }) {
                                val l = SportData.leagueOf(games.first())
                                TeamLogo(l?.let { SportData.leagueLogos[it.path] }, "", dpx(20))
                                Text(raw = true, text = name, color = Tok.TextHi, fontSize = spx(14), fontWeight = FontWeight.SemiBold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                if (games.any { SportHubFavs.isFavGame(it) }) AppIcon(AIcon.Star, DS.Gold, size = dpx(14))
                                if (games.any { it.state == 1 }) Box(Modifier.size(dpx(7)).clip(RoundedCornerShape(50)).background(PT.Live))
                                Text(raw = true, text = games.size.toString(), color = Muted, fontSize = spx(13))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(dpx(18)))
            // Right: the games
            LazyColumn(state = rightLs, modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(dpx(6)),
                contentPadding = PaddingValues(bottom = dpx(24))) {
                when {
                    loading -> item(key = "load") { HubLoading("Lade Spiele …") }
                    list.isEmpty() && !SportData.lastOk -> item(key = "err") {
                        HubEmpty(AIcon.Warning, "Daten konnten nicht geladen werden", "ESPN antwortet gerade nicht. Bitte versuche es erneut.", "Erneut versuchen") { retry++ }
                    }
                    shown.isEmpty() -> item(key = "empty") {
                        if (liveOnly && liveN == 0) HubEmpty(AIcon.Live, "Gerade läuft nichts", "Sobald ein Spiel deiner Ligen beginnt, erscheint es hier.")
                        else HubEmpty(AIcon.Info, "Keine Spiele für diese Auswahl.", "Ändere die Filter oder schau in den Spielplan.")
                    }
                    else -> itemsIndexed(flat, key = { _, e -> if (e is String) "h$e" else "g" + (e as SportData.Game).key }) { _, e ->
                        if (e is String) {
                            val games = shown.filter { it.groupName == e }
                            val l = SportData.leagueOf(games.first())
                            CompetitionHeader(e, l?.let { SportData.leagueLogos[it.path] }, games.count { it.state == 1 }, games.size)
                        } else {
                            val g = e as SportData.Game
                            ScoreRow(g, chanOf[g.key], onOpen = { hub.open(g) }, onWatch = { hub.watch(g) })
                        }
                    }
                }
            }
        }
    }
}

/** Day switch: ‹ Yesterday Today Tomorrow ›, plus the chosen day when it is further away. */
@Composable
internal fun DaySwitch(day: Int, range: IntRange, onDay: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = DS.PageX), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dpx(8))) {
        FilterPill("", false, Modifier.trackFocus(), aicon = AIcon.Left) { onDay((day - 1).coerceIn(range)) }
        if (day < -1) FilterPill(dayLabel(System.currentTimeMillis() / 1000 + day * 86_400L), true, Modifier.trackFocus(), raw = true) { }
        FilterPill("Gestern", day == -1, Modifier.trackFocus()) { onDay(-1) }
        FilterPill("Heute", day == 0, Modifier.trackFocus()) { onDay(0) }
        FilterPill("Morgen", day == 1, Modifier.trackFocus()) { onDay(1) }
        if (day > 1) FilterPill(dayLabel(System.currentTimeMillis() / 1000 + day * 86_400L), true, Modifier.trackFocus(), raw = true) { }
        FilterPill("", false, Modifier.trackFocus(), aicon = AIcon.Right) { onDay((day + 1).coerceIn(range)) }
    }
}

/** Filter row with a switch on the right (and an optional count). */
@Composable
private fun ToggleRow(label: String, on: Boolean, count: Int?, dot: Boolean = false, onClick: () -> Unit) {
    FRow(onClick, Modifier.clip(RoundedCornerShape(DS.RadiusS)).background(Tok.Card)) {
        if (dot) Box(Modifier.size(dpx(7)).clip(RoundedCornerShape(50)).background(PT.Live))
        Text(label, color = Tok.TextHi, fontSize = spx(15), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
        if (count != null) Text(raw = true, text = count.toString(), color = Muted, fontSize = spx(14))
        // Switch
        Box(
            Modifier.width(dpx(38)).height(dpx(22)).clip(RoundedCornerShape(50)).background(if (on) Accent2 else DS.Overlay3).padding(dpx(3)),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
        ) { Box(Modifier.size(dpx(16)).clip(RoundedCornerShape(50)).background(Color.White)) }
    }
}

/**
 * Compact score row: status/time | home name + logo | score box | logo + away name | channel with ▶ (own focus target:
 * OK there starts the channel at once). OK on the row opens the Match Center.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ScoreRow(g: SportData.Game, ch: Item?, onOpen: () -> Unit, onWatch: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(DS.RadiusS)
    val k = statusKind(g)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dpx(8))) {
        Row(
            Modifier.weight(1f).clip(shape)
                .background(if (f) Tok.CardFocus else Tok.Card)
                .border(if (f) DS.Border else 1.dp, if (f) Accent2 else Tok.Line, shape)
                .onFocusChanged { f = it.isFocused }
                .trackFocus()
                .combinedClickable(onClick = onOpen, onLongClick = if (ch != null) onWatch else null)
                .padding(horizontal = dpx(14), vertical = dpx(9)),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dpx(12))
        ) {
            // Status: minute in red while live, start time while upcoming, FT etc. when done
            Box(Modifier.width(dpx(84))) {
                when (k) {
                    1 -> Text(raw = true, text = statusText(g), color = PT.Live, fontSize = spx(15), fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    0 -> Text(raw = true, text = hhmm(g.start), color = Tok.TextHi, fontSize = spx(15), fontWeight = FontWeight.Bold, maxLines = 1)
                    else -> MatchStatus(g)
                }
            }
            if (g.solo) {
                Column(Modifier.weight(1f)) {
                    Text(raw = true, text = listOf(g.title, g.note).filter { it.isNotBlank() }.joinToString(" · "), color = Tok.TextHi, fontSize = spx(16),
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val lead = g.field.firstOrNull()
                    if (lead != null) Text(raw = true, text = "1. " + lead.name + (if (lead.value.isNotBlank()) "  " + lead.value else ""), color = Muted, fontSize = spx(13), maxLines = 1)
                }
            } else {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dpx(8), Alignment.End)) {
                    if (SportHubFavs.isFavGame(g)) AppIcon(AIcon.Star, DS.Gold, size = dpx(14))
                    TeamText(g.home, g.state, Modifier.weight(1f, fill = false), TextAlign.End)
                    TeamLogo(g.home.logo, g.home.abbr, dpx(24))
                }
                ScoreBox(g)
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dpx(8))) {
                    TeamLogo(g.away.logo, g.away.abbr, dpx(24))
                    TeamText(g.away, g.state, Modifier.weight(1f, fill = false), TextAlign.Start)
                }
            }
            Text(raw = true, text = ch?.title ?: "", color = Muted, fontSize = spx(13), maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End, modifier = Modifier.width(dpx(130)))
        }
        // ▶: watch on the matched channel; without a channel the slot stays empty so the columns line up
        Box(Modifier.size(dpx(40)), contentAlignment = Alignment.Center) {
            if (ch != null) PlayDot(onWatch)
        }
    }
}

@Composable
private fun TeamText(t: SportData.Team, state: Int, modifier: Modifier, align: TextAlign) {
    Text(
        raw = true, text = t.short, color = if (state == 2 && !t.winner && t.score != null) Muted else Tok.TextHi,
        fontSize = spx(16), fontWeight = if (t.winner) FontWeight.ExtraBold else FontWeight.SemiBold, maxLines = 1,
        overflow = TextOverflow.Ellipsis, textAlign = align, modifier = modifier
    )
}

/** Score in a small dark box ("1 : 0"), "v" before the start; long scores (cricket) in two lines. */
@Composable
private fun ScoreBox(g: SportData.Game) {
    val long = (g.home.score?.length ?: 0) > 6 || (g.away.score?.length ?: 0) > 6
    Box(
        Modifier.widthIn(min = dpx(76)).clip(RoundedCornerShape(DS.RadiusXs))
            .background(if (g.state == 0) Color.Transparent else Color(0x33000000))
            .padding(horizontal = dpx(10), vertical = dpx(3)),
        contentAlignment = Alignment.Center
    ) {
        when {
            g.state == 0 -> Text(raw = true, text = "v", color = Muted, fontSize = spx(15), fontWeight = FontWeight.Bold)
            long -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(raw = true, text = g.home.score ?: "", color = Tok.TextHi, fontSize = spx(13), fontWeight = FontWeight.Bold, maxLines = 1)
                Text(raw = true, text = g.away.score ?: "", color = Tok.TextHi, fontSize = spx(13), fontWeight = FontWeight.Bold, maxLines = 1)
            }
            else -> Text(raw = true, text = "${g.home.score ?: "-"} : ${g.away.score ?: "-"}", color = Tok.TextHi, fontSize = spx(19), fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

/** Round ▶ button (its own focus target). */
@Composable
private fun PlayDot(onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Box(
        Modifier.size(dpx(34)).clip(RoundedCornerShape(50))
            .background(if (f) Accent2 else Accent2.copy(alpha = 0.22f))
            .onFocusChanged { f = it.isFocused }
            .trackFocus()
            .combinedClickableCompat(onClick),
        contentAlignment = Alignment.Center
    ) { AppIcon(AIcon.Play, if (f) Color.Black else Accent2, size = dpx(16)) }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier = combinedClickable(onClick = onClick)

/**
 * Featured game, left-aligned like a TV hero: marks (favorite, LIVE minute, competition), the two teams big with
 * their scores, the channel it runs on, Watch live and Match Center. Background in the team colors.
 */
@Composable
internal fun FeaturedHero(g: SportData.Game, hub: HubCtx) {
    val shape = RoundedCornerShape(DS.RadiusL)
    val chans = hub.chans(g)
    val a = teamColor(g.home.color) ?: Accent1
    val b = teamColor(g.away.color) ?: Accent2
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.horizontalGradient(listOf(a.copy(alpha = 0.32f), Tok.Card, b.copy(alpha = 0.18f))))
            .border(1.dp, Tok.Line, shape)
            .padding(horizontal = dpx(28), vertical = dpx(20)),
        verticalArrangement = Arrangement.spacedBy(dpx(12))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dpx(10))) {
            if (SportHubFavs.isFavGame(g)) Row(
                Modifier.clip(RoundedCornerShape(50)).background(DS.Gold.copy(alpha = 0.18f)).padding(horizontal = dpx(10), vertical = dpx(3)),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dpx(6))
            ) {
                AppIcon(AIcon.Star, DS.Gold, size = dpx(14))
                Text("Favorit", color = DS.Gold, fontSize = spx(13), fontWeight = FontWeight.Bold)
            }
            MatchStatus(g)
            Text(raw = true, text = listOf(g.groupName, g.note).filter { it.isNotBlank() }.distinct().joinToString(" · "), color = Tok.TextMid,
                fontSize = spx(15), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (g.solo) MatchHeader(g)
        else Column(Modifier.widthIn(max = dpx(640)), verticalArrangement = Arrangement.spacedBy(dpx(6))) {
            HeroTeam(g.home, g)
            HeroTeam(g.away, g)
        }
        if (chans.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dpx(8))) {
            AppIcon(AIcon.Live, Tok.TextMid, size = dpx(16))
            Text(raw = true, text = chans[0].title, color = Tok.TextHi, fontSize = spx(15), fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(raw = true, text = "✓ " + Lang.tr("Auf deinem Sender"), color = Accent2, fontSize = spx(14), maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(dpx(14)), verticalAlignment = Alignment.CenterVertically) {
            WatchLiveButton(chans) { hub.watch(g) }
            HeroButton("Match Center", AIcon.Info, false, Modifier, 46) { hub.open(g) }
        }
    }
}

@Composable
private fun HeroTeam(t: SportData.Team, g: SportData.Game) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dpx(14))) {
        Box(Modifier.size(dpx(46)).clip(RoundedCornerShape(50)).background(Color(0x26FFFFFF)), contentAlignment = Alignment.Center) {
            TeamLogo(t.logo, t.abbr, dpx(34))
        }
        Text(raw = true, text = t.name, color = if (g.state == 2 && !t.winner && t.score != null) Tok.TextMid else Tok.TextHi,
            fontSize = spx(34), fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (g.state != 0) Text(raw = true, text = t.score ?: "", color = Accent2, fontSize = spx(if ((t.score?.length ?: 0) > 6) 20 else 38), fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}
