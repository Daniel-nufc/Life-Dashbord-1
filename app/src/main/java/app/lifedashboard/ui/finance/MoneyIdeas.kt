package app.lifedashboard.ui.finance

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.repository.FinanceOverview
import app.lifedashboard.planner.Inspired
import app.lifedashboard.ui.components.SectionCard
import app.lifedashboard.ui.components.TileHeader
import app.lifedashboard.ui.components.color
import app.lifedashboard.data.repository.euro
import java.time.LocalDate

/**
 * „Pro Tag“ und Tage ohne Ausgabe (Ideen: YNAB „safe to spend“, No-Spend-Challenge):
 * wie viel bis zum nächsten Taschengeld pro Tag drin ist und wie lange du schon nichts ausgegeben hast.
 */
@Composable
fun SpendingRhythmCard(d: FinanceOverview) {
    val today = LocalDate.now()
    val next = Inspired.nextIncomeDay(d.recurring.filter { it.active && it.amountCents > 0 }.map { it.dayOfMonth }, today)
    val spendDays = d.transactions.filter { it.amountCents < 0 }.map { LocalDate.ofEpochDay(it.dateEpochDay) }.toSet()
    val first = d.transactions.minOfOrNull { it.dateEpochDay }?.let(LocalDate::ofEpochDay)
    val streak = first?.let { Inspired.noSpendStreak(spendDays, today, it) } ?: 0
    SectionCard {
        TileHeader(Icons.Rounded.Savings, "Dein Geld-Rhythmus", LifeArea.JOB.color)
        if (next != null) {
            val days = (next.toEpochDay() - today.toEpochDay()).toInt()
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Inspired.perDay(d.availableCents, today, next).euro(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("  pro Tag", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
            }
            Text(
                "So viel kannst du täglich ausgeben, bis in $days ${if (days == 1) "Tag" else "Tagen"} wieder Geld kommt (${next.dayOfMonth}.${next.monthValue}.). Dein Puffer bleibt unangetastet.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (first != null) {
            Text(
                when {
                    streak == 0 -> "Heute schon etwas ausgegeben. Morgen startet die nächste Serie ohne Ausgabe."
                    streak == 1 -> "Heute noch nichts ausgegeben. Schaffst du den ganzen Tag?"
                    else -> "$streak Tage ohne Ausgabe am Stück – jeder davon bringt dich dem Führerschein näher."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (streak >= 2) LifeArea.JOB.color else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** „Was kostet das in Arbeit?“ – Preis eintippen, die App rechnet in Stunden auf der Baustelle um. */
@Composable
fun WorkHoursCard(wageCents: Long) {
    var price by rememberSaveable { mutableStateOf("") }
    val cents = price.replace(',', '.').toDoubleOrNull()?.let { (it * 100).toLong() } ?: 0
    val minutes = Inspired.workMinutes(cents, wageCents)
    SectionCard {
        TileHeader(Icons.Rounded.Construction, "Was kostet das in Arbeit?", LifeArea.JOB.color)
        OutlinedTextField(
            value = price,
            onValueChange = { price = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(8) },
            label = { Text("Preis in €") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            when {
                wageCents <= 0 -> "Trag unter Finanzen deinen Stundenlohn ein, dann rechne ich Preise in Arbeitszeit um."
                minutes == null -> "Bevor du etwas kaufst: Preis eintippen und sehen, wie lange du dafür auf der Baustelle stehst."
                minutes < 60 -> "Dafür arbeitest du $minutes Minuten. Ist es dir das wert?"
                else -> "Dafür arbeitest du %d:%02d Std (bei %s pro Stunde). Ist es dir das wert?".format(minutes / 60, minutes % 60, wageCents.euro())
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
