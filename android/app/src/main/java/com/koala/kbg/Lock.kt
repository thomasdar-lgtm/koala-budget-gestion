package com.koala.kbg

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Verrouillage par empreinte (ou code du téléphone) : à l'ouverture et au retour après 2 min en arrière-plan. */
object Lock {
    private const val DELAI = 2 * 60 * 1000L
    var locked by mutableStateOf(false)
    var prompting by mutableStateOf(false)   // fenêtre d'empreinte affichée : pas de bouton derrière
    var asks by mutableIntStateOf(0)          // chaque nouveau verrouillage relance la demande
    private var started = false
    private var stoppedAt = 0L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("kbg_prefs", Context.MODE_PRIVATE)
    fun enabled(ctx: Context) = prefs(ctx).getBoolean("lock", true)
    fun setEnabled(ctx: Context, v: Boolean) { prefs(ctx).edit().putBoolean("lock", v).apply(); if (!v) locked = false }

    private fun auths() = if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK
    fun available(ctx: Context) = runCatching { BiometricManager.from(ctx).canAuthenticate(auths()) == BiometricManager.BIOMETRIC_SUCCESS }.getOrDefault(false)
    fun active(ctx: Context) = enabled(ctx) && available(ctx)

    fun onStart(ctx: Context) {
        if (!active(ctx)) { locked = false; started = true; return }
        val lock = !started || (stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > DELAI)
        started = true
        if (lock && !locked) { locked = true; asks++ }
    }

    fun onStop() { stoppedAt = System.currentTimeMillis() }

    fun prompt(act: FragmentActivity) {
        if (!locked) return
        val p = BiometricPrompt(act, ContextCompat.getMainExecutor(act), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { prompting = false; locked = false }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { prompting = false }
        })
        val b = BiometricPrompt.PromptInfo.Builder().setTitle("Déverrouiller KBG").setAllowedAuthenticators(auths())
        if (Build.VERSION.SDK_INT < 30) b.setNegativeButtonText("Annuler")
        prompting = true
        runCatching { p.authenticate(b.build()) }.onFailure { prompting = false }
    }
}

/** écran affiché tant que KBG est verrouillé (cache aussi l'aperçu des applis récentes) */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Column(Modifier.fillMaxSize().background(K.Bg).tap { }.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        Image(painterResource(R.drawable.logo), null, Modifier.size(96.dp).clip(RoundedCornerShape(22.dp)))
        Spacer(Modifier.height(18.dp))
        // pendant la fenêtre d'empreinte : juste le logo ; le bouton n'apparaît que si elle a été fermée
        if (!Lock.prompting) {
            T("KBG est verrouillé", 20.sp, K.Tx, FontWeight.ExtraBold)
            T("Empreinte ou code du téléphone", 13.sp, K.Tx2, modifier = Modifier.padding(top = 4.dp, bottom = 26.dp), align = TextAlign.Center)
            Btn("🔓 Déverrouiller", Modifier.padding(horizontal = 30.dp), onClick = onUnlock)
        }
    }
}
