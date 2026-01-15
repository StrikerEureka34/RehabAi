# Hospital-Grade Physiotherapy Exercise Guidance System

## Core Design Philosophy

This system is built on five non-negotiable principles:

1. **User intent drives the flow** - The user selects the exercise; ML confirms, not commands
2. **Wrong feedback is worse than delayed feedback** - Conservative thresholds everywhere
3. **Silence is preferable to noisy feedback** - Sparse, authoritative corrections only
4. **The system behaves like a human physiotherapist** - No AI branding, no technical metrics
5. **No silent switching** - System enters uncertainty state rather than auto-correcting

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        FRONTEND (Browser)                           │
├─────────────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐  │
│  │   Exercise   │  │    Video     │  │    Feedback Display      │  │
│  │  Selection   │──│  + Skeleton  │──│  (Sparse, Human-phrased) │  │
│  │   (Prior)    │  │   (Subtle)   │  │                          │  │
│  └──────────────┘  └──────────────┘  └──────────────────────────┘  │
│         │                  │                      ▲                 │
│         │                  │ MoveNet              │                 │
│         │                  ▼ Pose Detection       │                 │
│         │          ┌──────────────┐               │                 │
│         │          │  Keypoints   │               │                 │
│         │          │  (17 joints) │               │                 │
│         │          └──────────────┘               │                 │
│         │                  │                      │                 │
└─────────│──────────────────│──────────────────────│─────────────────┘
          │                  │ HTTP POST @ 30fps    │
          │                  ▼                      │
┌─────────│──────────────────────────────────────────│─────────────────┐
│         │           BACKEND (Flask)               │                 │
├─────────▼──────────────────────────────────────────│─────────────────┤
│  ┌──────────────┐                                 │                 │
│  │   Session    │──────────────────────────────────                 │
│  │   Manager    │                                                   │
│  └──────────────┘                                                   │
│         │                                                           │
│         ▼                                                           │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                    STATE MACHINE                              │  │
│  ├──────────────────────────────────────────────────────────────┤  │
│  │  POSITIONING → VERIFYING → READY → ACTIVE → COMPLETED        │  │
│  │       │            │                  │                       │  │
│  │       │            │                  ▼                       │  │
│  │       │            │            UNCERTAINTY                   │  │
│  │       │            │           (if contradictory              │  │
│  │       │            │            evidence > 85%)               │  │
│  └───────│────────────│──────────────────────────────────────────┘  │
│          │            │                                             │
│          ▼            ▼                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐  │
│  │   Feature    │  │  Classifier  │  │    Quality Evaluator     │  │
│  │  Extraction  │──│   (LSTM)     │──│    (Per-exercise LSTM)   │  │
│  │  (16 angles) │  │              │  │                          │  │
│  └──────────────┘  └──────────────┘  └──────────────────────────┘  │
│         │                 │                      │                  │
│         │                 ▼                      ▼                  │
│         │          ┌──────────────────────────────────────────┐    │
│         │          │         BELIEF STATE                      │    │
│         │          │  - Exercise probabilities (softmax)       │    │
│         │          │  - Confirmation frames (need 30 @ >90%)   │    │
│         │          │  - Quality belief + variance              │    │
│         │          │  - Last feedback time (2s cooldown)       │    │
│         │          └──────────────────────────────────────────┘    │
│         │                        │                                  │
│         │                        ▼                                  │
│         │          ┌──────────────────────────────────────────┐    │
│         │          │       FEEDBACK GENERATOR                  │    │
│         │          │  - Only if belief is stable               │    │
│         │          │  - Only if quality confidence > 65%       │    │
│         │          │  - Only after 2s cooldown                 │    │
│         │          │  - Only if not in transition              │    │
│         │          └──────────────────────────────────────────┘    │
│         │                        │                                  │
│         ▼                        ▼                                  │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                    MOVEMENT STATE                             │  │
│  │  - Current phase (IDLE/ECCENTRIC/CONCENTRIC/TRANSITION)      │  │
│  │  - Phase frames (need 8 stable before counting)              │  │
│  │  - Rep counting (eccentric→concentric transition)            │  │
│  │  - Min 1.2s between reps                                     │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Exercise Confirmation Flow

```
User selects "Squat" ──▶ This creates a PRIOR
         │
         ▼
┌─────────────────────────────────────────────────────────┐
│           VERIFICATION STATE                             │
│                                                          │
│  Classifier runs every 10 frames                         │
│                                                          │
│  IF softmax_prob[squat] > 0.90                          │
│     AND maintained for 30 consecutive frames (~0.75s)   │
│     AND no strong contradictory joint kinematics        │
│  THEN:                                                   │
│     ✓ Exercise CONFIRMED                                │
│     ✓ Lock exercise identity                            │
│     ✓ Reduce classifier influence (monitoring only)    │
│                                                          │
│  ELSE:                                                   │
│     Show neutral guidance only:                          │
│     - "Position yourself"                                │
│     - "Get ready to begin"                               │
│     NO corrective feedback                               │
│                                                          │
└─────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────┐
│           ACTIVE STATE                                   │
│                                                          │
│  Exercise identity is LOCKED                             │
│  Downstream logic assumes correct exercise               │
│                                                          │
│  IF later evidence is overwhelmingly contradictory      │
│     (different exercise softmax > 0.85):                │
│  THEN:                                                   │
│     ⚠ Enter UNCERTAINTY state                           │
│     ⚠ Pause all feedback                                │
│     ⚠ Ask user to reconfirm:                           │
│       "It looks like a different movement.              │
│        Please confirm the exercise."                     │
│                                                          │
│  NEVER silently switch exercises                         │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

---

## Feedback Authority Rules

### When Feedback is BLOCKED

| Condition | Rationale |
|-----------|-----------|
| During initial setup (POSITIONING state) | User is still getting ready |
| During transition frames | Movement is between phases |
| High entropy window (entropy > 0.5) | Belief is too uncertain |
| Within 2s of last feedback | Prevent flooding |
| Quality confidence < 65% | Not confident enough to correct |
| Stable state frames < 15 | Haven't observed enough |

### Feedback Principles

**DO:**
- Speak sparingly, with authority
- Use human-phrased, discrete messages
- Examples: "Go slightly lower." / "That was good." / "Slow the movement."

**DON'T:**
- Show continuous quality scores
- Give per-frame commentary
- Display angle updates
- Show AI confidence or model accuracy

---

## Movement Phase Detection

```
┌─────────────────────────────────────────────────────────┐
│  SQUAT EXAMPLE                                          │
│                                                          │
│  CONCENTRIC (Standing): knee > 150° AND hip > 150°      │
│  ECCENTRIC (Squatting): knee < 110°                     │
│  TRANSITION: Between thresholds                          │
│                                                          │
│  Rep counted when:                                       │
│  1. ECCENTRIC → CONCENTRIC transition                   │
│  2. Current phase stable for ≥8 frames                  │
│  3. At least 1.2s since last rep                        │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

---

## UI/UX Design Direction

### Visual Tone
- Hospital-grade, professional, calm
- Minimal colors: white, soft blue (#3b82f6), grey
- No neon gradients
- No "AI" branding or language
- No gamification visuals

### UI Hierarchy
1. User body & exercise (primary focus)
2. Simple text feedback (secondary)
3. Progress indicators (optional - reps, set completion)

### UI Behavior Rules
- Skeleton overlay is subtle (60% opacity)
- UI does NOT react to every frame
- Feedback appears after meaningful events only
- Silence is acceptable and encouraged

---

## Temporal & Performance Constraints

| Metric | Target | Rationale |
|--------|--------|-----------|
| Pose estimation FPS | 30 | Balance quality vs. compute |
| Backend frame processing | Every 10 frames | Reduce server load |
| Quality evaluation | Every 5 frames | Responsive but not noisy |
| Feedback cooldown | 2000ms | Human-perceptible cadence |
| Rep interval minimum | 1200ms | Prevent double-counting |
| Confirmation frames | 30 frames | ~0.75s at 40fps |
| State stability frames | 8 frames | Prevent phase flicker |
| Acceptable latency | <150ms | If correctness preserved |

---

## API Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/exercises` | GET | List available exercises |
| `/api/session/start` | POST | Start session with user-selected exercise |
| `/api/session/<id>/frame` | POST | Process keypoints, get feedback |
| `/api/session/<id>/confirm` | POST | Manual exercise confirmation |
| `/api/session/<id>/reselect` | POST | Change exercise when uncertain |
| `/api/session/<id>/end` | POST | End session, get summary |
| `/api/session/<id>/status` | GET | Get current session state |
| `/api/health` | GET | Health check |

---

## Data Structures

### BeliefState
```python
@dataclass
class BeliefState:
    exercise_probabilities: Dict[str, float]  # Softmax outputs
    confirmation_frames: int                   # Frames at >90%
    confirmed: bool                            # Exercise locked
    locked_exercise: Optional[str]             # Which exercise
    quality_belief: float                      # 0-1 quality score
    quality_variance: float                    # Uncertainty
    last_feedback_time: float                  # For cooldown
```

### MovementState
```python
@dataclass
class MovementState:
    current_phase: MovementPhase   # IDLE/ECCENTRIC/CONCENTRIC/TRANSITION
    phase_frames: int              # Frames in current phase
    last_phase: MovementPhase      # For transition detection
    rep_count: int                 # Completed reps
    last_rep_time: float           # For min interval
    in_transition: bool            # Suppress feedback
    stable_state_frames: int       # For belief stability
```

### SessionState (Enum)
```
POSITIONING  → User getting into position
VERIFYING    → Confirming exercise selection  
READY        → Exercise confirmed, waiting
ACTIVE       → Exercise in progress
UNCERTAINTY  → Contradictory evidence
COMPLETED    → Session ended
```

---

## Configuration Constants

```python
# Exercise confirmation thresholds (conservative)
CONFIRMATION_SOFTMAX_THRESHOLD = 0.90  # Must be >90% confident
CONFIRMATION_FRAME_COUNT = 30          # Must maintain for ~0.75 seconds
CONTRADICTION_THRESHOLD = 0.85         # Evidence threshold to trigger reconfirmation

# Feedback timing (human-perceptible cadence)
MIN_FEEDBACK_INTERVAL_MS = 2000        # Minimum 2 seconds between feedback
BELIEF_STABILITY_FRAMES = 15           # Require stable belief for 15 frames
QUALITY_CONFIDENCE_THRESHOLD = 0.65    # Only give feedback when confident

# Rep detection
MIN_REP_INTERVAL_MS = 1200             # Minimum 1.2 seconds between reps
STATE_STABILITY_FRAMES = 8             # Require stable state for 8 frames
```

---

## Feedback Library

All feedback is human-phrased, sparse, and authoritative:

```python
FEEDBACK_LIBRARY = {
    'squat': {
        'positioning': ["Position yourself in front of the camera."],
        'ready': ["Ready when you are."],
        'positive': ["Good depth.", "That was good.", "Nice form."],
        'corrective': {
            'go_lower': "Go slightly lower.",
            'slow_down': "Slow the movement.",
            'align_knees': "Keep knees aligned."
        }
    },
    # Similar for other exercises...
    
    'uncertainty': {
        'reconfirm': "It looks like a different movement. Please confirm the exercise."
    }
}
```

The system speaks less, but with authority.
