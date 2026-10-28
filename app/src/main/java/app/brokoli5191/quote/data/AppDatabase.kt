package app.brokoli5191.quote.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [QuoteEntity::class, DailySelectionEntity::class, AuthorInfoEntity::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun quoteDao(): QuoteDao
    abstract fun authorInfoDao(): AuthorInfoDao

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
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .addCallback(object : RoomDatabase.Callback() {
                    // A recreated database has no community rows, so the sync cursor must start over.
                    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                        CommunitySyncManager.resetCursor(context.applicationContext)
                    }
                })
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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE quotes ADD COLUMN textDe TEXT")
                db.execSQL("ALTER TABLE quotes ADD COLUMN language TEXT NOT NULL DEFAULT 'en'")
                db.execSQL("ALTER TABLE quotes ADD COLUMN isAnonymous INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE quotes SET category = 'Inspirational' WHERE lower(category) = 'inspiration'")
            }
        }

        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE quotes ADD COLUMN reviewNote TEXT")
                db.execSQL("ALTER TABLE quotes ADD COLUMN linkedServerId TEXT")
                db.execSQL("ALTER TABLE quotes ADD COLUMN textEn TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `author_info` (`name` TEXT NOT NULL, `wikiTitle` TEXT, " +
                        "`imageUrl` TEXT, `description` TEXT, `fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`name`))"
                )
            }
        }
    }
}
