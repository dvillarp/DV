# WIMC · Where Is My Car · DV (Android nativo)

App Android nativa (Kotlin + Jetpack Compose + Google Maps SDK) para guardar dónde has aparcado con un botón y volver a él.

- 4 pestañas: **Inicio · Mapa · Historial · Ajustes**
- **APARCAR AHORA** funciona siempre: GPS alta precisión con timeout de 1,5 s → centro del mapa (si lo has colocado tú) → diálogo con opciones (reintentar, centro del mapa, coordenadas manuales, Madrid Sol). **Nunca** cae en Madrid Sol en silencio.
- Segundo toque mientras guarda = guarda al instante con lo que haya.
- Mapa Google nativo: tocar para aparcar (con confirmación), «Mi posición», «Coche», «Aparcar centro», botón «< Volver a Inicio».
- Historial (50 máx.) con botón USAR, DESAPARCAR, CÓMO LLEGAR (Google Maps, a pie) y COMPARTIR.
- Detección automática de coche (Activity Recognition): al dejar de conducir avisa «Parece que has parado. ¿Aparcar aquí?» (Aparcar ahora / Auto-aparcar siempre / Ignorar). Botón de prueba «Simular parada de coche» en Ajustes.
- minSdk 26 (Android 8.0), targetSdk 35.

## 1. Clave de Google Maps (necesaria para que se vea el mapa)

1. https://console.cloud.google.com → crea/elige un proyecto → activa **Maps SDK for Android**.
2. Credenciales → Crear clave de API. (Opcional: restringirla a `com.dv.wimc` + tu SHA-1.)
3. Ponla en `local.properties`:
   ```
   MAPS_API_KEY=AIza...tu_clave
   ```
   (o variable de entorno `MAPS_API_KEY`; en GitHub Actions, secreto `MAPS_API_KEY`).

Sin clave la app funciona igual (APARCAR, historial, navegar, compartir…) pero el mapa se ve gris.

## 2. Compilar el APK

**Android Studio:** abre esta carpeta → espera el sync → `Build > Build APK(s)`.
APK en `app/build/outputs/apk/debug/app-debug.apk`.

**Línea de comandos:** `./gradlew assembleDebug` (JDK 17 + Android SDK 35).

**GitHub Actions:** sube el proyecto a un repo, añade el secreto `MAPS_API_KEY` y ejecuta el workflow *Build APK* (pestaña Actions). Descargas el APK como artefacto y queda también en la rama `apk-build`.

## 3. Instalar en el móvil

1. Copia `WIMC-DV.apk` al móvil (cable, Drive, email…).
2. Ábrelo → permite «Instalar apps desconocidas» para esa app (el gestor de archivos/Chrome).
3. Primera vez: pulsa APARCAR y concede **Ubicación precisa**. En Android 12+ elige «Precisa», no «Aproximada».
4. Auto-aparcar sin preguntar necesita además ubicación «Permitir siempre» (Ajustes del móvil). Sin ella, recibes la notificación.

## Iconos

Carpeta `icon/`: `icon_1024.png`, `icon_512.png` (Play Store), `adaptive_foreground_1024.png` (+ fondo #1E3A6B) y los SVG fuente. El icono adaptativo ya está en `app/src/main/res` (vector).

## Decisiones respecto al documento de requisitos

- Datos en SharedPreferences (`wimc_parked`, `wimc_history`) en vez de DataStore: así también los lee el receiver en segundo plano.
- Detección de coche con la API de **transiciones** de Activity Recognition (IN_VEHICLE entrar/salir), no con umbrales de velocidad 15/3 km/h + 60 s: funciona en segundo plano sin servicio permanente y gasta menos batería.
- «Centro del mapa» como alternativa solo se usa si lo has movido tú con el dedo; se borra al guardar, para no reutilizar un centro antiguo.
- Cómo llegar usa `travelmode=walking` (volver andando al coche).
