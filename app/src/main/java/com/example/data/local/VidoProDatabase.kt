package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class VidoProConverters {
    @TypeConverter
    fun fromGenerationType(value: GenerationType): String = value.name

    @TypeConverter
    fun toGenerationType(value: String): GenerationType =
        GenerationType.entries.firstOrNull { it.name == value } ?: GenerationType.FIRE_VIDEO

    @TypeConverter
    fun fromOrderStatus(value: FireOrderStatus): String = value.name

    @TypeConverter
    fun toOrderStatus(value: String): FireOrderStatus =
        FireOrderStatus.entries.firstOrNull { it.name == value } ?: FireOrderStatus.READY
}

@Database(entities = [GenerationEntity::class], version = 1, exportSchema = false)
@TypeConverters(VidoProConverters::class)
abstract class VidoProDatabase : RoomDatabase() {
    abstract fun generationDao(): GenerationDao

    companion object {
        @Volatile
        private var INSTANCE: VidoProDatabase? = null

        fun getInstance(context: Context): VidoProDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VidoProDatabase::class.java,
                    "vidopro_studio.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
