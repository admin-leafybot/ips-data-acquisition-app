package ai.indoorbrain.data.local

import ai.indoorbrain.data.model.Bonus
import ai.indoorbrain.data.model.ButtonPress
import ai.indoorbrain.data.model.IMUData
import ai.indoorbrain.data.model.Session
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ai.indoorbrain.data.local.dao.*
import ai.indoorbrain.data.model.*

@Database(
    entities = [
        Session::class,
        ButtonPress::class,
        IMUData::class,
        Bonus::class
    ],
    version = 4,  // Incremented for floorIndex field in ButtonPress
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun buttonPressDao(): ButtonPressDao
    abstract fun imuDataDao(): IMUDataDao
    abstract fun bonusDao(): BonusDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ips_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

