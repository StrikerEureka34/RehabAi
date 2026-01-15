"""
RehabAI - Physiotherapy Exercise Guidance System
"""

from flask import Flask, request, jsonify, send_from_directory
from flask_cors import CORS
import numpy as np
import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers
from datetime import datetime
from collections import deque
import threading
import time
from enum import Enum
from dataclasses import dataclass, field
from typing import Optional, List, Dict, Any

app = Flask(__name__, static_folder='../frontend', static_url_path='')
CORS(app)

SEQUENCE_LENGTH = 40
NUM_KEYPOINTS = 17
NUM_FEATURES = 16
USE_ML_CONFIRMATION = False
CONFIRMATION_FRAME_COUNT = 15
POSE_VISIBILITY_THRESHOLD = 6
MIN_FEEDBACK_INTERVAL_MS = 1000
MIN_REP_INTERVAL_MS = 800
STATE_STABILITY_FRAMES = 2


class SessionState(Enum):
    POSITIONING = "positioning"
    VERIFYING = "verifying"
    READY = "ready"
    ACTIVE = "active"
    UNCERTAINTY = "uncertainty"
    COMPLETED = "completed"


class MovementPhase(Enum):
    IDLE = "idle"
    ECCENTRIC = "eccentric"
    CONCENTRIC = "concentric"
    TRANSITION = "transition"
    HOLD = "hold"


EXERCISES = {
    'squat': {
        'name': 'Squat',
        'description': 'Lower body strengthening',
        'target_reps': 10,
        'muscle_groups': ['Quadriceps', 'Glutes', 'Hamstrings']
    },
    'shoulder_press': {
        'name': 'Shoulder Press',
        'description': 'Upper body shoulder exercise',
        'target_reps': 12,
        'muscle_groups': ['Deltoids', 'Triceps', 'Upper Back']
    },
    'lunge': {
        'name': 'Lunge',
        'description': 'Lower body balance exercise',
        'target_reps': 8,
        'muscle_groups': ['Quadriceps', 'Glutes', 'Hamstrings']
    },
    'lateral_raise': {
        'name': 'Lateral Raise',
        'description': 'Shoulder isolation exercise',
        'target_reps': 12,
        'muscle_groups': ['Deltoids', 'Trapezius']
    }
}

FEEDBACK = {
    'squat': {
        'positioning': "Stand with feet shoulder-width apart.",
        'ready': "Ready when you are.",
        'corrective': {
            'go_lower': "Go slightly lower.",
            'align_knees': "Keep knees aligned.",
            'uneven_stance': "Even out your stance.",
            'lean_forward': "Keep your chest up."
        }
    },
    'shoulder_press': {
        'positioning': "Hold weights at shoulder height.",
        'ready': "Ready when you are.",
        'corrective': {
            'extend_fully': "Extend arms fully overhead.",
            'keep_symmetry': "Move both arms at the same rate.",
            'arms_uneven': "Keep arms at equal height.",
            'shrugging': "Relax your shoulders."
        }
    },
    'lunge': {
        'positioning': "Stand upright with feet together.",
        'ready': "Ready when you are.",
        'corrective': {
            'go_lower': "Go slightly lower.",
            'upright_torso': "Keep torso upright.",
            'knee_past_toes': "Front knee shouldn't pass toes.",
            'step_wider': "Step out wider."
        }
    },
    'lateral_raise': {
        'positioning': "Stand with arms at your sides.",
        'ready': "Ready when you are.",
        'corrective': {
            'raise_higher': "Raise arms to shoulder height.",
            'not_too_high': "Don't go above shoulder level.",
            'arms_uneven': "Raise both arms at the same rate.",
            'bent_elbows': "Keep slight bend in elbows."
        }
    }
}

sessions = {}
session_lock = threading.Lock()


@dataclass
class BeliefState:
    exercise_probabilities: Dict[str, float] = field(default_factory=dict)
    confirmation_frames: int = 0
    confirmed: bool = False
    locked_exercise: Optional[str] = None
    quality_belief: float = 0.5
    last_feedback_time: float = 0.0


@dataclass
class RepDetail:
    rep_number: int
    start_time: datetime
    end_time: Optional[datetime] = None
    duration_seconds: float = 0.0
    quality_score: float = 0.0
    quality_label: str = "Not assessed"
    remarks: List[str] = field(default_factory=list)


@dataclass
class MovementState:
    current_phase: MovementPhase = MovementPhase.IDLE
    phase_frames: int = 0
    last_phase: MovementPhase = MovementPhase.IDLE
    rep_count: int = 0
    last_rep_time: float = 0.0
    in_transition: bool = False
    stable_state_frames: int = 0
    angle_history: deque = field(default_factory=lambda: deque(maxlen=30))
    current_rep_start: Optional[datetime] = None
    rep_details: List = field(default_factory=list)
    current_rep_invalid: bool = False
    invalid_reason: str = ""


class ExerciseSession:
    def __init__(self, session_id: str, selected_exercise: str):
        self.session_id = session_id
        self.selected_exercise = selected_exercise
        self.start_time = datetime.now()
        self.state = SessionState.POSITIONING
        self.belief = BeliefState()
        self.movement = MovementState()
        self.keypoint_buffer = deque(maxlen=SEQUENCE_LENGTH)
        self.angle_buffer = deque(maxlen=SEQUENCE_LENGTH)
        self.quality_scores = []
        self.feedback_history = []
        self.current_rep_remarks: List[str] = []
        self.frame_count = 0
    
    def get_sequence(self) -> np.ndarray:
        if len(self.angle_buffer) < SEQUENCE_LENGTH:
            padding = [np.zeros(NUM_FEATURES) for _ in range(SEQUENCE_LENGTH - len(self.angle_buffer))]
            return np.array(padding + list(self.angle_buffer))
        return np.array(list(self.angle_buffer))
    
    def should_give_feedback(self) -> bool:
        now = time.time() * 1000
        if now - self.belief.last_feedback_time < MIN_FEEDBACK_INTERVAL_MS:
            return False
        if self.state == SessionState.UNCERTAINTY:
            return False
        return True
    
    def record_feedback(self, message: str, feedback_type: str):
        self.belief.last_feedback_time = time.time() * 1000
        self.feedback_history.append({
            'timestamp': datetime.now().isoformat(),
            'message': message,
            'type': feedback_type
        })
    
    def start_rep(self):
        self.movement.current_rep_start = datetime.now()
        self.current_rep_remarks = []
        self.movement.current_rep_invalid = False
        self.movement.invalid_reason = ""
    
    def complete_rep(self, quality_score: float) -> RepDetail:
        now = datetime.now()
        rep_number = self.movement.rep_count
        start = self.movement.current_rep_start or now
        duration = (now - start).total_seconds()
        
        if quality_score >= 0.8:
            quality_label = "Excellent"
        elif quality_score >= 0.6:
            quality_label = "Good"
        elif quality_score >= 0.4:
            quality_label = "Fair"
        else:
            quality_label = "Needs Improvement"
        
        rep_detail = RepDetail(
            rep_number=rep_number,
            start_time=start,
            end_time=now,
            duration_seconds=round(duration, 2),
            quality_score=round(quality_score, 2),
            quality_label=quality_label,
            remarks=self.current_rep_remarks.copy()
        )
        
        self.movement.rep_details.append(rep_detail)
        self.current_rep_remarks = []
        self.movement.current_rep_start = None
        return rep_detail
    
    def add_rep_remark(self, remark: str):
        if remark not in self.current_rep_remarks:
            self.current_rep_remarks.append(remark)
    
    def invalidate_current_rep(self, reason: str):
        self.movement.current_rep_invalid = True
        self.movement.invalid_reason = reason
    
    def generate_report(self) -> Dict[str, Any]:
        exercise_info = EXERCISES.get(self.selected_exercise, {})
        total_reps = len(self.movement.rep_details)
        avg_quality = sum(r.quality_score for r in self.movement.rep_details) / max(total_reps, 1)
        
        return {
            'session_id': self.session_id,
            'exercise': exercise_info.get('name', self.selected_exercise),
            'date': self.start_time.strftime('%Y-%m-%d %H:%M'),
            'duration_minutes': round((datetime.now() - self.start_time).total_seconds() / 60, 1),
            'total_reps': total_reps,
            'target_reps': exercise_info.get('target_reps', 10),
            'average_quality': round(avg_quality * 100),
            'reps': [
                {
                    'number': r.rep_number,
                    'duration': r.duration_seconds,
                    'quality': r.quality_label,
                    'score': round(r.quality_score * 100),
                    'notes': r.remarks
                }
                for r in self.movement.rep_details
            ]
        }


class ExerciseMLEngine:
    def __init__(self):
        self.classifier_model = None
        self.quality_models = {}
        self._initialize_models()
    
    def _initialize_models(self):
        classifier = keras.Sequential([
            layers.Input(shape=(SEQUENCE_LENGTH, NUM_FEATURES)),
            layers.Masking(mask_value=0.0),
            layers.LSTM(64, return_sequences=True, dropout=0.2),
            layers.LSTM(32, dropout=0.2),
            layers.Dense(32, activation='relu'),
            layers.Dropout(0.2),
            layers.Dense(len(EXERCISES), activation='softmax')
        ])
        classifier.compile(optimizer='adam', loss='categorical_crossentropy', metrics=['accuracy'])
        self.classifier_model = classifier
        
        for exercise_name in EXERCISES.keys():
            quality_model = keras.Sequential([
                layers.Input(shape=(SEQUENCE_LENGTH, NUM_FEATURES)),
                layers.Masking(mask_value=0.0),
                layers.LSTM(64, return_sequences=True, dropout=0.2),
                layers.LSTM(32, dropout=0.2),
                layers.Dense(32, activation='relu'),
                layers.Dropout(0.2),
                layers.Dense(1, activation='sigmoid')
            ])
            quality_model.compile(optimizer='adam', loss='mse', metrics=['mae'])
            self.quality_models[exercise_name] = quality_model
        
        print("✓ ML models initialized")
    
    def classify_exercise(self, sequence: np.ndarray) -> Dict[str, float]:
        sequence = sequence.reshape(1, SEQUENCE_LENGTH, NUM_FEATURES)
        predictions = self.classifier_model.predict(sequence, verbose=0)[0]
        exercise_names = list(EXERCISES.keys())
        return {name: float(predictions[i]) for i, name in enumerate(exercise_names)}
    
    def evaluate_quality(self, sequence: np.ndarray, exercise_type: str) -> tuple:
        if exercise_type not in self.quality_models:
            return 0.5, 0.5
        sequence = sequence.reshape(1, SEQUENCE_LENGTH, NUM_FEATURES)
        score = float(self.quality_models[exercise_type].predict(sequence, verbose=0)[0][0])
        confidence = abs(score - 0.5) * 2
        return score, confidence


ml_engine = ExerciseMLEngine()


def calculate_angle(p1, p2, p3) -> float:
    if p1 is None or p2 is None or p3 is None:
        return 0.0
    radians = np.arctan2(p3[1] - p2[1], p3[0] - p2[0]) - np.arctan2(p1[1] - p2[1], p1[0] - p2[0])
    angle = np.abs(radians * 180.0 / np.pi)
    if angle > 180.0:
        angle = 360 - angle
    return angle


def extract_keypoint(keypoints: List, name: str) -> Optional[tuple]:
    keypoint_map = {
        'nose': 0, 'left_eye': 1, 'right_eye': 2, 'left_ear': 3, 'right_ear': 4,
        'left_shoulder': 5, 'right_shoulder': 6, 'left_elbow': 7, 'right_elbow': 8,
        'left_wrist': 9, 'right_wrist': 10, 'left_hip': 11, 'right_hip': 12,
        'left_knee': 13, 'right_knee': 14, 'left_ankle': 15, 'right_ankle': 16
    }
    idx = keypoint_map.get(name)
    if idx is not None and idx < len(keypoints):
        kp = keypoints[idx]
        if kp.get('score', 0) > 0.3:
            return (kp['x'], kp['y'])
    return None


def extract_features(keypoints: List) -> np.ndarray:
    left_shoulder = extract_keypoint(keypoints, 'left_shoulder')
    right_shoulder = extract_keypoint(keypoints, 'right_shoulder')
    left_elbow = extract_keypoint(keypoints, 'left_elbow')
    right_elbow = extract_keypoint(keypoints, 'right_elbow')
    left_wrist = extract_keypoint(keypoints, 'left_wrist')
    right_wrist = extract_keypoint(keypoints, 'right_wrist')
    left_hip = extract_keypoint(keypoints, 'left_hip')
    right_hip = extract_keypoint(keypoints, 'right_hip')
    left_knee = extract_keypoint(keypoints, 'left_knee')
    right_knee = extract_keypoint(keypoints, 'right_knee')
    left_ankle = extract_keypoint(keypoints, 'left_ankle')
    right_ankle = extract_keypoint(keypoints, 'right_ankle')
    
    angles = [
        calculate_angle(left_hip, left_knee, left_ankle),
        calculate_angle(right_hip, right_knee, right_ankle),
        calculate_angle(left_shoulder, left_hip, left_knee),
        calculate_angle(right_shoulder, right_hip, right_knee),
        calculate_angle(left_shoulder, left_elbow, left_wrist),
        calculate_angle(right_shoulder, right_elbow, right_wrist),
        calculate_angle(left_elbow, left_shoulder, left_hip),
        calculate_angle(right_elbow, right_shoulder, right_hip),
        calculate_angle(left_shoulder, left_hip, right_hip),
        calculate_angle(right_shoulder, right_hip, left_hip),
        calculate_angle(left_hip, left_shoulder, right_shoulder),
        calculate_angle(right_hip, right_shoulder, left_shoulder),
        calculate_angle(left_knee, left_hip, right_hip),
        calculate_angle(right_knee, right_hip, left_hip),
        calculate_angle(left_wrist, left_elbow, left_shoulder),
        calculate_angle(right_wrist, right_elbow, right_shoulder),
    ]
    return np.array(angles)


def update_exercise_confirmation_pose(session: ExerciseSession, valid_keypoints: int):
    if session.belief.confirmed:
        return
    
    if valid_keypoints >= POSE_VISIBILITY_THRESHOLD:
        session.belief.confirmation_frames += 1
        if session.belief.confirmation_frames >= CONFIRMATION_FRAME_COUNT:
            session.belief.confirmed = True
            session.belief.locked_exercise = session.selected_exercise
            session.state = SessionState.READY
    else:
        session.belief.confirmation_frames = max(0, session.belief.confirmation_frames - 1)


def determine_movement_phase(session: ExerciseSession, angles: np.ndarray) -> MovementPhase:
    exercise = session.selected_exercise
    current_phase = MovementPhase.TRANSITION
    
    if exercise == 'squat':
        knee_angle = (angles[0] + angles[1]) / 2
        if knee_angle > 145:
            current_phase = MovementPhase.CONCENTRIC
        elif knee_angle < 130:
            current_phase = MovementPhase.ECCENTRIC
        else:
            current_phase = MovementPhase.TRANSITION
            
    elif exercise == 'shoulder_press':
        elbow_angle = (angles[4] + angles[5]) / 2
        if elbow_angle > 140:
            current_phase = MovementPhase.CONCENTRIC
        elif elbow_angle < 110:
            current_phase = MovementPhase.ECCENTRIC
        else:
            current_phase = MovementPhase.TRANSITION
            
    elif exercise == 'lunge':
        left_knee = angles[0]
        right_knee = angles[1]
        min_knee = min(left_knee, right_knee)
        
        if not hasattr(session, 'min_knee_seen'):
            session.min_knee_seen = 180
            session.max_knee_seen = 0
        session.min_knee_seen = min(session.min_knee_seen, min_knee)
        session.max_knee_seen = max(session.max_knee_seen, min_knee)
        
        mid_threshold = (session.min_knee_seen + session.max_knee_seen) / 2
        
        if session.max_knee_seen - session.min_knee_seen < 15:
            if min_knee > 140:
                current_phase = MovementPhase.CONCENTRIC
            elif min_knee < 125:
                current_phase = MovementPhase.ECCENTRIC
            else:
                current_phase = MovementPhase.TRANSITION
        else:
            if min_knee > mid_threshold + 10:
                current_phase = MovementPhase.CONCENTRIC
            elif min_knee < mid_threshold - 10:
                current_phase = MovementPhase.ECCENTRIC
            else:
                current_phase = MovementPhase.TRANSITION
            
    elif exercise == 'lateral_raise':
        shoulder_angle = (angles[6] + angles[7]) / 2
        if shoulder_angle > 60:
            current_phase = MovementPhase.CONCENTRIC
        elif shoulder_angle < 40:
            current_phase = MovementPhase.ECCENTRIC
        else:
            current_phase = MovementPhase.TRANSITION
    
    if current_phase == session.movement.current_phase:
        session.movement.phase_frames += 1
        session.movement.stable_state_frames += 1
    else:
        session.movement.last_phase = session.movement.current_phase
        session.movement.current_phase = current_phase
        session.movement.phase_frames = 1
        session.movement.stable_state_frames = 0
    
    session.movement.in_transition = (current_phase == MovementPhase.TRANSITION or 
                                       session.movement.phase_frames < STATE_STABILITY_FRAMES)
    return current_phase


def check_rep_completion(session: ExerciseSession, quality_score: float = 0.5) -> bool:
    now = time.time() * 1000
    
    if now - session.movement.last_rep_time < MIN_REP_INTERVAL_MS:
        return False
    
    if not hasattr(session.movement, 'seen_eccentric'):
        session.movement.seen_eccentric = False
    
    if session.movement.current_phase == MovementPhase.ECCENTRIC:
        if not session.movement.seen_eccentric:
            session.movement.seen_eccentric = True
            session.start_rep()
    
    if (session.movement.seen_eccentric and 
        session.movement.current_phase == MovementPhase.CONCENTRIC and
        session.movement.phase_frames >= 2):
        
        session.movement.last_rep_time = now
        session.movement.seen_eccentric = False
        
        if session.movement.current_rep_invalid:
            session.movement.current_rep_invalid = False
            session.movement.invalid_reason = ""
            return False
        
        session.movement.rep_count += 1
        session.complete_rep(quality_score)
        return True
    
    return False


def generate_feedback(session: ExerciseSession, angles: np.ndarray, 
                      quality_score: float, quality_confidence: float) -> Optional[Dict]:
    if not session.should_give_feedback():
        return None
    
    exercise = session.selected_exercise
    feedback_lib = FEEDBACK.get(exercise, {})
    
    if session.state == SessionState.POSITIONING:
        return {'message': feedback_lib.get('positioning', "Position yourself in front of the camera."), 'type': 'guidance'}
    
    if session.state == SessionState.VERIFYING:
        progress = min(100, int((session.belief.confirmation_frames / CONFIRMATION_FRAME_COUNT) * 100))
        if progress > 70:
            return {'message': "Almost ready...", 'type': 'guidance'}
        elif progress > 30:
            return {'message': "Hold still...", 'type': 'guidance'}
        return None
    
    if session.state == SessionState.READY:
        return {'message': feedback_lib.get('ready', "Ready when you are."), 'type': 'guidance'}
    
    if session.state == SessionState.ACTIVE:
        corrective = feedback_lib.get('corrective', {})
        
        if exercise == 'squat':
            left_knee, right_knee = angles[0], angles[1]
            left_hip, right_hip = angles[2], angles[3]
            avg_knee = (left_knee + right_knee) / 2
            knee_diff = abs(left_knee - right_knee)
            hip_diff = abs(left_hip - right_hip)
            
            if knee_diff > 35:
                session.invalidate_current_rep("Major knee asymmetry")
                return {'message': "Rep not counted. Keep both knees aligned.", 'type': 'warning'}
            
            if hip_diff > 25:
                session.invalidate_current_rep("Major hip asymmetry")
                return {'message': "Rep not counted. Even out your stance.", 'type': 'warning'}
            
            if session.movement.current_phase == MovementPhase.ECCENTRIC:
                if avg_knee > 140:
                    session.add_rep_remark("Depth insufficient")
                    return {'message': corrective.get('go_lower', "Go slightly lower."), 'type': 'corrective'}
            
            if knee_diff > 20:
                session.add_rep_remark("Knees misaligned")
                return {'message': corrective.get('align_knees', "Keep knees aligned."), 'type': 'corrective'}
                
        elif exercise == 'shoulder_press':
            left_elbow, right_elbow = angles[4], angles[5]
            left_shoulder, right_shoulder = angles[6], angles[7]
            elbow_diff = abs(left_elbow - right_elbow)
            shoulder_diff = abs(left_shoulder - right_shoulder)
            
            if elbow_diff > 50:
                session.invalidate_current_rep("Using only one arm")
                return {'message': "Rep not counted. Use both arms together.", 'type': 'warning'}
            
            if shoulder_diff > 40:
                session.invalidate_current_rep("Arms moving at different rates")
                return {'message': "Rep not counted. Move both arms at the same rate.", 'type': 'warning'}
            
            if elbow_diff > 25:
                session.add_rep_remark("Arms uneven")
                return {'message': corrective.get('arms_uneven', "Keep arms at equal height."), 'type': 'corrective'}
            
            if shoulder_diff > 20:
                session.add_rep_remark("Asymmetric movement")
                return {'message': corrective.get('keep_symmetry', "Move both arms at the same rate."), 'type': 'corrective'}
                
        elif exercise == 'lunge':
            left_knee, right_knee = angles[0], angles[1]
            left_hip, right_hip = angles[2], angles[3]
            left_elbow, right_elbow = angles[4], angles[5]
            min_knee = min(left_knee, right_knee)
            max_knee = max(left_knee, right_knee)
            avg_hip = (left_hip + right_hip) / 2
            elbow_diff = abs(left_elbow - right_elbow)
            
            if elbow_diff > 60:
                session.invalidate_current_rep("Arms not controlled")
                return {'message': "Rep not counted. Keep arms steady.", 'type': 'warning'}
            
            if max_knee - min_knee < 10 and session.movement.current_phase == MovementPhase.ECCENTRIC:
                session.invalidate_current_rep("No proper lunge")
                return {'message': "Rep not counted. Step forward into a lunge.", 'type': 'warning'}
            
            if avg_hip < 140:
                session.invalidate_current_rep("Torso leaning too much")
                return {'message': "Rep not counted. Keep torso upright.", 'type': 'warning'}
            
            if min_knee < 70:
                session.invalidate_current_rep("Knee too far forward")
                return {'message': "Rep not counted. Front knee shouldn't pass toes.", 'type': 'warning'}
            
            if session.movement.current_phase == MovementPhase.ECCENTRIC and min_knee > 145:
                session.add_rep_remark("Depth insufficient")
                return {'message': corrective.get('go_lower', "Go slightly lower."), 'type': 'corrective'}
                
        elif exercise == 'lateral_raise':
            left_shoulder, right_shoulder = angles[6], angles[7]
            left_elbow, right_elbow = angles[4], angles[5]
            avg_shoulder = (left_shoulder + right_shoulder) / 2
            shoulder_diff = abs(left_shoulder - right_shoulder)
            avg_elbow = (left_elbow + right_elbow) / 2
            
            if shoulder_diff > 50:
                session.invalidate_current_rep("Using only one arm")
                return {'message': "Rep not counted. Raise both arms together.", 'type': 'warning'}
            
            if session.movement.current_phase == MovementPhase.CONCENTRIC and avg_shoulder < 30:
                session.invalidate_current_rep("Arms barely raised")
                return {'message': "Rep not counted. Raise arms to shoulder height.", 'type': 'warning'}
            
            if avg_elbow < 100:
                session.invalidate_current_rep("Arms too bent")
                return {'message': "Rep not counted. Straighten your arms.", 'type': 'warning'}
            
            if shoulder_diff > 20:
                session.add_rep_remark("Arms uneven")
                return {'message': corrective.get('arms_uneven', "Raise both arms at the same rate."), 'type': 'corrective'}
        
        if session.movement.rep_count > 0:
            if session.movement.phase_frames <= 8 and session.movement.current_phase == MovementPhase.CONCENTRIC:
                return {'message': f"Good! {session.movement.rep_count} of {EXERCISES[exercise]['target_reps']}", 'type': 'positive'}
        
        if session.frame_count % 90 == 0:
            return {'message': "Keep going, good form.", 'type': 'guidance'}
    
    return None


@app.route('/')
def index():
    return send_from_directory('../frontend', 'index.html')


@app.route('/api/exercises', methods=['GET'])
def get_exercises():
    simplified = {}
    for key, ex in EXERCISES.items():
        simplified[key] = {
            'name': ex['name'],
            'description': ex['description'],
            'target_reps': ex['target_reps'],
            'muscle_groups': ex['muscle_groups']
        }
    return jsonify({'success': True, 'exercises': simplified})


@app.route('/api/session/start', methods=['POST'])
def start_session():
    data = request.json
    exercise_type = data.get('exercise_type', 'squat')
    
    if exercise_type not in EXERCISES:
        return jsonify({'success': False, 'error': 'Invalid exercise type'}), 400
    
    session_id = f"session_{int(time.time() * 1000)}"
    
    with session_lock:
        sessions[session_id] = ExerciseSession(session_id, exercise_type)
    
    return jsonify({
        'success': True,
        'session_id': session_id,
        'exercise': EXERCISES[exercise_type],
        'initial_guidance': FEEDBACK[exercise_type]['positioning']
    })


@app.route('/api/session/<session_id>/frame', methods=['POST'])
def process_frame(session_id):
    with session_lock:
        if session_id not in sessions:
            return jsonify({'success': False, 'error': 'Session not found'}), 404
        session = sessions[session_id]
    
    data = request.json
    keypoints = data.get('keypoints', [])
    
    if not keypoints:
        return jsonify({'success': False, 'error': 'No keypoints'}), 400
    
    angles = extract_features(keypoints)
    session.keypoint_buffer.append(keypoints)
    session.angle_buffer.append(angles)
    session.frame_count += 1
    
    valid_keypoints = sum(1 for kp in keypoints if kp.get('score', 0) > 0.3)
    sequence = session.get_sequence()
    
    update_exercise_confirmation_pose(session, valid_keypoints)
    
    if session.state == SessionState.POSITIONING:
        if valid_keypoints >= POSE_VISIBILITY_THRESHOLD:
            session.state = SessionState.VERIFYING
    
    elif session.state == SessionState.READY:
        determine_movement_phase(session, angles)
        if session.frame_count > 5:
            session.state = SessionState.ACTIVE
    
    elif session.state == SessionState.ACTIVE:
        determine_movement_phase(session, angles)
    
    quality_score = 0.5
    quality_confidence = 0.0
    
    if session.belief.confirmed and len(session.angle_buffer) >= SEQUENCE_LENGTH:
        if session.frame_count % 5 == 0:
            quality_score, quality_confidence = ml_engine.evaluate_quality(sequence, session.selected_exercise)
            session.belief.quality_belief = quality_score
    
    rep_completed = False
    if session.state == SessionState.ACTIVE:
        rep_completed = check_rep_completion(session, quality_score)
        if rep_completed:
            session.quality_scores.append(quality_score)
    
    feedback = generate_feedback(session, angles, quality_score, quality_confidence)
    
    if feedback:
        session.record_feedback(feedback['message'], feedback['type'])
    
    return jsonify({
        'success': True,
        'session_state': session.state.value,
        'exercise_confirmed': session.belief.confirmed,
        'movement_phase': session.movement.current_phase.value if session.state == SessionState.ACTIVE else None,
        'quality_score': float(quality_score) if session.belief.confirmed else None,
        'rep_count': session.movement.rep_count,
        'rep_completed': rep_completed,
        'feedback': feedback,
        'in_transition': session.movement.in_transition
    })


@app.route('/api/session/<session_id>/end', methods=['POST'])
def end_session(session_id):
    with session_lock:
        if session_id not in sessions:
            return jsonify({'success': False, 'error': 'Session not found'}), 404
        session = sessions[session_id]
    
    session.state = SessionState.COMPLETED
    
    avg_quality = sum(session.quality_scores) / max(len(session.quality_scores), 1)
    duration = (datetime.now() - session.start_time).total_seconds()
    
    summary = {
        'total_reps': session.movement.rep_count,
        'target_reps': EXERCISES[session.selected_exercise]['target_reps'],
        'average_quality': round(avg_quality * 100),
        'duration_seconds': round(duration),
        'feedback_count': len(session.feedback_history)
    }
    
    report = session.generate_report()
    
    with session_lock:
        del sessions[session_id]
    
    return jsonify({'success': True, 'summary': summary, 'report': report})


@app.route('/api/session/<session_id>/report', methods=['GET'])
def get_report(session_id):
    with session_lock:
        if session_id not in sessions:
            return jsonify({'success': False, 'error': 'Session not found'}), 404
        session = sessions[session_id]
    
    report = session.generate_report()
    return jsonify({'success': True, 'report': report})


if __name__ == '__main__':
    print("=" * 60)
    print("🏥 RehabAI - Physiotherapy Exercise System")
    print("=" * 60)
    print("✓ ML Models Loaded")
    print(f"✓ {len(EXERCISES)} Exercises Available")
    print("✓ Server running on http://localhost:5000")
    print("=" * 60)
    app.run(host='0.0.0.0', port=5000, debug=True)
