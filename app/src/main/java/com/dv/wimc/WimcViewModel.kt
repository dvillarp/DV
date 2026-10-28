package com.dv.wimc

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Location
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

const val MADRID_SOL_LAT = 40.4165
const val MADRID_SOL_LNG = -3.70256

class WimcViewModel(app: Application) : AndroidViewModel(app) {
    private val appCtx: Application = app
    private val store = Store(app)
    private val fused = LocationServices.getFusedLocationProviderClient(app)

    // ---- Estado de UI ----
    var tab by mutableIntStateOf(0)
    var parked by mutableStateOf(store.parked())
    var history by mutableStateOf(store.history())
    var userFix by mutableStateOf<ParkPoint?>(null)
    var mapCenter by mutableStateOf<LatLng?>(null)
    var focusRequest by mutableStateOf<LatLng?>(null)
    var isParking by mutableStateOf(false)
        private set
    var banner by mutableStateOf<String?>(null)
        private set
    var showGpsDialog by mutableStateOf(false)
    var showManualDialog by mutableStateOf(false)
    var showStopDialog by mutableStateOf(false)
    var pendingTap by mutableStateOf<LatLng?>(null)
    var autoDetect by mutableStateOf(store.autoDetect)
        private set
    var autoPark by mutableStateOf(store.autoPark)
        private set
    var permState by mutableStateOf(readPerm())
        private set

    // ---- Permisos ----
    fun hasFine() = DrivingDetection.hasPerm(appCtx, Manifest.permission.ACCESS_FINE_LOCATION)
    fun hasAnyLocation() = hasFine() || DrivingDetection.hasPerm(appCtx, Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun readPerm(): String = when {
        hasFine() -> "Precisa ✅"
        hasAnyLocation() -> "Aproximada ⚠️ (activa «Precisa»)"
        else -> "Denegada ❌"
    }

    fun reload() {
        parked = store.parked()
        history = store.history()
        autoDetect = store.autoDetect
        autoPark = store.autoPark
        permState = readPerm()
    }

    // ---- Banner ----
    private var bannerJob: Job? = null
    fun showBanner(msg: String, sticky: Boolean = false) {
        banner = msg
        bannerJob?.cancel()
        if (!sticky) {
            bannerJob = viewModelScope.launch {
                delay(3500)
                banner = null
            }
        }
    }

    // ---- APARCAR (debe funcionar SIEMPRE) ----
    private var parkSeq = 0
    private var parkTimeout: Job? = null
    private var cts: CancellationTokenSource? = null

    private fun finishParking() {
        parkSeq++
        isParking = false
        parkTimeout?.cancel()
        cts?.cancel()
        cts = null
    }

    @SuppressLint("MissingPermission")
    fun doPark() {
        // Segundo click mientras se está guardando -> fuerza guardado instantáneo con lo que haya
        if (isParking) {
            forceSave()
            return
        }
        if (!hasAnyLocation()) {
            showGpsDialog = true
            return
        }
        isParking = true
        val seq = ++parkSeq
        showBanner("Guardando...", sticky = true)
        val source = CancellationTokenSource()
        cts = source

        // Si el GPS no responde en 1500 ms -> centro del mapa (si el usuario lo ha colocado) o diálogo. NUNCA Sol en silencio.
        parkTimeout = viewModelScope.launch {
            delay(1500)
            if (seq == parkSeq && isParking) onTimeout()
        }
        try {
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, source.token)
                .addOnSuccessListener { loc -> if (seq == parkSeq && isParking) onFix(loc) }
                .addOnFailureListener { if (seq == parkSeq && isParking) onGpsFailed() }
        } catch (e: SecurityException) {
            onGpsFailed()
        }
    }

    private fun onTimeout() {
        finishParking()
        val c = mapCenter
        if (c != null) {
            savePark(c.latitude, c.longitude, null, "mapa-centro")
            showBanner("GPS lento - usando centro del mapa")
        } else {
            banner = null
            showGpsDialog = true
        }
    }

    private fun onFix(loc: Location?) {
        finishParking()
        if (loc == null) {
            banner = null
            showGpsDialog = true
            return
        }
        userFix = loc.toFix()
        val acc: Float? = if (loc.hasAccuracy()) loc.accuracy else null
        val approxNote = if (hasFine()) "" else " (aproximada: activa «Precisa»)"
        if (acc != null && acc < 100f) {
            savePark(loc.latitude, loc.longitude, acc, "gps")
            showBanner("✅ Ubicación precisa: ${acc.roundToInt()} m$approxNote")
        } else {
            val accTxt = acc?.let { "${it.roundToInt()} m" } ?: "sin dato"
            val c = mapCenter
            if (c != null) {
                savePark(c.latitude, c.longitude, null, "mapa-centro")
                showBanner("Precisión baja ($accTxt) - usando centro del mapa")
            } else {
                savePark(loc.latitude, loc.longitude, acc, "gps")
                showBanner("Precisión baja ($accTxt): revisa en el mapa$approxNote")
            }
        }
    }

    private fun onGpsFailed() {
        finishParking()
        banner = null
        showGpsDialog = true
    }

    private fun forceSave() {
        finishParking()
        val u = userFix
        val c = mapCenter
        when {
            u != null -> {
                savePark(u.lat, u.lng, u.acc, "gps-ultima")
                showBanner("Guardado con la última ubicación conocida")
            }
            c != null -> {
                savePark(c.latitude, c.longitude, null, "mapa-centro")
                showBanner("Guardado en el centro del mapa")
            }
            else -> {
                banner = null
                showGpsDialog = true
            }
        }
    }

    fun savePark(lat: Double, lng: Double, acc: Float?, source: String) {
        val p = ParkPoint(lat, lng, System.currentTimeMillis(), acc, source)
        store.savePark(p)
        parked = p
        history = store.history()
        mapCenter = null
        focusRequest = null
        showBanner("✅ Coche guardado")
        vibrate()
    }

    fun parkManual(lat: Double, lng: Double) = savePark(lat, lng, null, "manual")

    fun parkAtMadridSol() = savePark(MADRID_SOL_LAT, MADRID_SOL_LNG, null, "madrid-sol")

    fun parkAtMapCenterOrOpenMap() {
        val c = mapCenter
        if (c != null) {
            savePark(c.latitude, c.longitude, null, "mapa-centro")
        } else {
            tab = 1
            showBanner("Coloca el mapa donde quieras y pulsa «Aparcar centro», o toca el mapa")
        }
    }

    fun unpark() {
        store.setParked(null)
        parked = null
        showBanner("Des-aparcado")
    }

    fun usePoint(p: ParkPoint) {
        store.setParked(p)
        parked = p
        focusRequest = LatLng(p.lat, p.lng)
        tab = 1
    }

    fun clearHistory() {
        store.clearHistory()
        history = emptyList()
        showBanner("Historial borrado")
    }

    fun viewOnMap() {
        tab = 1
        val p = parked
        if (p != null) focusRequest = LatLng(p.lat, p.lng)
        else showBanner("Aún no hay coche aparcado: toca el mapa para aparcar")
    }

    // ---- Ubicación (sin aparcar) ----
    @SuppressLint("MissingPermission")
    fun locateUser(focus: Boolean) {
        if (!hasAnyLocation()) {
            showBanner("Sin permiso de ubicación (puedes aparcar tocando el mapa)")
            return
        }
        val src = CancellationTokenSource()
        try {
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, src.token)
                .addOnSuccessListener { loc ->
                    if (loc == null) {
                        showBanner("Sin posición GPS. Sal al exterior o toca el mapa")
                    } else {
                        userFix = loc.toFix()
                        val a = userFix?.acc
                        showBanner(if (a != null) "📡 Precisión: ${a.roundToInt()} m - ${accLabel(a)}" else "📡 Posición actualizada")
                        if (focus) focusRequest = LatLng(loc.latitude, loc.longitude)
                    }
                }
                .addOnFailureListener { showBanner("GPS no disponible. Toca el mapa para aparcar") }
        } catch (e: SecurityException) {
            showBanner("Sin permiso de ubicación")
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshQuiet() {
        if (!hasAnyLocation()) return
        try {
            fused.lastLocation.addOnSuccessListener { loc -> if (loc != null && userFix == null) userFix = loc.toFix() }
        } catch (e: SecurityException) {
        }
    }

    // ---- Ajustes ----
    fun setAutoDetect(on: Boolean) {
        store.autoDetect = on
        autoDetect = on
        if (!on) {
            store.autoPark = false
            autoPark = false
        }
    }

    fun setAutoPark(on: Boolean) {
        store.autoPark = on
        autoPark = on
        if (on) {
            store.autoDetect = true
            autoDetect = true
        }
    }

    fun simulateStop() {
        showStopDialog = true
        DrivingDetection.notifyStopped(appCtx)
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        runCatching {
            val v = appCtx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            v?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun Location.toFix() = ParkPoint(
        latitude, longitude, System.currentTimeMillis(), if (hasAccuracy()) accuracy else null, "gps"
    )
}
