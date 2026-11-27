package com.koala.kbg

import org.json.JSONArray
import org.json.JSONObject

/*
 * Données de KBG. Le format JSON est celui de la version web (export / import compatibles).
 * Montants en euros (Double arrondi au centime), dates "AAAA-MM-JJ", mois "AAAA-MM".
 */

const val DEBIT = "debit"
const val CREDIT = "credit"

data class Tx(
    val id: Long,
    val date: String,
    val libelle: String,
    val montant: Double,
    val categorie: String,
    val sens: String,
    val type: String = "manuel",          // "manuel" ou "rec" (créée par une récurrente cochée)
    val displayMonth: String? = null,     // mois de rattachement (sinon le mois de la date)
    val recId: String? = null,
    val carte: Boolean? = null,           // dépense payée par carte à débit différé (null = oui)
    val pointe: Boolean? = null,          // vue sur le relevé bancaire
) {
    val mois: String get() = displayMonth ?: date.take(7)
    val signe: Double get() = if (sens == CREDIT) montant else -montant
}

data class Rec(
    val id: String,
    val libelle: String,
    val montant: Double,
    val sens: String,
    val categorie: String,
    val jour: Int,
    val createdAt: Long? = null,
    val debut: String? = null,            // premier mois actif (crédit)
    val fin: String? = null,              // dernier mois actif (crédit)
    val credId: String? = null,
)

data class Cred(
    val id: String,
    val nom: String,
    val montant: Double,
    val taux: Double,                     // taux annuel en %
    val duree: Int,                       // nombre d'échéances
    val mensu: Double,                    // mensualité imposée (0 = calculée)
    val debut: String,                    // mois de la 1re échéance
    val jour: Int,
    val recId: String? = null,
    val type: String = "amort",           // "amort" (prêt classique) ou "renouv" (renouvelable)
    val plafond: Double = 0.0,            // renouvelable : montant maximum
    val tirages: List<Tirage> = emptyList(),
    val mensuHist: List<Pair<String, Double>> = emptyList(),   // renouvelable : nouvelle mensualité à partir d'un mois
) {
    val renouv: Boolean get() = type == "renouv"
}

/** argent pioché dans un crédit renouvelable (txId = l'entrée créée dans les opérations) */
data class Tirage(val date: String, val montant: Double, val txId: Long? = null)

data class Solde(val montant: Double, val date: String)

/** bulletin de paie : le PDF est rangé à part (paie/AAAA-MM.pdf), ici le mois et le net à payer */
data class Paie(val ym: String, val net: Double? = null)

data class St(
    val tx: List<Tx> = emptyList(),
    val rec: List<Rec> = emptyList(),
    val recDone: Map<String, List<String>> = emptyMap(),
    val recSkip: Map<String, List<String>> = emptyMap(),
    val bud: Map<String, Double> = emptyMap(),
    val solde: Solde? = null,
    val cred: List<Cred> = emptyList(),
    val paie: List<Paie> = emptyList(),
    val ovr: Map<String, String> = emptyMap(),   // ancienne surcharge de catégorie (version web)
    val lastSeen: String? = null,
    val updatedAt: Long = 0,
)

object Json {
    private fun JSONObject.str(k: String): String? = if (has(k) && !isNull(k)) opt(k)?.toString() else null
    private fun JSONObject.num(k: String): Double? = if (has(k) && !isNull(k)) opt(k).let {
        when (it) { is Number -> it.toDouble(); is String -> it.replace(',', '.').toDoubleOrNull(); else -> null }
    } else null

    private fun ids(a: JSONArray?): List<String> = a?.let { (0 until it.length()).map { i -> it.opt(i).toString() } } ?: emptyList()

    private fun months(o: JSONObject?): Map<String, List<String>> {
        if (o == null) return emptyMap()
        val m = LinkedHashMap<String, List<String>>()
        o.keys().forEach { k -> m[k] = ids(o.optJSONArray(k)) }
        return m
    }

    fun parse(text: String): St {
        val o = JSONObject(text)
        val tx = ArrayList<Tx>()
        o.optJSONArray("manTx")?.let { a ->
            for (i in 0 until a.length()) {
                val t = a.optJSONObject(i) ?: continue
                val date = t.str("date") ?: continue
                tx += Tx(
                    id = t.num("id")?.toLong() ?: (i + 1).toLong(),
                    date = date,
                    libelle = t.str("libelle") ?: "",
                    montant = Logic.r2(t.num("montant") ?: 0.0),
                    categorie = t.str("categorie") ?: "Divers",
                    sens = if (t.str("sens") == CREDIT) CREDIT else DEBIT,
                    type = t.str("type") ?: "manuel",
                    displayMonth = t.str("display_month"),
                    recId = t.str("recId"),
                    carte = if (t.has("carte") && !t.isNull("carte")) t.optBoolean("carte") else null,
                    pointe = if (t.optBoolean("pointe", false)) true else null,
                )
            }
        }
        val rec = ArrayList<Rec>()
        o.optJSONArray("rec")?.let { a ->
            for (i in 0 until a.length()) {
                val r = a.optJSONObject(i) ?: continue
                rec += Rec(
                    id = r.str("id") ?: "r$i",
                    libelle = r.str("libelle") ?: "",
                    montant = Logic.r2(r.num("montant") ?: 0.0),
                    sens = if (r.str("sens") == CREDIT) CREDIT else DEBIT,
                    categorie = r.str("categorie") ?: "Divers",
                    jour = (r.num("jour") ?: 5.0).toInt().coerceIn(1, 31),
                    createdAt = r.num("createdAt")?.toLong(),
                    debut = r.str("debut"), fin = r.str("fin"), credId = r.str("credId"),
                )
            }
        }
        val cred = ArrayList<Cred>()
        o.optJSONArray("cred")?.let { a ->
            for (i in 0 until a.length()) {
                val c = a.optJSONObject(i) ?: continue
                cred += Cred(
                    id = c.str("id") ?: "c$i", nom = c.str("nom") ?: "Crédit",
                    montant = c.num("montant") ?: 0.0, taux = c.num("taux") ?: 0.0,
                    duree = (c.num("duree") ?: 0.0).toInt(), mensu = c.num("mensu") ?: 0.0,
                    debut = c.str("debut") ?: "", jour = (c.num("jour") ?: 5.0).toInt(), recId = c.str("recId"),
                    type = c.str("type") ?: "amort", plafond = c.num("plafond") ?: 0.0,
                    tirages = c.optJSONArray("tirages")?.let { a -> (0 until a.length()).mapNotNull { k -> a.optJSONObject(k)?.let { t ->
                        val d = t.str("date"); val m = t.num("montant"); if (d != null && m != null) Tirage(d, m, t.num("txId")?.toLong()) else null } } } ?: emptyList(),
                    mensuHist = c.optJSONArray("mensuHist")?.let { a -> (0 until a.length()).mapNotNull { k -> a.optJSONObject(k)?.let { t ->
                        val y = t.str("ym"); val m = t.num("m"); if (y != null && m != null) y to m else null } } } ?: emptyList(),
                )
            }
        }
        val bud = LinkedHashMap<String, Double>()
        o.optJSONObject("bud")?.let { b -> b.keys().forEach { k -> b.num(k)?.let { if (it > 0) bud[k] = it } } }
        val ovr = LinkedHashMap<String, String>()
        o.optJSONObject("ovr")?.let { b -> b.keys().forEach { k -> (b.opt(k) as? String)?.let { ovr[k] = it } } }
        val paie = ArrayList<Paie>()
        o.optJSONArray("paie")?.let { a ->
            for (i in 0 until a.length()) {
                val p = a.optJSONObject(i) ?: continue
                val ym = p.str("ym") ?: continue
                paie += Paie(ym, p.num("net"))
            }
        }
        val solde = o.optJSONObject("solde")?.let { s ->
            val m = s.num("montant"); val d = s.str("date")
            if (m != null && d != null) Solde(m, d) else null
        }
        return St(
            tx = tx, rec = rec, recDone = months(o.optJSONObject("recDone")), recSkip = months(o.optJSONObject("recSkip")),
            bud = bud, solde = solde, cred = cred, paie = paie, ovr = ovr,
            lastSeen = o.str("lastSeen"), updatedAt = o.num("_updatedAt")?.toLong() ?: 0,
        )
    }

    fun write(s: St): String {
        val o = JSONObject()
        o.put("manTx", JSONArray().apply {
            s.tx.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id); put("date", t.date); put("libelle", t.libelle); put("montant", t.montant)
                    put("categorie", t.categorie); put("sens", t.sens); put("type", t.type); put("manual", true)
                    t.displayMonth?.let { put("display_month", it) }
                    t.recId?.let { put("recId", it) }
                    t.carte?.let { put("carte", it) }
                    if (t.pointe == true) put("pointe", true)
                })
            }
        })
        o.put("rec", JSONArray().apply {
            s.rec.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id); put("libelle", r.libelle); put("montant", r.montant); put("sens", r.sens)
                    put("categorie", r.categorie); put("jour", r.jour)
                    r.createdAt?.let { put("createdAt", it) }
                    r.debut?.let { put("debut", it) }; r.fin?.let { put("fin", it) }; r.credId?.let { put("credId", it) }
                })
            }
        })
        o.put("cred", JSONArray().apply {
            s.cred.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id); put("nom", c.nom); put("montant", c.montant); put("taux", c.taux)
                    put("duree", c.duree); put("mensu", c.mensu); put("debut", c.debut); put("jour", c.jour)
                    c.recId?.let { put("recId", it) }
                    if (c.renouv) {
                        put("type", c.type); put("plafond", c.plafond)
                        put("tirages", JSONArray().apply { c.tirages.forEach { t -> put(JSONObject().put("date", t.date).put("montant", t.montant).apply { t.txId?.let { put("txId", it) } }) } })
                        put("mensuHist", JSONArray().apply { c.mensuHist.forEach { (y, m) -> put(JSONObject().put("ym", y).put("m", m)) } })
                    }
                })
            }
        })
        o.put("paie", JSONArray().apply {
            s.paie.forEach { p -> put(JSONObject().apply { put("ym", p.ym); p.net?.let { put("net", it) } }) }
        })
        fun months(m: Map<String, List<String>>) = JSONObject().apply { m.forEach { (k, v) -> put(k, JSONArray(v)) } }
        o.put("recDone", months(s.recDone))
        o.put("recSkip", months(s.recSkip))
        o.put("bud", JSONObject().apply { s.bud.forEach { (k, v) -> put(k, v) } })
        o.put("ovr", JSONObject().apply { s.ovr.forEach { (k, v) -> put(k, v) } })
        o.put("xbal", JSONObject())
        s.solde?.let { o.put("solde", JSONObject().put("montant", it.montant).put("date", it.date)) }
        s.lastSeen?.let { o.put("lastSeen", it) }
        o.put("_updatedAt", s.updatedAt)
        return o.toString()
    }
}

object Cats {
    val DEP = listOf(
        "Logement & Charges", "Alimentation", "Restaurants & Sorties", "Transport & Carburant",
        "Factures & Abonnements", "Santé", "Loisirs & Culture", "Streaming & Numérique", "Vêtements & Beauté",
        "Pokémon TCG", "Épargne & Remboursements", "Famille & Enfants", "Crédits & Prêts", "Divers",
    )
    val REV = listOf("Revenus Salaire", "Revenus Autres", "Remboursements reçus")
    val ALL = DEP + REV
    const val POKE = "Pokémon TCG"
    const val CREDITS = "Crédits & Prêts"
    private val ICO = mapOf(
        "Logement & Charges" to "🏠", "Alimentation" to "🛒", "Restaurants & Sorties" to "🍽️",
        "Transport & Carburant" to "⛽", "Factures & Abonnements" to "📱", "Santé" to "💊",
        "Loisirs & Culture" to "🎮", "Streaming & Numérique" to "📺", "Vêtements & Beauté" to "💆",
        "Épargne & Remboursements" to "🏦", "Famille & Enfants" to "👨‍👩‍👧", "Crédits & Prêts" to "🏛️",
        "Divers" to "📦", "Revenus Salaire" to "💼", "Revenus Autres" to "↙️", "Remboursements reçus" to "↩️",
    )
    fun ico(c: String): String = ICO[c] ?: "•"
    fun of(sens: String) = if (sens == CREDIT) REV else DEP
}
