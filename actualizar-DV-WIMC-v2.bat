@echo off
echo === Actualizador DV WIMC ===
cd /d C:\dv\Claude

echo [1/4] Actualizando codigo...

echo [2/4] Git add
git add .

echo [3/4] Commit
set /p MSG=Mensaje del commit (ej: WIMC v2): 
git commit -m "%MSG%"

echo [4/4] Push a GitHub y compilar APK
echo Intentando push normal...
git push origin main
if %errorlevel% neq 0 (
  echo Push rechazado, hay cambios en GitHub. Haciendo pull --rebase...
  git pull --rebase origin main
  if %errorlevel% neq 0 (
    echo Conflicto, forzando push con --force...
    git push origin main --force
  ) else (
    git push origin main
  )
)

echo Listo! Ve a https://github.com/dvillarp/DV/actions
pause
