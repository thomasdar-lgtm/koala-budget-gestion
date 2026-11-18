package com.koala.kbg

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.io.File

class MainActivity : ComponentActivity() {

    companion object {
        const val HOST = "appassets.androidplatform.net"
        const val START = "https://$HOST/assets/www/index.html"
    }

    private lateinit var web: WebView
    lateinit var store: Store
    private var fileCb: ValueCallback<Array<Uri>>? = null
    private var pendingUpdate: UpdateInfo? = null
    @Volatile private var downloading = false

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        fileCb?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        fileCb = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(filesDir)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#0F1117")) }
        web = WebView(this).apply { setBackgroundColor(Color.parseColor("#0F1117")) }
        root.addView(web, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, ins ->
            val b = ins.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            WindowInsetsCompat.CONSUMED
        }
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            textZoom = 100
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == HOST) return false
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                return true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCb?.onReceiveValue(null)
                fileCb = cb
                return try {
                    pickFile.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"))
                    true
                } catch (e: Exception) {
                    fileCb = null; false
                }
            }
        }
        web.addJavascriptInterface(Bridge(), "KBGA")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("(window.kbgBack&&window.kbgBack())?'1':'0'") { r ->
                    if (r != "\"1\"") moveTaskToBack(true)
                }
            }
        })

        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        if (web.url == null) web.loadUrl(START)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    private fun js(code: String) = runOnUiThread { web.evaluateJavascript(code, null) }
    private fun toast(msg: String) = runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }

    private fun shareFile(f: File, titre: String) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", f)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, f.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(f.name, uri)
        runOnUiThread { startActivity(Intent.createChooser(send, titre)) }
    }

    /** Pont appelé par index.html (window.KBGA). */
    inner class Bridge {
        @JavascriptInterface fun version(): String = BuildConfig.VERSION_NAME

        @JavascriptInterface fun saveState(json: String): Boolean =
            runCatching { store.save(json) }.isSuccess

        @JavascriptInterface fun loadState(): String = runCatching { store.load() }.getOrDefault("")

        @JavascriptInterface fun safetyCopy(json: String, motif: String): String =
            runCatching { store.safetyCopy(json, motif) }.getOrDefault("")

        @JavascriptInterface fun listBackups(): String = runCatching { store.list() }.getOrDefault("[]")

        @JavascriptInterface fun readBackup(name: String): String = runCatching { store.read(name) }.getOrDefault("")

        @JavascriptInterface fun share(json: String, name: String): Boolean = runCatching {
            val dir = File(cacheDir, "share").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "kbg.json" }
            val f = File(dir, safe).apply { writeText(json) }
            shareFile(f, "Partager une copie de KBG")
        }.isSuccess

        @JavascriptInterface fun shareBackup(name: String): Boolean = runCatching {
            val src = store.file(name) ?: return false
            val dir = File(cacheDir, "share").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val f = src.copyTo(File(dir, name), true)
            shareFile(f, "Partager cette sauvegarde")
        }.isSuccess

        /** Résultat renvoyé à kbgMajRes(x) : version (texte), null si à jour, 'offline' si erreur. */
        @JavascriptInterface fun checkUpdate() {
            Thread {
                val res = runCatching { Updater.check() }
                res.onSuccess { info ->
                    pendingUpdate = info
                    js("window.kbgMajRes&&kbgMajRes(" + (info?.let { JSONObject.quote(it.version) } ?: "null") + ")")
                }.onFailure { js("window.kbgMajRes&&kbgMajRes('offline')") }
            }.start()
        }

        /** Progression renvoyée à kbgMajProg(p) : 0..1, -1 = abandon/erreur. */
        @JavascriptInterface fun installUpdate() {
            val info = pendingUpdate ?: run { js("window.kbgMajProg&&kbgMajProg(-1)"); return }
            if (!Updater.canInstall(this@MainActivity)) {
                toast("Autorise KBG à installer des applis, puis appuie à nouveau sur Installer")
                runOnUiThread { Updater.askInstallPermission(this@MainActivity) }
                js("window.kbgMajProg&&kbgMajProg(-1)")
                return
            }
            if (downloading) return
            downloading = true
            Thread {
                var last = -1
                val f = runCatching {
                    Updater.download(this@MainActivity, info.url) { p ->
                        val pc = (p * 100).toInt()
                        if (pc != last) { last = pc; js("window.kbgMajProg&&kbgMajProg(${p})") }
                    }
                }.getOrNull()
                downloading = false
                if (f == null) {
                    toast("Téléchargement impossible, vérifie ta connexion")
                    js("window.kbgMajProg&&kbgMajProg(-1)")
                } else {
                    js("window.kbgMajProg&&kbgMajProg(1)")
                    runOnUiThread {
                        runCatching { Updater.install(this@MainActivity, f) }
                            .onFailure { toast("Installation impossible") }
                    }
                }
            }.start()
        }
    }
}
