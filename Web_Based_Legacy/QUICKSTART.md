# 🚀 RehabAI Quick Start Guide

## Project Structure

```
RehabAI/
├── backend/                      # Python Flask Backend
│   ├── app.py                   # Main Flask application with LSTM models
│   ├── requirements.txt         # Python dependencies
│   └── venv/                    # Virtual environment (created during setup)
│
├── frontend/                     # Web Frontend
│   └── index.html               # Single-page application with TensorFlow.js
│
├── setup.bat                     # Windows setup script
├── setup.sh                      # Mac/Linux setup script
├── start_backend.bat            # Windows backend launcher
├── start_backend.sh             # Mac/Linux backend launcher
├── start_frontend.bat           # Windows frontend launcher
├── start_frontend.sh            # Mac/Linux frontend launcher
└── README.md                    # Full documentation
```

---

## ⚡ Quick Start (3 Steps)

### **Step 1: Install**

**Windows:**
```bash
setup.bat
```

**Mac/Linux:**
```bash
chmod +x setup.sh start_backend.sh start_frontend.sh
./setup.sh
```

This will:
- Check Python installation
- Create virtual environment
- Install all dependencies (Flask, TensorFlow, etc.)

### **Step 2: Start Backend**

**Windows:**
```bash
start_backend.bat
```

**Mac/Linux:**
```bash
./start_backend.sh
```

Wait for:
```
✓ ML Models Loaded
✓ Server running on http://localhost:5000
```

### **Step 3: Start Frontend**

Open a **NEW terminal/command prompt**:

**Windows:**
```bash
start_frontend.bat
```

**Mac/Linux:**
```bash
./start_frontend.sh
```

Then open: **http://localhost:8000**

---

## 🎯 First Session Walkthrough

### 1. **Choose Exercise**
- You'll see 4 exercise cards
- Click on "Squat" to start

### 2. **Camera Setup**
- Click "Start Exercise"
- Allow camera access
- Position yourself:
  - 6-8 feet from camera
  - Full body visible
  - Good lighting

### 3. **Perform Exercise**
- The AI will detect your pose
- Green skeleton appears on your body
- Start performing squats:
  - Stand straight (system will say "Ready to squat")
  - Lower down (system will guide depth)
  - Stand back up (rep counted if form is good)

### 4. **Watch Feedback**
You'll see:
- ✅ "Good rep! Count: 1" (when correct)
- ⚠️ "Squat deeper!" (if not deep enough)
- ❌ "Keep knees aligned" (if form issues)
- Quality score updates in real-time

### 5. **Complete Session**
- Reach 10 reps or click "Stop"
- View your stats:
  - Total reps
  - Average quality score
  - Session duration

### 6. **Check Dashboard**
- Click "Dashboard" tab
- See cumulative stats across all sessions

---

## 🎨 Features Overview

### **Exercise Library (4 Exercises)**

| Exercise | Target | Reps | Difficulty |
|----------|--------|------|------------|
| 🧘 Squat | Lower Body | 10 | Beginner |
| 💪 Shoulder Press | Upper Body | 12 | Intermediate |
| 🦵 Lunge | Legs + Balance | 10 | Intermediate |
| 🏋️ Lateral Raise | Shoulders | 15 | Advanced |

### **Real-Time AI Analysis**
- ✅ Automatic rep counting
- ✅ Form correction feedback
- ✅ Quality scoring (0-100)
- ✅ Exercise classification
- ✅ Progress tracking

### **Multi-Tab Interface**
- **Exercise Tab**: Live workout session
- **Dashboard Tab**: Progress statistics
- **About Tab**: How the AI works

---

## 🔧 Troubleshooting

### **Backend Won't Start**

**Problem:** "Python not found"
```bash
# Install Python 3.8+ from python.org
# Make sure to check "Add Python to PATH" during installation
```

**Problem:** "No module named 'flask'"
```bash
cd backend
venv\Scripts\activate  # Windows
source venv/bin/activate  # Mac/Linux
pip install -r requirements.txt
```

### **Frontend Won't Load**

**Problem:** Can't connect to backend
```bash
# Make sure backend is running first!
# Should see: "Server running on http://localhost:5000"
```

**Problem:** Camera not working
```bash
# Allow camera permissions in browser
# Chrome: Settings → Privacy → Camera
# Try: http://localhost:8000 (not file://)
```

### **Pose Detection Issues**

**Problem:** "No person detected"
- Stand 6-8 feet from camera
- Ensure full body is visible
- Improve lighting
- Try plain background

**Problem:** Skeleton not appearing
- Check console for errors (F12)
- Refresh page
- Clear browser cache

---

## 💻 System Requirements

### **Minimum**
- **OS**: Windows 10, macOS 10.14, Ubuntu 18.04
- **RAM**: 4GB
- **Browser**: Chrome 90+, Edge 90+, Firefox 88+
- **Webcam**: 720p
- **Internet**: Required for first load (CDN libraries)

### **Recommended**
- **RAM**: 8GB+
- **Webcam**: 1080p
- **Internet**: 10 Mbps+

---

## 🎓 Learning Resources

### **Understanding the AI**

1. **Pose Detection (MoveNet)**
   - Detects 17 body keypoints
   - Runs at ~30fps on client-side
   - Lightweight model (6MB)

2. **Feature Extraction**
   - Calculates 16 joint angles
   - Normalizes for position/scale
   - Creates feature vector for LSTM

3. **LSTM Classification**
   - Analyzes 30-frame sequences (1 second)
   - Classifies exercise type
   - Outputs confidence score

4. **Quality Grading**
   - Separate LSTM per exercise
   - Compares to ideal movement pattern
   - Outputs quality score (0-1)

### **Code Deep Dive**

**Frontend (index.html):**
- Lines 1-500: HTML structure & styling
- Lines 500-800: JavaScript application logic
- Lines 800-900: TensorFlow.js pose detection
- Lines 900-1000: Backend API integration

**Backend (app.py):**
- Lines 1-100: Imports & configuration
- Lines 100-200: Session management
- Lines 200-400: LSTM model architecture
- Lines 400-600: Feature extraction
- Lines 600-800: API endpoints

---

## 📊 API Testing

### **Test Backend Health**
```bash
curl http://localhost:5000/api/health
```

Expected:
```json
{
  "status": "healthy",
  "active_sessions": 0,
  "timestamp": "2025-11-01T10:30:00"
}
```

### **Test Exercise List**
```bash
curl http://localhost:5000/api/exercises
```

---

## 🚀 What's Next?

### **Immediate Improvements**
1. Try all 4 exercises
2. Beat your quality scores
3. Build consistency streaks

### **Advanced Features (Coming Soon)**
- Video recording & replay
- Custom workout plans
- Multi-user support
- Mobile app
- Wearable integration

### **Contribute**
- Report bugs: GitHub Issues
- Suggest exercises: Feature Requests
- Improve AI: Model PRs

---

## 📞 Quick Links

- **Full Documentation**: README.md
- **Architecture Deep Dive**: See "System Architecture" in README
- **API Reference**: See "API Documentation" in README
- **GitHub**: [Your Repo URL]

---

<div align="center">

### 🎉 **Ready to Transform Your Rehab!**

**Start your first session now:** http://localhost:8000

</div>

---

## 📝 Quick Commands Reference

```bash
# Setup
setup.bat              # Windows setup
./setup.sh             # Mac/Linux setup

# Start servers
start_backend.bat      # Windows backend
./start_backend.sh     # Mac/Linux backend
start_frontend.bat     # Windows frontend
./start_frontend.sh    # Mac/Linux frontend

# Access
http://localhost:5000  # Backend API
http://localhost:8000  # Frontend UI

# Stop
Ctrl + C               # Stop any server
```

---

**Happy Training! 💪🏥**
