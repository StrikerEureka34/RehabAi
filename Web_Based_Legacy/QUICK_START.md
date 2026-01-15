# 🚀 RehabAI - Quick Start Guide

## How to Start RehabAI (Manual Steps)

### Option 1: Use the Startup Scripts (Easiest)

#### Windows:
1. **Start Backend:**
   - Double-click: `C:\RehabAI\start_backend.bat`
   - Wait for message: "✓ Server running on http://localhost:5000"

2. **Start Frontend:**
   - Double-click: `C:\RehabAI\start_frontend.bat`
   - Wait for message: "Serving HTTP on port 8080"

3. **Open Application:**
   - Double-click: `C:\RehabAI\OPEN_APP.bat`
   - Or manually open browser to: http://localhost:8080

---

### Option 2: Manual Commands

#### Terminal 1 - Start Backend:
```powershell
# Navigate to backend directory
cd C:\RehabAI\backend

# Activate virtual environment
.\venv\Scripts\Activate.ps1

# Start Flask server
python app.py
```

**Expected Output:**
```
✓ LSTM models initialized
🏥 RehabAI Backend Server Starting...
✓ ML Models Loaded
✓ 4 Exercises Available: squat, shoulder_press, lunge, lateral_raise
✓ Server running on http://localhost:5000
```

---

#### Terminal 2 - Start Frontend:
```powershell
# Navigate to frontend directory
cd C:\RehabAI\frontend

# Start HTTP server
python -m http.server 8080
```

**Expected Output:**
```
Serving HTTP on :: port 8080 (http://[::]:8080/) ...
```

---

#### Open Browser:
- Navigate to: **http://localhost:8080**
- Or: http://127.0.0.1:8080

---

## 🎯 Using the Application

### Step 1: Select Exercise
- Click on one of the 4 exercise cards:
  - **Squat** (10 reps target)
  - **Shoulder Press** (12 reps target)
  - **Lunge** (10 reps target)
  - **Lateral Raise** (15 reps target)

### Step 2: Wait for Model Loading
- First time: Takes 10-30 seconds to download MoveNet model
- You'll see: "Loading AI model (this may take a moment)..."
- When ready: Message changes to "AI model loaded! Ready to start."

### Step 3: Start Exercise
1. Click the green **"START"** button
2. **Allow camera access** when browser asks
3. Position yourself so **full body is visible** in the frame
4. You should see:
   - ✓ Your video feed (mirrored)
   - ✓ Colored skeleton overlay on your body
   - ✓ Status badge changes to "Active" (blue)

### Step 4: Perform Exercise
- **For Squat:**
  - Stand straight (state: 'up')
  - Squat down (state: 'down')
  - Stand back up → **Rep counted!**

- **For Shoulder Press:**
  - Start with arms at shoulder level
  - Raise arms overhead
  - Lower back down → **Rep counted!**

- **For Lunge:**
  - Step forward into lunge
  - Lower back knee
  - Return to standing → **Rep counted!**

- **For Lateral Raise:**
  - Arms at sides
  - Raise to shoulder height
  - Lower down → **Rep counted!**

### Step 5: Monitor Progress
Watch the real-time updates:
- **Rep Counter**: Increments with each completed rep
- **Quality Score**: Shows form quality (0-100%)
- **Progress Bar**: Fills as you reach target reps
- **Live Feedback**: Gives form corrections and encouragement

### Step 6: End Session
1. Click the red **"STOP"** button
2. View your session summary
3. Click **"Back to Exercises"** to try another exercise
4. Check **Dashboard** tab for cumulative stats

---

## 🔧 Troubleshooting

### Backend Won't Start

**Problem:** Error when running `python app.py`

**Solution:**
```powershell
# Make sure you're in the backend directory
cd C:\RehabAI\backend

# Activate virtual environment first
.\venv\Scripts\Activate.ps1

# Check if it's activated (you should see (venv) in prompt)
# Now try starting again
python app.py
```

---

### Frontend Port Already in Use

**Problem:** "Address already in use" error on port 8080

**Solution:** Use a different port:
```powershell
cd C:\RehabAI\frontend
python -m http.server 8888
```
Then open: http://localhost:8888

---

### Camera Not Working

**Problem:** No video feed appears

**Check:**
1. ✓ Camera permission allowed in browser
2. ✓ No other app using camera (close Zoom, Teams, etc.)
3. ✓ Browser supports WebRTC (Chrome/Edge recommended)

**Fix:**
- Click the camera icon in browser address bar
- Set to "Always allow"
- Refresh page

---

### No Skeleton Overlay

**Problem:** Video appears but no pose detected

**Check:**
1. ✓ Good lighting in room
2. ✓ Full body visible in frame
3. ✓ Standing 5-8 feet from camera
4. ✓ Plain background (less clutter = better detection)

**Fix:**
- Step back from camera
- Turn on more lights
- Wear contrasting clothes

---

### Reps Not Counting

**Problem:** Performing exercise but counter stays at 0

**Check Console (F12 → Console tab):**
Look for messages like:
```
State changed: idle → down, quality: 0.75
State changed: down → up, quality: 0.82
✓ Rep counted! Total: 1
```

**If no state changes:**
- Exercise movement might be too small
- Perform full range of motion
- Move slower and more deliberately

**If states change but no rep count:**
- Quality score might be below 0.5
- Check feedback messages for form corrections
- Backend might not be running (check Terminal 1)

---

### "Initializing AI..." Stuck

**Problem:** Page stuck loading forever

**Check Browser Console (F12 → Console):**

**If you see:** `TensorFlow.js not loaded`
- Hard refresh: `Ctrl + Shift + R`
- Check internet connection (needs to download libraries)

**If you see:** `Failed to load AI model`
- Wait 30-60 seconds (model is large)
- Clear browser cache
- Try different browser

**If no errors:**
- Wait patiently (first load can take 30+ seconds)
- Check Network tab for download progress

---

## 📊 Checking if Everything Works

### Backend Health Check:
```powershell
Invoke-WebRequest -Uri http://localhost:5000/api/health | Select-Object -ExpandProperty Content
```

**Expected:**
```json
{
  "status": "healthy",
  "active_sessions": 0,
  "timestamp": "2025-11-01T..."
}
```

### Frontend Check:
```powershell
Invoke-WebRequest -Uri http://localhost:8080/ -UseBasicParsing | Select-Object -ExpandProperty StatusCode
```

**Expected:** `200`

---

## 🛑 How to Stop

### Stop Backend:
- Go to Terminal 1 (where backend is running)
- Press `Ctrl + C`

### Stop Frontend:
- Go to Terminal 2 (where frontend is running)
- Press `Ctrl + C`

---

## 📁 Project Structure

```
C:\RehabAI\
├── backend/
│   ├── app.py              # Flask server (800+ lines)
│   ├── requirements.txt    # Python dependencies
│   └── venv/              # Virtual environment
├── frontend/
│   └── index.html         # Complete web app (1000+ lines)
├── start_backend.bat      # Windows: Start backend
├── start_backend.sh       # Mac/Linux: Start backend
├── start_frontend.bat     # Windows: Start frontend
├── start_frontend.sh      # Mac/Linux: Start frontend
├── OPEN_APP.bat          # Windows: Open browser
├── README.md             # Full documentation
└── TESTING_GUIDE.md      # Detailed testing guide
```

---

## 🔑 Key URLs

- **Frontend (App):** http://localhost:8080
- **Backend (API):** http://localhost:5000
- **API Health:** http://localhost:5000/api/health
- **API Exercises:** http://localhost:5000/api/exercises

---

## 💡 Pro Tips

1. **First Time Setup:**
   - Backend takes ~10 seconds to load LSTM models
   - Frontend first load takes ~30 seconds to download MoveNet
   - Subsequent loads are instant (cached)

2. **Best Camera Position:**
   - Full body in frame (head to feet)
   - 5-8 feet distance
   - Good lighting (face camera toward light source)
   - Plain background

3. **Best Browsers:**
   - ✅ Chrome (recommended)
   - ✅ Edge (recommended)
   - ⚠️ Firefox (works but slower)
   - ❌ Safari (limited WebRTC support)

4. **Performance:**
   - Frontend: 30 FPS pose detection
   - Backend: 10 FPS processing
   - Minimal lag (<100ms)

5. **Privacy:**
   - All processing is LOCAL
   - Video never leaves your computer
   - No data is stored or transmitted

---

## 📞 Getting Help

### Debug Checklist:
- [ ] Backend running? (check Terminal 1)
- [ ] Frontend running? (check Terminal 2)
- [ ] Browser console open? (F12)
- [ ] Camera permission granted?
- [ ] Full body visible in frame?
- [ ] Good lighting?
- [ ] Internet connected? (for first load)

### Collect Debug Info:
1. **Backend logs** (from Terminal 1)
2. **Console errors** (F12 → Console tab, copy red errors)
3. **Network logs** (F12 → Network tab, filter: `/api/`)
4. **Browser & OS version**

---

## 🎉 Success Indicators

### You know it's working when:
1. ✅ Backend shows: "✓ Server running on http://localhost:5000"
2. ✅ Frontend shows: "Serving HTTP on :: port 8080"
3. ✅ Browser shows colorful exercise cards
4. ✅ Can click exercise card without errors
5. ✅ Loading overlay appears then disappears
6. ✅ START button is enabled (green)
7. ✅ Camera feed appears when clicked START
8. ✅ Skeleton overlay draws on your body
9. ✅ Rep counter increments when exercising
10. ✅ Feedback messages update in real-time

**If all 10 are ✅ → RehabAI is fully functional!** 🎉

---

*Need more help? Check `TESTING_GUIDE.md` for detailed troubleshooting*
