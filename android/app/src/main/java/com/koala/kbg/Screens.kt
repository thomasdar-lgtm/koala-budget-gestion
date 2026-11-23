package com.koala.kbg

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ═══ AUJOURD'HUI ═══════════════════════════════════════════
@Composable
fun HomeScreen() {
    val s = Db.s; val today = Db.today; val ym = Logic.ym(today)
    val so = Logic.calcSoldes(s, ym, today)
    val txM = s.tx.filter { it.mois == ym }
    val rev = txM.filter { it.sens == CREDIT }.sumOf { it.montant }
    val dep = txM.filter { it.sens == DEBIT }.sumOf { it.montant }
    val jours = Logic.dim(ym); val day = today.dayOfMonth
    val en = Logic.encours(s, Logic.ds(today)); val cr = Logic.crTotaux(s, today)
    Page {
        Header("KBG", icon = { Image(painterResource(R.drawable.logo), null, Modifier.size(34.dp).clip(RoundedCornerShape(7.dp))) })
        Hero {
            HeroLabel("Solde")
            HeroAmount(Logic.fmtS(so.reel), if (so.reel >= 0) Color.White else K.Re) { Nav.open(Sheet.Solde) }
            if (so.anchor == null) T("Saisir mon solde bancaire ›", 12.5.sp, K.Ye, modifier = Modifier.padding(top = 2.dp).clickable { Nav.open(Sheet.Solde) })
            Chips {
                Chip("Fin de mois", Logic.fmtS(so.prev), if (so.prev >= 0) Color.White else K.Re)
                if (en > 0.004) Chip("💳", Logic.fmt(en) + " le " + Logic.fmtD(Logic.debitDate(Logic.ds(today))).take(5))
                if (cr.cap > 0.004) Chip("🏛️", Logic.fmt(cr.cap)) { Nav.go("credits") }
            }
            Spacer(Modifier.height(14.dp))
            Bar(day.toFloat() / jours, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.55f))), 6.dp, Color.White.copy(alpha = 0.12f))
            T("${Logic.fmtM(ym)} · jour $day/$jours", 11.sp, K.HeroLbl, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), align = TextAlign.End)
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Kpi("Entrées", (if (rev > 0) "+" else "") + Logic.fmt(rev), K.Gr)
            Kpi("Sorties", (if (dep > 0) "-" else "") + Logic.fmt(dep), K.Re)
            val jr = jours - day + 1
            Kpi("Par jour", if (so.prev > 0) Logic.fmt(so.prev / jr) else "0 €", if (so.prev > 0) K.Tx else K.Re)
        }
        // bilan du mois écoulé : les 7 premiers jours, jusqu'à fermeture
        val pym = Logic.prevM(ym)
        if (day <= 7 && (Nav.bilanVu ?: Db.pref("bilanVu")) != pym) {
            val b = Logic.bilan(s, pym, today)
            if (!b.vide) BilanCard(b, onOpen = { Nav.anTab = "bilan"; Nav.anYM = pym; Nav.go("analyse") },
                onClose = { Nav.bilanVu = pym; Db.setPref("bilanVu", pym) })
        }
        // prélèvements des 3 prochains jours
        val done = Logic.done(s, ym)
        val soon = Logic.recsOf(s, ym).filter { it.id !in done && it.sens == DEBIT && it.jour in day..(day + 3) }.sortedBy { it.jour }
        if (soon.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().padding(bottom = 14.dp).clip(RoundedCornerShape(10.dp)).background(K.Sf).padding(horizontal = 12.dp, vertical = 9.dp)) {
                T("📅 " + soon.joinToString(" · ") { "${it.libelle} ${Logic.fmt(Logic.recAmt(s, it, ym))} le ${it.jour}" }, 12.5.sp, K.Tx2)
            }
        }
        Card {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                T("BUDGETS", 11.sp, K.Tx2, FontWeight.SemiBold, Modifier.weight(1f))
                SmallBtn("Régler", K.Sf2, K.Tx2) { Nav.open(Sheet.Budgets) }
            }
            val L = Logic.budList(s, ym)
            if (L.isEmpty()) T("Aucun budget", 12.5.sp, K.Tx3)
            L.forEach { b ->
                val rest = b.bud - b.sp
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CatIcon(b.cat, 13.sp); Spacer(Modifier.width(6.dp))
                    T(b.cat, 13.sp, K.Tx, modifier = Modifier.weight(1f), lines = 1)
                    T("${Math.round(b.sp)} / ${Math.round(b.bud)} €", 13.sp, if (rest < 0) K.Re else K.Tx2)
                }
                Spacer(Modifier.height(5.dp))
                val br = when { b.pct >= 1 -> listOf(K.Re, Color(0xFFC0392B)); b.pct >= 0.8 -> listOf(K.Ye, K.Or); else -> listOf(K.Gr, Color(0xFF38B2AC)) }
                Bar(b.pct.toFloat(), Brush.horizontalGradient(br), 5.dp)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/** bilan d'un mois : entrées, sorties, solde, écarts par catégorie, budgets */
@Composable
fun BilanCard(b: Logic.Bilan, onOpen: (() -> Unit)? = null, onClose: (() -> Unit)? = null) {
    Card(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
            T("📋 Bilan de ${Logic.fmtM(b.ym)}", 15.sp, K.Tx, FontWeight.ExtraBold, Modifier.weight(1f))
            if (onClose != null) T("✕", 16.sp, K.Tx3, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClose).padding(6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
            Column(Modifier.weight(1f)) { T("Entrées", 11.sp, K.Tx2); T("+" + Logic.fmt(b.rev), 14.sp, K.Gr, FontWeight.Bold) }
            Column(Modifier.weight(1f)) { T("Sorties", 11.sp, K.Tx2); T("-" + Logic.fmt(b.dep), 14.sp, K.Re, FontWeight.Bold) }
            Column(Modifier.weight(1f)) { T("Résultat", 11.sp, K.Tx2); T((if (b.net >= 0) "+" else "-") + Logic.fmt(b.net), 14.sp, if (b.net >= 0) K.Gr else K.Re, FontWeight.Bold) }
        }
        b.soldeFin?.let { KV("Solde en fin de mois", Logic.fmtS(it)) }
        if (b.hausses.isNotEmpty()) {
            T("En hausse", 11.sp, K.Tx2, FontWeight.SemiBold, Modifier.padding(top = 8.dp, bottom = 2.dp))
            b.hausses.forEach { e -> EcartRow(e) }
        }
        if (b.baisses.isNotEmpty()) {
            T("En baisse", 11.sp, K.Tx2, FontWeight.SemiBold, Modifier.padding(top = 8.dp, bottom = 2.dp))
            b.baisses.forEach { e -> EcartRow(e) }
        }
        if (b.buds.isNotEmpty()) {
            val over = b.buds.filter { it.sp > it.bud + 0.004 }
            T("Budgets : ${b.buds.size - over.size} / ${b.buds.size} tenus", 12.5.sp, if (over.isEmpty()) K.Gr else K.Ye, FontWeight.Bold,
                Modifier.padding(top = 10.dp))
            over.forEach { o -> T("• ${o.cat} dépassé de ${Logic.fmt(o.sp - o.bud)}", 12.sp, K.Tx2) }
        }
        if (onOpen != null) T("Voir le détail ›", 12.sp, K.Ac, FontWeight.SemiBold, Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun EcartRow(e: Logic.Ecart) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        CatIcon(e.cat, 13.sp); Spacer(Modifier.width(6.dp))
        T(e.cat, 12.5.sp, K.Tx, modifier = Modifier.weight(1f), lines = 1)
        T(Logic.fmt(e.cur), 12.5.sp, K.Tx2)
        T("  " + (if (e.diff > 0) "+" else "-") + Logic.fmt(e.diff), 12.5.sp, if (e.diff > 0) K.Re else K.Gr, FontWeight.Bold)
    }
}

// ═══ OPÉRATIONS ════════════════════════════════════════════
@Composable
fun OpsScreen() {
    val s = Db.s; val ym = Nav.opYM
    var sens by rememberSaveable { mutableStateOf("all") }
    var type by rememberSaveable { mutableStateOf("all") }
    var nonPt by rememberSaveable { mutableStateOf(false) }
    val q = Nav.opQ
    BackHandler(enabled = q != null) { Nav.opQ = null }
    Page {
        Header("📝 Opérations", right = {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (q != null) K.Ac else K.Sf2)
                .clickable { Nav.opQ = if (q == null) "" else null }, contentAlignment = Alignment.Center) {
                T(if (q == null) "🔍" else "✕", if (q == null) 16.sp else 17.sp, if (q == null) K.Tx2 else Color.White)
            }
        })
        if (q != null) { SearchOps(s, q); return@Page }
        Seg(listOf("all" to "Tout", DEBIT to "Sorties", CREDIT to "Entrées"), sens) { sens = it }
        Seg(listOf("all" to "Toutes", "classic" to "Classiques", "rec" to "Récurrentes"), type) { type = it }
        MonthNav(ym) { Nav.opYM = it }
        val done = Logic.done(s, ym)
        // pointage : ce qui n'est pas encore vu sur le relevé
        val ap = Logic.aPointer(s, ym, Db.today)
        if (ap.isNotEmpty() || nonPt) {
            val tot = ap.sumOf { it.signe }
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(10.dp)).background(if (nonPt) K.Ac.copy(alpha = 0.18f) else K.Sf)
                .clickable { nonPt = !nonPt }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                T("◯ À pointer : ${ap.size}", 12.5.sp, K.Tx, FontWeight.SemiBold)
                T(" · " + (if (tot >= 0) "+" else "-") + Logic.fmt(tot), 12.5.sp, K.Tx2, modifier = Modifier.weight(1f))
                T(if (nonPt) "Tout voir" else "Voir seulement", 12.sp, K.Ac, FontWeight.Bold)
            }
        }
        val apIds = ap.map { it.id }.toSet()
        val classic = if (type == "rec") emptyList() else s.tx.filter { it.mois == ym && it.type != "rec" && (sens == "all" || it.sens == sens) && (!nonPt || it.id in apIds) }
        val recs = if (type == "classic") emptyList() else Logic.recsOf(s, ym).filter { r -> (sens == "all" || r.sens == sens) &&
            (!nonPt || Logic.recTxOf(s, r, ym)?.id?.let { it in apIds } == true) }
        Card(pad = 12.dp) {
            if (classic.isEmpty() && recs.isEmpty()) Empty("📋", "Aucune opération")
            classic.forEachIndexed { i, t ->
                if (i > 0) Divider()
                TxRow(s, t) { Nav.open(Sheet.EditTx(t.id)) }
            }
            recs.forEachIndexed { i, r ->
                if (i > 0 || classic.isNotEmpty()) Divider()
                RecRow(s, r, ym, r.id in done)
            }
        }
    }
}

/** recherche tous mois confondus : libellé, catégorie ou montant */
@Composable
private fun SearchOps(s: St, q: String) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    var page by rememberSaveable { mutableIntStateOf(1) }
    Field(q, { Nav.opQ = it; page = 1 }, "Libellé, catégorie ou montant", Modifier.focusRequester(focus), placeholder = "ex : Leclerc, 86")
    if (q.isBlank()) { T("Tous les mois sont fouillés. Un montant sans centimes (86) trouve aussi 86,40 €.", 12.sp, K.Tx3, modifier = Modifier.padding(4.dp)); return }
    val r = Logic.search(s, q)
    val net = r.sumOf { it.signe }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
        T("${r.size} résultat" + (if (r.size > 1) "s" else ""), 12.5.sp, K.Tx2, modifier = Modifier.weight(1f))
        if (r.isNotEmpty()) { T("Total ", 12.5.sp, K.Tx2); T((if (net >= 0) "+" else "-") + Logic.fmt(net), 12.5.sp, if (net >= 0) K.Gr else K.Re, FontWeight.Bold) }
    }
    val shown = r.take(30 * page)
    Card(pad = 12.dp) {
        if (shown.isEmpty()) Empty("🔍", "Aucune opération trouvée")
        shown.forEachIndexed { i, t -> if (i > 0) Divider(); TxRow(s, t) { Nav.open(Sheet.EditTx(t.id)) } }
    }
    if (r.size > shown.size) BtnGhost("Voir plus (${r.size - shown.size} restantes)") { page++ }
}

@Composable
private fun RecRow(s: St, r: Rec, ym: String, isDone: Boolean) {
    val rt = if (isDone) Logic.recTxOf(s, r, ym) else null
    val mt = rt?.montant ?: Logic.recAmt(s, r, ym)
    val diff = rt != null && kotlin.math.abs(rt.montant - r.montant) > 0.004
    val edit = { Nav.open(if (r.credId != null) Sheet.CreditDetail(r.credId) else Sheet.RecEdit(r.id)) }
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Check(isDone) { Db.edit { Logic.toggleRecUser(it, r.id, ym) } }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.clickable(onClick = edit)) { IconBox(r.categorie, r.sens, 32.dp) }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp).clickable(onClick = edit)) {
            T(r.libelle + "  🔁", 14.sp, K.Tx, FontWeight.SemiBold, lines = 1)
            Row {
                T("Vers le ${r.jour} · ${r.categorie}", 11.5.sp, K.Tx2, lines = 1, modifier = Modifier.weight(1f, fill = false))
                if (diff) T(" · prévu ${Logic.fmt(r.montant)}", 11.5.sp, K.Ye, lines = 1)
            }
        }
        Row(Modifier.clickable { Nav.open(Sheet.RecMonth(r.id, ym)) }, verticalAlignment = Alignment.CenterVertically) {
            T((if (r.sens == CREDIT) "+" else "-") + Logic.fmt(mt), 14.sp, if (r.sens == CREDIT) K.Gr else K.Re, FontWeight.Bold)
            T(" ✎", 11.sp, K.Tx3)
        }
        Spacer(Modifier.width(10.dp))
        if (rt != null) Pointe(rt) else Spacer(Modifier.width(34.dp))
    }
}

// ═══ POKÉMON ═══════════════════════════════════════════════
@Composable
fun PokeScreen() {
    val s = Db.s; val ym = Nav.pokeYM
    val txs = s.tx.filter { it.mois == ym && Logic.cat(s, it) == Cats.POKE }.sortedByDescending { it.date }
    val total = txs.filter { it.sens == DEBIT }.sumOf { it.montant }
    Page {
        Header("Pokémon TCG", icon = { Image(painterResource(R.drawable.poke), null, Modifier.height(30.dp).clip(RoundedCornerShape(3.dp))) })
        Hero {
            HeroLabel("Total dépensé Pokémon TCG")
            HeroAmount((if (total > 0) "- " else "") + Logic.fmt(total))
            T("${Logic.fmtM(ym)} · ${txs.size} opération(s)", 12.5.sp, K.HeroLbl)
        }
        MonthNav(ym) { Nav.pokeYM = it }
        Card(pad = 12.dp) {
            if (txs.isEmpty()) Empty("🃏", "Aucune dépense Pokémon ce mois")
            txs.forEachIndexed { i, t -> if (i > 0) Divider(); TxRow(s, t) { Nav.open(Sheet.EditTx(t.id)) } }
        }
    }
}

// ═══ CRÉDITS ═══════════════════════════════════════════════
@Composable
fun CreditsScreen() {
    val s = Db.s; val today = Db.today; val t = Logic.crTotaux(s, today)
    Page {
        Header("🏛️ Crédits")
        Hero {
            HeroLabel("Capital restant dû")
            HeroAmount(Logic.fmt(t.cap))
            if (s.cred.isNotEmpty()) Chips {
                Chip("", Logic.fmt(t.mens) + " / mois")
                Chip("Intérêts restants", Logic.fmt(t.itRest))
                if (t.fin.isNotEmpty()) Chip("Libre en", Logic.fmtM(t.fin))
            }
        }
        if (s.cred.isEmpty()) Card { Empty("🏛️", "Aucun crédit") }
        s.cred.map { it to Logic.crEtat(it, today) }
            .sortedWith(compareBy<Pair<Cred, Logic.Etat>> { if (it.second.nRest > 0) 0 else 1 }.thenBy { it.second.fin })
            .forEach { (c, e) ->
                val fini = e.nRest == 0
                val pct = if (c.montant > 0) ((1 - e.cap / c.montant) * 100).toInt().coerceIn(0, 100) else 0
                Card(Modifier.clickable { Nav.open(Sheet.CreditDetail(c.id)) }) {
                    val a = if (fini) 0.55f else 1f
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                        T(c.nom, 15.sp, K.Tx.copy(alpha = a), FontWeight.Bold, Modifier.weight(1f), lines = 1)
                        if (fini) T("Terminé ✓", 14.sp, K.Gr, FontWeight.ExtraBold)
                        else { T(Logic.fmt(e.m), 14.sp, K.Tx, FontWeight.ExtraBold); T(" / mois", 11.sp, K.Tx3) }
                    }
                    Bar(pct / 100f, Brush.horizontalGradient(listOf(K.Ac, Color(0xFF06D6A0))))
                    Spacer(Modifier.height(8.dp))
                    Row { T("Reste ", 12.sp, K.Tx2); T(Logic.fmt(e.cap), 12.sp, K.Tx, FontWeight.Bold); T(" sur " + Logic.fmt(c.montant), 12.sp, K.Tx2, modifier = Modifier.weight(1f)); T("$pct %", 12.sp, K.Tx2) }
                    Spacer(Modifier.height(3.dp))
                    Row {
                        T((if (fini) "Fini en " else "Fin ") + Logic.fmtM(e.fin) + " · " + e.nRest + " éch.", 12.sp, K.Tx2, modifier = Modifier.weight(1f))
                        T("Intérêts ", 12.sp, K.Tx2); T(Logic.fmt(e.itRest), 12.sp, K.Tx, FontWeight.Bold)
                    }
                }
            }
        Btn("＋ Nouveau crédit") { Nav.open(Sheet.CreditEdit(null)) }
    }
}
