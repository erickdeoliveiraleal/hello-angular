@echo off
setlocal

rem Sobe o backend (Spring Boot, porta 8080) e o frontend (Angular, porta 4200),
rem cada um na sua janela. Para parar, feche as janelas ou use Ctrl+C nelas.

set "RAIZ=%~dp0"

rem --- Java -------------------------------------------------------------------
rem Se o JAVA_HOME estiver vazio ou apontar para um JDK que não existe mais,
rem usa o JDK 17+ mais recente instalado pelo Eclipse Adoptium.
if exist "%JAVA_HOME%\bin\java.exe" goto java_ok
set "JAVA_HOME="
for /d %%J in ("%ProgramFiles%\Eclipse Adoptium\jdk-17*" "%ProgramFiles%\Eclipse Adoptium\jdk-2*") do set "JAVA_HOME=%%~fJ"
if not defined JAVA_HOME (
    echo [ERRO] Nenhum JDK 17 ou superior encontrado. Instale o Java 17+ ou ajuste o JAVA_HOME.
    pause
    exit /b 1
)
echo JAVA_HOME invalido; usando "%JAVA_HOME%" so para esta execucao.
:java_ok

rem --- Node -------------------------------------------------------------------
where node >nul 2>&1
if errorlevel 1 (
    echo [ERRO] Node.js nao encontrado no PATH.
    pause
    exit /b 1
)

if not exist "%RAIZ%node_modules" (
    echo Instalando dependencias do frontend ^(npm install^)...
    pushd "%RAIZ%"
    call npm install
    if errorlevel 1 (
        popd
        echo [ERRO] npm install falhou.
        pause
        exit /b 1
    )
    popd
)

rem --- Subir ------------------------------------------------------------------
echo Subindo o backend em http://localhost:8080 ...
rem As janelas abertas pelo start herdam o JAVA_HOME definido acima.
rem Caminho completo do mvnw.cmd: algumas configuracoes do Windows nao procuram programas na pasta atual.
start "hello-angular backend" /d "%RAIZ%backend" cmd /k ""%RAIZ%backend\mvnw.cmd" spring-boot:run"

echo Subindo o frontend em http://localhost:4200 ...
start "hello-angular frontend" /d "%RAIZ%" cmd /k "npm start -- --open"

echo.
echo Pronto. O navegador abre sozinho quando o frontend terminar de compilar.
echo O backend leva alguns segundos a mais para ficar disponivel.
endlocal
