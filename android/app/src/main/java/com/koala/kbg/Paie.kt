package com.koala.kbg

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val MOIS_NOMS = listOf("Janvier", "Février", "Mars", "Avril", "Mai", "Juin", "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre")

/** Bulletins de paie : une année en grille de 12 mois. Toucher un mois ouvre son PDF, ⋯ pour le modifier. */
@Composable
fun PaieScreen() {
    val ctx = LocalContext.current
    val s = Db.s; val today = Db.today
    LaunchedEffect(Unit) { val l = withContext(Dispatchers.IO) { Db.netsManquants() }; Db.appliquerNets(l) }
    val annees = (s.paie.map { it.ym.take(4) } + today.year.toString()).distinct().sortedDescending()
    var an by remember { mutableStateOf(s.paie.maxOfOrNull { it.ym }?.take(4) ?: today.year.toString()) }
    val duAn = s.paie.filter { it.ym.startsWith(an) }
    val total = duAn.sumOf { it.net ?: 0.0 }
    val ymNow = Logic.ym(today)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 30.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(K.Sf2).clickable { Nav.page = null }, contentAlignment = Alignment.Center) {
                T("‹", 24.sp, K.Tx2)
            }
            Spacer(Modifier.width(12.dp))
            T("Bulletins de paie", 22.sp, K.Tx, FontWeight.ExtraBold, Modifier.weight(1f), lines = 1)
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(K.Ac).clickable { Nav.paieMois = null; Db.dropStaged(); Nav.paieMultiReq++ },
                contentAlignment = Alignment.Center) { T("+", 24.sp, Color.White, FontWeight.Light) }
        }

        Hero {
            HeroLabel("Net perçu en $an")
            HeroAmount(Logic.fmt(total))
            T("${duAn.size} bulletin" + (if (duAn.size > 1) "s" else ""), 12.5.sp, K.HeroLbl, modifier = Modifier.padding(top = 2.dp))
        }

        if (annees.size > 1) Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            annees.forEach { y ->
                val on = y == an
                Box(Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) K.Ac else K.Sf2).clickable { an = y }
                    .padding(horizontal = 16.dp, vertical = 8.dp)) { T(y, 13.sp, if (on) Color.White else K.Tx2, FontWeight.Bold) }
            }
        }

        (0 until 4).forEach { r ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                (0 until 3).forEach { k ->
                    val m = r * 3 + k
                    val ym = "$an-" + (m + 1).toString().padStart(2, '0')
                    val p = duAn.find { it.ym == ym }
                    Box(Modifier.weight(1f)) { MoisCase(MOIS_NOMS[m], p, futur = ym > ymNow,
                        onOpen = { if (!Db.openPaie(ctx, ym)) Nav.notify("Aucune appli pour lire les PDF", true) },
                        onEdit = { Nav.open(Sheet.PaieEdit(ym)) },
                        onAdd = { Nav.paieMois = ym; Db.dropStaged(); Nav.paieReq++ }) }
                }
            }
        }
    }
}

@Composable
private fun MoisCase(nom: String, p: Paie?, futur: Boolean, onOpen: () -> Unit, onEdit: () -> Unit, onAdd: () -> Unit) {
    val sh = RoundedCornerShape(14.dp)
    if (p != null) {
        Column(Modifier.fillMaxWidth().height(86.dp).clip(sh).background(K.Sf).border(1.dp, K.Ac.copy(alpha = 0.35f), sh)
            .clickable(onClick = onOpen).padding(start = 12.dp, top = 8.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(nom, 12.5.sp, K.Tx2, FontWeight.SemiBold, Modifier.weight(1f), lines = 1)
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onEdit), contentAlignment = Alignment.Center) {
                    T("⋯", 16.sp, K.Tx3, FontWeight.Bold)
                }
            }
            Spacer(Modifier.weight(1f))
            T(p.net?.let { Logic.fmt(it).removeSuffix(" €").substringBefore(',') + " €" } ?: "📄", 17.sp, K.Tx, FontWeight.ExtraBold, lines = 1)
        }
    } else {
        Box(Modifier.fillMaxWidth().height(86.dp).clip(sh)
            .drawBehind {
                drawRoundRect(if (futur) K.Bd.copy(alpha = 0.4f) else K.Bd, cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
            }
            .then(if (futur) Modifier else Modifier.clickable(onClick = onAdd))
            .padding(12.dp)) {
            T(nom, 12.5.sp, if (futur) K.Tx3.copy(alpha = 0.5f) else K.Tx3, FontWeight.SemiBold, lines = 1)
            if (!futur) T("+", 20.sp, K.Tx3, FontWeight.Light, Modifier.align(Alignment.BottomStart))
        }
    }
}

// ── modifier / ranger un bulletin ──────────────────────────
@Composable
internal fun PaieEditSheet(ym0: String?) {
    val ctx = LocalContext.current
    val p0 = ym0?.let { y -> Db.s.paie.find { it.ym == y } }
    var ym by remember { mutableStateOf(p0?.ym ?: Nav.paieMois ?: Db.stagedYm ?: Logic.paieMoisDefaut(Db.today)) }
    var net by remember { mutableStateOf(numStr(p0?.net ?: if (Db.hasStaged()) Db.stagedNet else null)) }
    SheetTitle(if (p0 != null) Logic.fmtM(p0.ym) else "Nouveau bulletin")
    if (p0 != null) Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Btn("Ouvrir", Modifier.weight(1f)) { if (!Db.openPaie(ctx, p0.ym)) Nav.notify("Aucune appli pour lire les PDF", true) }
        Btn("Partager", Modifier.weight(1f), K.Sf2, K.Tx) { if (!Db.sharePaie(ctx, p0.ym)) Nav.notify("Partage impossible", true) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Row(Modifier.weight(1f).padding(top = 8.dp).height(56.dp).clip(RoundedCornerShape(10.dp)).background(K.Sf2), verticalAlignment = Alignment.CenterVertically) {
            T("‹", 22.sp, K.Tx2, modifier = Modifier.padding(horizontal = 12.dp).tap { ym = Logic.prevM(ym) })
            T(Logic.fmtM(ym), 13.5.sp, K.Tx, FontWeight.SemiBold, Modifier.weight(1f), TextAlign.Center)
            T("›", 22.sp, K.Tx2, modifier = Modifier.padding(horizontal = 12.dp).tap { ym = Logic.nextM(ym) })
        }
        Field(net, { net = it }, "Net €", Modifier.width(120.dp), number = true)
    }
    if (Db.s.paie.any { it.ym == ym && it.ym != p0?.ym }) T("⚠️ Remplace le bulletin de ${Logic.fmtM(ym)}", 12.sp, K.Ye, modifier = Modifier.padding(bottom = 10.dp))
    Btn("Enregistrer") {
        if (p0 == null && !Db.hasStaged()) { Nav.notify("Choisis d'abord le PDF", true); return@Btn }
        if (Db.savePaie(ym, num(net)?.let { Logic.r2(it) }, p0?.ym)) { Nav.paieMois = null; Nav.close(); Nav.notify("📄 ${Logic.fmtM(ym)}") }
        else Nav.notify("Enregistrement impossible", true)
    }
    if (p0 != null) Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        T("Remplacer le PDF", 13.sp, K.Tx2, FontWeight.SemiBold, Modifier.clip(RoundedCornerShape(8.dp))
            .clickable { Nav.paieMois = p0.ym; Db.dropStaged(); Nav.close(); Nav.paieReq++ }.padding(8.dp))
        T("Supprimer", 13.sp, K.Re, FontWeight.SemiBold, Modifier.clip(RoundedCornerShape(8.dp)).clickable {
            Nav.confirm("Supprimer ce bulletin ?", Logic.fmtM(p0.ym), "Supprimer") { Db.delPaie(p0.ym); Nav.close() }
        }.padding(8.dp))
    }
}

/** plusieurs bulletins choisis d'un coup */
@Composable
internal fun PaieLotSheet() {
    val lots = Nav.lot
    val mois = remember {
        val pris = lots.mapNotNull { it.ym }.toMutableSet()
        var d = Logic.paieMoisDefaut(Db.today)
        mutableStateListOf(*lots.map { l -> l.ym ?: run { while (d in pris) d = Logic.prevM(d); pris += d; d } }.toTypedArray())
    }
    val nets = remember { mutableStateListOf(*lots.map { numStr(it.net) }.toTypedArray()) }
    SheetTitle("${lots.size} bulletins")
    lots.forEachIndexed { i, l ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Row(Modifier.weight(1f).padding(top = 8.dp).height(56.dp).clip(RoundedCornerShape(10.dp)).background(K.Sf2)
                .then(if (l.ym == null) Modifier.border(1.dp, K.Ye, RoundedCornerShape(10.dp)) else Modifier),
                verticalAlignment = Alignment.CenterVertically) {
                T("‹", 22.sp, K.Tx2, modifier = Modifier.padding(horizontal = 12.dp).tap { mois[i] = Logic.prevM(mois[i]) })
                T(Logic.fmtM(mois[i]), 13.5.sp, K.Tx, FontWeight.SemiBold, Modifier.weight(1f), TextAlign.Center)
                T("›", 22.sp, K.Tx2, modifier = Modifier.padding(horizontal = 12.dp).tap { mois[i] = Logic.nextM(mois[i]) })
            }
            Field(nets[i], { nets[i] = it }, "Net €", Modifier.width(120.dp), number = true)
        }
    }
    val doublons = mois.groupBy { it }.filter { it.value.size > 1 }.keys
    if (doublons.isNotEmpty()) T("⛔ ${doublons.joinToString { Logic.fmtM(it) }} en double", 12.sp, K.Re, modifier = Modifier.padding(bottom = 10.dp))
    else mois.filter { m -> Db.s.paie.any { it.ym == m } }.takeIf { it.isNotEmpty() }?.let {
        T("⚠️ Remplace ${it.joinToString { m -> Logic.fmtM(m) }}", 12.sp, K.Ye, modifier = Modifier.padding(bottom = 10.dp))
    }
    Btn("Ranger", bg = if (doublons.isEmpty()) K.Ac else K.Sf3) {
        if (doublons.isNotEmpty()) return@Btn
        val n = Db.saveLot(lots.mapIndexed { i, l -> Triple(l.file, mois[i], num(nets[i])?.let { Logic.r2(it) }) })
        Nav.lot = emptyList(); Nav.close(); Nav.notify("📄 $n bulletin" + (if (n > 1) "s rangés" else " rangé"))
    }
}
