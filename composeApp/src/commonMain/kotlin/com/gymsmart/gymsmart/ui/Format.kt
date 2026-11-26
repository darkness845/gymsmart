package com.gymsmart.gymsmart.ui

/**
 * Formatos de número para la interfaz, en convención española.
 *
 * Viven aquí y no en cada pantalla para que "7.842" se escriba igual en el inicio y en el perfil.
 */

/** Separador de miles español: 7842 → "7.842". */
fun formatThousands(n: Long): String {
    val digits = kotlin.math.abs(n).toString()
    val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
    return if (n < 0) "-$grouped" else grouped
}

/**
 * Un peso guardado como texto ("78.0", "78,5") listo para enseñar: sin decimales si es entero y con
 * coma si no ("78", "78,5"). Devuelve `null` si el texto no es un número, para que quien lo pinta
 * decida qué poner en su lugar.
 */
fun formatWeight(raw: String): String? {
    val v = raw.trim().replace(',', '.').toDoubleOrNull() ?: return null
    val rounded = kotlin.math.round(v * 10) / 10.0
    return if (rounded == kotlin.math.floor(rounded)) rounded.toLong().toString()
    else rounded.toString().replace('.', ',')
}
