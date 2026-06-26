package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import kotlinx.coroutines.flow.Flow

// --- Room Entities ---

@Entity(tableName = "diagnoses")
data class DiagnosisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cropName: String,
    val disease: String,
    val confidence: Int,
    val symptoms: String,
    val causes: String,
    val treatmentsChemical: String, // Stored as newline-separated values
    val treatmentsOrganic: String,  // Stored as newline-separated values
    val treatmentsPreventive: String, // Stored as newline-separated values
    val imageUrl: String,           // Path or URI of the image analyzed
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "portfolio_items")
data class PortfolioItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uriString: String,
    val timestamp: Long = System.currentTimeMillis()
)

// --- Type Converters (Optional or keep simple helper extension functions) ---
class StringListConverter {
    @TypeConverter
    fun fromString(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return value.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    @TypeConverter
    fun fromList(list: List<String>): String {
        return list.joinToString("\n")
    }
}

// --- DAOs ---

@Dao
interface DiagnosisDao {
    @Query("SELECT * FROM diagnoses ORDER BY timestamp DESC")
    fun getAllDiagnoses(): Flow<List<DiagnosisEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiagnosis(diagnosis: DiagnosisEntity): Long

    @Query("DELETE FROM diagnoses WHERE id = :id")
    suspend fun deleteDiagnosisById(id: Long)

    @Query("DELETE FROM diagnoses")
    suspend fun clearAllDiagnoses()
}

@Dao
interface PortfolioDao {
    @Query("SELECT * FROM portfolio_items ORDER BY timestamp DESC")
    fun getAllPortfolioItems(): Flow<List<PortfolioItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPortfolioItem(item: PortfolioItemEntity): Long

    @Query("DELETE FROM portfolio_items WHERE id = :id")
    suspend fun deletePortfolioItemById(id: Long)

    @Query("DELETE FROM portfolio_items")
    suspend fun clearAllPortfolioItems()
}

// --- App Database ---

@Database(entities = [DiagnosisEntity::class, PortfolioItemEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun diagnosisDao(): DiagnosisDao
    abstract fun portfolioDao(): PortfolioDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "agroscan_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
