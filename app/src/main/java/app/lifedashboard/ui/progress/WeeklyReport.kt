package app.lifedashboard.ui.progress

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import app.lifedashboard.planner.ProgressState
import java.io.File
import java.time.LocalDate
import java.time.temporal.IsoFields

/**
 * Wochenbericht als Bild (1080 × 1350, passt in WhatsApp & Co.): Schule, Sport, Serie, Level.
 * Wird nur erzeugt, wenn du auf „Teilen“ tippst – nichts verlässt das Handy von selbst.
 */
object WeeklyReport {

    private fun hm(minutes: Int) = if (minutes >= 60) "%d:%02d h".format(minutes / 60, minutes % 60) else "$minutes min"

    fun render(state: ProgressState, name: String, today: LocalDate = LocalDate.now()): Bitmap {
        val w = 1080
        val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val green = 0xFF3EE29A.toInt()
        val blue = 0xFF3BC9F0.toInt()
        val orange = 0xFFFF8A4C.toInt()
        c.drawColor(0xFF0A0A0C.toInt())
        val glow = Paint().apply { shader = RadialGradient(w * 0.85f, 120f, w * 0.9f, intArrayOf(0x663EE29A, 0x00000000), null, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), glow)
        val glow2 = Paint().apply { shader = RadialGradient(80f, h * 1.0f, w * 0.7f, intArrayOf(0x443BC9F0, 0x00000000), null, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), glow2)

        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        fun text(size: Float, color: Int, typeface: Typeface = Typeface.DEFAULT, spacing: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; this.typeface = typeface; letterSpacing = spacing
        }
        val muted = 0xFFA3A3A8.toInt()
        val white = 0xFFF3F2EE.toInt()
        val week = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        c.drawText("MEINE WOCHE · KW $week", 80f, 150f, text(34f, green, bold, 0.12f))
        c.drawText(name.ifBlank { "Life Dashboard" }, 80f, 240f, text(84f, white, bold))
        c.drawText("Level ${state.level.level} · ${state.level.title}", 80f, 300f, text(40f, muted))

        // vier große Kacheln
        val tiles = listOf(
            Triple("Schule", hm(state.studyWeek.minutes), state.studyWeek.fraction) to blue,
            Triple("Sport", hm(state.sportWeek.minutes), state.sportWeek.fraction) to green,
            Triple("Serie", "${state.streak.current} Tage", (state.streak.current / 7f).coerceAtMost(1f)) to orange,
            Triple("Punkte", "${state.level.xpTotal}", state.level.fraction) to white,
        )
        val tileW = (w - 80 * 2 - 40) / 2f
        val tileH = 360f
        tiles.forEachIndexed { i, (t, color) ->
            val x = 80f + (i % 2) * (tileW + 40)
            val y = 380f + (i / 2) * (tileH + 40)
            val rect = RectF(x, y, x + tileW, y + tileH)
            c.drawRoundRect(rect, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFF16161A.toInt() })
            c.drawRoundRect(rect, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; this.color = 0x22FFFFFF })
            c.drawText(t.first.uppercase(), x + 44, y + 80, text(30f, muted, bold, 0.12f))
            c.drawText(t.second, x + 44, y + 200, text(76f, white, bold))
            // Balken
            val barY = y + tileH - 80
            c.drawRoundRect(RectF(x + 44, barY, x + tileW - 44, barY + 16), 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0x22FFFFFF })
            val end = x + 44 + (tileW - 88) * t.third.coerceIn(0.02f, 1f)
            c.drawRoundRect(
                RectF(x + 44, barY, end, barY + 16), 8f, 8f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(x + 44, 0f, end, 0f, color and 0x99FFFFFF.toInt(), color, Shader.TileMode.CLAMP) },
            )
        }
        val km = (state.ironman.totalRunMeters + state.ironman.totalBikeMeters + state.ironman.totalSwimMeters) / 1000
        val line = listOfNotNull(
            state.ironman.current?.let { "Ironman-Weg: ${it.stage.name} · ${(it.fraction * 100).toInt()} %" },
            if (km > 0) "$km km Ausdauer insgesamt" else null,
            "${state.earnedBadges} von ${state.badges.size} Abzeichen",
        )
        line.forEachIndexed { i, l -> c.drawText(l, 80f, 1220f - (line.size - 1 - i) * 56f, text(38f, if (i == 0) white else muted)) }
        c.drawText("Life Dashboard", w - 80f - text(30f, muted).measureText("Life Dashboard"), h - 60f, text(30f, muted))
        return bmp
    }

    fun share(context: Context, state: ProgressState, name: String) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "wochenbericht.png")
        file.outputStream().use { render(state, name).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, "Meine Woche: ${state.studyWeek.minutes} Min Schule, ${state.sportWeek.minutes} Min Sport, ${state.streak.current} Tage Serie.")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Wochenbericht teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
