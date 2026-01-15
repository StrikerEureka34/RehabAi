#!/bin/bash

echo "============================================================"
echo "  Starting RehabAI Backend Server"
echo "============================================================"
echo ""

cd backend
source venv/bin/activate

echo "Starting Flask server on http://localhost:5000"
echo "Press Ctrl+C to stop the server"
echo ""

python app.py
