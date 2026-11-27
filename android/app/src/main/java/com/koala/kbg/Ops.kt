package com.koala.kbg

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

private val JOURS = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")
private val MOIS_MIN = listOf("janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre")

/** « Vendredi 18 décembre » */
private fun jourLong(d: String): String = runCatching {
    val x = LocalDate.parse(d); JOURS[x.dayOfWeek.value - 1] + " " + x.dayOfMonth + " " + MOIS_MIN[x.monthValue - 1]
}.getOrDefault(d)

// ═══ OPÉRATIONS ════════════════════════════════════════════
@Composable
fun OpsScreen() {
    val s = Db.s; val ym = Nav.opYM; val today = Db.today
    var filtre by rememberSaveable { mutableStateOf("all") }
    val q = Nav.opQ
    BackHandler(enabled = q != null) { Nav.opQ = null }
    Page {
        Header("Opérations", icon = { TabIcon(R.drawable.nav_ops) }, right = {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (q != null) K.Ac else K.Sf2)
                .clickable { Nav.opQ = if (q == null) "" else null }, contentAlignment = Alignment.Center) {
                T(if (q == null) "🔍" else "✕", if (q == null) 16.sp else 17.sp, if (q == null) K.Tx2 else Color.White)
            }
        })
        if (q != null) { SearchOps(s, q); return@Page }

        val txM = s.tx.filter { it.mois == ym }
        val rev = txM.filter { Logic.isRevenu(s, it) }.sumOf { it.montant }
        val dep = txM.filter { it.sens == DEBIT }.sumOf { it.montant }
        val recs = Logic.recsOf(s, ym); val done = Logic.done(s, ym)
        val ap = Logic.aPointer(s, ym, today); val apIds = ap.map { it.id }.toSet()

        // ── en-tête du mois ──
        val sh = RoundedCornerShape(18.dp)
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(sh).background(K.Hero).border(1.dp, K.Ac.copy(alpha = 0.3f), sh).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).clickable { Nav.opYM = Logic.prevM(ym) }, contentAlignment = Alignment.Center) { T("‹", 20.sp, Color.White) }
                T(Logic.fmtM(ym), 16.sp, Color.White, FontWeight.ExtraBold, Modifier.weight(1f), TextAlign.Center)
                Box(Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).clickable { Nav.opYM = Logic.nextM(ym) }, contentAlignment = Alignment.Center) { T("›", 20.sp, Color.White) }
            }
            Spacer(Modifier.height(14.dp))
            Row {
                MoisStat("Entrées", "+" + Logic.fmt(rev), K.Gr, Modifier.weight(1f))
                MoisStat("Sorties", "-" + Logic.fmt(dep), K.Re, Modifier.weight(1f))
                MoisStat("Solde", (if (rev - dep >= 0) "+" else "-") + Logic.fmt(rev - dep), Color.White, Modifier.weight(1f))
            }
            if (recs.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                val nd = recs.count { it.id in done }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("🔁 Récurrentes", 11.5.sp, K.HeroLbl, modifier = Modifier.weight(1f))
                    T("$nd / ${recs.size} passées", 11.5.sp, Color.White, FontWeight.Bold)
                }
                Spacer(Modifier.height(5.dp))
                Bar(nd.toFloat() / recs.size, Brush.horizontalGradient(listOf(K.Ac, K.Gr)), 5.dp, Color.White.copy(alpha = 0.1f))
            }
        }

        // ── filtres ──
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("all" to "Tout", DEBIT to "Sorties", CREDIT to "Entrées", "rec" to "🔁 Récurrentes", "pt" to "◯ À pointer" + (if (ap.isNotEmpty()) "  ${ap.size}" else ""))
                .forEach { (k, l) -> Filtre(l, k == filtre) { filtre = k } }
        }

        fun garde(t: Tx) = when (filtre) { DEBIT, CREDIT -> t.sens == filtre; "rec" -> t.type == "rec"; "pt" -> t.id in apIds; else -> true }
        val upcoming = if (filtre == "pt") emptyList() else recs.filter { it.id !in done && (filtre !in listOf(DEBIT, CREDIT) || it.sens == filtre) }.sortedBy { it.jour }
        val liste = txM.filter(::garde).sortedWith(compareByDescending<Tx> { it.date }.thenByDescending { it.id })

        if (upcoming.isNotEmpty()) {
            JourTete("À venir", upcoming.sumOf { if (it.sens == CREDIT) Logic.recAmt(s, it, ym) else -Logic.recAmt(s, it, ym) })
            Card(pad = 12.dp) {
                upcoming.forEachIndexed { i, r -> if (i > 0) Divider(); AVenirRow(s, r, ym) }
            }
        }
        if (liste.isEmpty() && upcoming.isEmpty()) Card { Empty("", "Aucune opération", R.drawable.nav_ops) }
        liste.groupBy { it.date }.forEach { (d, l) ->
            JourTete(if (d == Logic.ds(today)) "Aujourd'hui" else if (d == Logic.ds(today.minusDays(1))) "Hier" else jourLong(d), l.sumOf { it.signe })
            Card(pad = 12.dp) {
                l.forEachIndexed { i, t ->
                    if (i > 0) Divider()
                    TxRow(s, t, date = false) {
                        val r = if (t.type == "rec") s.rec.find { it.id == t.recId } else null
                        Nav.open(if (r != null) Sheet.RecMonth(r.id, t.mois) else Sheet.EditTx(t.id))
                    }
                }
            }
        }
    }
}

@Composable
private fun MoisStat(label: String, v: String, col: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        T(label.uppercase(), 10.sp, K.HeroLbl, FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        T(v, 15.sp, col, FontWeight.ExtraBold, lines = 1)
    }
}

@Composable
private fun Filtre(l: String, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(if (on) K.Ac else K.Sf).border(1.dp, if (on) K.Ac else K.Bd, RoundedCornerShape(20.dp))
        .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)) { T(l, 12.5.sp, if (on) Color.White else K.Tx2, FontWeight.SemiBold) }
}

@Composable
private fun JourTete(titre: String, net: Double) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        T(titre, 12.5.sp, K.Tx2, FontWeight.Bold, Modifier.weight(1f))
        T((if (net >= 0) "+" else "-") + Logic.fmt(net), 12.sp, K.Tx3, FontWeight.SemiBold)
    }
}

/** récurrente pas encore passée : cocher quand elle est passée */
@Composable
private fun AVenirRow(s: St, r: Rec, ym: String) {
    val edit = { Nav.open(if (r.credId != null) Sheet.CreditDetail(r.credId) else Sheet.RecEdit(r.id)) }
    Row(Modifier.fillMaxWidth().clickable(onClick = edit).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBox(r.categorie, r.sens)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            T(r.libelle, 14.5.sp, K.Tx, FontWeight.SemiBold, lines = 1)
            T("Le ${r.jour} · " + Cats.court(r.categorie), 11.5.sp, K.Tx2, lines = 1)
        }
        T((if (r.sens == CREDIT) "+" else "-") + Logic.fmt(Logic.recAmt(s, r, ym)), 15.sp, K.Tx3, FontWeight.ExtraBold,
            Modifier.clip(RoundedCornerShape(8.dp)).clickable { Nav.open(Sheet.RecMonth(r.id, ym)) }.padding(4.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(34.dp).clip(CircleShape).clickable { Db.edit { Logic.toggleRecUser(it, r.id, ym) } }, contentAlignment = Alignment.Center) {
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).border(1.5.dp, K.Tx3, RoundedCornerShape(7.dp)))
        }
    }
}

/** recherche tous mois confondus : libellé, catégorie ou montant */
@Composable
private fun SearchOps(s: St, q: String) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    var page by rememberSaveable { mutableIntStateOf(1) }
    Field(q, { Nav.opQ = it; page = 1 }, "Libellé, catégorie ou montant", Modifier.focusRequester(focus))
    if (q.isBlank()) return
    val r = Logic.search(s, q)
    val net = r.sumOf { it.signe }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
        T("${r.size} résultat" + (if (r.size > 1) "s" else ""), 12.5.sp, K.Tx2, modifier = Modifier.weight(1f))
        if (r.isNotEmpty()) T((if (net >= 0) "+" else "-") + Logic.fmt(net), 12.5.sp, if (net >= 0) K.Gr else K.Re, FontWeight.Bold)
    }
    val shown = r.take(30 * page)
    Card(pad = 12.dp) {
        if (shown.isEmpty()) Empty("🔍", "Aucune opération trouvée")
        shown.forEachIndexed { i, t -> if (i > 0) Divider(); TxRow(s, t) { Nav.open(Sheet.EditTx(t.id)) } }
    }
    if (r.size > shown.size) BtnGhost("Voir plus (${r.size - shown.size})") { page++ }
}

// ═══ FORMULAIRES ═══════════════════════════════════════════
/** sens en pilule : Dépense / Entrée */
@Composable
private fun SensPill(sens: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(14.dp)).background(K.Sf2).padding(4.dp)) {
        listOf(DEBIT to "Dépense", CREDIT to "Entrée").forEach { (k, l) ->
            val on = sens == k; val c = if (k == DEBIT) K.Re else K.Gr
            Box(Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (on) c.copy(alpha = 0.2f) else Color.Transparent)
                .clickable { onChange(k) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                T(l, 14.sp, if (on) c else K.Tx3, FontWeight.Bold)
            }
        }
    }
}

/** grand montant centré, « € » ajouté à l'affichage */
private object Euro : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val out = AnnotatedString(text.text + " €")
        return TransformedText(out, object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset
            override fun transformedToOriginal(offset: Int) = minOf(offset, text.length)
        })
    }
}

@Composable
private fun Montant(v: String, col: Color, focus: FocusRequester? = null, onChange: (String) -> Unit) {
    BasicTextField(
        value = v, onValueChange = { n -> onChange(n.filter { it.isDigit() || it == ',' || it == '.' }) }, singleLine = true,
        textStyle = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = col, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), cursorBrush = SolidColor(col),
        visualTransformation = if (v.isEmpty()) VisualTransformation.None else Euro,
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp).then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (v.isEmpty()) T("0 €", 44.sp, col.copy(alpha = 0.35f), FontWeight.ExtraBold, align = TextAlign.Center)
                inner()
            }
        },
    )
}

/** catégories en tuiles (4 par ligne) */
@Composable
private fun CatGrid(cats: List<String>, value: String, onChange: (String) -> Unit) {
    cats.chunked(4).forEach { ligne ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ligne.forEach { c ->
                val on = c == value; val col = catCol(c)
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (on) col.copy(alpha = 0.22f) else K.Sf2)
                    .border(1.5.dp, if (on) col else Color.Transparent, RoundedCornerShape(14.dp)).clickable { onChange(c) }
                    .padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(col.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { CatIcon(c, 17.sp) }
                    Spacer(Modifier.height(5.dp))
                    T(Cats.court(c), 11.sp, if (on) K.Tx else K.Tx2, if (on) FontWeight.Bold else FontWeight.Medium, lines = 1)
                }
            }
            repeat(4 - ligne.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun Interrupteur(titre: String, sous: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { T(titre, 14.sp, K.Tx, FontWeight.SemiBold); if (sous.isNotEmpty()) T(sous, 11.5.sp, K.Tx3) }
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = K.Ac, checkedThumbColor = Color.White,
            uncheckedTrackColor = K.Sf3, uncheckedThumbColor = K.Tx3, uncheckedBorderColor = K.Bd))
    }
}

@Composable
private fun Puce(l: String, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) K.Ac.copy(alpha = 0.2f) else K.Sf2)
        .border(1.dp, if (on) K.Ac else Color.Transparent, RoundedCornerShape(10.dp)).clickable(onClick = onClick)
        .padding(horizontal = 13.dp, vertical = 9.dp)) { T(l, 13.sp, if (on) K.Tx else K.Tx2, FontWeight.SemiBold) }
}

/** nouvelle opération (id = null) ou modification */
@Composable
internal fun TxFormSheet(id: Long?, sens0: String = DEBIT) {
    val ctx = LocalContext.current
    val s = Db.s; val today = Db.today
    val t0 = id?.let { i -> s.tx.find { it.id == i } }
    if (id != null && t0 == null) { Nav.close(); return }
    var sens by remember { mutableStateOf(t0?.sens ?: sens0) }
    var amt by remember { mutableStateOf(numStr(t0?.montant)) }
    var lib by remember { mutableStateOf(t0?.libelle ?: "") }
    var date by remember { mutableStateOf(t0?.date ?: Logic.ds(today)) }
    var dm by remember { mutableStateOf(t0?.mois ?: Logic.ym(today)) }
    var cat by remember { mutableStateOf(t0?.let { Logic.cat(s, it) } ?: "") }
    var carte by remember { mutableStateOf(t0?.carte != false) }
    var rec by remember { mutableStateOf(false) }
    var jour by remember { mutableStateOf(today.dayOfMonth.toString()) }
    val focus = remember { FocusRequester() }
    if (t0 == null) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val habits = remember(sens) { if (t0 == null) Logic.habits(s, sens, today) else emptyList() }
    val col = if (sens == DEBIT) K.Re else K.Gr
    fun fill(t: Tx, withAmount: Boolean) {
        if (withAmount) amt = numStr(t.montant)
        lib = t.libelle
        val c = Logic.cat(s, t); if (c in Cats.of(sens)) cat = c
        if (t.sens == DEBIT) carte = t.carte != false
    }

    SensPill(sens) { sens = it; if (cat !in Cats.of(it)) cat = "" }
    Montant(amt, col, focus) { amt = it }
    Field(lib, { v -> lib = v
        if (cat.isEmpty()) habits.find { it.libelle.trim().equals(v.trim(), ignoreCase = true) }?.let { fill(it, false) }
    }, "Description")
    if (habits.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            habits.take(8).forEach { h ->
                val c = Logic.cat(s, h)
                Row(Modifier.clip(RoundedCornerShape(18.dp)).background(K.Sf2).clickable { fill(h, true) }.padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(catCol(c).copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { CatIcon(c, 12.sp) }
                    Spacer(Modifier.width(6.dp)); T(h.libelle, 12.5.sp, K.Tx, FontWeight.Medium); T("  " + Logic.fmt(h.montant), 11.5.sp, K.Tx3)
                }
            }
        }
    }

    CatGrid(Cats.of(sens), cat) { cat = it }
    if (sens == DEBIT && !rec && cat.isNotEmpty()) Logic.budOf(s, cat, dm)?.let { b ->
        val sp = b.sp - (if (t0 != null && t0.mois == dm && Logic.cat(s, t0) == cat) t0.montant else 0.0)
        val rest = b.bud - sp
        Column(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 12.dp)) {
            Row { T("Budget ${Cats.court(cat).lowercase()}", 12.sp, K.Tx2, modifier = Modifier.weight(1f))
                T(if (rest >= 0) "reste ${Logic.fmt(rest)}" else "dépassé de ${Logic.fmt(rest)}", 12.sp, if (rest < 0) K.Re else if (sp / b.bud >= 0.8) K.Ye else K.Gr, FontWeight.Bold) }
            Spacer(Modifier.height(5.dp))
            Bar((sp / b.bud).toFloat(), Brush.horizontalGradient(listOf(catCol(cat), catCol(cat).copy(alpha = 0.6f))), 5.dp)
        }
    }

    if (!rec) {
        val td = Logic.ds(today); val hier = Logic.ds(today.minusDays(1))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Puce("Aujourd'hui", date == td) { date = td; dm = td.take(7) }
            Puce("Hier", date == hier) { date = hier; dm = hier.take(7) }
            Puce("📅 " + (if (date != td && date != hier) Logic.fmtD(date).take(5) else "Autre"), date != td && date != hier) { pickDate(ctx, date) { date = it; dm = it.take(7) } }
        }
        if (dm != date.take(7) || t0?.displayMonth != null) Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            T("Compté sur", 12.5.sp, K.Tx2, modifier = Modifier.weight(1f))
            T("‹", 20.sp, K.Tx2, modifier = Modifier.padding(horizontal = 10.dp).tap { dm = Logic.prevM(dm) })
            T(Logic.fmtM(dm), 13.sp, K.Tx, FontWeight.SemiBold)
            T("›", 20.sp, K.Tx2, modifier = Modifier.padding(horizontal = 10.dp).tap { dm = Logic.nextM(dm) })
        }
    }

    val opts = (sens == DEBIT && !rec && t0?.type != "rec") || t0 == null
    if (opts) Column(Modifier.fillMaxWidth().padding(bottom = 14.dp).clip(RoundedCornerShape(14.dp)).background(K.Sf2).padding(vertical = 4.dp)) {
        if (sens == DEBIT && !rec && t0?.type != "rec") Interrupteur("💳 Payé par carte", "prélevé en fin de mois", carte) { carte = it }
        if (t0 == null) {
            if (sens == DEBIT && !rec) Divider()
            Interrupteur("🔁 Tous les mois", if (rec) "" else "en faire une récurrente", rec) { rec = it }
            if (rec) Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                T("Jour du mois", 13.sp, K.Tx2, modifier = Modifier.weight(1f))
                T("‹", 20.sp, K.Tx2, modifier = Modifier.padding(horizontal = 10.dp).tap { jour = (((jour.toIntOrNull() ?: 1) + 29) % 31 + 1).toString() })
                T("le $jour", 14.sp, K.Tx, FontWeight.Bold)
                T("›", 20.sp, K.Tx2, modifier = Modifier.padding(horizontal = 10.dp).tap { jour = ((jour.toIntOrNull() ?: 1) % 31 + 1).toString() })
            }
        }
    }

    Btn(when { t0 != null -> "Enregistrer"; rec -> "Ajouter la récurrente"; sens == DEBIT -> "Ajouter la dépense"; else -> "Ajouter l'entrée" }, bg = col.copy(alpha = 0.9f)) {
        val v = num(amt)
        if (lib.isBlank() || v == null || v <= 0) { Nav.notify("Montant et description requis", true); return@Btn }
        val c = cat.ifEmpty { "Divers" }.let { if (sens == CREDIT && it == "Divers") "Revenus Autres" else it }
        when {
            t0 != null -> {
                Db.edit { st -> st.copy(tx = st.tx.map {
                    if (it.id != t0.id) it else it.copy(libelle = lib.trim(), montant = Logic.r2(v), sens = sens, categorie = c, date = date,
                        displayMonth = if (dm != date.take(7)) dm else null, carte = if (it.type != "rec") sens == DEBIT && carte else it.carte)
                }, ovr = st.ovr - t0.id.toString()) }
                Nav.notify("Opération modifiée")
            }
            rec -> {
                val now = System.currentTimeMillis()
                Db.edit { it.copy(rec = it.rec + Rec("r$now", lib.trim(), Logic.r2(v), sens, c, (jour.toIntOrNull() ?: 1).coerceIn(1, 31), now)) }
                Db.tick(); Nav.notify("🔁 Récurrente ajoutée")
            }
            else -> {
                Db.edit { st -> st.copy(tx = st.tx + Tx(Logic.nextId(st), date, lib.trim(), Logic.r2(v), c, sens, "manuel", dm, carte = if (sens == DEBIT) carte else false)) }
                val al = if (sens == DEBIT) Logic.budAlert(Db.s, c, dm) else null
                when {
                    al == null -> Nav.notify((if (sens == DEBIT) "💸 " else "💰 ") + Logic.fmt(v))
                    al.second -> Nav.notify(al.first, true)
                    else -> Nav.warn(al.first)
                }
            }
        }
        Nav.close()
    }
    if (t0 != null) T("Supprimer", 13.5.sp, K.Re, FontWeight.SemiBold, Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp))
        .clickable { Db.edit { st -> st.copy(tx = st.tx.filter { it.id != t0.id }) }; Nav.close(); Nav.notify("Supprimée") }.padding(12.dp), TextAlign.Center)
}

// ═══ RÉCURRENTE ════════════════════════════════════════════
@Composable
internal fun RecEditSheet(id: String?) {
    val r0 = id?.let { i -> Db.s.rec.find { it.id == i } }
    var lib by remember { mutableStateOf(r0?.libelle ?: "") }
    var amt by remember { mutableStateOf(numStr(r0?.montant)) }
    var sens by remember { mutableStateOf(r0?.sens ?: DEBIT) }
    var cat by remember { mutableStateOf(r0?.categorie ?: "") }
    var jour by remember { mutableStateOf(r0?.jour ?: 5) }
    val col = if (sens == DEBIT) K.Re else K.Gr
    T("🔁 " + (if (r0 != null) "Récurrente" else "Nouvelle récurrente"), 13.sp, K.Tx2, FontWeight.Bold, Modifier.fillMaxWidth().padding(bottom = 10.dp), TextAlign.Center)
    SensPill(sens) { sens = it; if (cat !in Cats.of(it)) cat = "" }
    Montant(amt, col) { amt = it }
    Field(lib, { lib = it }, "Nom")
    CatGrid(Cats.of(sens), cat) { cat = it }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(K.Sf2).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        T("Chaque mois", 13.5.sp, K.Tx2, modifier = Modifier.weight(1f))
        T("‹", 20.sp, K.Tx2, modifier = Modifier.padding(horizontal = 10.dp).tap { jour = (jour + 29) % 31 + 1 })
        T("le $jour", 15.sp, K.Tx, FontWeight.Bold)
        T("›", 20.sp, K.Tx2, modifier = Modifier.padding(horizontal = 10.dp).tap { jour = jour % 31 + 1 })
    }
    Spacer(Modifier.height(10.dp))
    Btn(if (r0 != null) "Enregistrer" else "Ajouter", bg = col.copy(alpha = 0.9f)) {
        val v = num(amt)
        if (lib.isBlank() || v == null || v <= 0) { Nav.notify("Montant et nom requis", true); return@Btn }
        val c = cat.ifEmpty { "Divers" }
        if (r0 != null) Db.edit { st -> st.copy(rec = st.rec.map { if (it.id == r0.id) it.copy(libelle = lib.trim(), montant = Logic.r2(v), sens = sens, categorie = c, jour = jour) else it }) }
        else { val now = System.currentTimeMillis(); Db.edit { it.copy(rec = it.rec + Rec("r$now", lib.trim(), Logic.r2(v), sens, c, jour, now)) } }
        Db.tick(); Nav.close()
    }
    if (r0 != null) T("Supprimer", 13.5.sp, K.Re, FontWeight.SemiBold, Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).clickable {
        Nav.confirm("Supprimer cette récurrente ?", "Les mois passés ne changent pas.", "Supprimer") {
            Db.edit { st -> st.copy(rec = st.rec.filter { it.id != r0.id }) }; Nav.close()
        }
    }.padding(12.dp), TextAlign.Center)
}

/** montant d'une récurrente pour un mois précis */
@Composable
internal fun RecMonthSheet(rid: String, ym: String) {
    val s = Db.s
    val r = s.rec.find { it.id == rid } ?: run { Nav.close(); return }
    val t = Logic.recTxOf(s, r, ym)
    var amt by remember { mutableStateOf(numStr(t?.montant ?: Logic.recAmt(s, r, ym))) }
    val col = if (r.sens == DEBIT) K.Re else K.Gr
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBox(r.categorie, r.sens)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            T(r.libelle, 16.sp, K.Tx, FontWeight.ExtraBold, lines = 1)
            T(Logic.fmtM(ym) + " · habituellement " + Logic.fmt(r.montant), 12.sp, K.Tx2, lines = 1)
        }
    }
    Montant(amt, col) { amt = it }
    Btn(if (t != null) "Enregistrer" else "Marquer comme passée", bg = col.copy(alpha = 0.9f)) {
        val v = num(amt)
        if (v == null || v <= 0) { Nav.notify("Montant invalide", true); return@Btn }
        Db.edit { Logic.setRecMonth(it, rid, ym, v) }; Nav.close()
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        if (t != null) T("Pas encore passée", 13.sp, K.Tx2, FontWeight.SemiBold, Modifier.clip(RoundedCornerShape(8.dp))
            .clickable { Db.edit { Logic.toggleRecUser(it, rid, ym) }; Nav.close() }.padding(10.dp)) else Spacer(Modifier.width(1.dp))
        T("Modifier la récurrente", 13.sp, K.Tx2, FontWeight.SemiBold, Modifier.clip(RoundedCornerShape(8.dp))
            .clickable { Nav.open(if (r.credId != null) Sheet.CreditDetail(r.credId) else Sheet.RecEdit(r.id)) }.padding(10.dp))
    }
}
