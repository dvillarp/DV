@echo off
setlocal
echo === DV WIMC 4.2 FINAL - GENERADOR TOTAL Y FIX GRADLE ===
cd /d C:\dv\Claude

echo [1/6] Escribiendo pubspec.yaml limpio y compatible...
powershell -Command "$c = @'
name: dv
description: WIMC - Where Is My Car - DV
publish_to: 'none'
version: 4.2.0+42

environment:
  sdk: '>=3.2.0 <4.0.0'

dependencies:
  flutter:
    sdk: flutter
  geolocator: ^11.0.0
  shared_preferences: ^2.2.3
  url_launcher: ^6.2.6
  share_plus: ^9.0.0
  flutter_map: ^6.1.0
  latlong2: ^0.9.1
  intl: ^0.19.0

flutter:
  uses-material-design: true
'@; [IO.File]::WriteAllText('pubspec.yaml', $c)"

echo [2/6] Escribiendo lib\main.dart con las 4 TABS y logica critica WIMC 4.2...
if not exist lib mkdir lib
powershell -Command "$c = @'
import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:geolocator/geolocator.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:share_plus/share_plus.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import 'package:intl/intl.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);
  runApp(const WimcDvApp());
}

class WimcDvApp extends StatelessWidget {
  const WimcDvApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'WIMC DV',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        scaffoldBackgroundColor: const Color(0xFFF5F7FB),
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF1446FF)),
      ),
      home: const WimcMainScreen(),
    );
  }
}

class ParkSpot {
  final double lat;
  final double lng;
  final int ts;
  final double acc;

  ParkSpot({required this.lat, required this.lng, required this.ts, required this.acc});

  Map<String, dynamic> toJson() => {'lat': lat, 'lng': lng, 'ts': ts, 'acc': acc};

  factory ParkSpot.fromJson(Map<String, dynamic> j) => ParkSpot(
        lat: (j['lat'] as num).toDouble(),
        lng: (j['lng'] as num).toDouble(),
        ts: j['ts'] as int,
        acc: (j['acc'] as num).toDouble(),
      );
}

class WimcMainScreen extends StatefulWidget {
  const WimcMainScreen({super.key});

  @override
  State<WimcMainScreen> createState() => _WimcMainScreenState();
}

class _WimcMainScreenState extends State<WimcMainScreen> {
  static const Color colHeader = Color(0xFF1E3A6B);
  static const Color colPrimary = Color(0xFF1446FF);
  static const Color colRed = Color(0xFFE30613);
  static const Color colGreen = Color(0xFF16A34A);
  static const Color colBg = Color(0xFFF5F7FB);

  int _currentTab = 0;
  ParkSpot? _parked;
  List<ParkSpot> _history = [];
  bool _isParking = false;
  String? _bannerText;
  double? _lastAcc;
  LatLng _mapCenter = const LatLng(40.4168, -3.7038);
  LatLng? _userPos;
  bool _autoDetect = false;
  final MapController _mapController = MapController();
  Timer? _fallbackTimer;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    final prefs = await SharedPreferences.getInstance();
    final pStr = prefs.getString('wimc_parked');
    final hStr = prefs.getString('wimc_history');
    final auto = prefs.getBool('wimc_auto') ?? false;

    ParkSpot? loadedSpot;
    if (pStr != null) {
      try {
        loadedSpot = ParkSpot.fromJson(jsonDecode(pStr));
      } catch (_) {}
    }

    List<ParkSpot> loadedHist = [];
    if (hStr != null) {
      try {
        final List arr = jsonDecode(hStr);
        loadedHist = arr.map((e) => ParkSpot.fromJson(e)).toList();
      } catch (_) {}
    }

    setState(() {
      _parked = loadedSpot;
      _history = loadedHist;
      _autoDetect = auto;
      if (loadedSpot != null) {
        _mapCenter = LatLng(loadedSpot.lat, loadedSpot.lng);
        _lastAcc = loadedSpot.acc;
      }
    });
  }

  void _showBanner(String msg) {
    setState(() => _bannerText = msg);
  }

  Future<void> _savePark(double lat, double lng, double accuracy, {String? customBanner}) async {
    final spot = ParkSpot(
      lat: lat,
      lng: lng,
      ts: DateTime.now().millisecondsSinceEpoch,
      acc: accuracy,
    );
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('wimc_parked', jsonEncode(spot.toJson()));

    final newHist = [spot, ..._history];
    if (newHist.length > 50) newHist.removeRange(50, newHist.length);
    await prefs.setString('wimc_history', jsonEncode(newHist.map((e) => e.toJson()).toList()));

    HapticFeedback.mediumImpact();

    setState(() {
      _parked = spot;
      _history = newHist;
      _lastAcc = accuracy;
      _mapCenter = LatLng(lat, lng);
      _isParking = false;
      _bannerText = customBanner ?? '✅ Coche guardado (${lat.toStringAsFixed(5)}, ${lng.toStringAsFixed(5)})';
    });
  }

  Future<void> _doPark() async {
    // Segundo click mientras isParking = true -> fuerza guardado instantaneo con centro del mapa
    if (_isParking) {
      _fallbackTimer?.cancel();
      await _savePark(_mapCenter.latitude, _mapCenter.longitude, 50.0,
          customBanner: '✅ Guardado instantáneo en centro del mapa');
      return;
    }

    setState(() {
      _isParking = true;
      _bannerText = '⏳ Guardando...';
    });

    bool finished = false;
    _fallbackTimer?.cancel();
    _fallbackTimer = Timer(const Duration(milliseconds: 1500), () {
      if (!finished) {
        finished = true;
        _savePark(_mapCenter.latitude, _mapCenter.longitude, 99.0,
            customBanner: '⚠️ GPS lento - usando centro del mapa');
      }
    });

    try {
      bool serviceEnabled = await Geolocator.isLocationServiceEnabled();
      if (!serviceEnabled) {
        if (!finished) {
          finished = true;
          _fallbackTimer?.cancel();
          setState(() => _isParking = false);
          _showGpsFailDialog();
        }
        return;
      }

      LocationPermission perm = await Geolocator.checkPermission();
      if (perm == LocationPermission.denied) {
        perm = await Geolocator.requestPermission();
      }
      if (perm == LocationPermission.denied || perm == LocationPermission.deniedForever) {
        if (!finished) {
          finished = true;
          _fallbackTimer?.cancel();
          setState(() => _isParking = false);
          _showGpsFailDialog();
        }
        return;
      }

      final pos = await Geolocator.getCurrentPosition(
        desiredAccuracy: LocationAccuracy.high,
        timeLimit: const Duration(milliseconds: 1400),
      );

      if (!finished) {
        finished = true;
        _fallbackTimer?.cancel();
        _userPos = LatLng(pos.latitude, pos.longitude);
        if (pos.accuracy < 100.0) {
          await _savePark(pos.latitude, pos.longitude, pos.accuracy,
              customBanner: '✅ Ubicación precisa: ${pos.accuracy.toInt()}m');
        } else {
          await _savePark(_mapCenter.latitude, _mapCenter.longitude, pos.accuracy,
              customBanner: '⚠️ Baja precisión GPS - guardado centro mapa');
        }
      }
    } catch (_) {
      if (!finished) {
        finished = true;
        _fallbackTimer?.cancel();
        setState(() => _isParking = false);
        _showGpsFailDialog();
      }
    }
  }

  void _showGpsFailDialog() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('GPS falló', style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text('No se pudo obtener ubicación precisa:', style: TextStyle(fontSize: 12)),
            const SizedBox(height: 10),
            ElevatedButton(
              onPressed: () {
                Navigator.pop(ctx);
                _doPark();
              },
              child: const Text('Reintentar GPS', style: TextStyle(fontSize: 12)),
            ),
            ElevatedButton(
              onPressed: () {
                Navigator.pop(ctx);
                _savePark(_mapCenter.latitude, _mapCenter.longitude, 50.0,
                    customBanner: '✅ Guardado en centro del mapa');
              },
              child: const Text('Aparcar en centro del mapa', style: TextStyle(fontSize: 12)),
            ),
            ElevatedButton(
              onPressed: () {
                Navigator.pop(ctx);
                _showManualCoordsDialog();
              },
              child: const Text('Introducir coordenadas manual', style: TextStyle(fontSize: 12)),
            ),
            OutlinedButton(
              onPressed: () {
                Navigator.pop(ctx);
                _savePark(40.4165, -3.70256, 100.0,
                    customBanner: '✅ Guardado en Madrid Sol (último recurso)');
              },
              child: const Text('Aparcar en Madrid Sol (último recurso)', style: TextStyle(fontSize: 11)),
            ),
          ],
        ),
      ),
    );
  }

  void _showManualCoordsDialog() {
    final latCtrl = TextEditingController(text: _mapCenter.latitude.toStringAsFixed(5));
    final lngCtrl = TextEditingController(text: _mapCenter.longitude.toStringAsFixed(5));
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Coordenadas manuales', style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(controller: latCtrl, decoration: const InputDecoration(labelText: 'Latitud'), keyboardType: TextInputType.number),
            TextField(controller: lngCtrl, decoration: const InputDecoration(labelText: 'Longitud'), keyboardType: TextInputType.number),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancelar')),
          ElevatedButton(
            onPressed: () {
              final lat = double.tryParse(latCtrl.text.replaceAll(',', '.')) ?? 40.4168;
              final lng = double.tryParse(lngCtrl.text.replaceAll(',', '.')) ?? -3.7038;
              Navigator.pop(ctx);
              _savePark(lat, lng, 10.0);
            },
            child: const Text('Guardar'),
          ),
        ],
      ),
    );
  }

  Future<void> _unpark() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('wimc_parked');
    setState(() {
      _parked = null;
      _bannerText = '🏁 Des-aparcado';
    });
  }

  Future<void> _openDirections() async {
    if (_parked == null) {
      _showBanner('⚠️ Primero aparca el coche');
      return;
    }
    final uri = Uri.parse('https://www.google.com/maps/dir/?api=1&destination=${_parked!.lat},${_parked!.lng}');
    await launchUrl(uri, mode: LaunchMode.externalApplication);
  }

  Future<void> _shareLocation() async {
    if (_parked == null) {
      _showBanner('⚠️ Primero aparca el coche');
      return;
    }
    await Share.share('Mi coche: https://www.google.com/maps?q=${_parked!.lat},${_parked!.lng}');
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: colBg,
      body: SafeArea(
        child: Column(
          children: [
            // HEADER 52px
            Container(
              height: 52,
              padding: const EdgeInsets.symmetric(horizontal: 12),
              color: colHeader,
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: [
                      _buildMiniLogo(),
                      const SizedBox(width: 8),
                      const Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('WIMC', style: TextStyle(color: Colors.white, fontSize: 14, fontWeight: FontWeight.w900)),
                          Text('Where Is My Car · DV', style: TextStyle(color: Color(0xFFCBD5E1), fontSize: 10)),
                        ],
                      ),
                    ],
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: _parked != null ? colGreen : colRed,
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Text(
                      _parked != null ? 'APARCADO' : 'NO APARCADO',
                      style: const TextStyle(color: Colors.white, fontSize: 10, fontWeight: FontWeight.bold),
                    ),
                  ),
                ],
              ),
            ),

            // BANNER FEEDBACK
            if (_bannerText != null)
              Container(
                width: double.infinity,
                color: const Color(0xFFDBEAFE),
                padding: const EdgeInsets.symmetric(vertical: 4, horizontal: 8),
                child: Text(
                  _bannerText!,
                  textAlign: TextAlign.center,
                  style: const TextStyle(fontSize: 11, color: colHeader, fontWeight: FontWeight.w600),
                ),
              ),

            // BODY CONTENT
            Expanded(
              child: IndexedStack(
                index: _currentTab,
                children: [
                  _buildTabInicio(),
                  _buildTabMapa(),
                  _buildTabHistorial(),
                  _buildTabAjustes(),
                ],
              ),
            ),

            // BOTTOM NAV 48px
            Container(
              height: 48,
              decoration: const BoxDecoration(
                color: Colors.white,
                border: Border(top: BorderSide(color: Color(0xFFE2E8F0))),
              ),
              child: Row(
                children: [
                  _buildNavItem(0, '🏠 Inicio'),
                  _buildNavItem(1, '🗺️ Mapa'),
                  _buildNavItem(2, '🕒 Historial'),
                  _buildNavItem(3, '⚙️ Ajustes'),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildMiniLogo() {
    return Container(
      width: 34,
      height: 36,
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(5),
      ),
      child: Stack(
        children: [
          Positioned(
            top: 0,
            right: 0,
            child: Container(
              width: 8,
              height: 8,
              color: const Color(0xFFCBD5E1),
            ),
          ),
          Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const SizedBox(height: 2),
              const Text('WIMC', style: TextStyle(color: Color(0xFF1C5DDB), fontSize: 8, fontWeight: FontWeight.w900)),
              const SizedBox(height: 1),
              Container(
                width: double.infinity,
                color: colRed,
                padding: const EdgeInsets.symmetric(vertical: 1),
                child: const Text('DV', textAlign: TextAlign.center, style: TextStyle(color: Colors.white, fontSize: 8, fontWeight: FontWeight.w900)),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildNavItem(int idx, String label) {
    final selected = _currentTab == idx;
    return Expanded(
      child: InkWell(
        onTap: () => setState(() => _currentTab = idx),
        child: Center(
          child: Text(
            label,
            style: TextStyle(
              fontSize: 11,
              fontWeight: selected ? FontWeight.bold : FontWeight.normal,
              color: selected ? colPrimary : Colors.grey[700],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildCard({required Widget child}) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: const [BoxShadow(color: Color(0x1A000000), blurRadius: 3, offset: Offset(0, 1))],
      ),
      child: child,
    );
  }

  // TAB 1: INICIO (SIN SCROLL)
  Widget _buildTabInicio() {
    final mins = _parked != null
        ? ((DateTime.now().millisecondsSinceEpoch - _parked!.ts) ~/ 60000)
        : 0;
    final accVal = _lastAcc?.toInt();
    final accLabel = accVal == null ? 'Media' : (accVal < 25 ? 'Excelente' : 'Media');

    return Padding(
      padding: const EdgeInsets.all(10),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          // Card 1: Estado
          _buildCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  _parked != null ? 'Aparcado hace ${mins}min' : 'Pulsa APARCAR para guardar',
                  style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 2),
                Text(
                  'Precisión: ${accVal ?? "--"}m - $accLabel',
                  style: const TextStyle(fontSize: 11, color: Colors.black54),
                ),
                if (_parked != null) ...[
                  const SizedBox(height: 6),
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: const Color(0xFFF0F7FF),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          '📍 ${_parked!.lat.toStringAsFixed(5)}, ${_parked!.lng.toStringAsFixed(5)}',
                          style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: colHeader),
                        ),
                        Text(
                          '🕒 ${DateFormat('dd/MM/yyyy HH:mm').format(DateTime.fromMillisecondsSinceEpoch(_parked!.ts))} · ±${_parked!.acc.toInt()}m',
                          style: const TextStyle(fontSize: 11, color: Colors.black54),
                        ),
                      ],
                    ),
                  ),
                ],
              ],
            ),
          ),

          // Card 2: Acciones principales
          _buildCard(
            child: Column(
              children: [
                SizedBox(
                  width: double.infinity,
                  height: 56,
                  child: ElevatedButton(
                    onPressed: _doPark,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: colPrimary,
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                    ),
                    child: Text(
                      _isParking ? '⏳ Guardando...' : '📍 APARCAR AHORA',
                      style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold),
                    ),
                  ),
                ),
                if (_parked != null) ...[
                  const SizedBox(height: 6),
                  SizedBox(
                    width: double.infinity,
                    height: 48,
                    child: ElevatedButton(
                      onPressed: _unpark,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: colRed,
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                      ),
                      child: const Text(
                        '🏁 DESAPARCAR',
                        style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold),
                      ),
                    ),
                  ),
                ],
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(
                      child: SizedBox(
                        height: 34,
                        child: OutlinedButton(
                          onPressed: _doPark,
                          style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 4)),
                          child: const Text('📡 Ubicación precisa', style: TextStyle(fontSize: 11)),
                        ),
                      ),
                    ),
                    const SizedBox(width: 6),
                    Expanded(
                      child: SizedBox(
                        height: 34,
                        child: OutlinedButton(
                          onPressed: () => setState(() => _currentTab = 1),
                          style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 4)),
                          child: const Text('👆 Tocar mapa para aparcar', style: TextStyle(fontSize: 11)),
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                const Text(
                  'Si el GPS falla, toca el mapa para aparcar manual',
                  style: TextStyle(fontSize: 10, color: Colors.grey),
                ),
              ],
            ),
          ),

          // Card 3: Acciones secundarias
          _buildCard(
            child: Row(
              children: [
                Expanded(
                  child: SizedBox(
                    height: 36,
                    child: OutlinedButton(
                      onPressed: () {
                        setState(() => _currentTab = 1);
                        if (_parked != null) {
                          _mapController.move(LatLng(_parked!.lat, _parked!.lng), 17);
                        }
                      },
                      style: OutlinedButton.styleFrom(padding: EdgeInsets.zero),
                      child: const Text('🗺️ Ver en mapa', style: TextStyle(fontSize: 11)),
                    ),
                  ),
                ),
                const SizedBox(width: 6),
                Expanded(
                  child: SizedBox(
                    height: 36,
                    child: OutlinedButton(
                      onPressed: _openDirections,
                      style: OutlinedButton.styleFrom(padding: EdgeInsets.zero),
                      child: const Text('🧭 Cómo llegar', style: TextStyle(fontSize: 11)),
                    ),
                  ),
                ),
                const SizedBox(width: 6),
                Expanded(
                  child: SizedBox(
                    height: 36,
                    child: OutlinedButton(
                      onPressed: _shareLocation,
                      style: OutlinedButton.styleFrom(padding: EdgeInsets.zero),
                      child: const Text('📤 Compartir', style: TextStyle(fontSize: 11)),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // TAB 2: MAPA (CON BOTON VOLVER A INICIO Y GOOGLE MAPS TILES)
  Widget _buildTabMapa() {
    return Column(
      children: [
        Container(
          color: Colors.white,
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
          child: Row(
            children: [
              SizedBox(
                height: 30,
                child: OutlinedButton(
                  onPressed: () => setState(() => _currentTab = 0),
                  style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 8)),
                  child: const Text('< Volver a Inicio', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold)),
                ),
              ),
              const SizedBox(width: 8),
              const Expanded(
                child: Text(
                  '👆 Toca en cualquier punto del mapa para aparcar allí (funciona sin GPS)',
                  style: TextStyle(fontSize: 10, color: Colors.black87),
                ),
              ),
            ],
          ),
        ),
        Expanded(
          child: FlutterMap(
            mapController: _mapController,
            options: MapOptions(
              initialCenter: _mapCenter,
              initialZoom: 16.0,
              onPositionChanged: (pos, _) {
                if (pos.center != null) {
                  _mapCenter = pos.center!;
                }
              },
              onTap: (tapPos, latlng) {
                showDialog(
                  context: context,
                  builder: (ctx) => AlertDialog(
                    title: const Text('¿Aparcar aquí?', style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold)),
                    content: Text('${latlng.latitude.toStringAsFixed(5)}, ${latlng.longitude.toStringAsFixed(5)}',
                        style: const TextStyle(fontSize: 12)),
                    actions: [
                      TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancelar')),
                      ElevatedButton(
                        onPressed: () {
                          Navigator.pop(ctx);
                          _savePark(latlng.latitude, latlng.longitude, 10.0);
                        },
                        child: const Text('Aparcar aquí'),
                      ),
                    ],
                  ),
                );
              },
            ),
            children: [
              TileLayer(
                urlTemplate: 'https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}',
                userAgentPackageName: 'com.dv.wimc',
              ),
              MarkerLayer(
                markers: [
                  if (_parked != null)
                    Marker(
                      point: LatLng(_parked!.lat, _parked!.lng),
                      width: 48,
                      height: 48,
                      child: const Icon(Icons.directions_car, color: colRed, size: 36),
                    ),
                  if (_userPos != null)
                    Marker(
                      point: _userPos!,
                      width: 40,
                      height: 40,
                      child: const Icon(Icons.my_location, color: colPrimary, size: 28),
                    ),
                ],
              ),
            ],
          ),
        ),
        Container(
          color: Colors.white,
          padding: const EdgeInsets.all(6),
          child: Row(
            children: [
              Expanded(
                child: SizedBox(
                  height: 36,
                  child: OutlinedButton(
                    onPressed: () async {
                      try {
                        final pos = await Geolocator.getCurrentPosition();
                        _userPos = LatLng(pos.latitude, pos.longitude);
                        _mapController.move(_userPos!, 17);
                        setState(() {});
                      } catch (_) {
                        _showBanner('⚠️ No se pudo obtener tu posición GPS');
                      }
                    },
                    style: OutlinedButton.styleFrom(padding: EdgeInsets.zero),
                    child: const Text('📍 Mi posición', style: TextStyle(fontSize: 11)),
                  ),
                ),
              ),
              const SizedBox(width: 6),
              Expanded(
                child: SizedBox(
                  height: 36,
                  child: OutlinedButton(
                    onPressed: () {
                      if (_parked != null) {
                        _mapController.move(LatLng(_parked!.lat, _parked!.lng), 17);
                      } else {
                        _showBanner('⚠️ No hay coche aparcado');
                      }
                    },
                    style: OutlinedButton.styleFrom(padding: EdgeInsets.zero),
                    child: const Text('🚗 Coche', style: TextStyle(fontSize: 11)),
                  ),
                ),
              ),
              const SizedBox(width: 6),
              Expanded(
                child: SizedBox(
                  height: 36,
                  child: ElevatedButton(
                    onPressed: () => _savePark(_mapCenter.latitude, _mapCenter.longitude, 15.0),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: colPrimary,
                      foregroundColor: Colors.white,
                      padding: EdgeInsets.zero,
                    ),
                    child: const Text('📌 Aparcar centro', style: TextStyle(fontSize: 11)),
                  ),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  // TAB 3: HISTORIAL (CON SCROLL INTERNO)
  Widget _buildTabHistorial() {
    return Padding(
      padding: const EdgeInsets.all(10),
      child: Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text('Historial guardado', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold)),
              SizedBox(
                height: 32,
                child: OutlinedButton(
                  onPressed: () async {
                    final prefs = await SharedPreferences.getInstance();
                    await prefs.remove('wimc_history');
                    setState(() => _history = []);
                  },
                  child: const Text('🗑️ Borrar historial', style: TextStyle(fontSize: 11, color: colRed)),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Expanded(
            child: _history.isEmpty
                ? const Center(child: Text('Sin historial', style: TextStyle(fontSize: 12, color: Colors.grey)))
                : ListView.separated(
                    itemCount: _history.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 6),
                    itemBuilder: (ctx, i) {
                      final item = _history[i];
                      final dt = DateFormat('dd/MM/yyyy HH:mm').format(DateTime.fromMillisecondsSinceEpoch(item.ts));
                      return _buildCard(
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(dt, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold)),
                                Text('${item.lat.toStringAsFixed(5)}, ${item.lng.toStringAsFixed(5)}',
                                    style: const TextStyle(fontSize: 11, color: Colors.black54)),
                              ],
                            ),
                            SizedBox(
                              height: 30,
                              child: ElevatedButton(
                                onPressed: () {
                                  _savePark(item.lat, item.lng, item.acc);
                                  setState(() => _currentTab = 1);
                                  _mapController.move(LatLng(item.lat, item.lng), 17);
                                },
                                style: ElevatedButton.styleFrom(
                                  backgroundColor: colPrimary,
                                  foregroundColor: Colors.white,
                                ),
                                child: const Text('USAR', style: TextStyle(fontSize: 11)),
                              ),
                            ),
                          ],
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }

  // TAB 4: AJUSTES
  Widget _buildTabAjustes() {
    return Padding(
      padding: const EdgeInsets.all(10),
      child: Column(
        children: [
          _buildCard(
            child: const Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Ayuda Ubicación GPS', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                SizedBox(height: 4),
                Text(
                  'Si ubicación no funciona: Activa GPS y WiFi, sal al exterior, da permiso Ubicación Precisa en Android. Si sigue sin ir, el mapa SIEMPRE permite aparcar tocando.',
                  style: TextStyle(fontSize: 11, color: Colors.black87),
                ),
              ],
            ),
          ),
          const SizedBox(height: 8),
          _buildCard(
            child: Column(
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Text('Detección automática de coche', style: TextStyle(fontSize: 12)),
                    Switch(
                      value: _autoDetect,
                      onChanged: (v) async {
                        final prefs = await SharedPreferences.getInstance();
                        await prefs.setBool('wimc_auto', v);
                        setState(() => _autoDetect = v);
                      },
                    ),
                  ],
                ),
                SizedBox(
                  width: double.infinity,
                  height: 38,
                  child: ElevatedButton(
                    onPressed: () {
                      showDialog(
                        context: context,
                        builder: (ctx) => AlertDialog(
                          title: const Text('🚘 Parece que has parado. ¿Aparcar aquí?',
                              style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold)),
                          actions: [
                            TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Ignorar')),
                            ElevatedButton(
                              onPressed: () {
                                Navigator.pop(ctx);
                                _doPark();
                              },
                              child: const Text('Aparcar ahora'),
                            ),
                          ],
                        ),
                      );
                    },
                    style: ElevatedButton.styleFrom(backgroundColor: colHeader, foregroundColor: Colors.white),
                    child: const Text('🚘 Simular parada de coche', style: TextStyle(fontSize: 12)),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 8),
          _buildCard(
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text('Versión: WIMC 4.2 Final DV', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                Text('Historial: ${_history.length}/50', style: const TextStyle(fontSize: 11, color: Colors.grey)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
'@; [IO.File]::WriteAllText('lib\main.dart', $c)"

echo [3/6] Escribiendo .github\workflows\build.yml SIN FALLO GRADLE...
if not exist .github\workflows mkdir .github\workflows
powershell -Command "$c = @'
name: WIMC DV - APK Android
on:
  push:
    branches: [main]
  workflow_dispatch:

permissions:
  contents: write

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup Java 17
        uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: '17'

      - name: Setup Flutter
        uses: subosito/flutter-action@v2
        with:
          channel: stable
          cache: true

      - name: Recrear estructura Android limpia con permisos e icono DV
        run: |
          rm -rf android
          flutter create . --platforms=android --org=com.dv.wimc --project-name=dv

          # Inyectar permisos obligatorios en AndroidManifest.xml
          sed -i 's/<application/<uses-permission android:name="android.permission.INTERNET"\/><uses-permission android:name="android.permission.VIBRATE"\/><uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"\/><uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"\/><uses-permission android:name="android.permission.ACTIVITY_RECOGNITION"\/><application/g' android/app/src/main/AndroidManifest.xml
          sed -i 's/android:label="dv"/android:label="WIMC DV"/g' android/app/src/main/AndroidManifest.xml

          # Configurar compileSdk 35 y desactivar minify/shrink para evitar exit code 1 en Release
          if [ -f android/app/build.gradle.kts ]; then
            sed -i 's/compileSdk = flutter.compileSdkVersion/compileSdk = 35/g' android/app/build.gradle.kts
            sed -i 's/minSdk = flutter.minSdkVersion/minSdk = 24/g' android/app/build.gradle.kts
            sed -i 's/targetSdk = flutter.targetSdkVersion/targetSdk = 35/g' android/app/build.gradle.kts
          fi
          if [ -f android/app/build.gradle ]; then
            sed -i 's/compileSdk flutter.compileSdkVersion/compileSdk 35/g' android/app/build.gradle
            sed -i 's/compileSdkVersion flutter.compileSdkVersion/compileSdkVersion 35/g' android/app/build.gradle
            sed -i 's/minSdkVersion flutter.minSdkVersion/minSdkVersion 24/g' android/app/build.gradle
            sed -i 's/targetSdkVersion flutter.targetSdkVersion/targetSdkVersion 35/g' android/app/build.gradle
          fi

          # Crear icono adaptativo oficial WIMC DV (#1E3A6B + hoja + banda roja #E30613)
          mkdir -p android/app/src/main/res/drawable
          mkdir -p android/app/src/main/res/mipmap-anydpi-v26
          cat << 'EOF' > android/app/src/main/res/drawable/ic_launcher_background.xml
          <?xml version="1.0" encoding="utf-8"?>
          <vector xmlns:android="http://schemas.android.com/apk/res/android"
              android:width="108dp"
              android:height="108dp"
              android:viewportWidth="108"
              android:viewportHeight="108">
              <path android:fillColor="#1E3A6B" android:pathData="M0,0h108v108h-108z"/>
          </vector>
          EOF

          cat << 'EOF' > android/app/src/main/res/drawable/ic_launcher_foreground.xml
          <?xml version="1.0" encoding="utf-8"?>
          <vector xmlns:android="http://schemas.android.com/apk/res/android"
              android:width="108dp"
              android:height="108dp"
              android:viewportWidth="108"
              android:viewportHeight="108">
              <path android:fillColor="#FFFFFF" android:pathData="M30,22 L64,22 L78,36 L78,86 L30,86 Z"/>
              <path android:fillColor="#CBD5E1" android:pathData="M64,22 L64,36 L78,36 Z"/>
              <path android:fillColor="#CBD5E1" android:pathData="M38,76 L70,76 L70,79 L38,79 Z"/>
              <path android:fillColor="#E30613" android:pathData="M26,54 L82,54 L82,70 L26,70 Z"/>
              <path android:fillColor="#FFFFFF" android:pathData="M42,58 L48,58 C50,58 52,60 52,62 C52,64 50,66 48,66 L42,66 Z M45,60 L45,64 L48,64 C49,64 49.5,63 49.5,62 C49.5,61 49,60 48,60 Z M56,58 L59,58 L61,64 L63,58 L66,58 L62.5,66 L59.5,66 Z"/>
          </vector>
          EOF

          cat << 'EOF' > android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
          <?xml version="1.0" encoding="utf-8"?>
          <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
              <background android:drawable="@drawable/ic_launcher_background"/>
              <foreground android:drawable="@drawable/ic_launcher_foreground"/>
          </adaptive-icon>
          EOF

      - name: Instalar dependencias y Compilar APK
        run: |
          flutter pub get
          flutter build apk --release --no-shrink

      - name: Publicar APK WIMC DV en Releases
        uses: softprops/action-gh-release@v2
        with:
          tag_name: wimc-v${{ github.run_number }}
          name: WIMC DV 4.2 (Build ${{ github.run_number }}) - APK LISTO
          body: |
            App WIMC (Where Is My Car) - DV 4.2 Final lista para instalar en Android.
          files: |
            build/app/outputs/flutter-apk/app-release.apk
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
'@; [IO.File]::WriteAllText('.github\workflows\build.yml', $c)"

echo [4/6] Git add...
git add .

echo [5/6] Git commit...
git commit -m "WIMC DV 4.2 Final - Fix Gradle + 4 Tabs UI Completa"

echo [6/6] Subiendo a GitHub...
git push origin main --force

echo ========================================================
echo LISTO! Revisa el progreso y descarga el APK en:
echo https://github.com/dvillarp/DV/releases
echo https://github.com/dvillarp/DV/actions
echo ========================================================
pause