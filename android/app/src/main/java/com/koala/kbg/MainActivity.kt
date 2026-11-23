package com.koala.kbg

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay

/** État de l'interface (onglet, feuille ouverte, message, confirmation). */
object Nav {
    var tab by mutableStateOf("home")
    var sheet by mutableStateOf<Sheet?>(null)
    var notif by mutableStateOf<Pair<String, Boolean?>?>(null)   // true = erreur, null = avertissement
    var notifN by mutableStateOf(0)
    var ask by mutableStateOf<Ask?>(null)
    var importReq by mutableStateOf(0)

    // mois affichés
    var opYM by mutableStateOf(Logic.ym(java.time.LocalDate.now()))
    var pokeYM by mutableStateOf(Logic.ym(java.time.LocalDate.now()))
    var anTab by mutableStateOf("cat")
    var anYM by mutableStateOf<String?>(null)
    var bilanVu by mutableStateOf<String?>(null)
    var opQ by mutableStateOf<String?>(null)   // recherche dans Opérations (null = fermée)

    fun go(t: String) {
        sheet = null
        if (t == "saisie") { opYM = Logic.ym(Db.today); opQ = null }
        if (t == "pokemon") pokeYM = Logic.ym(Db.today)
        tab = t
    }
    fun open(s: Sheet) { sheet = s }
    fun close() { sheet = null }
    fun notify(msg: String, err: Boolean = false) { notif = msg to err; notifN++ }
    fun warn(msg: String) { notif = msg to null; notifN++ }
    fun confirm(title: String, text: String, ok: String, action: () -> Unit) { ask = Ask(title, text, ok, action) }
}

data class Ask(val title: String, val text: String, val ok: String, val action: () -> Unit)

sealed class Sheet {
    data object Menu : Sheet()
    data object Solde : Sheet()
    data object Budgets : Sheet()
    data object Backups : Sheet()
    data class AddTx(val sens: String = DEBIT) : Sheet()
    data class EditTx(val id: Long) : Sheet()
    data class RecEdit(val id: String?) : Sheet()
    data class RecMonth(val rid: String, val ym: String) : Sheet()
    data class CreditDetail(val id: String) : Sheet()
    data class CreditEdit(val id: String?) : Sheet()
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(K.Bg.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(K.Sf.toArgb()),
        )
        Db.init(this)
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!Lock.active(this))
        setContent { KbgTheme { KbgApp() } }
        shortcut(intent)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); shortcut(intent) }

    /** appui long sur l'icône : Nouvelle dépense / Nouvelle entrée */
    private fun shortcut(i: Intent?) {
        if (i == null) return
        val sens = when (i.action) { "com.koala.kbg.ADD_DEBIT" -> DEBIT; "com.koala.kbg.ADD_CREDIT" -> CREDIT; else -> return }
        i.action = null
        Nav.go("saisie"); Nav.open(Sheet.AddTx(sens))
    }

    override fun onStart() { super.onStart(); Lock.onStart(this) }
    override fun onStop() { super.onStop(); Lock.onStop() }
}

@Composable
fun KbgApp() {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { if (Db.importFrom(uri)) Nav.notify("Import réussi ✓") else Nav.notify("Fichier invalide", true) }
    }
    LaunchedEffect(Nav.importReq) {
        if (Nav.importReq > 0) runCatching { pick.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }
    }
    LifecycleResumeEffect(Unit) { Db.tick(); onPauseOrDispose { } }
    LaunchedEffect(Unit) {
        while (true) {
            runCatching { Updater.check() }.onSuccess { if (it != null && it.version != Upd.info?.version) { Upd.info = it; Upd.hidden = false } }
            delay(6 * 3600 * 1000L)
        }
    }
    BackHandler(enabled = Nav.sheet == null && Nav.tab != "home") { Nav.go("home") }

    Box(Modifier.fillMaxSize().background(K.Bg)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).statusBarsPadding()) {
                UpdateBanner()
                Box(Modifier.weight(1f)) {
                    when (Nav.tab) {
                        "home" -> HomeScreen()
                        "saisie" -> OpsScreen()
                        "pokemon" -> PokeScreen()
                        "credits" -> CreditsScreen()
                        "analyse" -> AnalyseScreen()
                    }
                }
            }
            BottomNav()
        }
        if (Nav.tab == "saisie") {
            Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 84.dp)
                .size(58.dp).clip(RoundedCornerShape(18.dp)).background(K.Ac).clickable { Nav.open(Sheet.AddTx()) },
                contentAlignment = Alignment.Center) { T("+", 30.sp, Color.White, FontWeight.Light) }
        }
        NotifHost(Modifier.align(Alignment.TopCenter))
    }
    SheetHost()
    val act = androidx.compose.ui.platform.LocalContext.current as? FragmentActivity
    LaunchedEffect(Lock.asks) { if (Lock.locked && act != null) Lock.prompt(act) }
    if (Lock.locked) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { act?.moveTaskToBack(true) },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            LockScreen { act?.let { Lock.prompt(it) } }
        }
    }
    Nav.ask?.let { a ->
        AlertDialog(
            onDismissRequest = { Nav.ask = null },
            title = { Text(a.title) }, text = { Text(a.text) },
            confirmButton = { TextButton({ Nav.ask = null; a.action() }) { Text(a.ok, color = K.Re, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton({ Nav.ask = null }) { Text("Annuler", color = K.Tx2) } },
            containerColor = K.Sf, titleContentColor = K.Tx, textContentColor = K.Tx2,
        )
    }
}

@Composable
internal fun BottomNav() {
    Row(Modifier.fillMaxWidth().background(K.Sf).border(width = 1.dp, color = K.Bd, shape = RoundedCornerShape(0.dp))
        .navigationBarsPadding().padding(top = 6.dp, bottom = 8.dp)) {
        listOf("home" to "Aujourd'hui", "saisie" to "Opérations", "pokemon" to "Pokémon", "credits" to "Crédits", "analyse" to "Analyse").forEach { (k, l) ->
            val on = Nav.tab == k
            Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { Nav.go(k) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) K.Ac.copy(alpha = 0.15f) else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
                    when (k) {
                        "home" -> T("🏠", 20.sp)
                        "saisie" -> T("📝", 20.sp)
                        "pokemon" -> Image(painterResource(R.drawable.poke), null, Modifier.height(24.dp).clip(RoundedCornerShape(2.dp)))
                        "credits" -> T("🏛️", 20.sp)
                        else -> T("📊", 20.sp)
                    }
                }
                Spacer(Modifier.height(2.dp))
                T(l, 10.sp, if (on) K.Ac else K.Tx2, if (on) FontWeight.Bold else FontWeight.Normal, lines = 1)
            }
        }
    }
}

@Composable
private fun NotifHost(modifier: Modifier) {
    val n = Nav.notif
    var visible by androidx.compose.runtime.remember { mutableStateOf(false) }
    LaunchedEffect(Nav.notifN) {
        if (Nav.notifN > 0) { visible = true; delay(if (Nav.notif?.second == false) 2200 else 3500); visible = false }
    }
    AnimatedVisibility(visible && n != null, modifier.statusBarsPadding().padding(top = 10.dp),
        enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
        val c = when (n?.second) { true -> K.Re; null -> K.Ye; else -> K.Gr }
        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(K.Sf).border(1.dp, c, RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp)) { T(n?.first ?: "", 13.sp, c, FontWeight.SemiBold) }
    }
}

/** en-tête d'écran : titre + bouton menu */
@Composable
fun Header(title: String, icon: @Composable (() -> Unit)? = null, right: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { icon(); Spacer(Modifier.width(9.dp)) }
        T(title, 22.sp, K.Tx, FontWeight.ExtraBold, Modifier.weight(1f), lines = 1)
        if (right != null) { right(); Spacer(Modifier.width(10.dp)) }
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(K.Sf2).clickable { Nav.open(Sheet.Menu) },
            contentAlignment = Alignment.Center) { T("☰", 18.sp, K.Tx2) }
    }
}

/** page défilante avec marges */
@Composable
fun Page(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 96.dp), content = content)
}
