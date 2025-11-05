@echo off
echo ============================================================
echo   Starting RehabAI Backend Server
echo ============================================================
echo.

cd backend
call venv\Scripts\activate.bat

echo Starting Flask server on http://localhost:5000
echo Press Ctrl+C to stop the server
echo.

python app.py
