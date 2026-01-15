#!/bin/bash

echo "============================================================"
echo "  RehabAI Setup Script - Mac/Linux"
echo "============================================================"
echo ""

echo "[1/5] Checking Python installation..."
if ! command -v python3 &> /dev/null; then
    echo "ERROR: Python 3 is not installed"
    echo "Please install Python 3.8+ from https://www.python.org/"
    exit 1
fi
python3 --version
echo ""

echo "[2/5] Creating virtual environment..."
cd backend
if [ -d "venv" ]; then
    echo "Virtual environment already exists, skipping..."
else
    python3 -m venv venv
    echo "Virtual environment created successfully!"
fi
echo ""

echo "[3/5] Activating virtual environment..."
source venv/bin/activate
echo ""

echo "[4/5] Installing Python dependencies..."
pip install --upgrade pip
pip install -r requirements.txt
if [ $? -ne 0 ]; then
    echo "ERROR: Failed to install dependencies"
    exit 1
fi
echo "Dependencies installed successfully!"
echo ""

echo "[5/5] Setup complete!"
echo ""
echo "============================================================"
echo "  Installation Summary"
echo "============================================================"
echo "  Backend: Ready (Python + Flask + TensorFlow)"
echo "  Frontend: Ready (HTML + TensorFlow.js)"
echo ""
echo "  Next Steps:"
echo "  1. Run './start_backend.sh' to start the backend server"
echo "  2. Run './start_frontend.sh' to start the frontend server"
echo "  3. Open http://localhost:8000 in your browser"
echo "============================================================"
echo ""
