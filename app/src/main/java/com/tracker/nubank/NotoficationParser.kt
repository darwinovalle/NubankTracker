package com.tracker.nubank

data class TransactionData(
    val monto: String,
    val comercio: String,
    val textoOriginal: String
)

object NotificationParser {

    fun parse(title: String, text: String): TransactionData? {

        // Buscar monto en el TÍTULO primero (ej: "Enviaste $90,00")
        // Luego en el texto si no está en el título
        val montoPattern = Regex("""\$\s*([\d]{1,3}(?:[.,]\d{3})*(?:[.,]\d{2})?|\d+(?:[.,]\d{2})?)""")

        val matchTitle = montoPattern.find(title)
        val matchText = montoPattern.find(text)

        val match = matchTitle ?: matchText

        val monto = if (match != null) {
            match.groupValues[1]
                .replace(".", "")   // Quitar separadores de miles
                .replace(",", ".")  // Convertir coma decimal a punto
                .trim()
        } else {
            "0"
        }

        // Detectar tipo de transacción del título
        val tipo = when {
            title.contains("Enviaste", ignoreCase = true) -> "Envío"
            title.contains("Recibiste", ignoreCase = true) -> "Recibido"
            title.contains("Compra", ignoreCase = true) -> "Compra"
            title.contains("Pago", ignoreCase = true) -> "Pago"
            title.contains("Retiro", ignoreCase = true) -> "Retiro"
            else -> "-"
        }

        return TransactionData(
            monto = monto,
            comercio = tipo,
            textoOriginal = "$title | $text"
        )
    }
}