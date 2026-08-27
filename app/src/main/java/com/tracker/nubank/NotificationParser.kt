package com.tracker.nubank

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

data class TransactionData(
    val monto: String,
    val comercio: String,
    val textoOriginal: String
)

/**
 * Parses NuBank notification text into a transaction.
 *
 * Amounts are normalized to a plain decimal string with two decimals
 * (e.g. `"1234.56"`), independent of the source country's separators.
 */
object NotificationParser {

    // Captures the number after an optional "R" + "$", allowing grouped digits and
    // space/NBSP separators (normalized to a space beforehand).
    // e.g. "R$ 1.234,56", "$90.00", "$1,234.56", "$85.000", "$1 234.56"
    private val AMOUNT_REGEX = Regex("""R?\$\s*(\d+(?:[.,\s]\d+)*)""")

    /**
     * @param country the country whose number format applies
     * @return the parsed transaction, or `null` when no amount is present
     *         (promos, app updates, etc. are ignored instead of logging junk rows).
     */
    fun parse(title: String, text: String, country: Country): TransactionData? {
        // 1. Find the amount: title first, then the body text.
        val raw = findAmount(title, text) ?: return null
        val monto = normalizeAmount(raw) ?: return null

        // 2. Detect the transaction type (ES + PT-BR keywords).
        val tipo = detectType(title)

        return TransactionData(
            monto = monto,
            comercio = tipo,
            textoOriginal = "$title | $text"
        )
    }

    private fun findAmount(title: String, text: String): String? {
        // Replace non-breaking spaces (common between amount groups) with a regular space.
        val titleClean = title.replace(' ', ' ').replace(' ', ' ')
        val textClean = text.replace(' ', ' ').replace(' ', ' ')

        return AMOUNT_REGEX.find(titleClean)?.groupValues?.get(1)
            ?: AMOUNT_REGEX.find(textClean)?.groupValues?.get(1)
    }

    /**
     * Normalizes a raw amount like "1.234,56" / "90.00" / "85.000" to "1234.56".
     *
     * Separator rule (handles every BR/MX/CO case):
     * - If both "," and "." are present, the LAST one is the decimal separator.
     * - If only one separator is present, a trailing group of exactly 2 digits is
     *   a decimal; a trailing group of 3 digits is a thousands group.
     *   This is what makes `$85.000` (COP) → 85000 instead of 85.0.
     */
    private fun normalizeAmount(raw: String): String? {
        val clean = raw.replace(" ", "").trim()
        if (!clean.all { it.isDigit() || it == '.' || it == ',' }) return null
        if (clean.isEmpty()) return null

        val hasDot = clean.contains('.')
        val hasComma = clean.contains(',')

        var decimalSep: Char? = null
        var normalized: String

        when {
            hasDot && hasComma -> {
                // Last separator wins as decimal.
                decimalSep = if (clean.lastIndexOf('.') > clean.lastIndexOf(',')) '.' else ','
                val thousandsSep = if (decimalSep == '.') ',' else '.'
                normalized = clean.replace(thousandsSep.toString(), "").replace(decimalSep, '.')
            }
            hasDot -> {
                decimalSep = if (isDecimalGroup(clean, '.')) '.' else null
                normalized = clean.replace(".", if (decimalSep == null) "" else ".")
            }
            hasComma -> {
                decimalSep = if (isDecimalGroup(clean, ',')) ',' else null
                normalized = clean.replace(",", if (decimalSep == null) "" else ".")
            }
            else -> {
                normalized = clean
            }
        }

        return try {
            BigDecimal(normalized)
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString()
        } catch (e: NumberFormatException) {
            null
        }
    }

    /**
     * True when the separator at [sep] in [s] is a decimal separator.
     * A trailing group of 1–2 digits is a decimal; exactly 3 digits is a thousands group.
     */
    private fun isDecimalGroup(s: String, sep: Char): Boolean {
        val lastIndex = s.lastIndexOf(sep)
        if (lastIndex < 0 || lastIndex == s.length - 1) return false
        return (s.length - 1 - lastIndex) <= 2
    }

    private fun detectType(title: String): String {
        return when {
            title.contains("Enviou", true) || title.contains("Enviaste", true) -> "Envío"
            title.contains("Recebeu", true) || title.contains("Recibiste", true) -> "Recibido"
            title.contains("Comprou", true) || title.contains("Compra", true) ||
                title.contains("Compraste", true) -> "Compra"
            title.contains("Pagou", true) || title.contains("Pago", true) ||
                title.contains("Pagaste", true) -> "Pago"
            title.contains("Saque", true) || title.contains("Retiro", true) ||
                title.contains("Retiraste", true) -> "Retiro"
            else -> "-"
        }
    }
}
