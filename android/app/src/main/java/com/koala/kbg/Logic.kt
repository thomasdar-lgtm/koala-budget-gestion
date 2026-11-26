package com.koala.kbg

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/** Calculs de KBG (repris à l'identique de la version web). Fonctions pures : l'état n'est jamais modifié sur place. */
object Logic {

    fun r2(x: Double): Double = Math.round(x * 100.0) / 100.0

    // ── dates ───────────────────────────────────────────────
    fun ds(d: LocalDate): String = d.toString()
    fun ym(d: LocalDate): String = d.toString().take(7)
    fun prevM(ym: String): String = YearMonth.parse(ym).minusMonths(1).toString()
    fun nextM(ym: String): String = YearMonth.parse(ym).plusMonths(1).toString()
    fun dim(ym: String): Int = YearMonth.parse(ym).lengthOfMonth()
    fun dayOf(ym: String, jour: Int): String = ym + "-" + minOf(jour, dim(ym)).toString().padStart(2, '0')

    private val MFU = listOf("Janvier", "Février", "Mars", "Avril", "Mai", "Juin", "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre")
    val MFR = listOf("Jan", "Fév", "Mar", "Avr", "Mai", "Jun", "Jul", "Aoû", "Sep", "Oct", "Nov", "Déc")
    fun fmtM(ym: String): String = if (ym.length < 7) "" else MFU[ym.substring(5, 7).toInt() - 1] + " " + ym.take(4)
    fun fmtD(d: String): String = if (d.length < 10) d else d.substring(8, 10) + "/" + d.substring(5, 7) + "/" + d.take(4)

    /** 1 234,50 € (valeur absolue, comme la version web) */
    fun fmt(n: Double): String {
        val c = abs(n * 100).roundToLong()
        val e = (c / 100).toString().reversed().chunked(3).joinToString(" ").reversed()
        return e + "," + (c % 100).toString().padStart(2, '0') + " €"
    }
    /** avec le signe moins si négatif */
    fun fmtS(n: Double): String = (if (n < -0.004) "-" else "") + fmt(n)

    // ── catégories ──────────────────────────────────────────
    fun cat(s: St, t: Tx): String = s.ovr[t.id.toString()] ?: t.categorie

    // ── récurrentes ─────────────────────────────────────────
    fun recOn(r: Rec, ym: String) = (r.debut == null || ym >= r.debut) && (r.fin == null || ym <= r.fin)
    fun recsOf(s: St, ym: String) = s.rec.filter { recOn(it, ym) }
    fun done(s: St, ym: String): List<String> = s.recDone[ym] ?: emptyList()
    fun skipped(s: St, ym: String): List<String> = s.recSkip[ym] ?: emptyList()

    fun recTxOf(s: St, r: Rec, ym: String): Tx? =
        s.tx.find { it.type == "rec" && it.mois == ym && (it.recId == r.id || (it.recId == null && it.libelle == r.libelle)) }

    /** montant prévu d'une récurrente pour un mois (échéance exacte pour un crédit) */
    fun recAmt(s: St, r: Rec, ym: String): Double {
        if (r.credId != null) {
            val c = s.cred.find { it.id == r.credId }
            if (c != null) crPlan(c).rows.find { it.ym == ym }?.let { return it.pay }
        }
        return r.montant
    }

    fun nextId(s: St): Long = (s.tx.maxOfOrNull { it.id } ?: 0) + 1

    /** coche / décoche une récurrente pour un mois (crée ou supprime l'opération du mois) */
    fun toggleRec(s: St, rid: String, ym: String): St {
        val r = s.rec.find { it.id == rid } ?: return s
        val d = done(s, ym)
        return if (rid !in d) {
            var tx = s.tx
            if (recTxOf(s, r, ym) == null) {
                tx = tx + Tx(
                    id = nextId(s), date = dayOf(ym, r.jour), libelle = r.libelle, montant = recAmt(s, r, ym),
                    categorie = r.categorie, sens = r.sens, type = "rec", displayMonth = ym, recId = r.id,
                )
            }
            s.copy(tx = tx, recDone = s.recDone + (ym to d + rid))
        } else {
            s.copy(
                tx = s.tx.filter { !(it.type == "rec" && it.mois == ym && (it.recId == r.id || (it.recId == null && it.libelle == r.libelle))) },
                recDone = s.recDone + (ym to d - rid),
            )
        }
    }

    fun setRecSkip(s: St, rid: String, ym: String, skip: Boolean): St {
        val a = skipped(s, ym)
        val n = if (skip) (if (rid in a) a else a + rid) else a - rid
        return s.copy(recSkip = s.recSkip + (ym to n))
    }

    /** case cochée à la main dans Opérations : décocher = « pas encore passée », l'auto-cochage la laisse tranquille */
    fun toggleRecUser(s: St, rid: String, ym: String): St {
        val t = toggleRec(s, rid, ym)
        return setRecSkip(t, rid, ym, rid !in done(t, ym))
    }

    /** montant réel d'un mois (modifie l'opération du mois, jamais la récurrente) */
    fun setRecMonth(s: St, rid: String, ym: String, v: Double): St {
        val r = s.rec.find { it.id == rid } ?: return s
        var t = if (recTxOf(s, r, ym) == null) toggleRec(s, rid, ym) else s
        t = setRecSkip(t, rid, ym, false)
        val tx = recTxOf(t, r, ym) ?: return t
        return t.copy(tx = t.tx.map { if (it === tx) it.copy(montant = r2(v)) else it })
    }

    private fun createdOf(r: Rec): Long =
        r.createdAt ?: if (Regex("^r\\d{12,}$").matches(r.id)) r.id.drop(1).toLongOrNull() ?: 0 else 0

    /** coche toute seule les récurrentes dont le jour est passé, et rattrape la fin du mois précédent */
    fun autoCheckRec(s0: St, today: LocalDate): St {
        var s = s0
        val ym = ym(today); val day = today.dayOfMonth
        recsOf(s, ym).forEach { r ->
            if (r.id !in done(s, ym) && r.id !in skipped(s, ym) && r.jour <= day) s = toggleRec(s, r.id, ym)
        }
        val last = s.lastSeen; val pym = prevM(ym)
        if (last != null && last.length >= 10 && last.take(7) == pym) {
            val lastDay = last.substring(8, 10).toInt()
            val lastTs = LocalDate.parse(last.take(10)).atTime(23, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            recsOf(s, pym).forEach { r ->
                if (createdOf(r) > lastTs) return@forEach
                if (r.id !in done(s, pym) && r.id !in skipped(s, pym) && r.jour > lastDay) s = toggleRec(s, r.id, pym)
            }
        }
        val t = ds(today)
        if (s.lastSeen != t) s = s.copy(lastSeen = t)
        return s
    }

    // ── carte à débit différé ───────────────────────────────
    fun isCard(t: Tx) = t.sens == DEBIT && t.type != "rec" && t.carte != false
    fun lastBizDay(y: Int, m: Int): String {
        var d = YearMonth.of(y, m).atEndOfMonth()
        while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) d = d.minusDays(1)
        return d.toString()
    }
    fun debitDate(ds: String): String {
        val y = ds.take(4).toInt(); val m = ds.substring(5, 7).toInt()
        val l = lastBizDay(y, m)
        if (ds < l) return l
        val n = YearMonth.of(y, m).plusMonths(1)
        return lastBizDay(n.year, n.monthValue)
    }
    fun encours(s: St, d: String): Double = s.tx.filter { isCard(it) && it.date <= d && debitDate(it.date) > d }.sumOf { it.montant }

    // ── soldes ──────────────────────────────────────────────
    data class Soldes(val reel: Double, val prev: Double, val anchor: Solde?)

    fun pendNet(s: St, ym: String): Double {
        val d = done(s, ym)
        return recsOf(s, ym).filter { it.id !in d }.sumOf { if (it.sens == CREDIT) recAmt(s, it, ym) else -recAmt(s, it, ym) }
    }

    fun calcSoldes(s: St, ym: String, today: LocalDate): Soldes {
        val td = ds(today); val pn = pendNet(s, ym)
        val a = s.solde
        if (a != null) {
            val after = s.tx.filter { it.date > a.date }
            val reel = a.montant - encours(s, a.date) + after.filter { it.date <= td }.sumOf { it.signe }
            val futur = after.filter { it.date > td }.sumOf { it.signe }
            return Soldes(reel, reel + futur + pn, a)
        }
        val net = s.tx.filter { it.mois == ym }.sumOf { it.signe }
        return Soldes(net, net + pn, null)
    }

    /** solde vu par la banque au soir d'une date (null sans solde de référence) */
    fun soldeAt(s: St, d: String): Double? {
        val a = s.solde ?: return null
        val base = a.montant - encours(s, a.date)
        val dispo = if (d >= a.date) base + s.tx.filter { it.date > a.date && it.date <= d }.sumOf { it.signe }
        else base - s.tx.filter { it.date > d && it.date <= a.date }.sumOf { it.signe }
        return dispo + encours(s, d)
    }

    // ── budgets ─────────────────────────────────────────────
    data class Bud(val cat: String, val bud: Double, val sp: Double) { val pct get() = sp / bud }
    fun budList(s: St, ym: String): List<Bud> {
        val sp = HashMap<String, Double>()
        s.tx.filter { it.mois == ym && it.sens == DEBIT }.forEach { val k = cat(s, it); sp[k] = (sp[k] ?: 0.0) + it.montant }
        return s.bud.filter { it.value > 0 }.map { Bud(it.key, it.value, sp[it.key] ?: 0.0) }.sortedByDescending { it.pct }
    }

    // ── saisie rapide : libellés fréquents des 90 derniers jours ──
    fun habits(s: St, sens: String, today: LocalDate): List<Tx> {
        val lim = ds(today.minusDays(90))
        val m = LinkedHashMap<String, Pair<Int, Tx>>()
        s.tx.filter { it.type != "rec" && it.sens == sens && it.date >= lim }.forEach { t ->
            val k = t.libelle.trim().lowercase(); if (k.isEmpty()) return@forEach
            val e = m[k]
            m[k] = if (e == null) 1 to t else (e.first + 1) to (if (t.date >= e.second.date) t else e.second)
        }
        return m.values.sortedWith(compareByDescending<Pair<Int, Tx>> { it.first }.thenByDescending { it.second.date }).map { it.second }
    }

    // ── crédits ─────────────────────────────────────────────
    data class Row(val ym: String, val date: String, val it: Double, val cap: Double, val pay: Double, val bal: Double)
    data class Plan(val m: Double, val rows: List<Row>, val err: String? = null)

    /** échéancier : mensualité constante (calculée, ou celle indiquée par la banque) */
    fun crPlan(c: Cred): Plan {
        val t = c.taux / 1200.0
        if (c.debut.length < 7 || (c.mensu <= 0 && c.duree <= 0)) return Plan(0.0, emptyList(), "Durée ou mensualité requise")
        val m = r2(if (c.mensu > 0) c.mensu else if (t != 0.0) c.montant * t / (1 - (1 + t).pow(-c.duree)) else c.montant / c.duree)
        val rows = ArrayList<Row>()
        var bal = c.montant; var ym = c.debut; var k = 0
        while (k < 600 && bal > 0.004) {
            val it = r2(bal * t)
            var cap = r2(m - it)
            if (cap <= 0) return Plan(m, emptyList(), "La mensualité ne couvre pas les intérêts")
            if (cap > bal || (c.mensu <= 0 && k == c.duree - 1)) cap = bal
            bal = r2(bal - cap)
            rows += Row(ym, dayOf(ym, c.jour), it, cap, r2(it + cap), bal)
            ym = nextM(ym); k++
        }
        return Plan(m, rows)
    }

    data class Etat(
        val m: Double, val err: String?, val rows: List<Row>, val nPaid: Int, val nRest: Int, val cap: Double,
        val itPaid: Double, val itRest: Double, val itTot: Double, val totPay: Double, val fin: String, val prochaine: Row?,
    )

    fun crEtat(c: Cred, today: LocalDate): Etat {
        val p = crPlan(c); val td = ds(today)
        val paid = p.rows.filter { it.date <= td }; val rest = p.rows.drop(paid.size)
        return Etat(
            m = p.m, err = p.err, rows = p.rows, nPaid = paid.size, nRest = rest.size,
            cap = paid.lastOrNull()?.bal ?: c.montant,
            itPaid = r2(paid.sumOf { it.it }), itRest = r2(rest.sumOf { it.it }), itTot = r2(p.rows.sumOf { it.it }),
            totPay = r2(p.rows.sumOf { it.pay }), fin = p.rows.lastOrNull()?.ym ?: c.debut, prochaine = rest.firstOrNull(),
        )
    }

    data class Totaux(val cap: Double, val mens: Double, val itRest: Double, val fin: String)
    fun crTotaux(s: St, today: LocalDate): Totaux {
        var cap = 0.0; var mens = 0.0; var it = 0.0; var fin = ""
        s.cred.forEach { c -> val e = crEtat(c, today); if (e.nRest > 0) { cap += e.cap; mens += e.m; it += e.itRest; if (e.fin > fin) fin = e.fin } }
        return Totaux(r2(cap), r2(mens), r2(it), fin)
    }

    /** récurrente du crédit : créée, mise à jour, arrêtée à la dernière échéance */
    fun saveCredit(s: St, c0: Cred, now: Long): St {
        val p = crPlan(c0); if (p.rows.isEmpty()) return s
        val c = if (c0.duree <= 0) c0.copy(duree = p.rows.size) else c0
        val old = s.rec.find { it.id == c.recId }
        val rid = old?.id ?: "r$now"
        val r = Rec(
            id = rid, libelle = c.nom, montant = p.m, sens = DEBIT, categorie = Cats.CREDITS, jour = c.jour,
            createdAt = old?.createdAt ?: now, debut = c.debut, fin = p.rows.last().ym, credId = c.id,
        )
        val cc = c.copy(recId = rid)
        val creds = if (s.cred.any { it.id == c.id }) s.cred.map { if (it.id == c.id) cc else it } else s.cred + cc
        val recs = if (old != null) s.rec.map { if (it.id == rid) r else it } else s.rec + r
        return s.copy(cred = creds, rec = recs)
    }

    fun delCredit(s: St, id: String): St {
        val c = s.cred.find { it.id == id } ?: return s
        return s.copy(cred = s.cred.filter { it.id != id }, rec = s.rec.filter { it.id != c.recId })
    }

    // ── bilan du mois ───────────────────────────────────────
    data class Ecart(val cat: String, val cur: Double, val prev: Double) { val diff get() = cur - prev }
    data class Bilan(
        val ym: String, val rev: Double, val dep: Double, val nb: Int, val soldeFin: Double?,
        val hausses: List<Ecart>, val baisses: List<Ecart>, val buds: List<Bud>,
    ) {
        val net get() = rev - dep
        val vide get() = nb == 0
    }

    /** résumé d'un mois, comparé au mois d'avant (écarts de moins de 5 € ignorés) */
    fun bilan(s: St, ym: String, today: LocalDate): Bilan {
        val tx = s.tx.filter { it.mois == ym }
        fun parCatAll(m: String): Map<String, Double> {
            val r = HashMap<String, Double>()
            s.tx.filter { it.mois == m && it.sens == DEBIT }.forEach { val c = cat(s, it); r[c] = (r[c] ?: 0.0) + it.montant }
            return r
        }
        val cur = parCatAll(ym); val prev = parCatAll(prevM(ym))
        val ec = (cur.keys + prev.keys).map { Ecart(it, cur[it] ?: 0.0, prev[it] ?: 0.0) }.filter { abs(it.diff) >= 5 }
        return Bilan(
            ym = ym, rev = r2(tx.filter { it.sens == CREDIT }.sumOf { it.montant }), dep = r2(tx.filter { it.sens == DEBIT }.sumOf { it.montant }),
            nb = tx.size, soldeFin = soldeFinMois(s, ym, today),
            hausses = ec.filter { it.diff > 0 }.sortedByDescending { it.diff }.take(3),
            baisses = ec.filter { it.diff < 0 }.sortedBy { it.diff }.take(3),
            buds = budList(s, ym),
        )
    }

    /** budget d'une catégorie pour un mois (null si pas de budget) */
    fun budOf(s: St, cat: String, ym: String): Bud? = budList(s, ym).find { it.cat == cat }

    /** message après une saisie : 80 % atteints ou budget dépassé (null sinon) ; second = dépassé */
    fun budAlert(s: St, cat: String, ym: String): Pair<String, Boolean>? {
        val b = budOf(s, cat, ym) ?: return null
        return when {
            b.sp > b.bud + 0.004 -> "⛔ $cat : budget dépassé de ${fmt(b.sp - b.bud)}" to true
            b.pct >= 0.8 -> "⚠️ $cat : ${Math.round(b.pct * 100)} % du budget (${fmt(b.bud - b.sp)} restants)" to false
            else -> null
        }
    }

    // ── bulletins de paie ───────────────────────────────────
    /** ajoute ou remplace le bulletin d'un mois (old = mois d'origine si on change de mois) */
    fun setPaie(s: St, p: Paie, old: String? = null): St =
        s.copy(paie = (s.paie.filter { it.ym != p.ym && it.ym != old } + p).sortedByDescending { it.ym })
    fun delPaie(s: St, ym: String): St = s.copy(paie = s.paie.filter { it.ym != ym })
    /** par année (récente en haut) : bulletins + total net */
    fun paieParAn(s: St): List<Triple<String, List<Paie>, Double>> =
        s.paie.sortedByDescending { it.ym }.groupBy { it.ym.take(4) }.map { (y, l) -> Triple(y, l, r2(l.sumOf { it.net ?: 0.0 })) }
    private val MOIS = listOf("janvier" to 1, "janv" to 1, "fevrier" to 2, "fevr" to 2, "fev" to 2, "mars" to 3, "avril" to 4, "avr" to 4,
        "mai" to 5, "juin" to 6, "juillet" to 7, "juil" to 7, "aout" to 8, "septembre" to 9, "sept" to 9, "octobre" to 10, "oct" to 10,
        "novembre" to 11, "nov" to 11, "decembre" to 12, "dec" to 12)
    private val MOIS_RX = MOIS.joinToString("|") { it.first }
    private val MOIS_PLEIN = MOIS.filter { it.first.length >= 4 && it.first !in listOf("janv", "fevr", "juil", "sept") }.joinToString("|") { it.first }
    private fun moisNum(n: String) = MOIS.first { it.first == n }.second
    private fun ymOk(y: Int, m: Int) = if (y in 2000..2099 && m in 1..12) "%04d-%02d".format(y, m) else null

    /** mois d'un bulletin d'après le nom du fichier (« Bulletin_novembre_2026.pdf », « BS_2026-11 », « 112026 »…) */
    fun moisDepuisNom(nom: String): String? {
        val n = plain(nom)
        Regex("(?<![a-z])($MOIS_RX)(?![a-z])[^a-z0-9]{0,3}(20\\d{2})").find(n)?.let { return ymOk(it.groupValues[2].toInt(), moisNum(it.groupValues[1])) }
        Regex("(20\\d{2})[^a-z0-9]{0,3}($MOIS_RX)(?![a-z])").find(n)?.let { return ymOk(it.groupValues[1].toInt(), moisNum(it.groupValues[2])) }
        Regex("(?<!\\d)(20\\d{2})[-_. ]?(0[1-9]|1[0-2])").find(n)?.let { return ymOk(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
        Regex("(?<!\\d)(0[1-9]|1[0-2])[-_. ]?(20\\d{2})(?!\\d)").find(n)?.let { return ymOk(it.groupValues[2].toInt(), it.groupValues[1].toInt()) }
        return null
    }

    /** mois d'un bulletin d'après son texte : « Novembre 2026 » le plus fréquent, sinon « période du 01/11/2026 » */
    fun moisDepuisTexte(txt: String): String? {
        val n = plain(txt)
        val c = HashMap<String, Int>()
        Regex("(?<![a-z])($MOIS_PLEIN)(?![a-z])\\s{0,3}(20\\d{2})").findAll(n).forEach { m ->
            ymOk(m.groupValues[2].toInt(), moisNum(m.groupValues[1]))?.let { c[it] = (c[it] ?: 0) + 1 }
        }
        c.maxByOrNull { it.value }?.let { return it.key }
        Regex("(periode|du)[^0-9]{0,12}(\\d{2})/(\\d{2})/(20\\d{2})").find(n)?.let { return ymOk(it.groupValues[4].toInt(), it.groupValues[3].toInt()) }
        return null
    }

    /** texte lisible d'un PDF (meilleur effort : chaînes ( ) des flux, compressés ou non) */
    fun pdfText(b: ByteArray): String {
        val raw = String(b, Charsets.ISO_8859_1)
        val parts = StringBuilder()
        var i = 0
        while (true) {
            val s = raw.indexOf("stream", i); if (s < 0) break
            var a = s + 6
            if (a < raw.length && raw[a] == '\r') a++
            if (a < raw.length && raw[a] == '\n') a++
            val e = raw.indexOf("endstream", a); if (e < 0) break
            val chunk = b.copyOfRange(a, e)
            val inf = runCatching {
                val z = java.util.zip.Inflater(); z.setInput(chunk)
                val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(8192)
                while (!z.finished()) { val n = z.inflate(buf); if (n == 0 && (z.needsInput() || z.needsDictionary())) break; out.write(buf, 0, n) }
                z.end(); String(out.toByteArray(), Charsets.ISO_8859_1)
            }.getOrNull()
            parts.append(strings(inf ?: String(chunk, Charsets.ISO_8859_1))).append(' ')
            i = e + 9
        }
        return parts.toString()
    }

    /** contenu des chaînes PDF entre parenthèses, mis bout à bout */
    private fun strings(s: String): String {
        val sb = StringBuilder(); var depth = 0; var k = 0
        while (k < s.length) {
            val ch = s[k]
            when {
                ch == '\\' && depth > 0 && k + 1 < s.length -> { val nx = s[k + 1]; sb.append(when (nx) { 'n' -> ' '; 'r' -> ' '; 't' -> ' '; else -> nx }); k++ }
                ch == '(' -> { if (depth > 0) sb.append(ch); depth++ }
                ch == ')' && depth > 0 -> { depth--; if (depth > 0) sb.append(ch) else sb.append(' ') }
                depth > 0 -> sb.append(ch)
                ch == ']' || (ch == 'T' && k + 1 < s.length && (s[k + 1] == '*' || s[k + 1] == 'd' || s[k + 1] == 'D')) -> sb.append(' ')
            }
            k++
        }
        return sb.toString()
    }

    /** mois d'un bulletin : nom du fichier, sinon contenu */
    fun moisBulletin(nom: String, b: ByteArray?): String? = moisDepuisNom(nom) ?: b?.let { runCatching { moisDepuisTexte(pdfText(it)) }.getOrNull() }

    private const val MONTANT = "(\\d{1,3}(?:[. \u00a0\u202f]\\d{3})+,\\d{2}|\\d+,\\d{2}|\\d+\\.\\d{2})"
    private fun montant(s: String): Double? {
        val t = s.replace(" ", "").replace("\u00a0", "").replace("\u202f", "")
        val v = if (t.contains(',')) t.replace(".", "").replace(',', '.') else t
        return v.toDoubleOrNull()?.takeIf { it in 50.0..50000.0 }
    }

    /** net payé d'un bulletin : « net à payer au salarié », « net payé »… (jamais le net avant impôt) */
    fun netDepuisTexte(txt: String): Double? {
        val n = plain(txt)
        listOf("net a payer au salarie", "net paye", "net verse", "net a payer(?! avant)").forEach { lbl ->
            Regex("$lbl[^0-9-]{0,40}$MONTANT").find(n)?.let { m -> montant(m.groupValues[1])?.let { return r2(it) } }
        }
        return null
    }

    /** mois et net d'un bulletin (le texte du PDF n'est lu qu'une fois) */
    fun lireBulletin(nom: String, b: ByteArray): Pair<String?, Double?> {
        val txt = runCatching { pdfText(b) }.getOrDefault("")
        return (moisDepuisNom(nom) ?: runCatching { moisDepuisTexte(txt) }.getOrNull()) to runCatching { netDepuisTexte(txt) }.getOrNull()
    }

    /** mois proposé pour un nouveau bulletin : le mois en cours à partir du 20, sinon le précédent */
    fun paieMoisDefaut(today: LocalDate): String = if (today.dayOfMonth >= 20) ym(today) else prevM(ym(today))

    // ── pointage ────────────────────────────────────────────
    fun togglePointe(s: St, id: Long): St =
        s.copy(tx = s.tx.map { if (it.id == id) it.copy(pointe = if (it.pointe == true) null else true) else it })

    /** opérations du mois pas encore vues sur le relevé (jusqu'à aujourd'hui) */
    fun aPointer(s: St, ym: String, today: LocalDate): List<Tx> {
        val td = ds(today)
        return s.tx.filter { it.mois == ym && it.pointe != true && it.date <= td }
    }

    // ── recherche ───────────────────────────────────────────
    private fun plain(x: String) = java.text.Normalizer.normalize(x, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").lowercase().trim()

    /** opérations de tous les mois dont le libellé, la catégorie ou le montant correspond (86 trouve 86,40) */
    fun search(s: St, q: String): List<Tx> {
        val p = plain(q); if (p.isEmpty()) return emptyList()
        val n = p.replace(" ", "").replace('€', ' ').trim().replace(',', '.').toDoubleOrNull()
        val entier = n != null && !p.contains(',') && !p.contains('.')
        return s.tx.filter { t ->
            plain(t.libelle).contains(p) || plain(cat(s, t)).contains(p) ||
                (n != null && (abs(t.montant - n) < 0.005 || (entier && kotlin.math.floor(t.montant) == n)))
        }.sortedWith(compareByDescending<Tx> { it.date }.thenByDescending { it.id })
    }

    // ── analyse ─────────────────────────────────────────────
    fun lastMonths(n: Int, today: LocalDate): List<String> {
        val r = ArrayList<String>(); var m = ym(today)
        repeat(n) { r.add(0, m); m = prevM(m) }
        return r
    }
    /** solde réel (hors encours carte) en fin de chaque mois */
    fun soldeFinMois(s: St, ym: String, today: LocalDate): Double? {
        val l = YearMonth.parse(ym).atEndOfMonth().toString()
        val d = if (l < ds(today)) l else ds(today)
        val v = soldeAt(s, d) ?: return null
        return r2(v - encours(s, d))
    }
    fun parCat(s: St, ym: String): List<Pair<String, Double>> {
        val m = LinkedHashMap<String, Double>()
        s.tx.filter { it.mois == ym && it.sens == DEBIT }.forEach { val c = cat(s, it); m[c] = (m[c] ?: 0.0) + it.montant }
        return m.entries.sortedByDescending { it.value }.take(10).map { it.key to it.value }
    }
}
