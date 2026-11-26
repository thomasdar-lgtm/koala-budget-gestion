package com.koala.kbg

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetHost() {
    val sh = Nav.sheet ?: return
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { Nav.close() }, sheetState = state, containerColor = K.Sf,
        dragHandle = { BottomSheetDefaults.DragHandle(color = K.Tx3) },
    ) {
        key(sh) {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp).padding(bottom = 18.dp)) {
                when (sh) {
                    Sheet.Menu -> MenuSheet()
                    Sheet.Solde -> SoldeSheet()
                    Sheet.Budgets -> BudgetsSheet()
                    Sheet.Backups -> BackupsSheet()
                    is Sheet.AddTx -> AddTxSheet(sh.sens)
                    is Sheet.EditTx -> EditTxSheet(sh.id)
                    is Sheet.RecEdit -> RecEditSheet(sh.id)
                    is Sheet.RecMonth -> RecMonthSheet(sh.rid, sh.ym)
                    is Sheet.CreditDetail -> CreditDetailSheet(sh.id)
                    is Sheet.CreditEdit -> CreditEditSheet(sh.id)
                    Sheet.Paies -> PaiesSheet()
                    is Sheet.PaieEdit -> PaieEditSheet(sh.ym)
                }
            }
        }
    }
}

// ═══ MENU ══════════════════════════════════════════════════
@Composable
internal fun MenuSheet() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var majS by remember { mutableStateOf("installée : v" + BuildConfig.VERSION_NAME) }
    T("KBG", 20.sp, K.Tx, FontWeight.ExtraBold)
    T("v" + BuildConfig.VERSION_NAME, 12.sp, K.Tx3, modifier = Modifier.padding(bottom = 6.dp))
    MenuSec("Banque")
    MenuItem("Pointer avec la banque", "solde réel et écart") { Nav.open(Sheet.Solde) }
    MenuSec("Documents")
    MenuItem("Bulletins de paie", Db.s.paie.size.let { if (it == 0) "aucun" else "$it bulletin" + (if (it > 1) "s" else "") }) { Nav.open(Sheet.Paies) }
    MenuSec("Données")
    MenuItem("Sauvegardes", "une par jour · 30 jours") { Nav.open(Sheet.Backups) }
    MenuItem("Partager une copie", "Drive, Gmail…") { runCatching { Db.shareCopy(ctx) }.onFailure { Nav.notify("Partage impossible", true) } }
    MenuItem("Importer", "fichier .json") { Nav.close(); Nav.importReq++ }
    MenuSec("Sécurité")
    var lockOn by remember { mutableStateOf(Lock.enabled(ctx)) }
    val dispo = remember { Lock.available(ctx) }
    MenuItem("Verrouillage par empreinte", if (!dispo) "indisponible" else if (lockOn) "activé ✓" else "désactivé") {
        if (!dispo) { Nav.notify("Aucune empreinte ni code configuré sur ce téléphone", true); return@MenuItem }
        lockOn = !lockOn; Lock.setEnabled(ctx, lockOn)
        if (android.os.Build.VERSION.SDK_INT >= 33) (ctx as? android.app.Activity)?.setRecentsScreenshotEnabled(!lockOn)
        Nav.notify(if (lockOn) "🔒 Verrouillage activé" else "Verrouillage désactivé")
    }
    MenuSec("Mise à jour")
    MenuItem("Rechercher une mise à jour", majS) {
        majS = "recherche…"
        scope.launch {
            val r = runCatching { Updater.check() }
            r.onSuccess { info ->
                if (info != null) { Upd.info = info; Upd.hidden = false; majS = "v${info.version} disponible"; Nav.close() }
                else majS = "à jour (v${BuildConfig.VERSION_NAME}) ✓"
            }.onFailure { majS = "hors ligne, réessaie" }
        }
    }
}

// ═══ SOLDE BANCAIRE ════════════════════════════════════════
@Composable
internal fun SoldeSheet() {
    val s = Db.s; val a = s.solde
    var amt by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(Logic.ds(Db.today)) }
    SheetTitle(if (a != null) "Pointer avec la banque" else "Solde bancaire",
        "Le solde affiché par ta banque. KBG part de ce montant et ajoute ensuite les opérations datées après ce jour.")
    Field(amt, { amt = it }, "Solde affiché par la banque (€)", number = true, placeholder = "ex : 1250,40 (− si à découvert)")
    DateField("Solde au soir du", date) { date = it }
    val k = Logic.soldeAt(s, date)
    if (k != null) {
        val en = Logic.encours(s, date)
        Column(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            T("La banque devrait afficher ${Logic.fmtS(k)} au ${Logic.fmtD(date).take(5)}" +
                (if (en > 0.004) " (dont ${Logic.fmt(en)} de carte pas encore prélevés)" else "") + ".", 12.5.sp, K.Tx2, align = TextAlign.Center)
            num(amt)?.let { v ->
                val e = Logic.r2(v - k)
                if (kotlin.math.abs(e) < 0.01) T("✅ Pile : aucun écart", 13.sp, K.Gr, FontWeight.Bold)
                else {
                    T("Écart : " + (if (e > 0) "+" else "-") + Logic.fmt(e), 13.sp, K.Ye, FontWeight.Bold)
                    T((if (e < 0) "Une dépense manque dans KBG, ou une entrée est en trop." else "Une entrée manque dans KBG, ou une dépense est en trop.") +
                        " Enregistrer recale KBG sur la banque.", 12.sp, K.Tx3, align = TextAlign.Center)
                }
            }
        }
    }
    Btn("✅ Enregistrer") {
        val v = num(amt)
        if (v == null || date.length != 10) { Nav.notify("Montant et date requis", true); return@Btn }
        Db.edit { it.copy(solde = Solde(Logic.r2(v), date)) }
        Nav.close(); Nav.notify("✅ Solde bancaire enregistré")
    }
    if (a != null) T("Référence actuelle : ${Logic.fmtS(a.montant)} au ${Logic.fmtD(a.date)}", 11.sp, K.Tx3,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp), align = TextAlign.Center)
}

// ═══ BUDGETS ═══════════════════════════════════════════════
@Composable
internal fun BudgetsSheet() {
    val vals = remember { Cats.DEP.associateWith { mutableStateOf(numStr(Db.s.bud[it])) } }
    SheetTitle("Budgets mensuels", "Plafond par mois. Laisse vide pour ne pas suivre une catégorie.")
    Cats.DEP.forEach { c ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            CatIcon(c, 14.sp); Spacer(Modifier.width(8.dp))
            T(c, 13.sp, K.Tx, modifier = Modifier.weight(1f), lines = 1)
            val st = vals.getValue(c)
            Field(st.value, { st.value = it }, "€", Modifier.width(110.dp), number = true)
        }
    }
    Btn("✅ Enregistrer") {
        val b = LinkedHashMap<String, Double>()
        Cats.DEP.forEach { c -> num(vals.getValue(c).value)?.let { if (it > 0) b[c] = Logic.r2(it) } }
        Db.edit { it.copy(bud = b) }
        Nav.close(); Nav.notify("✅ Budgets enregistrés")
    }
}

// ═══ SAUVEGARDES ═══════════════════════════════════════════
@Composable
internal fun BackupsSheet() {
    val ctx = LocalContext.current
    val list = remember { Db.backups() }
    SheetTitle("Sauvegardes", "Une copie par jour sur ce téléphone, gardée 30 jours. « Partager » l'envoie ailleurs (Drive, Gmail…).")
    if (list.isEmpty()) T("Aucune sauvegarde pour l'instant", 12.5.sp, K.Tx3, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), align = TextAlign.Center)
    list.forEachIndexed { i, b ->
        if (i > 0) Divider()
        val (t, sub) = bkLabel(b)
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { T(t, 13.5.sp, K.Tx, FontWeight.Bold); T(sub, 11.sp, K.Tx3) }
            SmallBtn("Partager") { runCatching { Db.shareBackup(ctx, b.name) } }
            Spacer(Modifier.width(8.dp))
            SmallBtn("Restaurer", K.Ac, androidx.compose.ui.graphics.Color.White) {
                Nav.confirm("Restaurer ?", "« $t » remplacera les données actuelles. Une copie de sécurité de l'état actuel est faite avant.", "Restaurer") {
                    if (Db.restore(b.name)) { Nav.close(); Nav.go("home"); Nav.notify("Données restaurées ✓") } else Nav.notify("Sauvegarde illisible", true)
                }
            }
        }
    }
}

private fun bkLabel(b: Store.Info): Pair<String, String> {
    val m = Regex("^kbg-(\\d{4})-(\\d{2})-(\\d{2})").find(b.name)
    val jour = m?.let { "${it.groupValues[3]}/${it.groupValues[2]}/${it.groupValues[1]}" } ?: ""
    val h = SimpleDateFormat("HH'h'mm", Locale.FRANCE).format(Date(b.time))
    return if (b.daily) "Copie du $jour" to "dernière modif. à $h"
    else "Copie de sécurité" to "$jour à $h" + when {
        "avant-import" in b.name -> " · avant import"
        "avant-restauration" in b.name -> " · avant restauration"
        else -> ""
    }
}

// ═══ NOUVELLE OPÉRATION ════════════════════════════════════
@Composable
internal fun AddTxSheet(sens0: String = DEBIT) {
    val s = Db.s; val today = Db.today
    var sens by remember { mutableStateOf(sens0) }
    var amt by remember { mutableStateOf("") }
    var lib by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(Logic.ds(today)) }
    var dm by remember { mutableStateOf(Logic.ym(today)) }
    var cat by remember { mutableStateOf("") }
    var carte by remember { mutableStateOf(true) }
    var rec by remember { mutableStateOf(false) }
    var jour by remember { mutableStateOf("") }
    val habits = remember(sens) { Logic.habits(s, sens, today) }
    fun fill(t: Tx, withAmount: Boolean) {
        if (withAmount) amt = numStr(t.montant)
        lib = t.libelle
        val c = Logic.cat(s, t); if (c in Cats.of(sens)) cat = c
        if (t.sens == DEBIT) carte = t.carte != false
    }
    SheetTitle("Nouvelle opération")
    SensToggle(sens) { sens = it; cat = "" }
    Field(amt, { amt = it }, "Montant (€)", number = true, big = true, placeholder = "0,00")
    Field(lib, { v -> lib = v
        if (cat.isEmpty()) habits.find { it.libelle.trim().equals(v.trim(), ignoreCase = true) }?.let { fill(it, false) }
    }, "Description")
    if (habits.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            habits.take(8).forEach { h -> SmallBtn(h.libelle + " · " + Logic.fmt(h.montant), K.Sf2, K.Tx2) { fill(h, true) } }
        }
    }
    if (!rec) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DateField("Date", date, Modifier.weight(1f)) { date = it; dm = it.take(7) }
        Column(Modifier.weight(1f)) {
            T("Mois rattaché", 11.sp, K.Tx3, modifier = Modifier.padding(bottom = 4.dp))
            Row(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(10.dp)).background(K.Sf2), verticalAlignment = Alignment.CenterVertically) {
                T("‹", 22.sp, K.Tx2, modifier = Modifier.padding(horizontal = 10.dp).tap { dm = Logic.prevM(dm) })
                T(Logic.MFR[dm.substring(5, 7).toInt() - 1] + " " + dm.take(4), 14.sp, K.Tx, FontWeight.SemiBold, Modifier.weight(1f), TextAlign.Center)
                T("›", 22.sp, K.Tx2, modifier = Modifier.padding(horizontal = 10.dp).tap { dm = Logic.nextM(dm) })
            }
        }
    }
    Sec("Catégorie")
    CatPicker(Cats.of(sens), cat) { cat = it }
    if (sens == DEBIT && !rec && cat.isNotEmpty()) Logic.budOf(s, cat, dm)?.let { b ->
        val rest = b.bud - b.sp
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(10.dp)).background(K.Sf2).padding(horizontal = 12.dp, vertical = 9.dp)) {
            T("Budget ${Logic.MFR[dm.substring(5, 7).toInt() - 1].lowercase()} : ${Math.round(b.sp)} / ${Math.round(b.bud)} €", 12.5.sp, K.Tx2, modifier = Modifier.weight(1f))
            T(if (rest >= 0) "reste ${Logic.fmt(rest)}" else "dépassé de ${Logic.fmt(rest)}", 12.5.sp,
                if (rest < 0) K.Re else if (b.pct >= 0.8) K.Ye else K.Gr, FontWeight.Bold)
        }
    }
    if (sens == DEBIT && !rec) CheckRow(carte, "💳 Payé par carte", "(prélevé fin de mois)") { carte = !carte }
    CheckRow(rec, "Opération récurrente (tous les mois)") { rec = !rec }
    if (rec) Field(jour, { jour = it }, "Jour du mois", number = true, placeholder = "ex : 5")
    Btn("✅ Enregistrer") {
        val v = num(amt)
        if (lib.isBlank() || v == null || v <= 0) { Nav.notify("Montant et description requis", true); return@Btn }
        if (rec) {
            val j = (jour.toIntOrNull() ?: today.dayOfMonth).coerceIn(1, 31)
            val now = System.currentTimeMillis()
            Db.edit { it.copy(rec = it.rec + Rec("r$now", lib.trim(), Logic.r2(v), sens, cat.ifEmpty { "Divers" }, j, now)) }
            Db.tick()
            Nav.notify("🔁 Récurrente ajoutée")
        } else {
            Db.edit { st ->
                st.copy(tx = st.tx + Tx(Logic.nextId(st), date, lib.trim(), Logic.r2(v), cat, sens, "manuel", dm,
                    carte = if (sens == DEBIT) carte else false))
            }
            val al = if (sens == DEBIT && cat.isNotEmpty()) Logic.budAlert(Db.s, cat, dm) else null
            when {
                al == null -> Nav.notify((if (sens == DEBIT) "💸 " else "💰 ") + Logic.fmt(v) + " enregistré(e)")
                al.second -> Nav.notify(al.first, true)
                else -> Nav.warn(al.first)
            }
        }
        Nav.close()
    }
}

// ═══ MODIFIER UNE OPÉRATION ════════════════════════════════
@Composable
internal fun EditTxSheet(id: Long) {
    val t0 = Db.s.tx.find { it.id == id } ?: run { Nav.close(); return }
    var lib by remember { mutableStateOf(t0.libelle) }
    var amt by remember { mutableStateOf(numStr(t0.montant)) }
    var sens by remember { mutableStateOf(t0.sens) }
    var cat by remember { mutableStateOf(Logic.cat(Db.s, t0)) }
    var date by remember { mutableStateOf(t0.date) }
    var carte by remember { mutableStateOf(t0.carte != false) }
    SheetTitle("Modifier l'opération")
    Field(lib, { lib = it }, "Libellé")
    Field(amt, { amt = it }, "Montant (€)", number = true)
    SensToggle(sens) { sens = it }
    CatPicker(Cats.ALL, cat) { cat = it }
    DateField("Date", date) { date = it }
    if (t0.type != "rec" && sens == DEBIT) CheckRow(carte, "💳 Payé par carte", "(prélevé fin de mois)") { carte = !carte }
    Btn("✅ Enregistrer") {
        val v = num(amt)
        if (lib.isBlank()) { Nav.notify("Libellé requis", true); return@Btn }
        if (v == null || v <= 0) { Nav.notify("Montant invalide", true); return@Btn }
        Db.edit { st ->
            st.copy(tx = st.tx.map {
                if (it.id != id) it else it.copy(libelle = lib.trim(), montant = Logic.r2(v), sens = sens, categorie = cat, date = date,
                    carte = if (it.type != "rec") sens == DEBIT && carte else it.carte)
            }, ovr = st.ovr - id.toString())
        }
        Nav.close(); Nav.notify("Opération modifiée")
    }
    BtnDanger("🗑️ Supprimer") {
        Db.edit { st -> st.copy(tx = st.tx.filter { it.id != id }) }
        Nav.close(); Nav.notify("Supprimée")
    }
}

// ═══ RÉCURRENTE ════════════════════════════════════════════
@Composable
internal fun RecEditSheet(id: String?) {
    val r0 = id?.let { i -> Db.s.rec.find { it.id == i } }
    var lib by remember { mutableStateOf(r0?.libelle ?: "") }
    var amt by remember { mutableStateOf(numStr(r0?.montant)) }
    var sens by remember { mutableStateOf(r0?.sens ?: DEBIT) }
    var cat by remember { mutableStateOf(r0?.categorie ?: "") }
    var jour by remember { mutableStateOf(r0?.jour?.toString() ?: "") }
    SheetTitle(if (r0 != null) "Modifier la récurrente" else "Nouvelle récurrente")
    Field(lib, { lib = it }, "Nom")
    Field(amt, { amt = it }, "Montant (€)", number = true)
    SensToggle(sens) { sens = it; if (cat !in Cats.of(it)) cat = "" }
    CatPicker(Cats.of(sens), cat) { cat = it }
    Field(jour, { jour = it }, "Jour du mois", number = true, placeholder = "ex : 5")
    Btn(if (r0 != null) "✅ Enregistrer" else "✅ Ajouter") {
        val v = num(amt)
        if (lib.isBlank()) { Nav.notify("Nom requis", true); return@Btn }
        if (v == null || v <= 0) { Nav.notify("Montant invalide", true); return@Btn }
        val j = (jour.toIntOrNull() ?: 5).coerceIn(1, 31)
        val c = cat.ifEmpty { "Divers" }
        if (r0 != null) Db.edit { st -> st.copy(rec = st.rec.map { if (it.id == r0.id) it.copy(libelle = lib.trim(), montant = Logic.r2(v), sens = sens, categorie = c, jour = j) else it }) }
        else { val now = System.currentTimeMillis(); Db.edit { it.copy(rec = it.rec + Rec("r$now", lib.trim(), Logic.r2(v), sens, c, j, now)) } }
        Db.tick()
        Nav.close(); Nav.notify(if (r0 != null) "Récurrente modifiée" else "Récurrente ajoutée")
    }
    if (r0 != null) BtnDanger("🗑️ Supprimer") {
        Nav.confirm("Supprimer cette récurrente ?", "Les mois passés ne seront pas modifiés.", "Supprimer") {
            Db.edit { st -> st.copy(rec = st.rec.filter { it.id != r0.id }) }
            Nav.close(); Nav.notify("Récurrente supprimée")
        }
    }
}

@Composable
internal fun RecMonthSheet(rid: String, ym: String) {
    val s = Db.s
    val r = s.rec.find { it.id == rid } ?: run { Nav.close(); return }
    val t = Logic.recTxOf(s, r, ym)
    var amt by remember { mutableStateOf(numStr(t?.montant ?: Logic.recAmt(s, r, ym))) }
    SheetTitle("Montant de ${Logic.fmtM(ym)}", "${r.libelle} · montant habituel : ${Logic.fmt(r.montant)}. Seul ce mois-ci est modifié.")
    Field(amt, { amt = it }, "Montant (€)", number = true, big = true)
    Btn(if (t != null) "✅ Enregistrer" else "✅ Cocher avec ce montant") {
        val v = num(amt)
        if (v == null || v <= 0) { Nav.notify("Montant invalide", true); return@Btn }
        Db.edit { Logic.setRecMonth(it, rid, ym, v) }
        Nav.close(); Nav.notify("✅ ${r.libelle} : ${Logic.fmt(v)} ce mois-ci")
    }
}

// ═══ CRÉDITS ═══════════════════════════════════════════════
@Composable
internal fun CreditDetailSheet(id: String) {
    val c = Db.s.cred.find { it.id == id } ?: run { Nav.close(); return }
    val e = Logic.crEtat(c, Db.today)
    SheetTitle(c.nom)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            KV("Emprunté", Logic.fmt(c.montant)); KV("Mensualité", Logic.fmt(e.m)); KV("Capital restant", Logic.fmt(e.cap))
            KV("Intérêts payés", Logic.fmt(e.itPaid)); KV("Coût total", Logic.fmt(e.itTot))
        }
        Column(Modifier.weight(1f)) {
            KV("Taux", c.taux.toString().removeSuffix(".0").replace('.', ',') + " %"); KV("Échéances", "${e.nPaid} / ${e.rows.size}")
            KV("Fin", Logic.fmtM(e.fin)); KV("Intérêts restants", Logic.fmt(e.itRest)); KV("Remboursé", Logic.fmt(e.totPay))
        }
    }
    e.prochaine?.let { p ->
        T("Prochaine échéance : ${Logic.fmt(p.pay)} le ${Logic.fmtD(p.date)} (dont ${Logic.fmt(p.it)} d'intérêts)", 12.sp, K.Tx2, modifier = Modifier.padding(vertical = 10.dp))
    }
    // échéancier résumé par année
    val yNow = Db.today.year.toString()
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp)) {
        listOf("Année", "Éch.", "Capital", "Intérêts", "Reste fin").forEachIndexed { i, h ->
            T(h, 11.sp, K.Tx3, FontWeight.Bold, Modifier.weight(if (i < 2) 0.7f else 1.2f), if (i == 0) TextAlign.Start else TextAlign.End)
        }
    }
    Divider()
    e.rows.groupBy { it.ym.take(4) }.forEach { (y, rs) ->
        val col = if (y == yNow) K.Ac else K.Tx
        val w = if (y == yNow) FontWeight.Bold else FontWeight.Normal
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            T(y, 11.5.sp, col, w, Modifier.weight(0.7f))
            T(rs.size.toString(), 11.5.sp, col, w, Modifier.weight(0.7f), TextAlign.End)
            T(Logic.fmt(rs.sumOf { it.cap }), 11.5.sp, col, w, Modifier.weight(1.2f), TextAlign.End)
            T(Logic.fmt(rs.sumOf { it.it }), 11.5.sp, col, w, Modifier.weight(1.2f), TextAlign.End)
            T(Logic.fmt(rs.last().bal), 11.5.sp, col, w, Modifier.weight(1.2f), TextAlign.End)
        }
        Divider()
    }
    Spacer(Modifier.height(14.dp))
    Btn("✏️ Modifier") { Nav.open(Sheet.CreditEdit(c.id)) }
    BtnDanger("🗑️ Supprimer") {
        Nav.confirm("Supprimer « ${c.nom} » ?", "Sa mensualité est retirée des récurrentes ; les opérations déjà passées restent.", "Supprimer") {
            Db.edit { Logic.delCredit(it, c.id) }
            Nav.close(); Nav.notify("Crédit supprimé")
        }
    }
}

@Composable
internal fun CreditEditSheet(id: String?) {
    val c = id?.let { i -> Db.s.cred.find { it.id == i } }
    var nom by remember { mutableStateOf(c?.nom ?: "") }
    var mt by remember { mutableStateOf(numStr(c?.montant)) }
    var taux by remember { mutableStateOf(numStr(c?.taux)) }
    var dur by remember { mutableStateOf(if (c != null && c.mensu <= 0) c.duree.toString() else "") }
    var d1 by remember { mutableStateOf(c?.let { Logic.dayOf(it.debut, it.jour) } ?: "") }
    var mens by remember { mutableStateOf(if (c != null && c.mensu > 0) numStr(c.mensu) else "") }
    fun form(): Pair<Cred?, String?> {
        val m = num(mt); val d = dur.toIntOrNull() ?: 0; val ms = num(mens) ?: 0.0
        if (m == null || m <= 0) return null to "Montant emprunté requis"
        if (d1.length != 10) return null to "Date de 1re échéance requise"
        if (d <= 0 && ms <= 0) return null to "Durée ou mensualité requise"
        return Cred(c?.id ?: "c${System.currentTimeMillis()}", nom.trim().ifEmpty { "Crédit" }, Logic.r2(m), num(taux) ?: 0.0,
            d, if (d > 0) 0.0 else Logic.r2(ms), d1.take(7), d1.substring(8, 10).toInt(), c?.recId) to null
    }
    SheetTitle(if (c != null) "Modifier le crédit" else "Nouveau crédit")
    Field(nom, { nom = it }, "Nom", placeholder = "ex : Crédit Dispo CA")
    Field(mt, { mt = it }, "Montant emprunté (€)", number = true, placeholder = "ex : 10000")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(taux, { taux = it }, "Taux annuel (%)", Modifier.weight(1f), number = true, placeholder = "ex : 6")
        Field(dur, { dur = it }, "Durée (mois)", Modifier.weight(1f), number = true, placeholder = "ex : 60")
    }
    DateField("Date de la 1re échéance", d1) { d1 = it }
    Field(mens, { mens = it }, "Mensualité de la banque (facultatif)", number = true, placeholder = "ex : 193")
    T("Sans durée, KBG calcule la fin à partir de cette mensualité. Elle est ajoutée seule à tes récurrentes, jusqu'à la dernière échéance.",
        11.sp, K.Tx3, modifier = Modifier.padding(bottom = 12.dp))
    val (f, _) = form()
    if (f != null) {
        val p = Logic.crPlan(f)
        Box(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
            if (p.err != null) T(p.err, 12.5.sp, K.Re, align = TextAlign.Center)
            else T("${Logic.fmt(p.m)} / mois · ${p.rows.size} mois · fin ${Logic.fmtM(p.rows.last().ym)}\nIntérêts au total : ${Logic.fmt(p.rows.sumOf { it.it })}",
                12.5.sp, K.Tx, align = TextAlign.Center)
        }
    }
    Btn(if (c != null) "✅ Enregistrer" else "✅ Ajouter") {
        val (cr, err) = form()
        if (cr == null) { Nav.notify(err ?: "Erreur", true); return@Btn }
        val p = Logic.crPlan(cr); if (p.err != null) { Nav.notify(p.err, true); return@Btn }
        Db.edit { Logic.saveCredit(it, cr, System.currentTimeMillis()) }
        Db.tick()
        Nav.open(Sheet.CreditDetail(cr.id)); Nav.notify(if (c != null) "Crédit modifié" else "✅ Crédit ajouté")
    }
}

// ═══ BULLETINS DE PAIE ═════════════════════════════════════
@Composable
internal fun PaiesSheet() {
    val ctx = LocalContext.current
    val s = Db.s
    SheetTitle("Bulletins de paie", "Rangés dans KBG (protégés par l'empreinte). « Partager » en envoie une copie vers Drive, Gmail…")
    Btn("＋ Ajouter un bulletin (PDF)") { Db.dropStaged(); Nav.close(); Nav.paieReq++ }
    Spacer(Modifier.height(14.dp))
    if (s.paie.isEmpty()) T("Aucun bulletin pour l'instant", 12.5.sp, K.Tx3, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), align = TextAlign.Center)
    Logic.paieParAn(s).forEach { (y, l, tot) ->
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            T(y, 15.sp, K.Tx, FontWeight.ExtraBold, Modifier.weight(1f))
            if (tot > 0) { T("Net ", 12.sp, K.Tx2); T(Logic.fmt(tot), 12.5.sp, K.Gr, FontWeight.Bold) }
        }
        Divider()
        l.forEach { p ->
            val ok = remember(p.ym) { Db.paieOk(p.ym) }
            Row(Modifier.fillMaxWidth().clickable { Nav.open(Sheet.PaieEdit(p.ym)) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T("📄 " + Logic.fmtM(p.ym), 14.sp, K.Tx, FontWeight.SemiBold)
                    T(if (!ok) "fichier manquant" else p.net?.let { "net " + Logic.fmt(it) } ?: "net non renseigné", 11.5.sp, if (!ok) K.Re else K.Tx2)
                }
                if (ok) {
                    SmallBtn("Ouvrir", K.Ac, androidx.compose.ui.graphics.Color.White) { if (!Db.openPaie(ctx, p.ym)) Nav.notify("Aucune appli pour lire les PDF", true) }
                    Spacer(Modifier.width(8.dp))
                    SmallBtn("Partager") { if (!Db.sharePaie(ctx, p.ym)) Nav.notify("Partage impossible", true) }
                }
            }
            Divider()
        }
    }
}

@Composable
internal fun PaieEditSheet(ym0: String?) {
    val p0 = ym0?.let { y -> Db.s.paie.find { it.ym == y } }
    var ym by remember { mutableStateOf(p0?.ym ?: Logic.paieMoisDefaut(Db.today)) }
    var net by remember { mutableStateOf(numStr(p0?.net)) }
    val nouveau = remember { Db.hasStaged() }
    SheetTitle(if (p0 != null) "Bulletin de ${Logic.fmtM(p0.ym)}" else "Nouveau bulletin",
        if (nouveau) "📄 PDF prêt à être rangé" else "")
    T("Mois du bulletin", 11.sp, K.Tx3, modifier = Modifier.padding(bottom = 4.dp))
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).height(52.dp).clip(RoundedCornerShape(10.dp)).background(K.Sf2), verticalAlignment = Alignment.CenterVertically) {
        T("‹", 24.sp, K.Tx2, modifier = Modifier.padding(horizontal = 16.dp).tap { ym = Logic.prevM(ym) })
        T(Logic.fmtM(ym), 15.sp, K.Tx, FontWeight.SemiBold, Modifier.weight(1f), TextAlign.Center)
        T("›", 24.sp, K.Tx2, modifier = Modifier.padding(horizontal = 16.dp).tap { ym = Logic.nextM(ym) })
    }
    Field(net, { net = it }, "Net à payer (€)", number = true, placeholder = "facultatif")
    val autre = Db.s.paie.any { it.ym == ym && it.ym != p0?.ym }
    if (autre) T("⚠️ Un bulletin existe déjà pour ${Logic.fmtM(ym)} : il sera remplacé.", 12.sp, K.Ye, modifier = Modifier.padding(bottom = 10.dp))
    Btn("✅ Enregistrer") {
        if (p0 == null && !Db.hasStaged()) { Nav.notify("Choisis d'abord le PDF", true); return@Btn }
        val v = num(net)?.let { Logic.r2(it) }
        if (Db.savePaie(ym, v, p0?.ym)) { Nav.open(Sheet.Paies); Nav.notify("📄 Bulletin de ${Logic.fmtM(ym)} rangé") }
        else Nav.notify("Enregistrement impossible", true)
    }
    if (p0 != null) {
        BtnGhost("📄 Remplacer le PDF") { Db.dropStaged(); Nav.close(); Nav.paieReq++ }
        BtnDanger("🗑️ Supprimer") {
            Nav.confirm("Supprimer ce bulletin ?", "Le PDF de ${Logic.fmtM(p0.ym)} sera effacé de KBG.", "Supprimer") {
                Db.delPaie(p0.ym); Nav.open(Sheet.Paies); Nav.notify("Bulletin supprimé")
            }
        }
    } else BtnGhost("Annuler") { Db.dropStaged(); Nav.open(Sheet.Paies) }
}
