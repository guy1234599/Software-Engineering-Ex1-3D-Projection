@echo off
setlocal EnableExtensions
rem Windows Java equivalent to: python Ex0.py volume.nrrd planes.csv output.mp4
rem Compile once with IntelliJ Maven > Lifecycle > test, or with mvn test.
cd /d "%~dp0"
if not exist "target\classes\org\example\projection\Main.class" (
  echo ERROR: Compiled Main.class not found. Run Maven Lifecycle -^> test in IntelliJ first.
  exit /b 2
)
set "JC_CODEC=%USERPROFILE%\.m2\repository\org\jcodec\jcodec\0.2.5\jcodec-0.2.5.jar"
set "JC_AWT=%USERPROFILE%\.m2\repository\org\jcodec\jcodec-javase\0.2.5\jcodec-javase-0.2.5.jar"
if not exist "%JC_CODEC%" (
  echo ERROR: JCodec dependency missing. Run Maven Lifecycle -^> test in IntelliJ first.
  exit /b 2
)
if not exist "%JC_AWT%" (
  echo ERROR: JCodec JavaSE dependency missing. Run Maven Lifecycle -^> test in IntelliJ first.
  exit /b 2
)
set "JAVA_CMD=java"
where java >nul 2>nul
if errorlevel 1 (
  if exist "%USERPROFILE%\.jdks\openjdk-24.0.1\bin\java.exe" (
    set "JAVA_CMD=%USERPROFILE%\.jdks\openjdk-24.0.1\bin\java.exe"
  ) else (
    echo ERROR: Java not found on PATH. Set JAVA_HOME or run from IntelliJ.
    exit /b 2
  )
)
if "%~1"=="" (
  echo No arguments: using bundled course MRI and 60-plane demo.
  set "IN=samples\MRHead_1.nrrd"
  set "PL=samples\mrhead-demo-60.csv"
  set "OUT=target\mrhead-demo-60.mp4"
) else (
  if "%~3"=="" (
    echo Usage: run-demo.cmd file.nrrd file.csv file.mp4
    exit /b 2
  )
  if not "%~4"=="" (
    echo Usage: run-demo.cmd file.nrrd file.csv file.mp4
    exit /b 2
  )
  set "IN=%~1"
  set "PL=%~2"
  set "OUT=%~3"
)
"%JAVA_CMD%" -cp "target\classes;%JC_CODEC%;%JC_AWT%" org.example.projection.Main "%IN%" "%PL%" "%OUT%"
exit /b %ERRORLEVEL%
