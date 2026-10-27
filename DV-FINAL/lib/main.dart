
import 'package:flutter/material.dart';
void main() => runApp(DVApp());

class DVApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'DV',
      theme: ThemeData(primarySwatch: Colors.blue),
      home: Scaffold(
        appBar: AppBar(title: Text('DV - Lista para compilar')),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.check_circle, size: 100, color: Colors.green),
              SizedBox(height: 20),
              Text('DV funcionando!', style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold)),
              SizedBox(height: 10),
              Text('Este APK y EXE se generaron solos en GitHub'),
              SizedBox(height: 30),
              ElevatedButton(onPressed: () {}, child: Text('Boton de prueba DV')),
            ],
          ),
        ),
      ),
    );
  }
}
