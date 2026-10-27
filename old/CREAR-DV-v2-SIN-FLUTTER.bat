@echo off
echo ===============================
echo   CREADOR DV v2 - SIN FLUTTER
echo   No necesitas instalar nada
echo ===============================
echo.
where git >nul 2>nul
if %errorlevel% neq 0 (
  echo [ERROR] Necesitas git: https://git-scm.com/download/win
  pause
  exit /b
)

echo [1/3] Preparando carpeta DV...
mkdir DV 2>nul
cd DV
mkdir .github\workflows 2>nul
copy /Y "..\DV\.github\workflows\build.yml" ".github\workflows\build.yml" >nul
echo # DV > README.md

echo [2/3] Git...
git init
git branch -M main
git add .
git commit -m "DV - compila en la nube"

echo [3/3] Subir a GitHub...
set /p GHUSER=Tu usuario GitHub (ej: dvillarp): 
echo Creando repo https://github.com/%GHUSER%/DV ...
gh repo create %GHUSER%/DV --public --source=. --remote=origin --push 2>nul
if %errorlevel% neq 0 (
  echo.
  echo No tienes gh, hazlo manual en 10 seg:
  echo 1. Ve a https://github.com/new y crea repo DV publico vacio
  echo 2. Luego ejecuta:
  echo    git remote add origin https://github.com/%GHUSER%/DV.git
  echo    git push -u origin main
  pause
  exit /b
)

echo.
echo LISTO!
echo Ve a https://github.com/%GHUSER%/DV/actions y en 6-8 min tendras los links en Releases
echo.
pause
