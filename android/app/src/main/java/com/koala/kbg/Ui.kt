package com.koala.kbg

import android.app.DatePickerDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/** Couleurs de la version web (sombre violet). */
object K {
    val Bg = Color(0xFF0F1117); val Sf = Color(0xFF1A1D27); val Sf2 = Color(0xFF232635); val Sf3 = Color(0xFF2A2D3E)
    val Bd = Color(0xFF2E3147); val Ac = Color(0xFF6C63FF); val Ac2 = Color(0xFF5A52D5)
    val Gr = Color(0xFF4ECCA3); val Re = Color(0xFFFF6584); val Ye = Color(0xFFFFD166); val Or = Color(0xFFFD9644)
    val Tx = Color(0xFFE8E8F0); val Tx2 = Color(0xFF8E92B0); val Tx3 = Color(0xFF5A5E78)
    val HeroLbl = Color(0xFFA8A2FF); val ChipTx = Color(0xFFD6D3FF)
    val Hero = Brush.linearGradient(listOf(Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFF1A1D27)))
    val Shape = RoundedCornerShape(12.dp)
}

val KbgColors = darkColorScheme(
    primary = K.Ac, onPrimary = Color.White, secondary = K.Gr, background = K.Bg, onBackground = K.Tx,
    surface = K.Sf, onSurface = K.Tx, surfaceVariant = K.Sf2, onSurfaceVariant = K.Tx2, outline = K.Bd,
    error = K.Re, surfaceContainerHigh = K.Sf, surfaceContainerHighest = K.Sf2, surfaceContainer = K.Sf,
    primaryContainer = K.Ac, onPrimaryContainer = Color.White,
)

@Composable
fun KbgTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = KbgColors, content = content)

fun Modifier.tap(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = MutableInteractionSource(), indication = null, onClick = onClick,
)

// ── textes ─────────────────────────────────────────────────
@Composable
fun T(
    s: String, size: TextUnit = 14.sp, color: Color = K.Tx, weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier, align: TextAlign? = null, lines: Int = Int.MAX_VALUE,
) = Text(s, modifier = modifier, fontSize = size, color = color, fontWeight = weight, textAlign = align,
    maxLines = lines, overflow = if (lines < Int.MAX_VALUE) TextOverflow.Ellipsis else TextOverflow.Clip)

/** petit titre en capitales */
@Composable
fun Sec(s: String, modifier: Modifier = Modifier) =
    Text(s.uppercase(), modifier = modifier.padding(bottom = 10.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        color = K.Tx2, letterSpacing = 0.8.sp)

// ── blocs ──────────────────────────────────────────────────
@Composable
fun Card(modifier: Modifier = Modifier, pad: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.fillMaxWidth().padding(bottom = 14.dp).clip(K.Shape).background(K.Sf)
        .border(1.dp, K.Bd, K.Shape).padding(pad), content = content)

@Composable
fun Hero(content: @Composable ColumnScope.() -> Unit) {
    val sh = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().padding(bottom = 14.dp).clip(sh).background(K.Hero)
            .border(1.dp, K.Ac.copy(alpha = 0.3f), sh).padding(horizontal = 18.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, content = content,
    )
}

@Composable
fun HeroLabel(s: String) = Text(s.uppercase(), fontSize = 12.sp, color = K.HeroLbl, letterSpacing = 1.sp)

@Composable
fun HeroAmount(s: String, color: Color = Color.White, onClick: (() -> Unit)? = null) =
    Text(s, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = color, letterSpacing = (-1).sp,
        modifier = if (onClick != null) Modifier.tap(onClick) else Modifier)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Chips(content: @Composable () -> Unit) =
    FlowRow(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }

@Composable
fun Chip(label: String, value: String = "", valueColor: Color = Color.White, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.1f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (label.isNotEmpty()) T(label, 12.sp, K.ChipTx)
        if (value.isNotEmpty()) T((if (label.isNotEmpty()) " " else "") + value, 12.sp, valueColor, FontWeight.Bold)
    }
}

@Composable
fun Bar(pct: Float, brush: Brush, height: Dp = 6.dp, track: Color = K.Sf3) {
    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(4.dp)).background(track)) {
        Box(Modifier.fillMaxWidth(pct.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(4.dp)).background(brush))
    }
}

/** sélecteur segmenté (Tout / Sorties / Entrées…) */
@Composable
fun Seg(options: List<Pair<String, String>>, value: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(11.dp)).background(K.Sf)
        .border(1.dp, K.Bd, RoundedCornerShape(11.dp)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { (k, l) ->
            val on = k == value
            Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (on) K.Sf3 else Color.Transparent)
                .clickable { onChange(k) }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                T(l, 12.5.sp, if (on) K.Tx else K.Tx2, FontWeight.SemiBold)
            }
        }
    }
}

/** ‹ Novembre 2026 › */
@Composable
fun MonthNav(ym: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).clickable { onChange(Logic.prevM(ym)) }, contentAlignment = Alignment.Center) { T("‹", 24.sp, K.Tx2) }
        T(Logic.fmtM(ym), 15.sp, K.Tx, FontWeight.Bold, Modifier.weight(1f), TextAlign.Center)
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).clickable { onChange(Logic.nextM(ym)) }, contentAlignment = Alignment.Center) { T("›", 24.sp, K.Tx2) }
    }
}

/** icône de catégorie (le dos de carte pour Pokémon) */
@Composable
fun CatIcon(cat: String, size: TextUnit = 16.sp) {
    if (cat == Cats.POKE) Image(painterResource(R.drawable.poke), null, Modifier.height((size.value * 1.35f).dp).clip(RoundedCornerShape(2.dp)))
    else Text(Cats.ico(cat), fontSize = size)
}

@Composable
fun IconBox(cat: String, sens: String, box: Dp = 36.dp) {
    Box(Modifier.size(box).clip(RoundedCornerShape(10.dp)).background(if (sens == CREDIT) K.Gr.copy(alpha = 0.12f) else K.Re.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center) { CatIcon(cat) }
}

/** ligne d'opération */
@Composable
fun TxRow(s: St, t: Tx, onClick: () -> Unit) {
    val c = Logic.cat(s, t)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBox(c, t.sens)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            T(t.libelle, 14.sp, K.Tx, FontWeight.SemiBold, lines = 1)
            T("$c · ${Logic.fmtD(t.date)}", 11.5.sp, K.Tx2, lines = 1)
        }
        T((if (t.sens == CREDIT) "+" else "-") + Logic.fmt(t.montant), 14.sp, if (t.sens == CREDIT) K.Gr else K.Re, FontWeight.Bold)
    }
}

@Composable
fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(K.Bd))

@Composable
fun Empty(ico: String, s: String) =
    Column(Modifier.fillMaxWidth().padding(vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        T(ico, 30.sp); Spacer(Modifier.height(6.dp)); T(s, 13.sp, K.Tx2, align = TextAlign.Center)
    }

/** case à cocher carrée verte */
@Composable
fun Check(on: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Box(modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(if (on) K.Gr else K.Sf2)
        .border(2.dp, if (on) K.Gr else K.Bd, RoundedCornerShape(6.dp))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center) { if (on) T("✓", 13.sp, K.Bg, FontWeight.Bold) }
}

@Composable
fun CheckRow(on: Boolean, label: String, sub: String = "", onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(10.dp)).background(K.Sf2).clickable(onClick = onClick).padding(11.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Check(on); Spacer(Modifier.width(10.dp)); T(label, 13.sp, K.Tx2); if (sub.isNotEmpty()) T(" $sub", 12.sp, K.Tx3)
    }
}

// ── formulaires ────────────────────────────────────────────
@Composable
fun Field(
    value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    number: Boolean = false, big: Boolean = false, placeholder: String = "",
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier.fillMaxWidth().padding(bottom = 10.dp),
        label = { Text(label) }, singleLine = true,
        placeholder = if (placeholder.isNotEmpty()) ({ Text(placeholder, color = K.Tx3) }) else null,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontSize = if (big) 26.sp else 16.sp, fontWeight = if (big) FontWeight.Bold else FontWeight.Normal,
            textAlign = if (big) TextAlign.Center else TextAlign.Start, color = K.Tx,
        ),
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = K.Ac, unfocusedBorderColor = K.Bd, focusedLabelColor = K.Ac, unfocusedLabelColor = K.Tx2,
            cursorColor = K.Ac, focusedContainerColor = K.Sf2, unfocusedContainerColor = K.Sf2,
        ),
    )
}

fun num(s: String): Double? = s.replace(" ", "").replace(" ", "").replace(',', '.').toDoubleOrNull()
fun numStr(d: Double?): String = if (d == null || d == 0.0) "" else Logic.r2(d).toString().removeSuffix(".0").replace('.', ',')

/** champ date (calendrier Android) */
@Composable
fun DateField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    val ctx = LocalContext.current
    Column(modifier.padding(bottom = 10.dp)) {
        T(label, 11.sp, K.Tx3, modifier = Modifier.padding(bottom = 4.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(K.Sf2).border(1.dp, K.Bd, RoundedCornerShape(10.dp))
            .clickable {
                val d = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
                DatePickerDialog(ctx, { _, y, m, dd -> onChange(LocalDate.of(y, m + 1, dd).toString()) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
            }.height(52.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
            T(if (value.length == 10) Logic.fmtD(value) else "Choisir…", 15.sp, if (value.length == 10) K.Tx else K.Tx3)
        }
    }
}

/** sens : ↓ Dépense / ↑ Entrée */
@Composable
fun SensToggle(sens: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(DEBIT to "↓ Dépense", CREDIT to "↑ Entrée").forEach { (k, l) ->
            val on = sens == k; val c = if (k == DEBIT) K.Re else K.Gr
            Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) c.copy(alpha = 0.16f) else K.Sf2)
                .border(1.dp, if (on) c else K.Bd, RoundedCornerShape(10.dp)).clickable { onChange(k) }.padding(vertical = 11.dp),
                contentAlignment = Alignment.Center) { T(l, 14.sp, if (on) c else K.Tx2, FontWeight.Bold) }
        }
    }
}

/** choix de catégorie en pastilles */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CatPicker(cats: List<String>, value: String, onChange: (String) -> Unit) {
    FlowRow(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cats.forEach { c ->
            val on = c == value
            Row(Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) K.Ac else K.Sf2)
                .border(1.dp, if (on) K.Ac else K.Bd, RoundedCornerShape(18.dp)).clickable { onChange(c) }
                .padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                CatIcon(c, 13.sp); Spacer(Modifier.width(5.dp)); T(c, 12.sp, if (on) Color.White else K.Tx2, FontWeight.Medium)
            }
        }
    }
}

// ── boutons ────────────────────────────────────────────────
@Composable
fun Btn(label: String, modifier: Modifier = Modifier, bg: Color = K.Ac, fg: Color = Color.White, onClick: () -> Unit) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg).clickable(onClick = onClick).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center) { T(label, 15.sp, fg, FontWeight.Bold) }
}

@Composable
fun BtnGhost(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) = Btn(label, modifier.padding(top = 8.dp), K.Sf2, K.Tx2, onClick)

@Composable
fun BtnDanger(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) = Btn(label, modifier.padding(top = 8.dp), K.Re, Color.White, onClick)

@Composable
fun SmallBtn(label: String, bg: Color = K.Sf2, fg: Color = K.Tx, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(9.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 8.dp)) {
        T(label, 12.sp, fg, FontWeight.Bold)
    }
}

/** ligne du menu : titre + sous-titre */
@Composable
fun MenuItem(title: String, sub: String = "", color: Color = K.Tx, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(11.dp)).background(K.Sf2).clickable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        T(title, 14.sp, color, FontWeight.SemiBold, Modifier.weight(1f))
        if (sub.isNotEmpty()) T(sub, 12.sp, K.Tx3)
    }
}

@Composable
fun MenuSec(s: String) = Text(s.uppercase(), Modifier.padding(top = 10.dp, bottom = 8.dp), fontSize = 11.sp, color = K.Tx3,
    fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)

@Composable
fun SheetTitle(s: String, sub: String = "") {
    T(s, 18.sp, K.Tx, FontWeight.ExtraBold, Modifier.padding(bottom = if (sub.isEmpty()) 14.dp else 4.dp))
    if (sub.isNotEmpty()) T(sub, 12.5.sp, K.Tx2, modifier = Modifier.padding(bottom = 14.dp))
}

@Composable
fun KV(k: String, v: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        T(k, 12.5.sp, K.Tx2, modifier = Modifier.weight(1f)); T(v, 12.5.sp, K.Tx, FontWeight.Bold)
    }
}

@Composable
fun RowScope.Kpi(label: String, value: String, color: Color = K.Tx) {
    Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(K.Sf).border(1.dp, K.Bd, RoundedCornerShape(10.dp))
        .padding(horizontal = 11.dp, vertical = 10.dp)) {
        T(label.uppercase(), 10.sp, K.Tx2, FontWeight.SemiBold); Spacer(Modifier.height(3.dp))
        T(value, 15.sp, color, FontWeight.Bold, lines = 1)
    }
}

@Composable
fun rememberStr(v: String) = remember { mutableStateOf(v) }
