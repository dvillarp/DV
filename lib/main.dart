import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:geocoding/geocoding.dart';
import 'package:intl/intl.dart';

void main() => runApp(WIMCApp());

class WIMCApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'WIMC DV',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        primarySwatch: Colors.blue,
        scaffoldBackgroundColor: Color(0xFFF5F7FB),
      ),
      home: WIMCHome(),
    );
  }
}

class WIMCHome extends StatefulWidget {
  @override
  State<WIMCHome> createState() => _WIMCHomeState();
}

class _WIMCHomeState extends State<WIMCHome> {
  double? lat;
  double? lng;
  String? address;
  DateTime? savedTime;
  bool loading = false;

  @override
  void initState() {
    super.initState();
    _loadSaved();
  }

  Future<void> _loadSaved() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      lat = prefs.getDouble('lat');
      lng = prefs.getDouble('lng');
      final t = prefs.getString('time');
      if (t != null) savedTime = DateTime.parse(t);
      address = prefs.getString('address');
    });
  }

  Future<void> _saveLocation() async {
    setState(() => loading = true);
    try {
      bool serviceEnabled = await Geolocator.isLocationServiceEnabled();
      if (!serviceEnabled) {
        _snack('Activa el GPS');
        setState(() => loading = false);
        return;
      }
      LocationPermission permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
      }
      if (permission == LocationPermission.denied || permission == LocationPermission.deniedForever) {
        _snack('Permiso de ubicación denegado');
        setState(() => loading = false);
        return;
      }

      Position pos = await Geolocator.getCurrentPosition(desiredAccuracy: LocationAccuracy.high);
      String addr = '';
      try {
        List<Placemark> placemarks = await placemarkFromCoordinates(pos.latitude, pos.longitude);
        if (placemarks.isNotEmpty) {
          final p = placemarks.first;
          addr = "${p.street}, ${p.locality}";
        }
      } catch (_) {}

      final prefs = await SharedPreferences.getInstance();
      await prefs.setDouble('lat', pos.latitude);
      await prefs.setDouble('lng', pos.longitude);
      await prefs.setString('time', DateTime.now().toIso8601String());
      await prefs.setString('address', addr);

      setState(() {
        lat = pos.latitude;
        lng = pos.longitude;
        address = addr;
        savedTime = DateTime.now();
        loading = false;
      });
      _snack('¡Coche guardado!');
    } catch (e) {
      setState(() => loading = false);
      _snack('Error: $e');
    }
  }

  void _snack(String m) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(m)));
  }

  Future<void> _openInMaps() async {
    if (lat == null || lng == null) return;
    final url = Uri.parse('https://www.google.com/maps/search/?api=1&query=$lat,$lng');
    if (await canLaunchUrl(url)) await launchUrl(url, mode: LaunchMode.externalApplication);
  }

  Future<void> _navigate() async {
    if (lat == null || lng == null) return;
    // Navegación directa
    final url = Uri.parse('https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=walking');
    if (await canLaunchUrl(url)) await launchUrl(url, mode: LaunchMode.externalApplication);
  }

  Future<void> _clear() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.clear();
    setState(() {
      lat = null;
      lng = null;
      address = null;
      savedTime = null;
    });
    _snack('Ubicación borrada');
  }

  @override
  Widget build(BuildContext context) {
    final hasCar = lat != null && lng != null;
    final timeStr = savedTime != null ? DateFormat('HH:mm - dd/MM').format(savedTime!) : '--:--';

    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(20.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // HEADER
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('WIMC', style: TextStyle(fontSize: 32, fontWeight: FontWeight.w900, letterSpacing: -1)),
                      Text('Where Is My Car - DV', style: TextStyle(fontSize: 13, color: Colors.black54, fontWeight: FontWeight.w600)),
                    ],
                  ),
                  IconButton(onPressed: hasCar ? _clear : null, icon: Icon(Icons.delete_outline), tooltip: 'Borrar'),
                ],
              ),
              SizedBox(height: 24),

              // TARJETA ESTADO
              Container(
                padding: EdgeInsets.all(18),
                decoration: BoxDecoration(
                  color: hasCar ? Colors.white : Color(0xFFFFF3CD),
                  borderRadius: BorderRadius.circular(18),
                  border: Border.all(color: hasCar ? Color(0xFFE5E8F0) : Color(0xFFFFE69C)),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(hasCar ? Icons.local_parking : Icons.search, color: hasCar ? Colors.blue : Colors.orange, size: 20),
                        SizedBox(width: 8),
                        Text(hasCar ? 'COCHE GUARDADO' : 'SIN COCHE GUARDADO', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w800, letterSpacing: 0.8, color: hasCar ? Colors.blue : Colors.orange[800])),
                        Spacer(),
                        Text(timeStr, style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: Colors.black54)),
                      ],
                    ),
                    if (hasCar) ...[
                      SizedBox(height: 12),
                      Text(address != null && address!.isNotEmpty ? address! : '${lat!.toStringAsFixed(5)}, ${lng!.toStringAsFixed(5)}', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600)),
                      SizedBox(height: 4),
                      Text('${lat!.toStringAsFixed(6)}, ${lng!.toStringAsFixed(6)}', style: TextStyle(fontSize: 11, color: Colors.black45)),
                    ] else ...[
                      SizedBox(height: 8),
                      Text('Pulsa el botón grande para guardar donde has aparcado.', style: TextStyle(fontSize: 13, color: Colors.black54)),
                    ]
                  ],
                ),
              ),

              Spacer(),

              // BOTON GIGANTE GUARDAR
              SizedBox(
                height: 110,
                child: ElevatedButton(
                  onPressed: loading ? null : _saveLocation,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Color(0xFF0B5FFF),
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                    elevation: 0,
                  ),
                  child: loading
                      ? CircularProgressIndicator(color: Colors.white)
                      : Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(Icons.my_location, size: 32),
                            SizedBox(height: 6),
                            Text('GUARDAR MI COCHE AQUÍ', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900, letterSpacing: -0.5)),
                            Text('Un toque y listo', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w500, color: Colors.white70)),
                          ],
                        ),
                ),
              ),

              SizedBox(height: 16),

              // DOS BOTONES ABAJO
              Row(
                children: [
                  Expanded(
                    child: SizedBox(
                      height: 72,
                      child: ElevatedButton(
                        onPressed: hasCar ? _navigate : null,
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.black,
                          foregroundColor: Colors.white,
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                          elevation: 0,
                        ),
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(Icons.navigation, size: 22),
                            SizedBox(height: 2),
                            Text('LLÉVAME', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w800)),
                          ],
                        ),
                      ),
                    ),
                  ),
                  SizedBox(width: 12),
                  Expanded(
                    child: SizedBox(
                      height: 72,
                      child: OutlinedButton(
                        onPressed: hasCar ? _openInMaps : null,
                        style: OutlinedButton.styleFrom(
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                          side: BorderSide(color: Color(0xFFE5E8F0)),
                          backgroundColor: Colors.white,
                        ),
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(Icons.map, color: Colors.black87, size: 22),
                            SizedBox(height: 2),
                            Text('VER MAPA', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w800, color: Colors.black)),
                          ],
                        ),
                      ),
                    ),
                  ),
                ],
              ),

              SizedBox(height: 8),
              Center(child: Text('WIMC DV • 100% offline • sin registro', style: TextStyle(fontSize: 10, color: Colors.black38))),
            ],
          ),
        ),
      ),
    );
  }
}