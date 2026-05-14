package com.udl.smartrain.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.udl.smartrain.domain.model.Session


@Database(entities = [Session::class], version = 4, exportSchema = false)
@TypeConverters(Converters::class) // Necessari per guardar objectes com 'Date'
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN dominantActivity TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sessions ADD COLUMN avgMlConfidence REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN mlPredictionCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN highIntensityCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN activityTimeline TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN ragTitle TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sessions ADD COLUMN ragAnswer TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sessions ADD COLUMN ragSourceTitles TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN ragProvider TEXT NOT NULL DEFAULT 'rules'")
                db.execSQL("ALTER TABLE sessions ADD COLUMN ragModel TEXT NOT NULL DEFAULT 'rule_based'")
                db.execSQL("ALTER TABLE sessions ADD COLUMN ragLatencyMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN ragUsedFallback INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN ragFallbackReason TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
