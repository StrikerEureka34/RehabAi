@echo off
echo ============================================================
echo   Starting RehabAI Frontend Server
echo ============================================================
echo.

cd frontend

echo Starting HTTP server on http://localhost:8000
echo Press Ctrl+C to stop the server
echo.
echo Open your browser and navigate to:
echo http://localhost:8000
echo.

python -m http.server 8000
