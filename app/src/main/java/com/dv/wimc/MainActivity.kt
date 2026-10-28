package com.dv.wimc

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.NotificationManagerCompat

class MainActivity : ComponentActivity() {
    private val vm: WimcViewModel by viewModels()

    private var afterLocationPerm: (() -> Unit)? = null

    private val locationPerm = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        vm.reload()
        val fine = res[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = res[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!fine && coarse) {
            vm.showBanner("Ubicación aproximada: activa «Precisa» en Ajustes > Ubicación > WIMC > Precisa")
        }
        val cb = afterLocationPerm
        afterLocationPerm = null
        cb?.invoke()
    }

    private var wantAutoPark = false

    private val detectPerm = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        finishEnableDetection()
    }

    private val bgLocationPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.reload()
        if (!granted) {
            vm.showBanner("Para auto-aparcar en segundo plano elige «Permitir siempre» en Ajustes > Ubicación > WIMC")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(0xFF1E3A6B.toInt()),
            navigationBarStyle = SystemBarStyle.light(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
        )
        setContent {
            WimcApp(
                vm = vm,
                onPark = { if (vm.isParking) vm.doPark() else ensureLocation(false) { vm.doPark() } },
                onPrecise = { ensureLocation(true) { vm.locateUser(false) } },
                onToggleDetect = { on -> if (on) enableDetection(false) else disableDetection() },
                onToggleAuto = { on -> if (on) enableDetection(true) else vm.setAutoPark(false) },
            )
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.reload()
        vm.refreshQuiet()
    }

    private fun handleIntent(i: Intent?) {
        when (i?.action) {
            DrivingDetection.ACTION_PARK_NOW -> {
                NotificationManagerCompat.from(this).cancel(DrivingDetection.NOTIF_ID)
                ensureLocation(false) { vm.doPark() }
            }
            DrivingDetection.ACTION_AUTO_ALWAYS -> {
                NotificationManagerCompat.from(this).cancel(DrivingDetection.NOTIF_ID)
                vm.setAutoPark(true)
                ensureLocation(false) { vm.doPark() }
            }
        }
    }

    /** Pide permiso de ubicación si falta; ejecuta [then] tanto si se concede como si no (APARCAR siempre responde). */
    private fun ensureLocation(requireFine: Boolean, then: () -> Unit) {
        val ok = if (requireFine) vm.hasFine() else vm.hasAnyLocation()
        if (ok) {
            then()
        } else {
            afterLocationPerm = then
            locationPerm.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    private fun enableDetection(withAutoPark: Boolean) {
        wantAutoPark = withAutoPark
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 29 && !DrivingDetection.hasPerm(this, Manifest.permission.ACTIVITY_RECOGNITION)) {
            needed += Manifest.permission.ACTIVITY_RECOGNITION
        }
        if (Build.VERSION.SDK_INT >= 33 && !DrivingDetection.hasPerm(this, Manifest.permission.POST_NOTIFICATIONS)) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        if (withAutoPark && !vm.hasFine()) needed += Manifest.permission.ACCESS_FINE_LOCATION
        if (needed.isEmpty()) finishEnableDetection() else detectPerm.launch(needed.toTypedArray())
    }

    private fun finishEnableDetection() {
        vm.reload()
        val recOk = Build.VERSION.SDK_INT < 29 ||
            DrivingDetection.hasPerm(this, Manifest.permission.ACTIVITY_RECOGNITION)
        if (!recOk) {
            vm.showBanner("Sin permiso de actividad física no se puede detectar el coche")
            return
        }
        vm.setAutoDetect(true)
        DrivingDetection.register(this)
        if (wantAutoPark) {
            vm.setAutoPark(true)
            if (Build.VERSION.SDK_INT >= 29 && vm.hasFine() &&
                !DrivingDetection.hasPerm(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            ) {
                bgLocationPerm.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
        wantAutoPark = false
    }

    private fun disableDetection() {
        vm.setAutoDetect(false)
        DrivingDetection.unregister(this)
    }
}
