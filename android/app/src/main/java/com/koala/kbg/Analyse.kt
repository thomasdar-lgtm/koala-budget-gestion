package com.koala.kbg

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

private val PAL = listOf(0xFF6C63FF, 0xFFFF6584, 0xFF4ECCA3, 0xFFFFD166, 0xFFA29BFE, 0xFFFD79A8, 0xFF00CEC9, 0xFFE17055, 0xFF74B9FF, 0xFF81ECEC, 0xFFFAB1A0).map { Color(it) }

@Composable
fun AnalyseScreen() {
    val tab = Nav.anTab
    Page {
        Header("Analyse", icon = { TabIcon(R.drawable.nav_ana) })
        Seg(listOf("cat" to "Catég.", "bilan" to "Bilan", "solde" to "Solde", "mois" to "Par mois", "histo" to "Histo"), tab) { Nav.anTab = it }
        Spacer(Modifier.height(6.dp))
        when (tab) {
            "cat" -> CatTab()
            "bilan" -> BilanTab()
            "solde" -> SoldeTab()
            "mois" -> MoisTab()
            else -> HistoTab()
        }
    }
}

@Composable
private fun CatTab() {
    var ym by rememberSaveable { mutableStateOf(Logic.prevM(Logic.ym(Db.today))) }
    val s = Db.s
    MonthNav(ym) { ym = it }
    val e = Logic.parCat(s, ym)
    if (e.isEmpty()) { Card { Empty("🍩", "Aucune dépense ce mois") }; return }
    val total = e.sumOf { it.second }
    Card {
        Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(190.dp)) {
                val sw = 34.dp.toPx(); var a = -90f
                e.forEachIndexed { i, (_, v) ->
                    val sweep = (v / total * 360).toFloat()
                    drawArc(PAL[i % PAL.size], a, sweep - 1.2f, false, topLeft = Offset(sw / 2, sw / 2),
                        size = Size(size.width - sw, size.height - sw), style = Stroke(sw))
                    a += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                T("Dépenses", 11.sp, K.Tx2); T(Logic.fmt(total), 16.sp, K.Tx, FontWeight.ExtraBold)
            }
        }
    }
    Card(pad = 12.dp) {
        e.forEachIndexed { i, (c, v) ->
            if (i > 0) Divider()
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(PAL[i % PAL.size]))
                Spacer(Modifier.width(10.dp)); CatIcon(c, 14.sp); Spacer(Modifier.width(6.dp))
                T(c, 13.5.sp, K.Tx, modifier = Modifier.weight(1f), lines = 1)
                Column(horizontalAlignment = Alignment.End) {
                    T(Logic.fmt(v), 13.5.sp, K.Re, FontWeight.Bold); T("${Math.round(v / total * 100)} %", 11.sp, K.Tx3)
                }
            }
        }
    }
}

@Composable
private fun BilanTab() {
    val ym = Nav.anYM ?: Logic.prevM(Logic.ym(Db.today))
    MonthNav(ym) { Nav.anYM = it }
    val b = Logic.bilan(Db.s, ym, Db.today)
    if (b.vide) Card { Empty("📋", "Aucune opération ce mois") } else BilanCard(b)
}

@Composable
private fun SoldeTab() {
    val s = Db.s; val today = Db.today
    if (s.solde == null) { Card { Empty("📈", "Saisis ton solde bancaire sur l'accueil pour voir son évolution.") }; return }
    val ms = Logic.lastMonths(12, today)
    val v = ms.map { Logic.soldeFinMois(s, it, today) ?: 0.0 }
    Card {
        Sec("Solde en fin de mois")
        LineChart(ms.map { Logic.MFR[it.substring(5, 7).toInt() - 1] + "'" + it.substring(2, 4) }, v)
    }
}

@Composable
private fun MoisTab() {
    val s = Db.s
    val ms = Logic.lastMonths(6, Db.today)
    val cr = ms.map { m -> s.tx.filter { it.mois == m && it.sens == CREDIT }.sumOf { it.montant } }
    val db = ms.map { m -> s.tx.filter { it.mois == m && it.sens == DEBIT }.sumOf { it.montant } }
    Card {
        Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(K.Gr)); T("  Entrées    ", 12.sp, K.Tx2)
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(K.Re)); T("  Sorties", 12.sp, K.Tx2)
        }
        BarChart(ms.map { Logic.MFR[it.substring(5, 7).toInt() - 1] }, cr, db)
    }
    Card(pad = 12.dp) {
        ms.indices.reversed().forEachIndexed { k, i ->
            if (k > 0) Divider()
            val net = cr[i] - db[i]
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                T(Logic.fmtM(ms[i]), 13.sp, K.Tx, modifier = Modifier.weight(1f))
                T((if (net >= 0) "+" else "-") + Logic.fmt(net), 13.sp, if (net >= 0) K.Gr else K.Re, FontWeight.Bold)
            }
        }
    }
}

// ── graphiques ─────────────────────────────────────────────
private fun niceStep(range: Double, n: Int = 4): Double {
    if (range <= 0) return 1.0
    val raw = range / n; val p = 10.0.pow(floor(log10(raw)))
    val f = raw / p
    return p * when { f <= 1 -> 1.0; f <= 2 -> 2.0; f <= 2.5 -> 2.5; f <= 5 -> 5.0; else -> 10.0 }
}

private fun euros(v: Double): String {
    val r = Math.round(v)
    val s = abs(r).toString().reversed().chunked(3).joinToString(" ").reversed()
    return (if (r < 0) "-" else "") + s + "€"
}

private fun DrawScope.axis(lo: Double, hi: Double, step: Double, left: Float, top: Float, h: Float, paint: Paint) {
    var y = lo
    while (y <= hi + step / 2) {
        val py = top + h - ((y - lo) / (hi - lo) * h).toFloat()
        drawLine(K.Bd, Offset(left, py), Offset(size.width, py), 1f)
        drawContext.canvas.nativeCanvas.drawText(euros(y), left - 6.dp.toPx(), py + 4.dp.toPx(), paint)
        y += step
    }
}

private fun labelPaint(px: Float, align: Paint.Align) = Paint().apply {
    color = K.Tx2.toArgb(); textSize = px; isAntiAlias = true; textAlign = align
}

@Composable
private fun LineChart(labels: List<String>, v: List<Double>) {
    Canvas(Modifier.fillMaxWidth().height(230.dp)) {
        val left = 54.dp.toPx(); val top = 8.dp.toPx(); val bottom = 22.dp.toPx()
        val h = size.height - top - bottom; val w = size.width - left - 8.dp.toPx()
        val mn = minOf(0.0, v.minOrNull() ?: 0.0); val mx = maxOf(0.0, v.maxOrNull() ?: 0.0)
        val step = niceStep(mx - mn)
        val lo = floor(mn / step) * step; val hi = maxOf(lo + step, ceil(mx / step) * step)
        axis(lo, hi, step, left, top, h, labelPaint(10.dp.toPx(), Paint.Align.RIGHT))
        val pts = v.mapIndexed { i, x ->
            Offset(left + 8.dp.toPx() + w * i / (v.size - 1).coerceAtLeast(1), top + h - ((x - lo) / (hi - lo) * h).toFloat())
        }
        val fill = Path().apply {
            moveTo(pts.first().x, top + h); pts.forEach { lineTo(it.x, it.y) }; lineTo(pts.last().x, top + h); close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(K.Ac.copy(alpha = 0.28f), K.Ac.copy(alpha = 0.02f)), top, top + h))
        val line = Path().apply { pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
        drawPath(line, K.Ac, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        pts.forEach { drawCircle(K.Ac, 3.5.dp.toPx(), it) }
        val lp = labelPaint(9.5.dp.toPx(), Paint.Align.CENTER)
        labels.forEachIndexed { i, l -> if (i % 2 == (labels.size - 1) % 2) drawContext.canvas.nativeCanvas.drawText(l, pts[i].x, size.height - 4.dp.toPx(), lp) }
    }
}

@Composable
private fun BarChart(labels: List<String>, a: List<Double>, b: List<Double>) {
    Canvas(Modifier.fillMaxWidth().height(220.dp)) {
        val left = 54.dp.toPx(); val top = 8.dp.toPx(); val bottom = 22.dp.toPx()
        val h = size.height - top - bottom; val w = size.width - left
        val mx = maxOf(a.maxOrNull() ?: 0.0, b.maxOrNull() ?: 0.0, 1.0)
        val step = niceStep(mx); val hi = ceil(mx / step) * step
        axis(0.0, hi, step, left, top, h, labelPaint(10.dp.toPx(), Paint.Align.RIGHT))
        val gw = w / labels.size; val bw = gw * 0.3f
        val lp = labelPaint(10.dp.toPx(), Paint.Align.CENTER)
        labels.forEachIndexed { i, l ->
            val cx = left + gw * i + gw / 2
            listOf(a[i] to K.Gr, b[i] to K.Re).forEachIndexed { k, (x, c) ->
                val bh = (x / hi * h).toFloat()
                drawRoundRect(c.copy(alpha = 0.75f), Offset(cx - bw - 1.5f + k * (bw + 3f), top + h - bh), Size(bw, bh),
                    androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            }
            drawContext.canvas.nativeCanvas.drawText(l, cx, size.height - 4.dp.toPx(), lp)
        }
    }
}

// ── historique ─────────────────────────────────────────────
@Composable
private fun HistoTab() {
    val s = Db.s
    var q by rememberSaveable { mutableStateOf("") }
    var mo by rememberSaveable { mutableStateOf("") }
    var ca by rememberSaveable { mutableStateOf("") }
    var se by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableIntStateOf(1) }
    Field(q, { q = it; page = 1 }, "🔍 Rechercher…")
    val months = s.tx.map { it.mois }.distinct().sortedDescending()
    Pills(listOf("" to "Tous les mois") + months.map { it to Logic.fmtM(it) }, mo) { mo = it; page = 1 }
    Pills(listOf("" to "Toutes catégories") + Cats.ALL.map { it to (if (it == Cats.POKE) "🃏" else Cats.ico(it)) + " " + it }, ca) { ca = it; page = 1 }
    Seg(listOf("" to "Tout", DEBIT to "Dépenses", CREDIT to "Entrées"), se) { se = it; page = 1 }
    val f = s.tx.filter {
        (q.isBlank() || it.libelle.contains(q.trim(), ignoreCase = true)) && (mo.isEmpty() || it.mois == mo) &&
            (ca.isEmpty() || Logic.cat(s, it) == ca) && (se.isEmpty() || it.sens == se)
    }.sortedByDescending { it.date }
    val shown = f.take(20 * page)
    Spacer(Modifier.height(6.dp))
    Card(pad = 12.dp) {
        if (shown.isEmpty()) Empty("📋", "Aucune opération trouvée")
        shown.forEachIndexed { i, t -> if (i > 0) Divider(); TxRow(s, t) { Nav.open(Sheet.EditTx(t.id)) } }
    }
    if (f.size > shown.size) BtnGhost("Voir plus (${f.size - shown.size} restantes)") { page++ }
}

@Composable
private fun Pills(options: List<Pair<String, String>>, value: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (k, l) ->
            val on = k == value
            Box(Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) K.Ac else K.Sf2).clickable { onChange(k) }
                .padding(horizontal = 12.dp, vertical = 7.dp)) { T(l, 12.sp, if (on) Color.White else K.Tx2, FontWeight.Medium, align = TextAlign.Center) }
        }
    }
}
