package app.lifedashboard.data.repository

import app.lifedashboard.data.db.TitleChoiceDao
import app.lifedashboard.data.db.TitleChoiceEntity
import app.lifedashboard.data.settings.SettingsRepository
import app.lifedashboard.planner.IcsPersonFilter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Zuordnung der Familienkalender-Titel: [mine] / [notMine] als Vergleichsschlüssel ([IcsPersonFilter.titleKey]). */
data class TitleChoices(val mine: Set<String> = emptySet(), val notMine: Set<String> = emptySet())

/**
 * Welche FamilyWall-Termine gehören Daniel? Gespeichert je Titel in der Datenbank,
 * damit Handy und Tablet (gleicher FamilyWall-Account) dieselbe Zuordnung haben.
 */
@Singleton
class TitleChoiceRepository @Inject constructor(
    private val dao: TitleChoiceDao,
    private val settings: SettingsRepository,
) {
    val choices: Flow<TitleChoices> = dao.observeAll().map { list ->
        TitleChoices(list.filter { it.mine }.map { it.titleKey }.toSet(), list.filter { !it.mine }.map { it.titleKey }.toSet())
    }.distinctUntilChanged()

    /** Titel als meins (true), nicht meins (false) oder wieder offen (null) merken. */
    suspend fun set(title: String, mine: Boolean?) {
        val key = IcsPersonFilter.titleKey(title)
        if (key.isBlank()) return
        when (mine) {
            null -> dao.delete(key)
            else -> if (dao.update(key, mine) == 0) dao.insert(TitleChoiceEntity(titleKey = key, mine = mine))
        }
    }

    /**
     * Einmalig: Zuordnungen aus v33 übernehmen und Daniels Titel vorbelegen (aus seinen Screenshots vom 13.11.2026:
     * grün = Daniel, gelb = meistens dabei). „Floorball Training“ steht schon als festes Training in der App → nicht doppelt.
     * Vorhandene Einträge (z. B. vom Tablet abgeglichen) werden nie überschrieben.
     */
    suspend fun ensureSeeded() {
        val (legacyMine, legacyNotMine, seeded) = settings.takeLegacyTitleChoices()
        val existing = dao.all().map { it.titleKey }.toSet()
        fun add(keys: Collection<String>, mine: Boolean) = keys.map(IcsPersonFilter::titleKey).filter { it !in existing }.toSet()
            .map { TitleChoiceEntity(titleKey = it, mine = mine) }
        val toInsert = mutableListOf<TitleChoiceEntity>()
        toInsert += add(legacyMine, true) + add(legacyNotMine, false)
        if (!seeded) toInsert += add(SEED_MINE, true) + add(SEED_NOT_MINE, false)
        toInsert.distinctBy { it.titleKey }.forEach { dao.insert(it) }
        if (!seeded) settings.markFamilyTitlesSeeded()
    }

    companion object {
        val SEED_MINE = listOf(
            "Spieltag", "Spieltag Kleinfeld", "Turnier", "Floorball Mixed Turnier Falken", "Spieltag Daniel U17 KF",
            "Friseur", "Vieleicht Hetha", "Daniel - Schülerreise", "Sponsoring TSC", "Augenärztin - alle mit Tropfen",
            "Treff mit Karin und Familie?",
        )
        val SEED_NOT_MINE = listOf(
            "Augentropfen Papa", "Training bei den Eisbären", "Sunshine - Essen vorbestellen", "Amtstag", "Kosmetik",
            "Hochzeitstag Oma&Opa", "Hochzeitstag A&M", "Spieltag Marie W", "Spieltag Marie GF", "Spieltag Marie U13",
            "Kurzurlaub mit Mandy und Konstanze", "Hüttenzauber Marie", "Kammermusik-Matinee", "Floorball Training",
            "Weihnachtsferien 2026 Berlin", "Herbstferien 2026 Berlin", "Reiterferien Barlin",
            "Winter - Berlin - Ferien im Schuljahr 2026/2027", "Maries Geburtstag", "Marie - Schule Staatsoper",
            "Abteilungsversammlung TSC", "QS-Weihnachtsfeier", "Kinderarzt Kontrolle Größe",
        )
    }
}
