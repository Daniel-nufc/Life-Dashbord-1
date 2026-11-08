package app.lifedashboard.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SportsHockey
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.lifedashboard.data.db.LifeArea

/** Jeder Lebensbereich hat eine feste Farbe, ein Symbol und einen Namen – überall gleich. */
val LifeArea.color: Color
    get() = when (this) {
        LifeArea.SCHOOL -> Color(0xFF3D7BF7)
        LifeArea.SPORT -> Color(0xFF1DB370)
        LifeArea.ETSY -> Color(0xFFF26B1D)
        LifeArea.JOB -> Color(0xFFE5A400)
        LifeArea.PRIVATE -> Color(0xFF9B59D9)
    }

val LifeArea.icon: ImageVector
    get() = when (this) {
        LifeArea.SCHOOL -> Icons.Rounded.School
        LifeArea.SPORT -> Icons.Rounded.SportsHockey
        LifeArea.ETSY -> Icons.Rounded.Storefront
        LifeArea.JOB -> Icons.Rounded.Construction
        LifeArea.PRIVATE -> Icons.Rounded.Home
    }

val LifeArea.label: String
    get() = when (this) {
        LifeArea.SCHOOL -> "Schule"
        LifeArea.SPORT -> "Sport"
        LifeArea.ETSY -> "Etsy"
        LifeArea.JOB -> "Job"
        LifeArea.PRIVATE -> "Privat"
    }

val LifeArea.emoji: String
    get() = when (this) {
        LifeArea.SCHOOL -> "🎓"
        LifeArea.SPORT -> "🏑"
        LifeArea.ETSY -> "🛍️"
        LifeArea.JOB -> "👷"
        LifeArea.PRIVATE -> "🏠"
    }
