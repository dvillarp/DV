@echo off
echo === Actualizador DV WIMC ===
cd /d C:\dv\Claude

echo [1/4] Actualizando codigo...
:: Este archivo se genera automaticamente por Meta AI
:: Pega aqui el contenido de lib/main.dart que te de Meta AI

echo [2/4] Git add
git add .

echo [3/4] Commit
set /p MSG=Mensaje del commit (ej: WIMC v2): 
git commit -m "%MSG%"

echo [4/4] Push a GitHub y compilar APK
git push origin main

echo Listo! Ve a https://github.com/dvillarp/DV/actions
pause
