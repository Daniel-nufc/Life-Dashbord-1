package app.lifedashboard.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import app.lifedashboard.core.security.DatabaseKeyProvider
import app.lifedashboard.data.db.AppDatabase
import app.lifedashboard.data.db.EventDao
import app.lifedashboard.data.db.JobShiftDao
import app.lifedashboard.data.db.RoutineDao
import app.lifedashboard.data.db.TaskDao
import app.lifedashboard.data.db.TimetableDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/** Ein Coroutine-Scope, der so lange lebt wie die App (für Hintergrundarbeit). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    fun provideTimetableDao(db: AppDatabase): TimetableDao = db.timetableDao()

    @Provides
    fun provideRoutineDao(db: AppDatabase): RoutineDao = db.routineDao()

    @Provides
    fun provideEventDao(db: AppDatabase): EventDao = db.eventDao()

    @Provides
    fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()

    @Provides
    fun provideJobShiftDao(db: AppDatabase): JobShiftDao = db.jobShiftDao()

    @Provides
    fun provideHolidayDao(db: AppDatabase): app.lifedashboard.data.db.HolidayDao = db.holidayDao()

    @Provides
    fun provideEtsyDao(db: AppDatabase): app.lifedashboard.data.db.EtsyDao = db.etsyDao()

    @Provides
    fun provideFinanceDao(db: AppDatabase): app.lifedashboard.data.db.FinanceDao = db.financeDao()

    @Provides
    fun provideSportDao(db: AppDatabase): app.lifedashboard.data.db.SportDao = db.sportDao()

    @Provides
    fun provideGoalDao(db: AppDatabase): app.lifedashboard.data.db.GoalDao = db.goalDao()

    @Provides
    fun providePlanDao(db: AppDatabase): app.lifedashboard.data.db.PlanDao = db.planDao()

    @Provides
    fun provideStudyDao(db: AppDatabase): app.lifedashboard.data.db.StudyDao = db.studyDao()

    @Provides
    fun provideIcalSourceDao(db: AppDatabase): app.lifedashboard.data.db.IcalSourceDao = db.icalSourceDao()

    @Provides
    fun provideTitleChoiceDao(db: AppDatabase): app.lifedashboard.data.db.TitleChoiceDao = db.titleChoiceDao()
}

/**
 * Speicher (DataStore + verschlüsselte Datenbank) als eigenes Modul,
 * damit Screenshot-Tests eine unverschlüsselte In-Memory-Datenbank einsetzen können.
 */
@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyProvider: DatabaseKeyProvider,
    ): AppDatabase {
        // SQLCipher: native Bibliothek laden, dann die Datenbank mit dem Keystore-Passwort öffnen.
        System.loadLibrary("sqlcipher")
        val factory = SupportOpenHelperFactory(keyProvider.getOrCreatePassphrase())
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .openHelperFactory(factory)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .addCallback(AppDatabase.SYNC_CALLBACK)
            .build()
    }
}
