package com.koala.kbg

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val version: String, val url: String)

object Upd {
    var info by mutableStateOf<UpdateInfo?>(null)
    var progress by mutableStateOf<Float?>(null)
    var hidden by mutableStateOf(false)
}

object Updater {
    private const val API = "https://api.github.com/repos/thomasdar-lgtm/koala-budget-gestion/releases/latest"

    fun newer(a: String, b: String): Boolean {
        val x = a.split(".").map { it.toIntOrNull() ?: 0 }
        val y = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrElse(i) { 0 }; val q = y.getOrElse(i) { 0 }
            if (p != q) return p > q
        }
        return false
    }

    /** nouvelle version si elle existe, null si à jour ; exception si hors ligne */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val c = URL(API).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            val o = JSONObject(c.inputStream.bufferedReader().readText())
            val v = o.getString("tag_name").removePrefix("v")
            val assets = o.getJSONArray("assets")
            var url: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) url = a.getString("browser_download_url")
            }
            if (url != null && newer(v, BuildConfig.VERSION_NAME)) UpdateInfo(v, url) else null
        } finally {
            c.disconnect()
        }
    }

    suspend fun download(ctx: Context, url: String, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val f = File(dir, "KBG.apk")
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 30000
        try {
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            val total = c.contentLengthLong
            var done = 0L
            c.inputStream.use { input ->
                f.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
            f
        } finally {
            c.disconnect()
        }
    }

    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    fun askInstallPermission(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun install(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

/** bandeau « Version x disponible · Installer » */
@Composable
fun UpdateBanner() {
    val info = Upd.info ?: return
    if (Upd.hidden) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp)).background(K.Ac).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("Version ", 14.sp, Color.White); T(info.version, 14.sp, Color.White, FontWeight.ExtraBold)
            T(" disponible", 14.sp, Color.White, modifier = Modifier.weight(1f))
            val p = Upd.progress
            if (p == null) {
                T("Installer", 13.sp, K.Ac, FontWeight.ExtraBold, Modifier.clip(RoundedCornerShape(9.dp)).background(Color.White)
                    .clickable {
                        if (!Updater.canInstall(ctx)) {
                            toast(ctx, "Autorise KBG à installer des applis, puis appuie à nouveau sur Installer")
                            Updater.askInstallPermission(ctx)
                            return@clickable
                        }
                        Upd.progress = 0f
                        scope.launch {
                            val f = runCatching { Updater.download(ctx, info.url) { Upd.progress = it } }.getOrNull()
                            Upd.progress = null
                            if (f == null) toast(ctx, "Téléchargement impossible, vérifie ta connexion")
                            else runCatching { Updater.install(ctx, f) }.onFailure { toast(ctx, "Installation impossible") }
                        }
                    }.padding(horizontal = 12.dp, vertical = 7.dp))
                T("  ×", 20.sp, Color.White.copy(alpha = 0.8f), modifier = Modifier.clickable { Upd.hidden = true })
            } else {
                T("${(p * 100).toInt()} %", 13.sp, Color.White, FontWeight.Bold)
            }
        }
        Upd.progress?.let {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth(), color = Color.White, trackColor = Color.White.copy(alpha = 0.25f))
        }
    }
}
