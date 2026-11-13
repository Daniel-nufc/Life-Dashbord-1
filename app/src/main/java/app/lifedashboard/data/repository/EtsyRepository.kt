package app.lifedashboard.data.repository

import app.lifedashboard.data.db.DesignStatus
import app.lifedashboard.data.db.EtsyDao
import app.lifedashboard.data.db.EtsyDesignEntity
import app.lifedashboard.data.db.EtsySaleEntity
import app.lifedashboard.data.db.EtsyStatsEntity
import app.lifedashboard.data.settings.SettingsRepository
import app.lifedashboard.planner.EtsyFees
import app.lifedashboard.planner.EtsyMath
import app.lifedashboard.planner.EtsyMonth
import app.lifedashboard.planner.EtsySaleData
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class EtsyOverview(
    val designs: List<EtsyDesignEntity>,
    val sales: List<EtsySaleEntity>,
    val stats: List<EtsyStatsEntity>,
    val thisMonth: EtsyMonth,
    val lastMonth: EtsyMonth,
    val top: List<Pair<String, Long>>,
    val fees: EtsyFees,
    val tip: String,
) {
    val revenueChange: Int? get() = EtsyMath.change(thisMonth.revenueCents, lastMonth.revenueCents)
    val online: Int get() = designs.count { it.status == DesignStatus.ONLINE }
    /** Erstes Motiv, das noch nicht online ist – „nächster Schritt“. */
    val nextDesign: EtsyDesignEntity? get() = designs.filter { it.status != DesignStatus.ONLINE }.maxByOrNull { it.status.ordinal }
}

fun DesignStatus.label(): String = when (this) {
    DesignStatus.IDEA -> "Idee"
    DesignStatus.CREATED -> "Erstellt"
    DesignStatus.CHECKED -> "Geprüft"
    DesignStatus.TEST_PRINT -> "Probedruck"
    DesignStatus.MOCKUP -> "Mockup"
    DesignStatus.LISTING -> "Listing"
    DesignStatus.ONLINE -> "Online"
}

fun DesignStatus.nextStepHint(): String = when (this) {
    DesignStatus.IDEA -> "Motiv in Canva erstellen (nur eigene Text-Prompts)"
    DesignStatus.CREATED -> "Qualität prüfen: Schärfe, Ränder, 2850 × 1200 px"
    DesignStatus.CHECKED -> "Probedruck bei Oma (Größe, Farben, Schärfe)"
    DesignStatus.TEST_PRINT -> "Mockup (Motiv auf Tasse) + Titelbild"
    DesignStatus.MOCKUP -> "Listing schreiben: Titel, Tags, KI-Hinweis"
    DesignStatus.LISTING -> "Hochladen: KI-Häkchen + „Designed by“ setzen"
    DesignStatus.ONLINE -> "Online"
}

@Singleton
class EtsyRepository @Inject constructor(
    private val dao: EtsyDao,
    private val settings: SettingsRepository,
) {
    fun overview(today: LocalDate = LocalDate.now()): Flow<EtsyOverview> = combine(
        dao.observeDesigns(),
        dao.observeSales(),
        dao.observeStats(),
        settings.settings,
    ) { designs, sales, stats, s ->
        val data = sales.map { EtsySaleData(LocalDate.ofEpochDay(it.dateEpochDay), it.orderRef, it.title, it.priceCents, it.quantity) }
        val month = YearMonth.from(today)
        EtsyOverview(
            designs = designs,
            sales = sales,
            stats = stats,
            thisMonth = EtsyMath.month(data, month, s.etsyFees),
            lastMonth = EtsyMath.month(data, month.minusMonths(1), s.etsyFees),
            top = EtsyMath.topProducts(data),
            fees = s.etsyFees,
            tip = EtsyMath.seasonalTip(today),
        )
    }

    suspend fun saveDesign(d: EtsyDesignEntity) = dao.upsertDesign(d)
    suspend fun deleteDesign(id: Long) = dao.deleteDesign(id)
    suspend fun saveSale(s: EtsySaleEntity) = dao.upsertSale(s)
    suspend fun deleteSale(id: Long) = dao.deleteSale(id)
    suspend fun saveStats(month: YearMonth, views: Int, favorites: Int) = dao.putStats(EtsyStatsEntity(monthKey = month.toString(), views = views, favorites = favorites))
    suspend fun saveFees(fees: EtsyFees) = settings.setEtsyFees(fees)
}
