package com.dv.wimc

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val Navy = Color(0xFF1E3A6B)
private val Blue = Color(0xFF1446FF)
private val Red = Color(0xFFE30613)
private val Green = Color(0xFF16A34A)
private val BgApp = Color(0xFFF5F7FB)
private val InfoBg = Color(0xFFF0F7FF)
private val Grey = Color(0xFF6B7280)
private val Ink = Color(0xFF111827)

@Composable
private fun T(
    text: String,
    size: Int,
    bold: Boolean = false,
    color: Color = Ink,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) = Text(
    text = text,
    modifier = modifier,
    color = color,
    fontSize = size.sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    textAlign = align,
    maxLines = maxLines,
    lineHeight = (size + 3).sp,
)

@Composable
private fun CardBox(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp), content = content)
    }
}

@Composable
private fun SmallOutline(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
    ) {
        T(text, 11, color = Navy, align = TextAlign.Center, maxLines = 2)
    }
}

// =====================================================================================
// RAÍZ
// =====================================================================================
@Composable
fun WimcApp(
    vm: WimcViewModel,
    onPark: () -> Unit,
    onPrecise: () -> Unit,
    onToggleDetect: (Boolean) -> Unit,
    onToggleAuto: (Boolean) -> Unit,
) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Blue, secondary = Navy, error = Red, background = BgApp, surface = Color.White,
        )
    ) {
        BackHandler(enabled = vm.tab != 0) { vm.tab = 0 }
        Column(Modifier.fillMaxSize().background(BgApp)) {
            Header(vm.parked != null)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (vm.tab) {
                    0 -> HomeTab(vm, onPark, onPrecise)
                    1 -> MapTab(vm)
                    2 -> HistoryTab(vm)
                    else -> SettingsTab(vm, onToggleDetect, onToggleAuto)
                }
                vm.banner?.let { Banner(it, Modifier.align(Alignment.TopCenter)) }
            }
            BottomNav(vm.tab) { vm.tab = it }
        }
        Dialogs(vm, onPark)
    }
}

@Composable
private fun Header(parked: Boolean) {
    Box(Modifier.fillMaxWidth().background(Navy)) {
        Row(
            Modifier.statusBarsPadding().height(52.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(36.dp))
            Spacer(Modifier.size(8.dp))
            Column(Modifier.weight(1f)) {
                T("WIMC", 15, bold = true, color = Color.White)
                T("Where Is My Car · DV", 11, color = Color(0xFFCBD5E1))
            }
            Box(
                Modifier.background(if (parked) Green else Red, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                T(if (parked) "APARCADO" else "NO APARCADO", 10, bold = true, color = Color.White)
            }
        }
    }
}

@Composable
private fun Banner(msg: String, modifier: Modifier = Modifier) {
    Box(
        modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp)
            .background(Color(0xFF111827), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        T(msg, 12, color = Color.White)
    }
}

@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf("🏠" to "Inicio", "🗺️" to "Mapa", "🕘" to "Historial", "⚙️" to "Ajustes")
    Surface(color = Color.White, shadowElevation = 8.dp) {
        Row(Modifier.navigationBarsPadding().height(48.dp).fillMaxWidth()) {
            items.forEachIndexed { i, item ->
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    T(item.first, 16)
                    T(item.second, 10, bold = i == selected, color = if (i == selected) Blue else Grey)
                }
            }
        }
    }
}

// =====================================================================================
// TAB 1: INICIO (sin scroll)
// =====================================================================================
@Composable
private fun HomeTab(vm: WimcViewModel, onPark: () -> Unit, onPrecise: () -> Unit) {
    val ctx = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(15_000)
            value = System.currentTimeMillis()
        }
    }
    val p = vm.parked
    val gpsAcc = vm.userFix?.acc

    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Card 1: estado
        CardBox {
            T(if (p != null) "Aparcado ${fmtAgo(p.ts, now)}" else "Pulsa APARCAR para guardar", 13, bold = true)
            T(
                if (gpsAcc != null) "Precisión GPS: ${gpsAcc.roundToInt()} m - ${accLabel(gpsAcc)}" else "Precisión GPS: sin datos",
                11, color = Grey,
            )
            if (p != null) {
                Column(
                    Modifier.fillMaxWidth().background(InfoBg, RoundedCornerShape(8.dp)).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    T("📍 ${fmtCoord(p.lat)}, ${fmtCoord(p.lng)}", 12, bold = true)
                    T("🕒 ${fmtDate(p.ts)}", 11)
                    T("🎯 Precisión: ${p.acc?.let { "${it.roundToInt()} m" } ?: "manual / sin dato"}", 11)
                }
            }
        }

        // Card 2: acciones principales
        CardBox {
            Button(
                onClick = onPark,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
            ) {
                T(if (vm.isParking) "⏳ Guardando..." else "📍 APARCAR AHORA", 14, bold = true, color = Color.White)
            }
            if (p != null) {
                Button(
                    onClick = { vm.unpark() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Red),
                ) {
                    T("🏁 DESAPARCAR", 14, bold = true, color = Color.White)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallOutline("📡 Ubicación precisa", Modifier.weight(1f), onPrecise)
                SmallOutline("👆 Tocar mapa para aparcar", Modifier.weight(1f)) { vm.tab = 1 }
            }
            T(
                "Si el GPS falla, toca el mapa para aparcar manual", 10, color = Grey,
                modifier = Modifier.fillMaxWidth(), align = TextAlign.Center,
            )
        }

        // Card 3: acciones secundarias
        CardBox {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallOutline("🗺️ Ver en mapa", Modifier.weight(1f)) { vm.viewOnMap() }
                SmallOutline("🧭 Cómo llegar", Modifier.weight(1f)) {
                    if (p != null) openNavigation(ctx, p) else vm.showBanner("Aún no hay coche aparcado")
                }
                SmallOutline("📤 Compartir", Modifier.weight(1f)) {
                    if (p != null) shareCar(ctx, p) else vm.showBanner("Aún no hay coche aparcado")
                }
            }
        }
    }
}

private fun openNavigation(ctx: Context, p: ParkPoint) {
    val uri = Uri.parse(
        "https://www.google.com/maps/dir/?api=1&destination=${coordStr(p.lat)},${coordStr(p.lng)}&travelmode=walking"
    )
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: Exception) {
    }
}

private fun shareCar(ctx: Context, p: ParkPoint) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Mi coche: https://www.google.com/maps?q=${coordStr(p.lat)},${coordStr(p.lng)}")
    }
    try {
        ctx.startActivity(Intent.createChooser(send, "Compartir ubicación del coche"))
    } catch (e: Exception) {
    }
}

// =====================================================================================
// TAB 2: MAPA (Google Maps nativo, sin scroll de pantalla)
// =====================================================================================
private fun carIcon(): BitmapDescriptor {
    return try {
        val s = 96
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xFFE30613.toInt()
        c.drawCircle(s / 2f, s / 2f, s / 2f - 2f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        paint.color = 0xFFFFFFFF.toInt()
        c.drawCircle(s / 2f, s / 2f, s / 2f - 4f, paint)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG)
        tp.textSize = 52f
        tp.textAlign = Paint.Align.CENTER
        c.drawText("🚗", s / 2f, s / 2f + 18f, tp)
        BitmapDescriptorFactory.fromBitmap(bmp)
    } catch (e: Exception) {
        BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
    }
}

@Composable
private fun MapTab(vm: WimcViewModel) {
    val p = vm.parked
    val u = vm.userFix
    val carLatLng = p?.let { LatLng(it.lat, it.lng) }
    val userLatLng = u?.let { LatLng(it.lat, it.lng) }
    val start = carLatLng ?: userLatLng
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            start ?: LatLng(MADRID_SOL_LAT, MADRID_SOL_LNG), if (start != null) 16f else 5.5f
        )
    }

    // Centro del mapa elegido a mano por el usuario (solo gestos), usado como fallback de APARCAR
    LaunchedEffect(camera.isMoving) {
        if (!camera.isMoving && camera.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) {
            vm.mapCenter = camera.position.target
        }
    }
    LaunchedEffect(vm.focusRequest) {
        val f = vm.focusRequest
        if (f != null) {
            camera.animate(CameraUpdateFactory.newLatLngZoom(f, 17f))
            vm.focusRequest = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(Color.White).height(36.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { vm.tab = 0 }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                T("< Volver a Inicio", 12, bold = true, color = Blue)
            }
        }
        T(
            "👆 Toca en cualquier punto del mapa para aparcar allí (funciona sin GPS)", 11, color = Grey,
            modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 4.dp),
        )
        Box(Modifier.weight(1f).fillMaxWidth().heightIn(min = 250.dp)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = camera,
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = true, myLocationButtonEnabled = false, mapToolbarEnabled = false,
                ),
                onMapClick = { vm.pendingTap = it },
            ) {
                if (carLatLng != null) {
                    Marker(
                        state = remember(carLatLng) { MarkerState(carLatLng) },
                        title = "Tu coche",
                        icon = remember { carIcon() },
                    )
                }
                if (userLatLng != null) {
                    Marker(
                        state = remember(userLatLng) { MarkerState(userLatLng) },
                        title = "Tú",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SmallOutline("📍 Mi posición", Modifier.weight(1f)) {
                val f = vm.userFix
                if (f != null) vm.focusRequest = LatLng(f.lat, f.lng)
                vm.locateUser(true)
            }
            SmallOutline("🚗 Coche", Modifier.weight(1f)) {
                val c = vm.parked
                if (c != null) vm.focusRequest = LatLng(c.lat, c.lng) else vm.showBanner("Aún no hay coche aparcado")
            }
            SmallOutline("📌 Aparcar centro", Modifier.weight(1f)) {
                val t = camera.position.target
                vm.savePark(t.latitude, t.longitude, null, "mapa-centro")
            }
        }
    }
}

// =====================================================================================
// TAB 3: HISTORIAL (scroll interno)
// =====================================================================================
@Composable
private fun HistoryTab(vm: WimcViewModel) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            T("Historial (${vm.history.size}/50)", 13, bold = true)
            OutlinedButton(
                onClick = { if (vm.history.isNotEmpty()) confirm = true },
                modifier = Modifier.height(34.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                T("🗑️ Borrar historial", 11, color = Red)
            }
        }
        Spacer(Modifier.height(6.dp))
        if (vm.history.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                T("Sin historial", 12, color = Grey)
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(vm.history) { h ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                T(fmtDate(h.ts), 12, bold = true)
                                T("${fmtCoord(h.lat)}, ${fmtCoord(h.lng)}", 11, color = Grey)
                            }
                            Button(
                                onClick = { vm.usePoint(h) },
                                modifier = Modifier.height(34.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Blue),
                            ) {
                                T("USAR", 11, bold = true, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { T("¿Borrar historial?", 16, bold = true) },
            text = { T("Se borrarán todos los aparcamientos guardados.", 13) },
            confirmButton = { TextButton(onClick = { vm.clearHistory(); confirm = false }) { T("Borrar", 13, bold = true, color = Red) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { T("Cancelar", 13) } },
        )
    }
}

// =====================================================================================
// TAB 4: AJUSTES
// =====================================================================================
@Composable
private fun SettingsTab(vm: WimcViewModel, onToggleDetect: (Boolean) -> Unit, onToggleAuto: (Boolean) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CardBox {
            T("📡 Ubicación", 13, bold = true)
            T(
                "Si ubicación no funciona: Activa GPS y WiFi, sal al exterior, da permiso Ubicación Precisa en Android. " +
                    "Si sigue sin ir, el mapa SIEMPRE permite aparcar tocando.", 11,
            )
            T("Permiso actual: ${vm.permState}", 11, color = Grey)
        }
        CardBox {
            T("🚗 Detección de coche", 13, bold = true)
            SwitchRow("Detección automática", "Avisa cuando dejas de conducir", vm.autoDetect, onToggleDetect)
            SwitchRow("Auto-aparcar", "Guarda solo, sin preguntar (necesita ubicación «Permitir siempre»)", vm.autoPark, onToggleAuto)
            OutlinedButton(
                onClick = { vm.simulateStop() },
                modifier = Modifier.fillMaxWidth().height(38.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                T("🚘 Simular parada de coche", 12, color = Navy)
            }
        }
        CardBox {
            T("💾 Almacenamiento", 13, bold = true)
            T("Los datos se guardan solo en este dispositivo.", 11)
            T("Historial: ${vm.history.size}/50 aparcamientos", 11, color = Grey)
        }
        T("WIMC 4.2 Final DV", 11, color = Grey, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            T(title, 12, bold = true)
            T(sub, 10, color = Grey)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// =====================================================================================
// DIÁLOGOS
// =====================================================================================
@Composable
private fun OptionButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(40.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
        T(text, 12, color = Navy, maxLines = 1)
    }
}

@Composable
private fun Dialogs(vm: WimcViewModel, onPark: () -> Unit) {
    // Toque en el mapa -> confirmar
    vm.pendingTap?.let { ll ->
        AlertDialog(
            onDismissRequest = { vm.pendingTap = null },
            title = { T("¿Aparcar aquí?", 16, bold = true) },
            text = { T("${coordStr(ll.latitude)}, ${coordStr(ll.longitude)}", 13) },
            confirmButton = {
                TextButton(onClick = {
                    vm.savePark(ll.latitude, ll.longitude, null, "mapa")
                    vm.pendingTap = null
                }) { T("Aparcar", 13, bold = true, color = Blue) }
            },
            dismissButton = { TextButton(onClick = { vm.pendingTap = null }) { T("Cancelar", 13) } },
        )
    }

    // GPS falló -> el usuario decide (nunca Sol en silencio)
    if (vm.showGpsDialog) {
        AlertDialog(
            onDismissRequest = { vm.showGpsDialog = false },
            title = { T("GPS falló", 16, bold = true) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    T("No se pudo obtener ubicación precisa", 13)
                    OptionButton("Reintentar GPS") { vm.showGpsDialog = false; onPark() }
                    OptionButton("Aparcar en centro del mapa") { vm.showGpsDialog = false; vm.parkAtMapCenterOrOpenMap() }
                    OptionButton("Introducir coordenadas manual") { vm.showGpsDialog = false; vm.showManualDialog = true }
                    OptionButton("Aparcar en Madrid Sol (último recurso)") { vm.showGpsDialog = false; vm.parkAtMadridSol() }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { vm.showGpsDialog = false }) { T("Cancelar", 13) } },
        )
    }

    if (vm.showManualDialog) ManualDialog(vm)

    // "Parece que has parado"
    if (vm.showStopDialog) {
        AlertDialog(
            onDismissRequest = { vm.showStopDialog = false },
            title = { T("Parece que has parado", 16, bold = true) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    T("¿Aparcar aquí?", 13)
                    OptionButton("Aparcar ahora") { vm.showStopDialog = false; onPark() }
                    OptionButton("Auto-aparcar siempre") { vm.showStopDialog = false; vm.setAutoPark(true); onPark() }
                    OptionButton("Ignorar") { vm.showStopDialog = false }
                }
            },
            confirmButton = {},
        )
    }
}

@Composable
private fun ManualDialog(vm: WimcViewModel) {
    var lat by remember { mutableStateOf("") }
    var lng by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { vm.showManualDialog = false },
        title = { T("Coordenadas manuales", 16, bold = true) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = lat, onValueChange = { lat = it }, singleLine = true,
                    label = { T("Latitud (ej. 40.41650)", 11) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
                OutlinedTextField(
                    value = lng, onValueChange = { lng = it }, singleLine = true,
                    label = { T("Longitud (ej. -3.70256)", 11) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
                err?.let { T(it, 11, color = Red) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val la = lat.trim().replace(',', '.').toDoubleOrNull()
                val lo = lng.trim().replace(',', '.').toDoubleOrNull()
                if (la == null || lo == null || la !in -90.0..90.0 || lo !in -180.0..180.0) {
                    err = "Coordenadas no válidas"
                } else {
                    vm.parkManual(la, lo)
                    vm.showManualDialog = false
                }
            }) { T("Aparcar", 13, bold = true, color = Blue) }
        },
        dismissButton = { TextButton(onClick = { vm.showManualDialog = false }) { T("Cancelar", 13) } },
    )
}
