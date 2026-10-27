
import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';
import 'package:url_launcher/url_launcher.dart';
void main() => runApp(const WIMCApp());
class WIMCApp extends StatelessWidget {
  const WIMCApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(debugShowCheckedModeBanner: false, home: WIMCHome());
}
class WIMCHome extends StatefulWidget {
  @override
  State<WIMCHome> createState() => _WIMCHomeState();
}
class _WIMCHomeState extends State<WIMCHome> {
  double? lat; double? lng; bool loading=false;
  Future<void> save() async {
    setState(()=>loading=true);
    if(!await Geolocator.isLocationServiceEnabled()){setState(()=>loading=false);return;}
    var p=await Geolocator.checkPermission();
    if(p==LocationPermission.denied) p=await Geolocator.requestPermission();
    if(p==LocationPermission.denied||p==LocationPermission.deniedForever){setState(()=>loading=false);return;}
    var pos=await Geolocator.getCurrentPosition();
    setState((){lat=pos.latitude; lng=pos.longitude; loading=false;});
  }
  Future<void> go() async {
    if(lat==null) return;
    final u=Uri.parse('https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=walking');
    if(await canLaunchUrl(u)) await launchUrl(u, mode: LaunchMode.externalApplication);
  }
  @override
  Widget build(BuildContext context){
    final has=lat!=null;
    return Scaffold(body: SafeArea(child: Padding(padding: EdgeInsets.all(20), child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children:[
      Text('DV WIMC', style: TextStyle(fontSize:36,fontWeight:FontWeight.w900)),
      Text('Where Is My Car', style: TextStyle(color: Colors.black54)),
      SizedBox(height:20),
      Container(padding: EdgeInsets.all(16), decoration: BoxDecoration(color: has?Colors.white:Color(0xFFFFF3CD), borderRadius: BorderRadius.circular(16)), child: Text(has?'${lat!.toStringAsFixed(6)}, ${lng!.toStringAsFixed(6)}':'Pulsa GUARDAR para guardar donde aparcas', style: TextStyle(fontWeight: FontWeight.w600))),
      Spacer(),
      SizedBox(height:110, child: ElevatedButton(onPressed: loading?null:save, style: ElevatedButton.styleFrom(backgroundColor: Color(0xFF0B5FFF), foregroundColor: Colors.white, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20))), child: loading?CircularProgressIndicator(color: Colors.white):Column(mainAxisAlignment: MainAxisAlignment.center, children:[Icon(Icons.my_location,size:32), Text('GUARDAR MI COCHE AQUI', style: TextStyle(fontWeight:FontWeight.w900))]))),
      SizedBox(height:12),
      SizedBox(height:70, child: ElevatedButton(onPressed: has?go:null, style: ElevatedButton.styleFrom(backgroundColor: Colors.black, foregroundColor: Colors.white, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16))), child: Text('LLEVAME', style: TextStyle(fontWeight:FontWeight.w900)))),
    ]))));
  }
}
