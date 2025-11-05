@echo off
echo.
echo ====================================
echo   RehabAI - Quick Start Launcher
echo ====================================
echo.
echo Opening RehabAI in your default browser...
echo.
echo Frontend: http://localhost:8000
echo Backend:  http://localhost:5000
echo.

start http://localhost:8000

echo.
echo Application opened!
echo.
echo IMPORTANT: Keep this window open
echo Press Ctrl+C to stop when done
echo.
echo Console logs will appear below:
echo ====================================
echo.

timeout /t 3 >nul
echo Checking servers...
echo.

curl http://localhost:5000/api/health 2>nul
if %errorlevel% equ 0 (
    echo [OK] Backend is running
) else (
    echo [ERROR] Backend not running! Run start_backend.bat first
)

echo.
echo Press any key to close this launcher...
pause >nul
