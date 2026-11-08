package app.lifedashboard.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import app.lifedashboard.core.security.DatabaseKeyProvider
import app.lifedashboard.data.db.AppDatabase
import app.lifedashboard.data.db.RoutineDao
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
            .build()
    }

    @Provides
    fun provideTimetableDao(db: AppDatabase): TimetableDao = db.timetableDao()

    @Provides
    fun provideRoutineDao(db: AppDatabase): RoutineDao = db.routineDao()
}
