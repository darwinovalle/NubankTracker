package com.tracker.nubank

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationParserTest {

    // ---------- Amount formats ----------

    @Test
    fun parsesBrazilianFormat() {
        val tx = NotificationParser.parse("Enviaste R$ 1.234,56", "para alguien", Country.BRL)
        assertEquals("1234.56", tx?.monto)
        assertEquals("Envío", tx?.comercio)
    }

    @Test
    fun parsesBrazilianFormatNoThousands() {
        val tx = NotificationParser.parse("Compra no débito de R$ 12,90", "IFOOD", Country.BRL)
        assertEquals("12.90", tx?.monto)
        assertEquals("Compra", tx?.comercio)
    }

    @Test
    fun parsesBrazilianWholeAmount() {
        val tx = NotificationParser.parse("Enviou R$ 500", "", Country.BRL)
        assertEquals("500.00", tx?.monto)
    }

    @Test
    fun parsesMexicanDollarFormat() {
        val tx = NotificationParser.parse("Compra en $1,234.56", "Mercado", Country.MXN)
        assertEquals("1234.56", tx?.monto)
    }

    @Test
    fun parsesMexicanDollarNoThousands() {
        // Previously this became 9000 (100x inflated) — must be 90.00
        val tx = NotificationParser.parse("Enviaste $90.00", "a alguien", Country.MXN)
        assertEquals("90.00", tx?.monto)
    }

    @Test
    fun parsesColombianThousandsWithoutCents() {
        // $85.000 COP is 85 thousand, not 85.0
        val tx = NotificationParser.parse("Recibiste $85.000", "", Country.COP)
        assertEquals("85000.00", tx?.monto)
    }

    @Test
    fun parsesAmountFromBodyWhenNotInTitle() {
        val tx = NotificationParser.parse(
            "NuBank",
            "Compra no débito de R$ 5,99 em Supermercado",
            Country.BRL
        )
        assertEquals("5.99", tx?.monto)
    }

    @Test
    fun parsesAmountWithNonBreakingSpace() {
        val tx = NotificationParser.parse("Compraste $1 234.56", "", Country.MXN)
        assertEquals("1234.56", tx?.monto)
    }

    @Test
    fun returnsNullWhenNoAmount() {
        assertNull(NotificationParser.parse("Atualização disponível", "Abra o app", Country.BRL))
        assertNull(NotificationParser.parse("Promoción", "Sin monto", Country.MXN))
    }

    // ---------- Transaction type ----------

    @Test
    fun detectsTypesInSpanishAndPortuguese() {
        assertEquals("Envío", NotificationParser.parse("Enviaste R$ 10,00", "", Country.BRL)?.comercio)
        assertEquals("Envío", NotificationParser.parse("Enviou R$ 10,00", "", Country.BRL)?.comercio)
        assertEquals("Recibido", NotificationParser.parse("Recibiste R$ 10,00", "", Country.BRL)?.comercio)
        assertEquals("Recibido", NotificationParser.parse("Recebeu R$ 10,00", "", Country.BRL)?.comercio)
        assertEquals("Compra", NotificationParser.parse("Compraste R$ 10,00", "", Country.BRL)?.comercio)
        assertEquals("Compra", NotificationParser.parse("Comprou R$ 10,00", "", Country.BRL)?.comercio)
        assertEquals("Pago", NotificationParser.parse("Pagou R$ 10,00", "", Country.BRL)?.comercio)
        assertEquals("Retiro", NotificationParser.parse("Saque R$ 10,00", "", Country.BRL)?.comercio)
    }

    @Test
    fun unknownTypeFallsBackToDash() {
        assertEquals("-", NotificationParser.parse("Aviso R$ 10,00", "", Country.BRL)?.comercio)
    }

    // ---------- Country resolution ----------

    @Test
    fun resolvesCountryFromPackage() {
        assertEquals(Country.BRL, Country.fromPackage("com.nu.production"))
        assertEquals(Country.MXN, Country.fromPackage("com.nu.production.mx"))
        assertEquals(Country.COP, Country.fromPackage("com.nu.production.co"))
        assertNull(Country.fromPackage("com.whatsapp"))
    }
}
