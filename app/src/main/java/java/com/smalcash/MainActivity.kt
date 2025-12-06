package com.smalcash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smalcash.data.Artikel
import com.smalcash.data.WarenkorbItem
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmalCashTheme {
                SmalCashApp()
            }
        }
    }
}

// SmalCash Farben
@Composable
fun SmalCashTheme(content: @Composable () -> Unit) {
    val smalCashColors = lightColorScheme(
        primary = Color(0xFF2E7D32),
        secondary = Color(0xFF388E3C),
        tertiary = Color(0xFF4CAF50),
        primaryContainer = Color(0xFF81C784),
        onPrimary = Color.White,
        surface = Color(0xFFF5F5F5),
        onSurface = Color(0xFF1B1B1B)
    )

    MaterialTheme(
        colorScheme = smalCashColors,
        content = content
    )
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SmalCashApp() {
    // Artikel-Liste
    val artikelListe = remember {
        listOf(
            Artikel(1, "Cola 0,5L", 3.50, 0.25, "Getränke", "🥤"),
            Artikel(2, "Bier 0,5L", 4.00, 0.25, "Getränke", "🍺"),
            Artikel(3, "Wasser 0,5L", 2.50, 0.25, "Getränke", "💧"),
            Artikel(4, "Kaffee", 2.00, 0.0, "Getränke", "☕"),
            Artikel(5, "Apfelschorle", 3.00, 0.25, "Getränke", "🧃"),
            Artikel(6, "Bratwurst", 3.50, 0.0, "Speisen", "🌭"),
            Artikel(7, "Pommes", 3.00, 0.0, "Speisen", "🍟"),
            Artikel(8, "Brezel", 2.00, 0.0, "Speisen", "🥨"),
            Artikel(9, "Pizza Stück", 3.50, 0.0, "Speisen", "🍕"),
            Artikel(10, "Schokoriegel", 1.50, 0.0, "Snacks", "🍫"),
            Artikel(11, "Chips", 2.50, 0.0, "Snacks", "🥔"),
            Artikel(12, "Gummibärchen", 2.00, 0.0, "Snacks", "🍬"),
            Artikel(13, "Pfand Rückgabe", -0.25, 0.0, "Pfand", "♻️")
        )
    }

    // States
    var warenkorb by remember { mutableStateOf(listOf<WarenkorbItem>()) }
    var tagesumsatz by remember { mutableDoubleStateOf(0.0) }
    var tagesprovision by remember { mutableDoubleStateOf(0.0) }
    var verkaufteArtikel by remember { mutableStateOf(listOf<WarenkorbItem>()) }
    var isAdmin by remember { mutableStateOf(false) }
    var showAdminLogin by remember { mutableStateOf(false) }
    var showAdminStatistik by remember { mutableStateOf(false) }
    var tapCount by remember { mutableIntStateOf(0) }

    // Euro-Format
    val euroFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY)

    // Berechnungen
    val reinerWarenwert = warenkorb.filter { it.artikel.preis > 0 }.sumOf { it.artikel.preis * it.anzahl }
    val gesamtsumme = warenkorb.sumOf { (it.artikel.preis + it.artikel.pfand) * it.anzahl }
    val transaktionsgebuehr = reinerWarenwert * 0.01

    val coroutineScope = rememberCoroutineScope()
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val adminBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    val bezahlVorgang: () -> Unit = {
        tagesumsatz += gesamtsumme
        tagesprovision += transaktionsgebuehr
        
        // Update verkaufteArtikel
        val aktuelleVerkaeufe = verkaufteArtikel.toMutableList()
        warenkorb.forEach { warenkorbItem ->
            val index = aktuelleVerkaeufe.indexOfFirst { it.artikel.id == warenkorbItem.artikel.id }
            if (index != -1) {
                val bestehenderEintrag = aktuelleVerkaeufe[index]
                aktuelleVerkaeufe[index] = bestehenderEintrag.copy(anzahl = bestehenderEintrag.anzahl + warenkorbItem.anzahl)
            } else {
                aktuelleVerkaeufe.add(warenkorbItem)
            }
        }
        verkaufteArtikel = aktuelleVerkaeufe

        println("💰 Verkauf: ${euroFormat.format(gesamtsumme)}")
        println("📊 Provision: ${euroFormat.format(transaktionsgebuehr)}")
        warenkorb = emptyList()
        coroutineScope.launch { bottomSheetState.hide() }
    }

    val logoutAdmin: () -> Unit = {
        isAdmin = false
        showAdminStatistik = false
    }

    BoxWithConstraints {
        val isWideScreen = maxWidth > 600.dp

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    "💰 SmalCash",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    modifier = Modifier.pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = { 
                                                tapCount++
                                                if (tapCount >= 6) {
                                                    showAdminLogin = true
                                                    tapCount = 0 // Zähler zurücksetzen
                                                }
                                            }
                                        )
                                    }
                                )
                                // Vereinsname als Badge
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "TSV Beispielheim",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if(isAdmin) {
                                    Button(onClick = { showAdminStatistik = true }) {
                                        Text("Statistik")
                                    }
                                }
                                Icon(
                                    Icons.Filled.Wifi,
                                    contentDescription = "Online",
                                    tint = Color.Green,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text("Kasse 1", fontSize = 14.sp)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White
                    )
                )
            },
            bottomBar = {
                if (!isWideScreen && warenkorb.isNotEmpty()) {
                    WarenkorbPreview(warenkorb, gesamtsumme, onBezahlClick = bezahlVorgang) {
                        coroutineScope.launch {
                            bottomSheetState.expand()
                        }
                    }
                } else {
                    BottomAppBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.height(60.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Tagesumsatz",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    euroFormat.format(tagesumsatz),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (isAdmin) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "Deine Provision (1%)",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        euroFormat.format(tagesprovision),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4CAF50)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            if (isWideScreen) {
                Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    ArtikelGrid(modifier = Modifier.weight(1.8f), artikelListe = artikelListe, onWarenkorbChange = { warenkorb = it }, warenkorb = warenkorb)
                    Warenkorb(modifier = Modifier.weight(1.2f), warenkorb = warenkorb, gesamtsumme = gesamtsumme, transaktionsgebuehr = transaktionsgebuehr, onWarenkorbChange = { warenkorb = it }, onBezahlClick = bezahlVorgang)
                }
            } else {
                ArtikelGrid(artikelListe = artikelListe, onWarenkorbChange = { warenkorb = it }, warenkorb = warenkorb, modifier = Modifier.padding(innerPadding))
            }
        }

        if (showAdminLogin) {
            AdminLoginDialog(
                onDismiss = { showAdminLogin = false },
                onLogin = { pin ->
                    if (pin == "1234") {
                        isAdmin = true
                        showAdminLogin = false
                    }
                }
            )
        }

        if (showAdminStatistik) {
            ModalBottomSheet(onDismissRequest = { logoutAdmin() }, sheetState = adminBottomSheetState) {
                AdminStatistikSheet(verkaufteArtikel, tagesprovision, onLogout = logoutAdmin)
            }
        }

        if (!isWideScreen && bottomSheetState.isVisible) {
            ModalBottomSheet(onDismissRequest = { coroutineScope.launch { bottomSheetState.hide() } }, sheetState = bottomSheetState) {
                Warenkorb(warenkorb = warenkorb, gesamtsumme = gesamtsumme, transaktionsgebuehr = transaktionsgebuehr, onWarenkorbChange = { warenkorb = it }, onBezahlClick = bezahlVorgang)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtikelGrid(modifier: Modifier = Modifier, artikelListe: List<Artikel>, warenkorb: List<WarenkorbItem>, onWarenkorbChange: (List<WarenkorbItem>) -> Unit) {
    val euroFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY)
    Column(
        modifier = modifier
            .padding(16.dp)
    ) {
        // Kategorien als Chips
        var selectedKategorie by remember { mutableStateOf("Alle") }
        val kategorien = listOf("Alle", "Getränke", "Speisen", "Snacks", "Pfand")

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            kategorien.forEach { kategorie ->
                FilterChip(
                    selected = selectedKategorie == kategorie,
                    onClick = { selectedKategorie = kategorie },
                    label = { Text(kategorie) },
                    leadingIcon = if (selectedKategorie == kategorie) {
                        {
                            Icon(
                                imageVector = Icons.Default.Done,
                                contentDescription = "Selected",
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else {
                        null
                    }
                )
            }
        }

        // Artikel-Grid
        val gefilterteArtikel = if (selectedKategorie == "Alle") {
            artikelListe
        } else {
            artikelListe.filter { it.kategorie == selectedKategorie }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp), // Responsive Grid
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(gefilterteArtikel.size) { index ->
                val artikel = gefilterteArtikel[index]

                ElevatedCard(
                    onClick = {
                        val existingItem = warenkorb.find { it.artikel.id == artikel.id }
                        if (existingItem != null) {
                            onWarenkorbChange(warenkorb.map {
                                if (it.artikel.id == artikel.id) {
                                    it.copy(anzahl = it.anzahl + 1)
                                } else it
                            })
                        } else {
                            onWarenkorbChange(warenkorb + WarenkorbItem(artikel))
                        }
                    },
                    modifier = Modifier
                        .height(120.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = when (artikel.kategorie) {
                            "Getränke" -> Color(0xFFE3F2FD)
                            "Speisen" -> Color(0xFFFFF3E0)
                            "Snacks" -> Color(0xFFFFEBEE)
                            "Pfand" -> Color(0xFFE8F5E9)
                            else -> MaterialTheme.colorScheme.surface
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            artikel.icon,
                            fontSize = 28.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            artikel.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center // Zentriert
                        )
                        Text(
                            euroFormat.format(artikel.preis),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (artikel.pfand > 0) {
                            Text(
                                "+${euroFormat.format(artikel.pfand)} Pfand",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Warenkorb(
    modifier: Modifier = Modifier, 
    warenkorb: List<WarenkorbItem>, 
    gesamtsumme: Double, 
    transaktionsgebuehr: Double, 
    onWarenkorbChange: (List<WarenkorbItem>) -> Unit,
    onBezahlClick: () -> Unit
) {
    val euroFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY)

    Card(
        modifier = modifier
            .fillMaxHeight()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "🛒 Warenkorb",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                if (warenkorb.isNotEmpty()) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            warenkorb.sumOf { it.anzahl }.toString(),
                            color = Color.White
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Warenkorb-Inhalt
            LazyColumn(modifier = Modifier.weight(1f)) { // ersetzt Column
                if (warenkorb.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Filled.ShoppingCart,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = Color.LightGray
                                )
                                Text(
                                    "Warenkorb leer",
                                    color = Color.Gray,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                } else {
                    items(warenkorb, key = { it.artikel.id }) { item -> // Hinzugefügter Key
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(item.artikel.icon, fontSize = 20.sp, color = Color.Black)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            item.artikel.name,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.Black
                                        )
                                        Text(
                                            "${item.anzahl}x ${euroFormat.format(item.artikel.preis)}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        euroFormat.format(
                                            (item.artikel.preis + item.artikel.pfand) * item.anzahl
                                        ),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.Black
                                    )
                                    IconButton(
                                        onClick = {
                                            onWarenkorbChange(if (item.anzahl > 1) {
                                                warenkorb.map {
                                                    if (it.artikel.id == item.artikel.id) {
                                                        it.copy(anzahl = it.anzahl - 1)
                                                    } else it
                                                }
                                            } else {
                                                warenkorb.filter { it.artikel.id != item.artikel.id }
                                            })
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.RemoveCircle,
                                            contentDescription = "Entfernen",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Summen-Bereich
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Zwischensumme:", fontSize = 14.sp)
                    Text(
                        euroFormat.format(warenkorb.sumOf { it.artikel.preis * it.anzahl }),
                        fontSize = 14.sp
                    )
                }

                val pfandSumme = warenkorb.sumOf { it.artikel.pfand * it.anzahl }
                if (pfandSumme != 0.0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Pfand:", fontSize = 14.sp)
                        Text(euroFormat.format(pfandSumme), fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Gesamtsumme
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "GESAMT:",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                euroFormat.format(gesamtsumme),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "inkl. ${euroFormat.format(transaktionsgebuehr)} Gebühr",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onWarenkorbChange(emptyList()) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    enabled = warenkorb.isNotEmpty()
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Löschen")
                }

                Button(
                    onClick = onBezahlClick,
                    modifier = Modifier.weight(1.5f),
                    enabled = gesamtsumme != 0.0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Icon(
                        Icons.Filled.Euro,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "BEZAHLT",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun WarenkorbPreview(warenkorb: List<WarenkorbItem>, gesamtsumme: Double, onBezahlClick: () -> Unit, onPreviewClick: () -> Unit) {
    val euroFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        color = MaterialTheme.colorScheme.primary,
        onClick = onPreviewClick
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Badge(containerColor = MaterialTheme.colorScheme.onPrimary) {
                Text(
                    text = warenkorb.sumOf { it.anzahl }.toString(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "Zum Warenkorb",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Button(
                onClick = onBezahlClick,
                enabled = gesamtsumme != 0.0,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Icon(Icons.Filled.Euro, contentDescription = "Direkt bezahlen")
                Spacer(modifier = Modifier.width(8.dp))
                Text(euroFormat.format(gesamtsumme))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLoginDialog(onDismiss: () -> Unit, onLogin: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Admin Login") },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it },
                label = { Text("PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
        },
        confirmButton = {
            Button(onClick = { onLogin(pin) }) {
                Text("Login")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
fun AdminStatistikSheet(verkaufteArtikel: List<WarenkorbItem>, tagesprovision: Double, onLogout: () -> Unit) {
    val euroFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY)
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Tagesstatistik", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(verkaufteArtikel) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${item.anzahl}x ${item.artikel.name}")
                    Text(euroFormat.format(item.artikel.preis * item.anzahl))
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Gesamtprovision: ${euroFormat.format(tagesprovision)}", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("Admin-Modus beenden")
        }
    }
}
