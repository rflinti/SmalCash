package com.smalcash.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.smalcash.data.Artikel
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Datenklassen für Firebase
data class Betreiber(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val aktiv: Boolean = true,
    val erstelltAm: Date = Date()
)

data class Kasse(
    val id: String = "",
    val betreiberId: String = "",
    val name: String = "",
    val standort: String = "",
    val aktiv: Boolean = true
)

data class Transaktion(
    val id: String = "",
    val betreiberId: String = "",
    val kasseId: String = "",
    val artikel: List<VerkaufArtikel> = listOf(),
    val gesamtsumme: Double = 0.0,
    val transaktionsgebuehr: Double = 0.0,  // Deine 1%
    val zeitstempel: Date = Date(),
    val kassierer: String = "",
    val synchronisiert: Boolean = false
)

data class VerkaufArtikel(
    val artikelId: String = "",
    val name: String = "",
    val preis: Double = 0.0,
    val pfand: Double = 0.0,
    val anzahl: Int = 1
)

// Firebase Repository
class FirebaseRepository {
    private val db = Firebase.firestore
    
    // Artikel abrufen für einen Betreiber
    suspend fun getArtikel(betreiberId: String): List<Artikel> {
        return try {
            val snapshot = db.collection("betreiber")
                .document(betreiberId)
                .collection("artikel")
                .whereEqualTo("aktiv", true)
                .get()
                .await()
            
            snapshot.documents.map { doc ->
                Artikel(
                    id = doc.getLong("id")?.toInt() ?: 0,
                    name = doc.getString("name") ?: "",
                    preis = doc.getDouble("preis") ?: 0.0,
                    pfand = doc.getDouble("pfand") ?: 0.0,
                    kategorie = doc.getString("kategorie") ?: ""
                )
            }
        } catch (e: Exception) {
            println("Fehler beim Laden der Artikel: ${e.message}")
            emptyList()
        }
    }
    
    // Transaktion speichern
    suspend fun speichereTransaktion(transaktion: Transaktion): Boolean {
        return try {
            // In Firebase speichern
            db.collection("transaktionen")
                .add(transaktion)
                .await()
            
            // Tagesumsatz aktualisieren
            val tagesId = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date())
            val tagesRef = db.collection("betreiber")
                .document(transaktion.betreiberId)
                .collection("tagesumsaetze")
                .document(tagesId)
            
            db.runTransaction { transaction ->
                val snapshot = transaction.get(tagesRef)
                val bisherUmsatz = snapshot.getDouble("umsatz") ?: 0.0
                val bisherGebuehr = snapshot.getDouble("gebuehren") ?: 0.0
                
                transaction.set(tagesRef, mapOf(
                    "datum" to tagesId,
                    "umsatz" to (bisherUmsatz + transaktion.gesamtsumme),
                    "gebuehren" to (bisherGebuehr + transaktion.transaktionsgebuehr),
                    "anzahlTransaktionen" to ((snapshot.getLong("anzahlTransaktionen") ?: 0) + 1)
                ))
            }.await()
            
            true
        } catch (e: Exception) {
            println("Fehler beim Speichern: ${e.message}")
            false
        }
    }
    
    // Betreiber-Stammdaten laden
    suspend fun getBetreiberInfo(betreiberId: String): Betreiber? {
        return try {
            val doc = db.collection("betreiber")
                .document(betreiberId)
                .get()
                .await()
            
            if (doc.exists()) {
                Betreiber(
                    id = doc.id,
                    name = doc.getString("name") ?: "",
                    email = doc.getString("email") ?: "",
                    aktiv = doc.getBoolean("aktiv") ?: true
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    // Kassen eines Betreibers laden
    suspend fun getKassen(betreiberId: String): List<Kasse> {
        return try {
            val snapshot = db.collection("betreiber")
                .document(betreiberId)
                .collection("kassen")
                .whereEqualTo("aktiv", true)
                .get()
                .await()
            
            snapshot.documents.map { doc ->
                Kasse(
                    id = doc.id,
                    betreiberId = betreiberId,
                    name = doc.getString("name") ?: "",
                    standort = doc.getString("standort") ?: "",
                    aktiv = doc.getBoolean("aktiv") ?: true
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // Demo-Daten erstellen (für den ersten Test)
    suspend fun erstelleDemoDaten() {
        val demoBetreiber = hashMapOf(
            "name" to "TSV Beispielheim",
            "email" to "kiosk@tsv-beispielheim.de",
            "aktiv" to true,
            "erstelltAm" to Date()
        )
        
        val betreiberRef = db.collection("betreiber").add(demoBetreiber).await()
        
        // Demo Kasse
        val demoKasse = hashMapOf(
            "name" to "Hauptkiosk",
            "standort" to "Vereinsheim",
            "aktiv" to true
        )
        betreiberRef.collection("kassen").add(demoKasse)
        
        // Demo Artikel
        val artikel = listOf(
            mapOf("id" to 1, "name" to "Cola 0,5L", "preis" to 3.5, "pfand" to 0.25, "kategorie" to "Getränke", "aktiv" to true),
            mapOf("id" to 2, "name" to "Bier 0,5L", "preis" to 4.0, "pfand" to 0.25, "kategorie" to "Getränke", "aktiv" to true),
            mapOf("id" to 3, "name" to "Wasser 0,5L", "preis" to 2.5, "pfand" to 0.25, "kategorie" to "Getränke", "aktiv" to true),
            mapOf("id" to 4, "name" to "Kaffee", "preis" to 2.0, "pfand" to 0.0, "kategorie" to "Getränke", "aktiv" to true),
            mapOf("id" to 5, "name" to "Bratwurst", "preis" to 3.5, "pfand" to 0.0, "kategorie" to "Speisen", "aktiv" to true),
            mapOf("id" to 6, "name" to "Pommes", "preis" to 3.0, "pfand" to 0.0, "kategorie" to "Speisen", "aktiv" to true)
        )
        
        artikel.forEach { artikelData ->
            betreiberRef.collection("artikel").add(artikelData)
        }
        
        println("Demo-Daten erfolgreich erstellt!")
    }
}
