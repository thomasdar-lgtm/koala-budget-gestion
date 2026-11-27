package com.koala.kbg

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Captures d'écran générées en CI (pas d'émulateur) pour vérifier le rendu. */
class ScreensShot {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.NoActionBar")

    private val day = LocalDate.of(2026, 12, 18)

    private fun sample(): St {
        var s = St(
            tx = listOf(
                Tx(1, "2026-12-02", "Leclerc", 86.4, "Alimentation", DEBIT),
                Tx(2, "2026-12-05", "Lot Évolutions Prismatiques", 120.0, "Pokémon TCG", DEBIT),
                Tx(3, "2026-12-08", "Vente Vinted", 45.0, "Revenus Autres", CREDIT),
                Tx(4, "2026-12-12", "Total Énergies", 62.3, "Transport & Carburant", DEBIT),
                Tx(5, "2026-12-15", "Restaurant", 38.0, "Restaurants & Sorties", DEBIT),
                Tx(6, "2026-11-04", "Leclerc", 140.0, "Alimentation", DEBIT),
                Tx(7, "2026-11-20", "Display Pokémon", 160.0, "Pokémon TCG", DEBIT),
                Tx(8, "2026-10-12", "Courses", 210.0, "Alimentation", DEBIT),
            ),
            rec = listOf(
                Rec("r1", "Salaire", 1850.0, CREDIT, "Revenus Salaire", 1, 1),
                Rec("r2", "Loyer", 620.0, DEBIT, "Logement & Charges", 5, 1),
                Rec("r3", "EDF", 74.0, DEBIT, "Factures & Abonnements", 20, 1),
                Rec("r4", "Netflix", 13.49, DEBIT, "Streaming & Numérique", 19, 1),
            ),
            bud = mapOf("Alimentation" to 400.0, "Pokémon TCG" to 100.0, "Restaurants & Sorties" to 60.0),
            solde = Solde(1240.5, "2026-12-01"),
            lastSeen = "2026-12-18",
        )
        s = Logic.saveCredit(s, Cred("c1", "Crédit Dispo", 10000.0, 6.0, 60, 0.0, "2026-12", 5), 1)
        s = Logic.saveCredit(s, Cred("c2", "Auto", 15000.0, 4.5, 48, 0.0, "2024-03", 10), 2)
        s = Logic.autoCheckRec(s, day)
        s = Logic.togglePointe(s, 1); s = Logic.togglePointe(s, 3)
        s.tx.find { it.libelle == "Loyer" }?.let { s = Logic.togglePointe(s, it.id) }
        return s
    }

    @Composable
    private fun Frame(content: @Composable () -> Unit) = KbgTheme {
        Column(Modifier.fillMaxSize().background(K.Bg)) {
            Box(Modifier.weight(1f)) { content() }
            BottomNav()
        }
    }

    @Composable
    private fun SheetFrame(content: @Composable () -> Unit) = KbgTheme {
        Column(Modifier.fillMaxSize().background(K.Sf).verticalScroll(rememberScrollState()).padding(18.dp)) { content() }
    }

    private fun shot(name: String, tab: String = "home", content: @Composable () -> Unit) {
        Db.preview(sample(), day); Nav.tab = tab; Nav.opYM = "2026-12"; Nav.pokeYM = "2026-12"
        paparazzi.snapshot(name) { content() }
    }

    @Test fun accueil() = shot("1_accueil") { Frame { HomeScreen() } }
    @Test fun operations() = shot("2_operations", "saisie") { Frame { OpsScreen() } }
    @Test fun pokemon() = shot("3_pokemon", "pokemon") { Frame { PokeScreen() } }
    @Test fun credits() = shot("4_credits", "credits") { Frame { CreditsScreen() } }
    @Test fun analyse() = shot("5_analyse", "analyse") { Frame { AnalyseScreen() } }
    @Test fun ajout() = shot("6_ajout") { SheetFrame { AddTxSheet() } }
    @Test fun menu() = shot("7_menu") { SheetFrame { MenuSheet() } }
    @Test fun creditDetail() = shot("8_credit") { SheetFrame { CreditDetailSheet("c2") } }
    @Test fun creditEdit() = shot("9_credit_saisie") { SheetFrame { CreditEditSheet("c1") } }
    @Test fun recherche() { Nav.opQ = "leclerc"; shot("11_recherche", "saisie") { Frame { OpsScreen() } }; Nav.opQ = null }
    @Test fun verrou() = shot("12_verrou") { KbgTheme { LockScreen { } } }
    @Test fun verrouEnCours() { Lock.prompting = true; shot("15_verrou_empreinte") { KbgTheme { LockScreen { } } }; Lock.prompting = false }
    @Test fun bilanAccueil() { Db.preview(sample(), LocalDate.of(2027, 1, 3)); Nav.tab = "home"; Nav.bilanVu = null; paparazzi.snapshot("13_bilan_accueil") { Frame { HomeScreen() } } }
    @Test fun bilanAnalyse() { Nav.anTab = "bilan"; Nav.anYM = "2026-12"; shot("14_bilan_analyse", "analyse") { Frame { AnalyseScreen() } }; Nav.anTab = "cat"; Nav.anYM = null }
    @Test fun paies() { Db.preview(sample().let { var s = it; listOf("2026-01" to 2438.45, "2026-02" to 2401.1, "2026-03" to 2455.0, "2026-05" to 2390.6, "2026-06" to 2512.3,
        "2026-07" to 2468.0, "2026-08" to 2399.9, "2026-09" to 2421.75, "2026-10" to 2440.0, "2026-11" to null, "2025-12" to 2380.0).forEach { (y, n) -> s = Logic.setPaie(s, Paie(y, n)) }; s }, day)
        Nav.page = "paie"; paparazzi.snapshot("16_paies") { KbgTheme { Column(Modifier.fillMaxSize().background(K.Bg)) { PaieScreen() } } }; Nav.page = null }
    @Test fun paieEdit() { Db.preview(Logic.setPaie(sample(), Paie("2026-11", 1912.4)), day)
        paparazzi.snapshot("17_paie_edit") { KbgTheme { Column(Modifier.fillMaxSize().background(K.Sf).padding(18.dp)) { PaieEditSheet("2026-11") } } } }
    @Test fun paieLot() { Db.preview(Logic.setPaie(sample(), Paie("2026-10", 1887.15)), day)
        val d = java.nio.file.Files.createTempDirectory("lot").toFile()
        Nav.lot = listOf(Db.Lot(java.io.File(d, "0.pdf"), "Bulletin_novembre_2026.pdf", "2026-11"), Db.Lot(java.io.File(d, "1.pdf"), "BS_2026-10.pdf", "2026-10"),
            Db.Lot(java.io.File(d, "2.pdf"), "scan_0042.pdf", null))
        paparazzi.snapshot("18_paie_lot") { KbgTheme { Column(Modifier.fillMaxSize().background(K.Sf).padding(18.dp)) { PaieLotSheet() } } }; Nav.lot = emptyList() }
    @Test fun pokeVide() { Nav.pokeYM = "2026-08"; Db.preview(sample(), day); Nav.tab = "pokemon"; paparazzi.snapshot("19_poke_vide") { Frame { PokeScreen() } } }
    @Test fun solde() = shot("10_solde") { SheetFrame { SoldeSheet() } }
}
