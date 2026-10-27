@echo off
echo ===============================
echo   CREADOR DV - 1 CLICK
echo ===============================
where flutter >nul 2>nul
if %errorlevel% neq 0 (
  echo [ERROR] Instala Flutter primero: https://docs.flutter.dev/get-started/install/windows
  pause
  exit /b
)
echo [1/4] Creando proyecto DV...
if not exist DV flutter create DV --platforms=android,windows --org=com.dv.app --project-name=dv
cd DV
mkdir .github\workflows 2>nul
echo [2/4] Copiando workflow...
copy /Y "..\DV\.github\workflows\build.yml" ".github\workflows\build.yml" >nul
echo [3/4] Git init...
git init
git branch -M main
git add .
git commit -m "DV inicial"
echo [4/4] Subiendo a GitHub...
set /p GHUSER=Tu usuario GitHub (ej: dvillarp): 
gh repo create %GHUSER%/DV --public --source=. --remote=origin --push
echo Listo! Ve a https://github.com/%GHUSER%/DV/actions
echo En 6 min tendras los links en Releases
pause
