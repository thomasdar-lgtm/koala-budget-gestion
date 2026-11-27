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
    val rev = txM.filter { Logic.isRevenu(s, it) }.sumOf { it.montant }
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

// ═══ POKÉMON ═══════════════════════════════════════════════
@Composable
fun PokeScreen() {
    val s = Db.s; val ym = Nav.pokeYM
    val txs = s.tx.filter { it.mois == ym && Logic.cat(s, it) == Cats.POKE }.sortedByDescending { it.date }
    val total = txs.filter { it.sens == DEBIT }.sumOf { it.montant }
    Page {
        Header("Pokémon TCG", icon = { TabIcon(R.drawable.nav_poke) })
        Hero {
            HeroLabel("Total dépensé Pokémon TCG")
            HeroAmount((if (total > 0) "- " else "") + Logic.fmt(total))
            T("${Logic.fmtM(ym)} · ${txs.size} opération(s)", 12.5.sp, K.HeroLbl)
        }
        MonthNav(ym) { Nav.pokeYM = it }
        Card(pad = 12.dp) {
            if (txs.isEmpty()) Empty("", "Aucune dépense Pokémon ce mois", R.drawable.nav_poke)
            txs.forEachIndexed { i, t -> if (i > 0) Divider(); TxRow(s, t) { Nav.open(Sheet.EditTx(t.id)) } }
        }
    }
}

// ═══ CRÉDITS ═══════════════════════════════════════════════
/** carte d'un crédit renouvelable : utilisé / plafond, disponible */
@Composable
private fun RevCard(c: Cred, e: Logic.Etat) {
    val pct = if (c.plafond > 0) (e.cap / c.plafond).toFloat().coerceIn(0f, 1f) else 0f
    Card(Modifier.clickable { Nav.open(Sheet.CreditDetail(c.id)) }) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            T(c.nom, 15.sp, K.Tx, FontWeight.Bold, Modifier.weight(1f, fill = false), lines = 1)
            T("  RENOUVELABLE", 9.5.sp, K.HeroLbl, FontWeight.Bold, Modifier.weight(1f))
            if (e.nRest > 0) { T(Logic.fmt(e.m), 14.sp, K.Tx, FontWeight.ExtraBold); T(" / mois", 11.sp, K.Tx3) }
        }
        Bar(pct, Brush.horizontalGradient(listOf(K.Or, K.Re)))
        Spacer(Modifier.height(8.dp))
        Row { T("Utilisé ", 12.sp, K.Tx2); T(Logic.fmt(e.cap), 12.sp, K.Tx, FontWeight.Bold); T(" / " + Logic.fmt(c.plafond), 12.sp, K.Tx2, modifier = Modifier.weight(1f))
            T("Dispo ", 12.sp, K.Tx2); T(Logic.fmt(maxOf(0.0, c.plafond - e.cap)), 12.sp, K.Gr, FontWeight.Bold) }
        if (e.nRest > 0) { Spacer(Modifier.height(3.dp)); T("Remboursé en ${Logic.fmtM(e.fin)} sans nouveau tirage", 12.sp, K.Tx2) }
    }
}

@Composable
fun CreditsScreen() {
    val s = Db.s; val today = Db.today; val t = Logic.crTotaux(s, today)
    Page {
        Header("Crédits", icon = { TabIcon(R.drawable.nav_cred) })
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
            .sortedWith(compareBy<Pair<Cred, Logic.Etat>> { if (it.second.nRest > 0 || it.first.renouv) 0 else 1 }.thenBy { it.second.fin })
            .forEach { (c, e) ->
                if (c.renouv) { RevCard(c, e); return@forEach }
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
