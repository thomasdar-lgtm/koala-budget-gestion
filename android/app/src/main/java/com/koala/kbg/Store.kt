package com.koala.kbg

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Données de KBG sur le téléphone (stockage privé de l'appli).
 *  - state.json : l'état courant
 *  - backups/kbg-AAAA-MM-JJ.json : une copie par jour (la dernière version de la journée), 30 gardées
 *  - backups/kbg-AAAA-MM-JJ-HHmm-<motif>.json : copie de sécurité avant import/restauration, 10 gardées
 */
class Store(root: File) {
    private val state = File(root, "state.json")
    val backups = File(root, "backups").apply { mkdirs() }

    companion object {
        const val KEEP_DAILY = 30
        const val KEEP_SAFETY = 10
        private val DAILY = Regex("^kbg-\\d{4}-\\d{2}-\\d{2}\\.json$")
        private val SAFE_NAME = Regex("^kbg-[0-9a-z-]+\\.json$")
        fun day(d: Date = Date()) = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(d)
        fun minute(d: Date = Date()) = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.FRANCE).format(d)
    }

    private fun write(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.writeText(text); tmp.delete() }
    }

    @Synchronized
    fun save(json: String) {
        write(state, json)
        write(File(backups, "kbg-${day()}.json"), json)
        prune()
    }

    @Synchronized
    fun load(): String = if (state.exists()) state.readText() else ""

    @Synchronized
    fun safetyCopy(json: String, motif: String): String {
        val m = motif.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "copie" }
        val name = "kbg-${minute()}-$m.json"
        write(File(backups, name), json)
        prune()
        return name
    }

    fun file(name: String): File? {
        if (!SAFE_NAME.matches(name)) return null
        val f = File(backups, name)
        return if (f.exists()) f else null
    }

    @Synchronized
    fun read(name: String): String = file(name)?.readText() ?: ""

    data class Info(val name: String, val daily: Boolean, val time: Long, val size: Long)

    @Synchronized
    fun infos(): List<Info> = all().sortedByDescending { it.lastModified() }
        .map { Info(it.name, DAILY.matches(it.name), it.lastModified(), it.length()) }

    private fun all() = backups.listFiles { f -> f.isFile && SAFE_NAME.matches(f.name) }?.toList() ?: emptyList()

    private fun prune() {
        val (daily, safety) = all().partition { DAILY.matches(it.name) }
        daily.sortedByDescending { it.name }.drop(KEEP_DAILY).forEach { it.delete() }
        safety.sortedByDescending { it.lastModified() }.drop(KEEP_SAFETY).forEach { it.delete() }
    }
}
