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
                    is Sheet.AddTx -> TxFormSheet(null, sh.sens)
                    is Sheet.EditTx -> TxFormSheet(sh.id)
                    is Sheet.RecEdit -> RecEditSheet(sh.id)
                    is Sheet.RecMonth -> RecMonthSheet(sh.rid, sh.ym)
                    is Sheet.CreditDetail -> CreditDetailSheet(sh.id)
                    is Sheet.CreditEdit -> CreditEditSheet(sh.id)
                    is Sheet.Pioche -> TirageSheet(sh.credId)
                    is Sheet.PaieEdit -> PaieEditSheet(sh.ym)
                    Sheet.PaieLot -> PaieLotSheet()
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
    MenuItem("Bulletins de paie", Db.s.paie.size.let { if (it == 0) "aucun" else "$it bulletin" + (if (it > 1) "s" else "") }) { Nav.close(); Nav.page = "paie" }
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

// ═══ CRÉDITS ═══════════════════════════════════════════════
@Composable
internal fun CreditDetailSheet(id: String) {
    val c = Db.s.cred.find { it.id == id } ?: run { Nav.close(); return }
    val e = Logic.crEtat(c, Db.today)
    if (c.renouv) { RevDetail(c, e); return }
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
    var type by remember { mutableStateOf(c?.type ?: "amort") }
    if (c == null) { SheetTitle("Nouveau crédit"); Seg(listOf("amort" to "Prêt classique", "renouv" to "Renouvelable"), type) { type = it }; Spacer(Modifier.height(6.dp)) }
    if (type == "renouv") { RevEdit(c); return }
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
    if (c != null) SheetTitle("Modifier le crédit")
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


// ═══ CRÉDIT RENOUVELABLE ═══════════════════════════════════
@Composable
private fun RevDetail(c: Cred, e: Logic.Etat) {
    val dispo = maxOf(0.0, c.plafond - e.cap)
    SheetTitle(c.nom)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            KV("Plafond", Logic.fmt(c.plafond)); KV("Utilisé", Logic.fmt(e.cap)); KV("Disponible", Logic.fmt(dispo))
        }
        Column(Modifier.weight(1f)) {
            KV("Mensualité", Logic.fmt(e.m)); KV("Taux", c.taux.toString().removeSuffix(".0").replace('.', ',') + " %")
            KV("Intérêts payés", Logic.fmt(e.itPaid))
        }
    }
    if (e.nRest > 0) T("Sans nouveau tirage : remboursé en ${Logic.fmtM(e.fin)}, encore ${Logic.fmt(e.itRest)} d'intérêts.", 12.sp, K.Tx2, modifier = Modifier.padding(vertical = 10.dp))
    else Spacer(Modifier.height(10.dp))
    Btn("💶 Piocher", bg = if (dispo > 0.004) K.Ac else K.Sf3) { if (dispo > 0.004) Nav.open(Sheet.Pioche(c.id)) else Nav.notify("Plafond atteint", true) }
    if (c.tirages.isNotEmpty()) {
        Sec("Tirages", Modifier.padding(top = 14.dp))
        c.tirages.sortedByDescending { it.date }.forEach { t ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                T(Logic.fmtD(t.date), 13.sp, K.Tx2, modifier = Modifier.weight(1f))
                T("+" + Logic.fmt(t.montant), 13.sp, K.Tx, FontWeight.Bold)
                T("  ✕", 14.sp, K.Tx3, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                    Nav.confirm("Annuler ce tirage ?", "${Logic.fmt(t.montant)} du ${Logic.fmtD(t.date)} : l'entrée correspondante est aussi retirée des opérations.", "Annuler le tirage") {
                        Db.edit { Logic.delTirage(it, c.id, t, System.currentTimeMillis()) }; Db.tick()
                    }
                }.padding(4.dp))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        T("Modifier", 13.sp, K.Tx2, FontWeight.SemiBold, Modifier.clip(RoundedCornerShape(8.dp)).clickable { Nav.open(Sheet.CreditEdit(c.id)) }.padding(8.dp))
        T("Supprimer", 13.sp, K.Re, FontWeight.SemiBold, Modifier.clip(RoundedCornerShape(8.dp)).clickable {
            Nav.confirm("Supprimer « ${c.nom} » ?", "Sa mensualité est retirée des récurrentes ; les opérations déjà passées restent.", "Supprimer") {
                Db.edit { Logic.delCredit(it, c.id) }; Nav.close()
            }
        }.padding(8.dp))
    }
}

@Composable
private fun RevEdit(c: Cred?) {
    var nom by remember { mutableStateOf(c?.nom ?: "") }
    var plaf by remember { mutableStateOf(numStr(c?.plafond)) }
    var du by remember { mutableStateOf(numStr(c?.montant)) }
    var taux by remember { mutableStateOf(numStr(c?.taux)) }
    var mens by remember { mutableStateOf(numStr(c?.let { Logic.mensuActuelle(it) })) }
    var d1 by remember { mutableStateOf(c?.let { Logic.dayOf(it.debut, it.jour) } ?: "") }
    fun form(): Cred? {
        val p = num(plaf) ?: return null; val m = num(mens) ?: return null
        if (p <= 0 || m <= 0 || d1.length != 10) return null
        val base = Cred(c?.id ?: "c${System.currentTimeMillis()}", nom.trim().ifEmpty { "Crédit renouvelable" }, Logic.r2(num(du) ?: 0.0), num(taux) ?: 0.0,
            0, Logic.r2(m), d1.take(7), d1.substring(8, 10).toInt(), c?.recId, "renouv", Logic.r2(p), c?.tirages ?: emptyList(), c?.mensuHist ?: emptyList())
        // modification : la nouvelle mensualité s'applique à partir de la prochaine échéance
        return if (c != null) Logic.setMensu(base.copy(mensu = c.mensu), m, Logic.ds(Db.today)) else base
    }
    if (c != null) SheetTitle("Modifier le crédit")
    Field(nom, { nom = it }, "Nom", placeholder = "ex : Crédit Dispo CA")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(plaf, { plaf = it }, "Plafond (€)", Modifier.weight(1f), number = true)
        Field(du, { du = it }, if (c == null) "Déjà utilisé (€)" else "Utilisé au départ", Modifier.weight(1f), number = true, placeholder = "0")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(taux, { taux = it }, "Taux annuel (%)", Modifier.weight(1f), number = true)
        Field(mens, { mens = it }, "Mensualité (€)", Modifier.weight(1f), number = true)
    }
    DateField(if (c == null) "Prochaine échéance" else "1re échéance suivie", d1) { d1 = it }
    form()?.let { f ->
        val p = Logic.crPlan(f)
        Box(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
            when {
                p.err != null -> T(p.err, 12.5.sp, K.Re, align = TextAlign.Center)
                p.rows.isNotEmpty() -> T("Remboursé en ${Logic.fmtM(p.rows.last().ym)} sans nouveau tirage · intérêts ${Logic.fmt(p.rows.sumOf { it.it })}", 12.5.sp, K.Tx2, align = TextAlign.Center)
            }
        }
    }
    Btn(if (c != null) "Enregistrer" else "Ajouter") {
        val f = form() ?: run { Nav.notify("Plafond, mensualité et échéance requis", true); return@Btn }
        Logic.crPlan(f).err?.let { Nav.notify(it, true); return@Btn }
        Db.edit { Logic.saveCredit(it, f, System.currentTimeMillis()) }; Db.tick()
        Nav.open(Sheet.CreditDetail(f.id))
    }
}

/** piocher dans un renouvelable */
@Composable
internal fun TirageSheet(credId: String) {
    val c = Db.s.cred.find { it.id == credId } ?: run { Nav.close(); return }
    val e = Logic.crEtat(c, Db.today)
    val dispo = maxOf(0.0, c.plafond - e.cap)
    var mt by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(Logic.ds(Db.today)) }
    var mens by remember { mutableStateOf(numStr(Logic.mensuActuelle(c))) }
    SheetTitle("Piocher dans ${c.nom}", "Disponible : ${Logic.fmt(dispo)}")
    Field(mt, { mt = it }, "Montant (€)", number = true, big = true, placeholder = "0,00")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        DateField("Date", date, Modifier.weight(1f)) { date = it }
        Field(mens, { mens = it }, "Mensualité (€)", Modifier.weight(1f).padding(top = 9.dp), number = true)
    }
    val v = num(mt)
    if (v != null && v > 0) {
        val m = num(mens) ?: Logic.mensuActuelle(c)
        val sim = Logic.addTirage(Db.s, c.id, date, v, m, 0).cred.find { it.id == c.id }?.let { Logic.crPlan(it) }
        if (v > dispo + 0.004) T("⛔ Plus que le disponible", 12.5.sp, K.Re, modifier = Modifier.padding(bottom = 10.dp))
        else if (sim?.err != null) T("⛔ ${sim.err}", 12.5.sp, K.Re, modifier = Modifier.padding(bottom = 10.dp))
        else if (sim != null && sim.rows.isNotEmpty()) T("Remboursé en ${Logic.fmtM(sim.rows.last().ym)} · intérêts ${Logic.fmt(sim.rows.sumOf { it.it })}", 12.5.sp, K.Tx2,
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), align = TextAlign.Center)
    }
    Btn("Piocher") {
        val x = num(mt)
        if (x == null || x <= 0) { Nav.notify("Montant requis", true); return@Btn }
        if (x > dispo + 0.004) { Nav.notify("Plus que le disponible", true); return@Btn }
        val m = num(mens) ?: Logic.mensuActuelle(c)
        val st = Logic.addTirage(Db.s, c.id, date, x, m, System.currentTimeMillis())
        st.cred.find { it.id == c.id }?.let { Logic.crPlan(it).err }?.let { Nav.notify(it, true); return@Btn }
        Db.edit { st }; Db.tick()
        Nav.open(Sheet.CreditDetail(c.id)); Nav.notify("💶 ${Logic.fmt(x)} ajoutés à ton compte")
    }
}
