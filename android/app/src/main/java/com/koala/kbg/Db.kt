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
