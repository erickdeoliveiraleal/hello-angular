@echo off
rem Sobe o backend (Spring Boot, porta 8080) e o frontend (Angular, porta 4200),
rem cada um na sua janela. Para parar, feche as janelas (ou Ctrl+C em cada uma).
rem Precisa de JAVA_HOME apontando para um JDK 17 ou superior.

cd /d "%~dp0"

if not defined JAVA_HOME (
  echo JAVA_HOME nao esta definido. Aponte-o para um JDK 17 ou superior.
  exit /b 1
)

if not exist node_modules (
  echo Instalando as dependencias do Angular...
  call npm install || exit /b 1
)

start "Backend - Spring Boot (8080)" /d "%~dp0backend" cmd /k "%~dp0backend\mvnw.cmd" spring-boot:run
start "Frontend - Angular (4200)" /d "%~dp0" cmd /k npx ng serve

echo Backend e frontend iniciando em janelas separadas.
echo Quando os dois estiverem prontos, abra http://localhost:4200/
