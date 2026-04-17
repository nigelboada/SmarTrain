package com.udl.smartrain.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.udl.smartrain.domain.model.Session


@Database(entities = [Session::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class) // Necessari per guardar objectes com 'Date'
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
}