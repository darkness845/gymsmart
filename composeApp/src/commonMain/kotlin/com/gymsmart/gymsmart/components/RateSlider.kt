package com.gymsmart.gymsmart.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gymsmart.gymsmart.nutrition.NutritionMath
import com.gymsmart.gymsmart.ui.theme.Palette
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Selector de ritmo con zonas de color.
 *
 * El valor interno es el ritmo en % de peso corporal por semana (la unidad correcta: ver
 * doc/10-fundamento-cientifico.md §3.1), pero lo que se enseña son **kilos por semana**, que es como
 * la gente piensa en esto.
 *
 * La zona verde es la banda que maximiza la retención de masa magra. Ir más lento que esa banda no se
 * marca de ninguna forma porque no tiene nada de malo — solo es más lento.
 *
 * **El extremo del deslizador es el tope *efectivo*, no solo el tope duro por composición corporal.**
 * Antes solo conocía el segundo, así que en perfiles sedentarios su tercio superior no cambiaba el
 * objetivo mientras las etiquetas prometían hasta un 54% más de pérdida semanal (AUD-5 en
 * doc/11-auditoria-kcal.md). Para saberlo necesita el gasto, de ahí [tdee] y [energyFloor].
 *
 * @param ratePct ritmo actual con signo.
 * @param tdee gasto diario con el que se calcula el objetivo. Si es `null` el deslizador cae al tope
 *   duro, que es el comportamiento anterior: se usa en pasos del onboarding donde todavía no hay gasto.
 * @param energyFloor suelo por disponibilidad energética, que puede morder antes que el tope del 25%.
 * @param onRateChange recibe el ritmo nuevo, ya con el signo que corresponde al objetivo.
 * @param colors colores del deslizador. Por defecto los del tema oscuro antiguo, que es el que sigue
 *   usando el onboarding; el perfil, ya migrado al estilo Melocotón, pasa los suyos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RateSlider(
    goal: String,
    weightKg: Double,
    ratePct: Double,
    onRateChange: (Double) -> Unit,
    tdee: Double? = null,
    energyFloor: Double = 0.0,
    colors: RateSliderColors = RateSliderColors.melocoton()
) {
    val hardMax = NutritionMath.maxRateFor(goal) ?: return
    val band    = NutritionMath.recommendedBandFor(goal) ?: return
    val sign    = NutritionMath.signFor(goal)

    // El deslizador trabaja en magnitud; el signo lo pone el objetivo. En perder/ganar un 0
    // significaría no hacer nada (mantenimiento, que no usa este deslizador), así que el mínimo real
    // es el primer paso de la rejilla.
    val minPct = 0.05
    val stepSize = 0.05

    // Tope efectivo, redondeado **hacia abajo** a la rejilla para que todas las posiciones del
    // deslizador sean alcanzables de verdad y la última no prometa más de lo que entrega.
    val effectiveMax = tdee
        ?.let { NutritionMath.effectiveMaxRateFor(goal, weightKg, it, energyFloor) }
        ?: hardMax
    val maxPct = (floor(effectiveMax / stepSize) * stepSize).coerceIn(0.0, hardMax)

    // Si no queda ni un paso de margen, no hay ritmo que ofrecer: con este gasto no cabe un ajuste
    // seguro. Decirlo es más útil que un deslizador que no hace nada (el estado `noSafeDeficit`).
    if (maxPct < minPct + 1e-9) {
        Text(
            "Con tu gasto actual no queda margen para un ajuste seguro en este objetivo. " +
                "Para avanzar, lo que toca es aumentar el gasto, no comer menos.",
            color = colors.warning,
            style = MaterialTheme.typography.bodySmall
        )
        return
    }

    val steps = (((maxPct - minPct) / stepSize).roundToInt() - 1).coerceAtLeast(0)

    // Se encaja en la rejilla: `defaultRateFor(gain)` es 0,375, que con pasos de 0,05 no es ninguna
    // posición del deslizador, así que al tocarlo el valor saltaba y ya no se podía recuperar (AUD-22).
    val snapped = (abs(ratePct) / stepSize).roundToInt() * stepSize
    val magnitude = snapped.coerceIn(minPct, maxPct)
    val info = NutritionMath.describeRate(goal, magnitude * sign, weightKg)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Slider(
            value = magnitude.toFloat(),
            onValueChange = { onRateChange(it.toDouble() * sign) },
            valueRange = minPct.toFloat()..maxPct.toFloat(),
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = colors.thumb,
                activeTrackColor = colors.thumb
            ),
            track = { state ->
                val fraction = { v: Double -> ((v - minPct) / (maxPct - minPct)).toFloat() }
                Canvas(modifier = Modifier.fillMaxWidth().height(12.dp)) {
                    val y = size.height / 2
                    val stroke = 6.dp.toPx()
                    // Toda la barra en gris: la parte lenta es válida, solo no destacada.
                    drawLine(
                        color = colors.track,
                        start = Offset(0f, y), end = Offset(size.width, y),
                        strokeWidth = stroke, cap = StrokeCap.Round
                    )
                    // Banda recomendada en verde.
                    drawLine(
                        color = colors.band,
                        start = Offset(fraction(band.start) * size.width, y),
                        end   = Offset(fraction(band.endInclusive) * size.width, y),
                        strokeWidth = stroke, cap = StrokeCap.Round
                    )
                    // Posición actual.
                    drawCircle(
                        color = colors.thumb,
                        radius = stroke,
                        center = Offset(fraction(magnitude) * size.width, y)
                    )
                }
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                info.kgLabel,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "${info.kcalLabel} · ${info.pctLabel}",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }

        if (info.isRecommended) {
            Text(
                "✓ Zona recomendada para conservar músculo",
                color = colors.ok,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/**
 * Colores de [RateSlider]. [band] es la zona recomendada para conservar músculo, y tiene que seguir
 * leyéndose como "zona buena" en cualquier paleta: por eso es verde en las dos.
 */
@Immutable
data class RateSliderColors(
    val track: Color,
    val band: Color,
    val thumb: Color,
    val text: Color,
    val textSecondary: Color,
    val ok: Color,
    val warning: Color
) {
    companion object {
        /** Los colores de Melocotón. Perfil y onboarding usan estos, así el deslizador es el mismo en los dos. */
        @Composable
        fun melocoton(): RateSliderColors {
            val p = Palette.current
            return RateSliderColors(
                track = p.track,
                band = p.success.copy(alpha = 0.55f),
                thumb = p.hero,
                text = p.textPrimary,
                textSecondary = p.textSecondary,
                ok = p.success,
                warning = p.accentText
            )
        }
    }
}
