package com.koala.kbg

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.util.concurrent.Executors

/** L'état de l'appli, enregistré sur le téléphone (fichier + une copie par jour, 30 jours). */
object Db {
    private lateinit var store: Store
    private lateinit var app: Context
    private val io = Executors.newSingleThreadExecutor()

    var s by mutableStateOf(St())
        private set
    var today by mutableStateOf(LocalDate.now())
        private set

    fun init(ctx: Context) {
        if (::store.isInitialized) return
        app = ctx.applicationContext
        store = Store(File(app.filesDir, "v2"))
        s = runCatching { Json.parse(store.load()) }.getOrNull() ?: St()
        tick()
    }

    /** réglages propres au téléphone (pas dans les données) */
    fun pref(k: String): String? = if (::app.isInitialized) app.getSharedPreferences("kbg_prefs", Context.MODE_PRIVATE).getString(k, null) else null
    fun setPref(k: String, v: String) { if (::app.isInitialized) app.getSharedPreferences("kbg_prefs", Context.MODE_PRIVATE).edit().putString(k, v).apply() }

    /** captures d'écran (tests) : état fixé, sans fichier */
    fun preview(st: St, day: LocalDate) { s = st; today = day }

    /** à l'ouverture et au retour dans l'appli : nouveau jour, récurrentes passées cochées, copie du jour */
    fun tick() {
        today = LocalDate.now()
        s = Logic.autoCheckRec(s, today)
        persist()
    }

    /** modification faite par l'utilisateur */
    fun edit(f: (St) -> St) {
        s = f(s).copy(updatedAt = System.currentTimeMillis())
        persist()
    }

    private fun persist() {
        val j = Json.write(s)
        io.execute { runCatching { store.save(j) } }
    }

    // ── bulletins de paie (PDF dans le stockage privé de l'appli) ──
    private fun paieDir() = File(app.filesDir, "v2/paie").apply { mkdirs() }
    fun paieFile(ym: String): File = File(paieDir(), "$ym.pdf")
    private fun staged() = File(app.cacheDir, "paie_tmp.pdf")
    fun paieOk(ym: String) = ::app.isInitialized && paieFile(ym).exists()

    /** copie le PDF choisi dans un fichier temporaire ; false si ce n'est pas un PDF */
    fun stagePdf(uri: Uri): Boolean = runCatching {
        val b = app.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        if (b.size < 5 || String(b, 0, 5, Charsets.ISO_8859_1) != "%PDF-") return false
        staged().writeBytes(b)
        stagedYm = Logic.moisBulletin(nomDe(uri), b)
        true
    }.getOrDefault(false)
    /** mois reconnu pour le PDF en attente (null si non reconnu) */
    var stagedYm: String? = null
        private set

    private fun nomDe(uri: Uri): String = runCatching {
        app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: (uri.lastPathSegment ?: "")

    /** plusieurs bulletins d'un coup : chaque PDF est mis de côté avec le mois reconnu */
    data class Lot(val file: File, val nom: String, val ym: String?)
    fun stageMany(uris: List<Uri>): Pair<List<Lot>, Int> {
        val dir = File(app.cacheDir, "paie_lot").apply { deleteRecursively(); mkdirs() }
        var refus = 0
        val lots = uris.mapIndexedNotNull { i, u ->
            runCatching {
                val b = app.contentResolver.openInputStream(u)!!.use { it.readBytes() }
                if (b.size < 5 || String(b, 0, 5, Charsets.ISO_8859_1) != "%PDF-") null
                else { val f = File(dir, "$i.pdf").apply { writeBytes(b) }; val n = nomDe(u); Lot(f, n, Logic.moisBulletin(n, b)) }
            }.getOrNull().also { if (it == null) refus++ }
        }
        return lots to refus
    }

    /** range les bulletins du lot (mois choisis) ; renvoie le nombre rangé */
    fun saveLot(items: List<Triple<File, String, Double?>>): Int {
        var n = 0; var st = s
        items.forEach { (f, ym, net) ->
            val dst = paieFile(ym)
            if (f.exists() && (f.renameTo(dst) || runCatching { f.copyTo(dst, true); f.delete() }.isSuccess)) { st = Logic.setPaie(st, Paie(ym, net)); n++ }
        }
        edit { st }
        return n
    }
    fun hasStaged() = ::app.isInitialized && staged().exists()
    fun dropStaged() { if (::app.isInitialized) staged().delete() }

    /** enregistre un bulletin : nouveau fichier (si choisi) et/ou changement de mois */
    fun savePaie(ym: String, net: Double?, old: String?): Boolean {
        val st = staged()
        if (st.exists()) { if (!st.renameTo(paieFile(ym))) { st.copyTo(paieFile(ym), true); st.delete() } }
        else if (old != null && old != ym && paieFile(old).exists()) paieFile(old).renameTo(paieFile(ym))
        if (old != null && old != ym) paieFile(old).delete()
        if (!paieFile(ym).exists()) return false
        edit { Logic.setPaie(it, Paie(ym, net), old) }
        return true
    }

    fun delPaie(ym: String) { paieFile(ym).delete(); edit { Logic.delPaie(it, ym) } }

    fun openPaie(ctx: Context, ym: String): Boolean = runCatching {
        val f = paieFile(ym); if (!f.exists()) return false
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        true
    }.getOrDefault(false)

    fun sharePaie(ctx: Context, ym: String): Boolean = runCatching {
        val f = paieFile(ym); if (!f.exists()) return false
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val name = "bulletin-paie-$ym.pdf"
        val g = f.copyTo(File(dir, name), true)
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", g)
        val send = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Bulletin de paie " + Logic.fmtM(ym)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(name, uri)
        ctx.startActivity(Intent.createChooser(send, "Partager le bulletin"))
        true
    }.getOrDefault(false)

    // ── import / sauvegardes / partage ──────────────────────
    fun importFrom(uri: Uri): Boolean {
        val txt = runCatching { app.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull() ?: return false
        val n = runCatching { Json.parse(txt) }.getOrNull() ?: return false
        val cur = Json.write(s)
        io.execute { runCatching { store.safetyCopy(cur, "avant-import") } }
        edit { n }
        tick()
        return true
    }

    fun backups(): List<Store.Info> = runCatching { store.infos() }.getOrDefault(emptyList())

    fun restore(name: String): Boolean {
        val n = runCatching { Json.parse(store.read(name)) }.getOrNull() ?: return false
        val cur = Json.write(s)
        io.execute { runCatching { store.safetyCopy(cur, "avant-restauration") } }
        edit { n }
        tick()
        return true
    }

    fun shareCopy(ctx: Context) = share(ctx, "kbg-copie-${today}.json", Json.write(s), "Partager une copie de KBG")

    fun shareBackup(ctx: Context, name: String) {
        val f = store.file(name) ?: return
        share(ctx, name, f.readText(), "Partager cette sauvegarde")
    }

    private fun share(ctx: Context, name: String, text: String, titre: String) {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, name).apply { writeText(text) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val send = Intent(Intent.ACTION_SEND).setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(name, uri)
        ctx.startActivity(Intent.createChooser(send, titre))
    }
}
