package com.smalcash.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

// Konverter für Date-Objekte
class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
    
    @TypeConverter
    fun fromArtikelList(artikel: List<VerkaufArtikelEntity>): String {
        return artikel.joinToString(";") { 
            "${it.artikelId}|${it.name}|${it.preis}|${it.pfand}|${it.anzahl}" 
        }
    }
    
    @TypeConverter
    fun toArtikelList(artikelString: String): List<VerkaufArtikelEntity> {
        return if (artikelString.isEmpty()) {
            emptyList()
        } else {
            artikelString.split(";").map { item ->
                val parts = item.split("|")
                VerkaufArtikelEntity(
                    artikelId = parts[0],
                    name = parts[1],
                    preis = parts[2].toDouble(),
                    pfand = parts[3].toDouble(),
                    anzahl = parts[4].toInt()
                )
            }
        }
    }
}

// Entitäten für Room
@Entity(tableName = "artikel_cache")
data class ArtikelEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val preis: Double,
    val pfand: Double,
    val kategorie: String,
    val betreiberId: String,
    val aktiv: Boolean = true,
    val letzteAktualisierung: Date = Date()
)

@Entity(tableName = "transaktionen_offline")
data class TransaktionEntity(
    @PrimaryKey(autoGenerate = true) val lokalId: Long = 0,
    val betreiberId: String,
    val kasseId: String,
    val artikel: List<VerkaufArtikelEntity>,
    val gesamtsumme: Double,
    val transaktionsgebuehr: Double,  // Deine 1%
    val zeitstempel: Date,
    val kassierer: String,
    val synchronisiert: Boolean = false,
    val firebaseId: String? = null
)

data class VerkaufArtikelEntity(
    val artikelId: String,
    val name: String,
    val preis: Double,
    val pfand: Double,
    val anzahl: Int
)

@Entity(tableName = "betreiber_cache")
data class BetreiberEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    val aktiv: Boolean,
    val letzteAktualisierung: Date = Date()
)

@Entity(tableName = "kassen_cache")  
data class KasseEntity(
    @PrimaryKey val id: String,
    val betreiberId: String,
    val name: String,
    val standort: String,
    val aktiv: Boolean,
    val letzteAktualisierung: Date = Date()
)

// DAOs (Data Access Objects)
@Dao
interface ArtikelDao {
    @Query("SELECT * FROM artikel_cache WHERE betreiberId = :betreiberId AND aktiv = 1")
    fun getArtikelForBetreiber(betreiberId: String): Flow<List<ArtikelEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtikel(artikel: List<ArtikelEntity>)
    
    @Query("DELETE FROM artikel_cache WHERE betreiberId = :betreiberId")
    suspend fun deleteArtikelForBetreiber(betreiberId: String)
}

@Dao
interface TransaktionDao {
    @Insert
    suspend fun insertTransaktion(transaktion: TransaktionEntity): Long
    
    @Query("SELECT * FROM transaktionen_offline WHERE synchronisiert = 0")
    suspend fun getUnsynchronisierteTransaktionen(): List<TransaktionEntity>
    
    @Update
    suspend fun updateTransaktion(transaktion: TransaktionEntity)
    
    @Query("UPDATE transaktionen_offline SET synchronisiert = 1, firebaseId = :firebaseId WHERE lokalId = :lokalId")
    suspend fun markiereSynchronisiert(lokalId: Long, firebaseId: String)
    
    @Query("SELECT COUNT(*) FROM transaktionen_offline WHERE synchronisiert = 0")
    fun getAnzahlUnsynchronisiert(): Flow<Int>
    
    @Query("SELECT SUM(gesamtsumme) FROM transaktionen_offline WHERE date(zeitstempel/1000, 'unixepoch') = date('now')")
    fun getTagesumsatz(): Flow<Double?>
    
    @Query("SELECT SUM(transaktionsgebuehr) FROM transaktionen_offline WHERE date(zeitstempel/1000, 'unixepoch') = date('now')")
    fun getTagesgebuehren(): Flow<Double?>
}

@Dao
interface BetreiberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBetreiber(betreiber: BetreiberEntity)
    
    @Query("SELECT * FROM betreiber_cache WHERE id = :id")
    suspend fun getBetreiberById(id: String): BetreiberEntity?
    
    @Query("SELECT * FROM betreiber_cache")
    fun getAlleBetreiber(): Flow<List<BetreiberEntity>>
}

@Dao
interface KasseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKassen(kassen: List<KasseEntity>)
    
    @Query("SELECT * FROM kassen_cache WHERE betreiberId = :betreiberId AND aktiv = 1")
    fun getKassenForBetreiber(betreiberId: String): Flow<List<KasseEntity>>
    
    @Query("DELETE FROM kassen_cache WHERE betreiberId = :betreiberId")
    suspend fun deleteKassenForBetreiber(betreiberId: String)
}

// Room Datenbank
@Database(
    entities = [
        ArtikelEntity::class,
        TransaktionEntity::class,
        BetreiberEntity::class,
        KasseEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SportKioskDatabase : RoomDatabase() {
    abstract fun artikelDao(): ArtikelDao
    abstract fun transaktionDao(): TransaktionDao
    abstract fun betreiberDao(): BetreiberDao
    abstract fun kasseDao(): KasseDao
    
    companion object {
        @Volatile
        private var INSTANCE: SportKioskDatabase? = null
        
        fun getDatabase(context: android.content.Context): SportKioskDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SportKioskDatabase::class.java,
                    "sportkiosk_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// Repository für Offline-Operationen
class OfflineRepository(private val database: SportKioskDatabase) {
    
    // Artikel
    fun getArtikel(betreiberId: String) = database.artikelDao().getArtikelForBetreiber(betreiberId)
    
    suspend fun aktualisiereArtikelCache(betreiberId: String, artikel: List<ArtikelEntity>) {
        database.artikelDao().deleteArtikelForBetreiber(betreiberId)
        database.artikelDao().insertArtikel(artikel)
    }
    
    // Transaktionen
    suspend fun speichereTransaktionOffline(transaktion: TransaktionEntity): Long {
        return database.transaktionDao().insertTransaktion(transaktion)
    }
    
    suspend fun getUnsynchronisierteTransaktionen() = 
        database.transaktionDao().getUnsynchronisierteTransaktionen()
    
    suspend fun markiereAlsSynchronisiert(lokalId: Long, firebaseId: String) {
        database.transaktionDao().markiereSynchronisiert(lokalId, firebaseId)
    }
    
    fun getAnzahlUnsynchronisiert() = database.transaktionDao().getAnzahlUnsynchronisiert()
    
    fun getTagesumsatz() = database.transaktionDao().getTagesumsatz()
    
    fun getTagesgebuehren() = database.transaktionDao().getTagesgebuehren()
    
    // Betreiber
    suspend fun speichereBetreiber(betreiber: BetreiberEntity) {
        database.betreiberDao().insertBetreiber(betreiber)
    }
    
    suspend fun getBetreiber(id: String) = database.betreiberDao().getBetreiberById(id)
    
    fun getAlleBetreiber() = database.betreiberDao().getAlleBetreiber()
    
    // Kassen
    suspend fun aktualisiereKassenCache(betreiberId: String, kassen: List<KasseEntity>) {
        database.kasseDao().deleteKassenForBetreiber(betreiberId)
        database.kasseDao().insertKassen(kassen)
    }
    
    fun getKassen(betreiberId: String) = database.kasseDao().getKassenForBetreiber(betreiberId)
}
