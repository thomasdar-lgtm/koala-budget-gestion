package com.koala.kbg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Valeurs de référence : celles de la version web (v1.85), vérifiées au centime. */
class LogicTest {
    private val today = LocalDate.of(2026, 11, 18)
    private fun eq(a: Double, b: Double) = assertEquals(a, b, 0.001)

    @Test fun creditCalcule() {
        val c = Cred("c1", "Dispo", 10000.0, 6.0, 60, 0.0, "2026-12", 5)
        val p = Logic.crPlan(c)
        eq(193.33, p.m); assertEquals(60, p.rows.size); eq(0.0, p.rows.last().bal)
        val e = Logic.crEtat(c, today)
        eq(1599.68, e.itTot); assertEquals("2031-11", e.fin); assertEquals(0, e.nPaid); eq(10000.0, e.cap)
    }

    @Test fun creditEnCours() {
        val c = Cred("c2", "Auto", 15000.0, 4.5, 48, 0.0, "2024-03", 10)
        val e = Logic.crEtat(c, today)
        eq(342.05, e.m); assertEquals(33, e.nPaid); eq(4980.15, e.cap); eq(1418.52, e.itTot); assertEquals("2028-02", e.fin)
        eq(342.17, e.rows.last().pay)
    }

    @Test fun creditParMensualite() {
        val c = Cred("c3", "Floa", 2000.0, 12.0, 0, 100.0, "2026-06", 15)
        val e = Logic.crEtat(c, today)
        assertEquals(23, e.rows.size); eq(242.69, e.itTot); assertEquals(6, e.nPaid); eq(1507.84, e.cap); assertEquals("2028-04", e.fin)
        assertTrue(Logic.crPlan(c.copy(mensu = 15.0)).err != null)
    }

    @Test fun creditRecurrente() {
        var s = St()
        s = Logic.saveCredit(s, Cred("c2", "Auto", 15000.0, 4.5, 48, 0.0, "2024-03", 10), 1_000_000_000_000)
        val r = s.rec.single()
        assertEquals("2024-03", r.debut); assertEquals("2028-02", r.fin); assertEquals(Cats.CREDITS, r.categorie); eq(342.05, r.montant)
        assertEquals(r.id, s.cred.single().recId)
        eq(342.17, Logic.recAmt(s, r, "2028-02"))
        assertTrue(Logic.recsOf(s, "2028-03").isEmpty())
        s = Logic.autoCheckRec(s, today)
        val t = s.tx.single(); assertEquals("2026-11-10", t.date); eq(342.05, t.montant)
        s = Logic.delCredit(s, "c2")
        assertTrue(s.rec.isEmpty()); assertEquals(1, s.tx.size)
    }

    @Test fun carteDiffere() {
        assertEquals("2026-11-30", Logic.debitDate("2026-11-10"))
        assertEquals("2026-12-31", Logic.debitDate("2026-11-30"))
        assertEquals("2027-01-29", Logic.lastBizDay(2027, 1))
    }

    @Test fun soldes() {
        val s = St(
            tx = listOf(
                Tx(1, "2026-11-10", "Courses", 50.0, "Alimentation", DEBIT),
                Tx(2, "2026-11-20", "Vente", 200.0, "Revenus Autres", CREDIT),
                Tx(3, "2026-11-28", "Plein", 30.0, "Transport & Carburant", DEBIT),
            ),
            rec = listOf(Rec("r1", "Loyer", 100.0, DEBIT, "Logement & Charges", 25)),
            solde = Solde(1000.0, "2026-11-15"),
        )
        val r = Logic.calcSoldes(s, "2026-11", LocalDate.of(2026, 11, 23))
        eq(1150.0, r.reel); eq(1020.0, r.prev)
        eq(1000.0, Logic.soldeAt(s, "2026-11-15")!!)
        eq(80.0, Logic.encours(s, "2026-11-28"))
        assertNull(Logic.soldeAt(s.copy(solde = null), "2026-11-15"))
    }

    @Test fun autoCochage() {
        var s = St(
            rec = listOf(
                Rec("r1", "Box", 30.0, DEBIT, "Factures & Abonnements", 5, createdAt = 1),
                Rec("r2", "Loyer", 600.0, DEBIT, "Logement & Charges", 25, createdAt = 1),
                Rec("r3", "Assurance", 40.0, DEBIT, "Divers", 3, createdAt = 1),
                Rec("r4", "Salaire", 1800.0, CREDIT, "Revenus Salaire", 30, createdAt = 1),
            ),
            recSkip = mapOf("2026-11" to listOf("r3")),
            lastSeen = "2026-10-28",
        )
        s = Logic.autoCheckRec(s, LocalDate.of(2026, 11, 23))
        assertEquals(listOf("r1"), s.recDone["2026-11"])
        assertEquals(listOf("r4"), s.recDone["2026-10"])
        assertEquals("2026-11-23", s.lastSeen)
        assertEquals(setOf("Box", "Salaire"), s.tx.map { it.libelle }.toSet())
        // décocher à la main : reste décochée
        s = Logic.toggleRecUser(s, "r1", "2026-11")
        s = Logic.autoCheckRec(s, LocalDate.of(2026, 11, 24))
        assertFalse("r1" in s.recDone["2026-11"]!!)
        // montant réel du mois
        s = Logic.setRecMonth(s, "r1", "2026-11", 34.5)
        eq(34.5, s.tx.first { it.recId == "r1" }.montant); assertTrue("r1" in s.recDone["2026-11"]!!)
    }

    @Test fun jsonCompatibleWeb() {
        val web = """{"manTx":[{"id":1,"date":"2026-11-01","libelle":"Lot cartes","montant":280,"sens":"debit","categorie":"Pokémon TCG","type":"manuel","manual":true,"carte":false},
            {"id":2,"date":"2026-11-05","libelle":"Salaire","montant":"2100,5","sens":"credit","categorie":"Revenus Salaire","type":"rec","display_month":"2026-11","recId":"r1"}],
            "ovr":{},"bud":{"Alimentation":400},"xbal":{},"rec":[{"id":"r1","libelle":"Salaire","montant":2100.5,"sens":"credit","categorie":"Revenus Salaire","jour":5}],
            "recDone":{"2026-11":["r1"]},"solde":{"montant":-12.5,"date":"2026-11-02"},"_updatedAt":1763000000000}"""
        val s = Json.parse(web)
        assertEquals(2, s.tx.size); eq(2100.5, s.tx[1].montant); assertEquals(false, s.tx[0].carte); assertEquals("2026-11", s.tx[1].displayMonth)
        eq(400.0, s.bud["Alimentation"]!!); eq(-12.5, s.solde!!.montant); assertEquals(listOf("r1"), s.recDone["2026-11"])
        assertEquals(s, Json.parse(Json.write(s)))
    }

    @Test fun format() {
        assertEquals("1 234,50 €", Logic.fmt(-1234.5))
        assertEquals("-12,00 €", Logic.fmtS(-12.0))
        assertEquals("Novembre 2026", Logic.fmtM("2026-11"))
    }

    @Test fun habitudes() {
        val s = St(tx = listOf(
            Tx(1, "2026-11-01", "Leclerc", 50.0, "Alimentation", DEBIT),
            Tx(2, "2026-11-10", "leclerc ", 62.0, "Alimentation", DEBIT),
            Tx(3, "2026-11-12", "Total", 40.0, "Transport & Carburant", DEBIT),
            Tx(4, "2026-06-01", "Vieux", 1.0, "Divers", DEBIT),
        ))
        val h = Logic.habits(s, DEBIT, today)
        assertEquals(listOf(2L, 3L), h.map { it.id })
    }

    @Test fun recherche() {
        val s = St(tx = listOf(
            Tx(1, "2026-11-01", "Leclerc", 86.4, "Alimentation", DEBIT),
            Tx(2, "2026-12-10", "LECLERC drive", 52.0, "Alimentation", DEBIT),
            Tx(3, "2026-12-12", "Électricité", 86.0, "Factures & Abonnements", DEBIT),
            Tx(4, "2026-10-12", "Vente", 12.5, "Revenus Autres", CREDIT),
        ))
        assertEquals(listOf(2L, 1L), Logic.search(s, " leclerc").map { it.id })
        assertEquals(listOf(3L), Logic.search(s, "electricite").map { it.id })
        assertEquals(listOf(3L, 1L), Logic.search(s, "86").map { it.id })
        assertEquals(listOf(1L), Logic.search(s, "86,40").map { it.id })
        assertEquals(listOf(1L, 2L), Logic.search(s, "aliment").map { it.id }.sortedBy { it })
        assertTrue(Logic.search(s, "  ").isEmpty())
    }

    @Test fun bilanDuMois() {
        val s = St(tx = listOf(
            Tx(1, "2026-11-04", "Leclerc", 140.0, "Alimentation", DEBIT),
            Tx(2, "2026-11-20", "Display", 160.0, "Pokémon TCG", DEBIT),
            Tx(3, "2026-12-02", "Leclerc", 230.0, "Alimentation", DEBIT),
            Tx(4, "2026-12-05", "Lot", 20.0, "Pokémon TCG", DEBIT),
            Tx(5, "2026-12-06", "Ciné", 3.0, "Loisirs & Culture", DEBIT),
            Tx(6, "2026-12-01", "Salaire", 1800.0, "Revenus Salaire", CREDIT),
        ), bud = mapOf("Alimentation" to 200.0, "Pokémon TCG" to 100.0))
        val b = Logic.bilan(s, "2026-12", LocalDate.of(2027, 1, 3))
        eq(1800.0, b.rev); eq(253.0, b.dep); eq(1547.0, b.net); assertEquals(6 - 2, b.nb)
        assertEquals(listOf("Alimentation"), b.hausses.map { it.cat }); eq(90.0, b.hausses[0].diff)
        assertEquals(listOf("Pokémon TCG"), b.baisses.map { it.cat }); eq(-140.0, b.baisses[0].diff)
        assertEquals(1, b.buds.count { it.sp > it.bud }); assertNull(b.soldeFin)
        assertTrue(Logic.bilan(s, "2026-10", LocalDate.of(2027, 1, 3)).vide)
    }

    @Test fun pointage() {
        var s = St(tx = listOf(
            Tx(1, "2026-12-02", "Leclerc", 86.4, "Alimentation", DEBIT),
            Tx(2, "2026-12-08", "Vente", 45.0, "Revenus Autres", CREDIT),
            Tx(3, "2026-12-28", "Futur", 10.0, "Divers", DEBIT),
        ))
        val d = LocalDate.of(2026, 12, 18)
        assertEquals(listOf(1L, 2L), Logic.aPointer(s, "2026-12", d).map { it.id })
        s = Logic.togglePointe(s, 1)
        assertEquals(listOf(2L), Logic.aPointer(s, "2026-12", d).map { it.id })
        assertEquals(true, Json.parse(Json.write(s)).tx[0].pointe); assertNull(Json.parse(Json.write(s)).tx[1].pointe)
        s = Logic.togglePointe(s, 1); assertNull(s.tx[0].pointe)
    }

    @Test fun alerteBudget() {
        val s = St(tx = listOf(Tx(1, "2026-12-02", "Leclerc", 368.0, "Alimentation", DEBIT)), bud = mapOf("Alimentation" to 400.0))
        assertEquals("⚠️ Alimentation : 92 % du budget (32,00 € restants)" to false, Logic.budAlert(s, "Alimentation", "2026-12"))
        val s2 = s.copy(tx = s.tx + Tx(2, "2026-12-03", "Lidl", 50.0, "Alimentation", DEBIT))
        assertEquals("⛔ Alimentation : budget dépassé de 18,00 €" to true, Logic.budAlert(s2, "Alimentation", "2026-12"))
        assertNull(Logic.budAlert(s, "Alimentation", "2026-11"))
        assertNull(Logic.budAlert(s, "Santé", "2026-12"))
        assertEquals("02/12/26", Logic.fmtD("2026-12-02").let { it.take(6) + it.takeLast(2) })
    }

    @Test fun bulletins() {
        var s = St()
        s = Logic.setPaie(s, Paie("2026-10", 1850.25)); s = Logic.setPaie(s, Paie("2026-11", 1900.0)); s = Logic.setPaie(s, Paie("2025-12", 1700.0))
        assertEquals(listOf("2026-11", "2026-10", "2025-12"), s.paie.map { it.ym })
        val an = Logic.paieParAn(s); assertEquals(listOf("2026", "2025"), an.map { it.first }); eq(3750.25, an[0].third)
        s = Logic.setPaie(s, Paie("2026-09", 1850.25), old = "2026-10")
        assertEquals(listOf("2026-11", "2026-09", "2025-12"), s.paie.map { it.ym })
        s = Logic.setPaie(s, Paie("2026-11", null)); assertNull(s.paie.first().net); assertEquals(3, s.paie.size)
        assertEquals(s.paie, Json.parse(Json.write(s)).paie)
        s = Logic.delPaie(s, "2025-12"); assertEquals(2, s.paie.size)
        assertEquals("2026-10", Logic.paieMoisDefaut(LocalDate.of(2026, 11, 5)))
        assertEquals("2026-11", Logic.paieMoisDefaut(LocalDate.of(2026, 11, 26)))
    }

    @Test fun moisDesBulletins() {
        assertEquals("2026-11", Logic.moisDepuisNom("Bulletin_novembre_2026.pdf"))
        assertEquals("2026-02", Logic.moisDepuisNom("BS Février 2026.pdf"))
        assertEquals("2026-09", Logic.moisDepuisNom("paie-sept-2026.pdf"))
        assertEquals("2026-11", Logic.moisDepuisNom("BS_2026-11.pdf"))
        assertEquals("2026-11", Logic.moisDepuisNom("20261125_bulletin.pdf"))
        assertEquals("2026-03", Logic.moisDepuisNom("bulletin_03_2026.pdf"))
        assertEquals("2025-12", Logic.moisDepuisNom("BP122025.pdf"))
        assertNull(Logic.moisDepuisNom("document.pdf"))
        assertNull(Logic.moisDepuisNom("decompte.pdf"))
        // PDF avec texte compressé
        val content = "BT /F1 9 Tf (BULLETIN DE PAIE) Tj 0 -12 Td (Entr\\351e le 01/09/2015) Tj (P\\351riode du 01/10/2026 au 31/10/2026) Tj (Octobre 2026) Tj [(Oct) -20 (obre 2026)] TJ ET"
        val z = java.util.zip.Deflater(); z.setInput(content.toByteArray(Charsets.ISO_8859_1)); z.finish()
        val buf = ByteArray(4096); val n = z.deflate(buf)
        val pdf = java.io.ByteArrayOutputStream().apply {
            write("%PDF-1.4\n1 0 obj << /Length $n /Filter /FlateDecode >>\nstream\n".toByteArray(Charsets.ISO_8859_1))
            write(buf, 0, n); write("\nendstream\nendobj\n%%EOF".toByteArray(Charsets.ISO_8859_1))
        }.toByteArray()
        assertTrue(Logic.pdfText(pdf).contains("BULLETIN DE PAIE"))
        assertEquals("2026-10", Logic.moisBulletin("scan.pdf", pdf))
        assertEquals("2026-10", Logic.moisDepuisTexte("Periode du 01/10/2026 au 31/10/2026"))
        assertEquals("2026-01", Logic.moisBulletin("BS janvier 2026.pdf", pdf))
    }

    @Test fun netDesBulletins() {
        val t = "MONTANT NET SOCIAL 2.378,94 NET A PAYER AVANT IMPOT SUR LE REVENU 2.516,44 dont 43,47 Imp\u00f4t sur le revenu 2.293,85 3,40 -77,99 NET A PAYER AU SALARIE (en Euros) 2.438,45 Total vers\u00e9 4.423,04"
        eq(2438.45, Logic.netDepuisTexte(t)!!)
        eq(1912.4, Logic.netDepuisTexte("Net pay\u00e9 en euros : 1 912,40")!!)
        eq(1700.0, Logic.netDepuisTexte("NET A PAYER 1700.00 EUR")!!)
        assertNull(Logic.netDepuisTexte("NET A PAYER AVANT IMPOT SUR LE REVENU 2.516,44"))
        assertNull(Logic.netDepuisTexte("rien ici"))
    }
}
