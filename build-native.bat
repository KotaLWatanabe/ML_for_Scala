@echo off
setlocal enabledelayedexpansion

REM ML for Scala Native Image Build Script for Windows
echo 🚀 Building ML for Scala Native Image...
echo ================================================

REM Check if GraalVM is installed
:check_graalvm
echo Checking GraalVM installation...
where native-image >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ native-image not found. Please install GraalVM and native-image.
    echo Installation instructions:
    echo 1. Install GraalVM: https://www.graalvm.org/downloads/
    echo 2. Install native-image: gu install native-image
    echo 3. Add GraalVM to PATH
    exit /b 1
)
echo ✅ GraalVM native-image found

REM Check if sbt is installed
:check_sbt
echo Checking sbt installation...
where sbt >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ sbt not found. Please install sbt.
    echo Installation: https://www.scala-sbt.org/download.html
    exit /b 1
)
echo ✅ sbt found

REM Handle command line arguments
if "%1"=="--help" goto show_help
if "%1"=="-h" goto show_help
if "%1"=="--clean-only" goto clean_only
if "%1"=="--skip-tests" set SKIP_TESTS=1

REM Clean previous builds
:clean_build
echo Cleaning previous builds...
call sbt clean
if %errorlevel% neq 0 (
    echo ❌ Clean failed
    exit /b 1
)
echo ✅ Clean completed

REM Compile Scala code
:compile_code
echo Compiling Scala code...
call sbt compile
if %errorlevel% neq 0 (
    echo ❌ Compilation failed
    exit /b 1
)
echo ✅ Compilation completed

REM Run tests (if not skipped)
:run_tests
if defined SKIP_TESTS (
    echo ⏭️ Skipping tests
    goto build_native_image
)
echo Running tests...
call sbt test
if %errorlevel% neq 0 (
    echo ⚠️ Some tests failed, but continuing with build...
) else (
    echo ✅ All tests passed
)

REM Build native image
:build_native_image
echo Building native image...
echo This may take several minutes...

call sbt nativeImage
if %errorlevel% neq 0 (
    echo ❌ Native image build failed
    exit /b 1
)

echo ✅ Native image build completed successfully!

REM Check if binary exists and show info
if exist ".\target\native-image\mlscala.exe" (
    echo 📦 Binary location: .\target\native-image\mlscala.exe
    for %%A in (".\target\native-image\mlscala.exe") do echo Binary size: %%~zA bytes
) else (
    echo ⚠️ Binary not found at expected location
)

REM Test the native binary
:test_native_binary
if exist ".\target\native-image\mlscala.exe" (
    echo Testing native binary...
    echo Running: .\target\native-image\mlscala.exe
    echo Output:
    echo ---
    ".\target\native-image\mlscala.exe"
    echo ---
    echo ✅ Native binary test completed
)

REM Show usage information
:show_usage
echo 🎉 Build completed successfully!
echo.
echo Usage:
echo   .\target\native-image\mlscala.exe    # Run the native binary
echo.
echo Generated files:
echo   .\target\native-image\mlscala.exe    # Native executable
echo   *.html                               # Generated plot files
echo.
echo To create a distributable binary:
echo   copy ".\target\native-image\mlscala.exe" ".\mlscala-windows.exe"
goto end

:clean_only
call :check_sbt
call :clean_build
echo ✅ Clean completed. Exiting.
goto end

:show_help
echo ML for Scala Native Image Build Script for Windows
echo.
echo Usage: %0 [OPTIONS]
echo.
echo Options:
echo   --help, -h     Show this help message
echo   --skip-tests   Skip running tests before build
echo   --clean-only   Only clean, don't build
echo.
goto end

:end
endlocal