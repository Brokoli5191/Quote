package app.brokoli5191.quote.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [QuoteEntity::class, DailySelectionEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun quoteDao(): QuoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aura_database"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE quotes ADD COLUMN submissionStatus TEXT NOT NULL DEFAULT 'not_submitted'")
                db.execSQL("ALTER TABLE quotes ADD COLUMN submissionId TEXT")
                db.execSQL("ALTER TABLE quotes ADD COLUMN submittedAt INTEGER")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE quotes ADD COLUMN origin TEXT NOT NULL DEFAULT 'bundled'")
                db.execSQL("ALTER TABLE quotes ADD COLUMN serverId TEXT")
                db.execSQL("ALTER TABLE quotes ADD COLUMN serverRevision INTEGER")
                db.execSQL("UPDATE quotes SET origin = 'personal' WHERE isUserAdded = 1")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_quotes_serverId ON quotes(serverId)")
            }
        }
    }
}
