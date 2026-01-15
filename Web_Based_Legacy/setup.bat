@echo off
echo ============================================================
echo   RehabAI Setup Script - Windows
echo ============================================================
echo.

echo [1/5] Checking Python installation...
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Python is not installed or not in PATH
    echo Please install Python 3.8+ from https://www.python.org/
    pause
    exit /b 1
)
python --version
echo.

echo [2/5] Creating virtual environment...
cd backend
if exist venv (
    echo Virtual environment already exists, skipping...
) else (
    python -m venv venv
    echo Virtual environment created successfully!
)
echo.

echo [3/5] Activating virtual environment...
call venv\Scripts\activate.bat
echo.

echo [4/5] Installing Python dependencies...
pip install --upgrade pip
pip install -r requirements.txt
if %errorlevel% neq 0 (
    echo ERROR: Failed to install dependencies
    pause
    exit /b 1
)
echo Dependencies installed successfully!
echo.

echo [5/5] Setup complete!
echo.
echo ============================================================
echo   Installation Summary
echo ============================================================
echo   Backend: Ready (Python + Flask + TensorFlow)
echo   Frontend: Ready (HTML + TensorFlow.js)
echo.
echo   Next Steps:
echo   1. Run 'start_backend.bat' to start the backend server
echo   2. Run 'start_frontend.bat' to start the frontend server
echo   3. Open http://localhost:8000 in your browser
echo ============================================================
echo.
pause
