package com.tracker.nubank

/**
 * Country whose NuBank app a notification came from.
 *
 * The country determines the *number format* used for amounts:
 * - Brazil: `R$ 1.234,56` (thousands `.`, decimal `,`)
 * - Mexico / Colombia: `$1,234.56` (thousands `,`, decimal `.`)
 */
enum class Country(
    val code: String,
    val symbol: String,
    val decimalSeparator: Char,
    val thousandsSeparator: Char
) {
    BRL("BR", "R$", ',', '.'),
    MXN("MX", "$", '.', ','),
    COP("CO", "$", '.', ',');

    companion object {
        /** Map a NuBank app package to its country, or null if it isn't one we know. */
        fun fromPackage(packageName: String): Country? = when (packageName) {
            "com.nu.production" -> BRL
            "com.nu.production.mx" -> MXN
            "com.nu.production.co" -> COP
            else -> null
        }
    }
}
