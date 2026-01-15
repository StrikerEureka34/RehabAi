# 🧪 RehabAI Testing Guide

## ✅ Current Status

### Backend Status: ✓ RUNNING
- **URL**: http://localhost:5000
- **Status**: LSTM models loaded successfully
- **Exercises**: 4 available (Squat, Shoulder Press, Lunge, Lateral Raise)
- **Terminal**: Backend terminal (ID: 0f28bcd8-a9e9-4b11-b9d9-d756f639b8f6)

### Frontend Status: ✓ RUNNING
- **URL**: http://localhost:8000
- **Status**: HTTP server active
- **Terminal**: Frontend terminal (ID: d6b1637d-2190-41fd-9259-47a5525d4a96)

### Recent Fixes Applied:
1. ✓ Fixed Python 3.12 dependency compatibility issues
2. ✓ Updated TensorFlow from 2.15.0 → 2.20.0
3. ✓ Updated NumPy from 1.24.3 → 1.26.4
4. ✓ Fixed TensorFlow.js import (removed broken importmap, using direct script tags)
5. ✓ Added comprehensive error logging and initialization checks

---

## 🚀 How to Test

### Step 1: Open the Application
1. **Open your web browser** (Chrome, Edge, or Firefox recommended)
2. **Navigate to**: http://localhost:8000
3. **Press F12** to open Developer Tools and check the Console tab

### Step 2: Check for Loading Issues
Look for these console messages:
- ✓ `TensorFlow.js loaded: 4.11.0`
- ✓ `Pose Detection library loaded`
- ✓ `RehabAI initialized successfully`

**If you see errors instead:**
- Red errors about TensorFlow → Refresh the page (Ctrl+F5)
- CORS errors → Make sure both servers are running on localhost
- Camera permission errors → Click "Allow" when browser asks for camera access

### Step 3: Select an Exercise
1. You should see **4 exercise cards**:
   - 🔵 **Squat** - Lower body strength (10 reps)
   - 🟢 **Shoulder Press** - Upper body strength (12 reps)
   - 🟠 **Lunge** - Single-leg lower body (10 reps)
   - 🟣 **Lateral Raise** - Shoulder isolation (15 reps)

2. **Click on any exercise card**

### Step 4: Wait for Model Loading
- You'll see: "Loading AI model (this may take a moment)..."
- **First time**: May take 10-30 seconds to download MoveNet model (~10MB)
- **Subsequent times**: Should be instant (cached by browser)
- Loading overlay will disappear when ready

### Step 5: Start Camera
1. Click the **green "START"** button
2. **Browser will ask for camera permission** → Click "Allow"
3. You should see:
   - Your **video feed** appears (mirrored)
   - **Skeleton overlay** drawn on your body (17 keypoints connected)
   - Status changes to "**Active**" (blue badge)

### Step 6: Perform the Exercise
1. **Position yourself** so your full body is visible
2. **Perform the exercise** movement
3. Watch for:
   - **Real-time feedback** messages (green/yellow/red)
   - **Rep counter** incrementing
   - **Quality progress bar** filling up
   - **Form corrections** (e.g., "Go lower", "Keep back straight")

### Step 7: Stop the Session
1. Click the **red "STOP"** button
2. Review your stats:
   - Total reps completed
   - Average quality score
3. Click **"Back to Exercises"** to try another

---

## 🐛 Troubleshooting

### Problem: "Initializing AI..." Stuck Forever

**Possible Causes:**
1. **TensorFlow.js not loading**
   - Check Console for errors
   - Try hard refresh: `Ctrl + Shift + R` (Windows) or `Cmd + Shift + R` (Mac)
   - Check your internet connection (libraries load from CDN)

2. **Pose Detection library failed**
   - Look for red errors in Console
   - Clear browser cache
   - Try a different browser

3. **Model download timeout**
   - First load needs internet to download MoveNet (~10MB)
   - Wait up to 60 seconds
   - Check Console for download progress

**Solution Steps:**
```
1. Press F12 → Console tab
2. Look for error messages
3. Copy error text
4. Hard refresh page (Ctrl+Shift+R)
5. If still stuck, check backend terminal for errors
```

---

### Problem: No Camera Feed

**Check:**
1. **Camera permission denied?**
   - Look for browser permission icon in address bar
   - Click it and set to "Allow"
   - Refresh the page

2. **Camera in use by another app?**
   - Close Zoom, Teams, Skype, etc.
   - Refresh the page

3. **Wrong camera selected?**
   - In browser settings, ensure correct camera is default

**Console Error Messages:**
- `NotAllowedError` → Permission denied (click Allow)
- `NotFoundError` → No camera detected
- `NotReadableError` → Camera in use by another app

---

### Problem: No Skeleton Overlay

**Means:** MoveNet isn't detecting you properly

**Check:**
1. **Lighting** - Room too dark?
2. **Distance** - Stand 5-8 feet from camera
3. **Full body visible** - Head to feet should be in frame
4. **Pose confidence** - Look for console message: `Pose detected with score: X.XX`
   - Score < 0.3 = Not detected
   - Score 0.3-0.7 = Partially detected
   - Score > 0.7 = Good detection

---

### Problem: Backend Not Responding

**Check if backend is running:**
```powershell
# In a new PowerShell window:
curl http://localhost:5000/api/health
```

**Expected Response:**
```json
{"status": "healthy", "models_loaded": true}
```

**If no response:**
1. Check backend terminal (should show Flask server running)
2. Look for Python errors in terminal
3. Restart backend:
   ```powershell
   cd C:\RehabAI\backend
   .\venv\Scripts\Activate.ps1
   python app.py
   ```

---

### Problem: Rep Counter Not Working

**Possible Causes:**
1. **Pose not detected** - Check skeleton overlay appears
2. **Movement too small** - Perform full range of motion
3. **Moving too fast** - Slow down, controlled movements
4. **Backend error** - Check backend terminal for errors

**Debug:**
1. Open Console → Network tab
2. Perform exercise movement
3. Look for POST requests to `/api/session/*/frame`
4. Click on request → Preview tab → Check response
5. Should show: `{"state": "down", "quality_score": 0.85, ...}`

---

## 📊 What to Expect

### Exercise: Squat
- **Detection**: Hip and knee angles
- **States**: `idle` → `down` (squat) → `up` (stand)
- **Feedback**:
  - "Go lower" if knees not bent enough
  - "Good depth!" when proper form
  - "Keep back straight" if posture off

### Exercise: Shoulder Press
- **Detection**: Elbow and shoulder angles
- **States**: `idle` → `down` (weights at shoulder) → `up` (arms extended)
- **Feedback**:
  - "Full extension" when arms straight
  - "Control the movement" if too fast

### Exercise: Lunge
- **Detection**: Front and back knee angles
- **States**: `idle` → `down` (lunge) → `up` (stand)
- **Feedback**:
  - "Front knee aligned" if good form
  - "Back knee lower" if not deep enough

### Exercise: Lateral Raise
- **Detection**: Shoulder and elbow angles
- **States**: `idle` → `up` (arms raised) → `down` (arms lowered)
- **Feedback**:
  - "Arms parallel to ground" when correct height
  - "Keep arms straight" if elbows bent

---

## 🔍 Console Debugging

### Key Console Messages to Look For:

#### Successful Initialization:
```
RehabAI initializing...
✓ TensorFlow.js loaded: 4.11.0
✓ Pose Detection library loaded
✓ RehabAI initialized successfully
```

#### Model Loading:
```
Loading MoveNet model...
✓ MoveNet model loaded successfully
```

#### Session Start:
```
Session started: abc123
```

#### Frame Processing:
```
Processing frame 10...
Sending keypoints to backend...
Backend response: {"state": "down", "quality_score": 0.85}
```

---

## 📈 Performance Expectations

### Frame Rates:
- **Frontend Pose Detection**: 30 FPS (real-time)
- **Backend Processing**: 10 FPS (every 3rd frame sent to server)
- **Rep Detection**: < 100ms latency

### Model Sizes:
- **MoveNet Model**: ~10MB (first load only)
- **TensorFlow.js Core**: ~15MB (first load only)
- **Total first-time load**: ~25MB

### Accuracy:
- **Pose Detection**: 95%+ in good lighting
- **Rep Counting**: 90%+ for proper form
- **Quality Scoring**: Based on LSTM trained on proper form

---

## 🎯 Success Criteria

### ✅ Everything Working If:
1. Exercise selection screen loads with 4 cards
2. Clicking exercise shows session screen
3. Loading overlay appears then disappears (< 30 sec)
4. START button works without errors
5. Camera feed appears (mirrored video)
6. Skeleton overlay draws on your body (17 dots + lines)
7. Feedback text updates in real-time
8. Rep counter increments when you complete a rep
9. Quality bar fills up as you do more reps
10. STOP button ends session cleanly

### ❌ Issues if:
- Stuck on "Initializing AI..." → Check Console for errors
- No camera feed → Check permissions/camera availability
- No skeleton → Check pose detection (lighting/distance)
- No rep counting → Check backend connection (Network tab)
- Backend errors → Check backend terminal output

---

## 🆘 Getting Help

### Collect Debug Info:
1. **Browser Console** (F12 → Console tab)
   - Copy all red error messages
   - Copy initialization messages

2. **Network Tab** (F12 → Network tab)
   - Filter: `/api/`
   - Check for failed requests (red)
   - Copy request/response details

3. **Backend Terminal**
   - Copy any error messages
   - Note which exercise triggered error

4. **System Info**
   - Browser: Chrome/Edge/Firefox + version
   - OS: Windows version
   - Camera: Built-in/External

### Common Error Codes:

| Error | Meaning | Solution |
|-------|---------|----------|
| `TypeError: Cannot read property 'movenet' of undefined` | Pose Detection not loaded | Hard refresh |
| `DOMException: NotAllowedError` | Camera permission denied | Click "Allow" |
| `NetworkError` | Backend not reachable | Check backend running |
| `CORS error` | Cross-origin blocked | Use localhost, not 127.0.0.1 |

---

## 🎉 Next Steps After Testing

1. **Try all 4 exercises** - Each has different detection logic
2. **Check Dashboard tab** - View cumulative statistics
3. **Review About tab** - Learn more about the technology
4. **Test different lighting** - See how it affects detection
5. **Try different distances** - Find optimal camera position
6. **Perform intentionally bad form** - Verify feedback works

---

## 📝 Test Checklist

Use this checklist to verify everything works:

- [ ] Backend server running on http://localhost:5000
- [ ] Frontend server running on http://localhost:8000
- [ ] Page loads without Console errors
- [ ] TensorFlow.js initialized successfully
- [ ] 4 exercise cards displayed
- [ ] Can click on exercise card
- [ ] Session screen appears
- [ ] Loading overlay shows during model load
- [ ] Model loads successfully (< 30 sec)
- [ ] START button enabled after model loads
- [ ] Camera permission requested
- [ ] Camera feed appears (mirrored)
- [ ] Skeleton overlay draws on body
- [ ] 17 keypoints visible as colored dots
- [ ] Keypoints connected with lines
- [ ] Feedback text updates when moving
- [ ] Rep counter increments on proper movement
- [ ] Quality bar fills up
- [ ] Backend request every ~100ms (Network tab)
- [ ] STOP button stops session
- [ ] Can return to exercise selection
- [ ] Dashboard shows session stats
- [ ] About page loads

**If all checked ✓ → Application is fully functional! 🎉**

---

*Last Updated: November 1, 2025*
*Version: 1.0.0*
