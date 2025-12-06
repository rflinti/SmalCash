package com.smalcash.data

// Datenklasse für Artikel
data class Artikel(
    val id: Int,
    val name: String,
    val preis: Double,
    val pfand: Double = 0.0,
    val kategorie: String,
    val icon: String = "☕"
)

// Warenkorb-Item
data class WarenkorbItem(
    val artikel: Artikel,
    val anzahl: Int = 1
)
