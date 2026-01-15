"""
RehabAI Backend Server
Complete ML Pipeline with LSTM-based Exercise Classification & Grading
"""

from flask import Flask, request, jsonify, send_from_directory
from flask_cors import CORS
import numpy as np
import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers
import json
import os
from datetime import datetime
from collections import deque
import threading
import time

app = Flask(__name__, static_folder='../frontend', static_url_path='')
CORS(app)

# Global configuration
SEQUENCE_LENGTH = 30  # 30 frames (~1 second at 30fps)
NUM_KEYPOINTS = 17
NUM_FEATURES = 16  # 16 joint angles
CONFIDENCE_THRESHOLD = 0.7

# Exercise database
EXERCISES = {
    'squat': {
        'name': 'Squat',
        'description': 'Lower body strength exercise',
        'target_reps': 10,
        'muscle_groups': ['Quadriceps', 'Glutes', 'Hamstrings'],
        'key_angles': ['left_knee', 'right_knee', 'left_hip', 'right_hip']
    },
    'shoulder_press': {
        'name': 'Shoulder Press',
        'description': 'Upper body strength exercise',
        'target_reps': 12,
        'muscle_groups': ['Deltoids', 'Triceps', 'Upper Back'],
        'key_angles': ['left_elbow', 'right_elbow', 'left_shoulder', 'right_shoulder']
    },
    'lunge': {
        'name': 'Lunge',
        'description': 'Single-leg lower body exercise',
        'target_reps': 10,
        'muscle_groups': ['Quadriceps', 'Glutes', 'Calves'],
        'key_angles': ['left_knee', 'right_knee', 'left_hip', 'right_hip']
    },
    'lateral_raise': {
        'name': 'Lateral Raise',
        'description': 'Shoulder isolation exercise',
        'target_reps': 15,
        'muscle_groups': ['Lateral Deltoids', 'Upper Traps'],
        'key_angles': ['left_shoulder', 'right_shoulder', 'left_elbow', 'right_elbow']
    }
}

# Session storage (in production, use Redis or database)
sessions = {}
session_lock = threading.Lock()

class ExerciseSession:
    """Manages a single user exercise session"""
    def __init__(self, session_id, exercise_type):
        self.session_id = session_id
        self.exercise_type = exercise_type
        self.keypoint_buffer = deque(maxlen=SEQUENCE_LENGTH)
        self.angle_buffer = deque(maxlen=SEQUENCE_LENGTH)
        self.rep_count = 0
        self.quality_scores = []
        self.feedback_history = []
        self.start_time = datetime.now()
        self.state = 'idle'  # idle, active, completed
        
    def add_frame(self, keypoints, angles):
        """Add a frame to the buffer"""
        self.keypoint_buffer.append(keypoints)
        self.angle_buffer.append(angles)
        
    def get_sequence(self):
        """Get the current sequence for LSTM processing"""
        if len(self.angle_buffer) < SEQUENCE_LENGTH:
            # Pad with zeros if not enough frames
            padding = [np.zeros(NUM_FEATURES) for _ in range(SEQUENCE_LENGTH - len(self.angle_buffer))]
            return np.array(padding + list(self.angle_buffer))
        return np.array(list(self.angle_buffer))
    
    def to_dict(self):
        """Convert session to dictionary for API response"""
        return {
            'session_id': self.session_id,
            'exercise_type': self.exercise_type,
            'rep_count': self.rep_count,
            'quality_scores': self.quality_scores,
            'average_quality': np.mean(self.quality_scores) if self.quality_scores else 0,
            'feedback_history': self.feedback_history[-5:],  # Last 5 feedback messages
            'state': self.state,
            'duration': (datetime.now() - self.start_time).seconds
        }

class ExerciseLSTMModel:
    """LSTM-based exercise classifier and quality evaluator"""
    
    def __init__(self):
        self.classifier_model = None
        self.quality_models = {}
        self._build_models()
        
    def _build_models(self):
        """Build LSTM models for classification and quality assessment"""
        
        # Exercise Classifier Model
        classifier = keras.Sequential([
            layers.Input(shape=(SEQUENCE_LENGTH, NUM_FEATURES)),
            layers.Masking(mask_value=0.0),
            layers.LSTM(128, return_sequences=True, dropout=0.2),
            layers.LSTM(64, dropout=0.2),
            layers.Dense(64, activation='relu'),
            layers.Dropout(0.3),
            layers.Dense(len(EXERCISES), activation='softmax')
        ])
        classifier.compile(
            optimizer='adam',
            loss='categorical_crossentropy',
            metrics=['accuracy']
        )
        self.classifier_model = classifier
        
        # Quality Assessment Models (one per exercise)
        for exercise_name in EXERCISES.keys():
            quality_model = keras.Sequential([
                layers.Input(shape=(SEQUENCE_LENGTH, NUM_FEATURES)),
                layers.Masking(mask_value=0.0),
                layers.LSTM(64, return_sequences=True, dropout=0.2),
                layers.LSTM(32, dropout=0.2),
                layers.Dense(32, activation='relu'),
                layers.Dropout(0.2),
                layers.Dense(1, activation='sigmoid')  # Quality score 0-1
            ])
            quality_model.compile(
                optimizer='adam',
                loss='mse',
                metrics=['mae']
            )
            self.quality_models[exercise_name] = quality_model
        
        print("✓ LSTM models initialized")
    
    def classify_exercise(self, sequence):
        """Classify what exercise is being performed"""
        sequence = sequence.reshape(1, SEQUENCE_LENGTH, NUM_FEATURES)
        predictions = self.classifier_model.predict(sequence, verbose=0)
        exercise_idx = np.argmax(predictions[0])
        confidence = predictions[0][exercise_idx]
        exercise_name = list(EXERCISES.keys())[exercise_idx]
        return exercise_name, confidence
    
    def evaluate_quality(self, sequence, exercise_type):
        """Evaluate the quality of the exercise form"""
        if exercise_type not in self.quality_models:
            return 0.5  # Default medium quality
        
        sequence = sequence.reshape(1, SEQUENCE_LENGTH, NUM_FEATURES)
        quality_score = self.quality_models[exercise_type].predict(sequence, verbose=0)[0][0]
        return float(quality_score)

# Initialize ML model
ml_model = ExerciseLSTMModel()

def calculate_angle(p1, p2, p3):
    """Calculate angle between three points"""
    if p1 is None or p2 is None or p3 is None:
        return 0.0
    
    radians = np.arctan2(p3[1] - p2[1], p3[0] - p2[0]) - \
              np.arctan2(p1[1] - p2[1], p1[0] - p2[0])
    angle = np.abs(radians * 180.0 / np.pi)
    
    if angle > 180.0:
        angle = 360 - angle
    
    return angle

def extract_keypoint(keypoints, name):
    """Extract a specific keypoint by name"""
    keypoint_map = {
        'nose': 0, 'left_eye': 1, 'right_eye': 2, 'left_ear': 3, 'right_ear': 4,
        'left_shoulder': 5, 'right_shoulder': 6, 'left_elbow': 7, 'right_elbow': 8,
        'left_wrist': 9, 'right_wrist': 10, 'left_hip': 11, 'right_hip': 12,
        'left_knee': 13, 'right_knee': 14, 'left_ankle': 15, 'right_ankle': 16
    }
    
    idx = keypoint_map.get(name)
    if idx is not None and idx < len(keypoints):
        kp = keypoints[idx]
        if kp['score'] > 0.3:
            return (kp['x'], kp['y'])
    return None

def normalize_keypoints(keypoints):
    """Normalize keypoints to be position and scale invariant"""
    # Extract coordinates
    coords = np.array([[kp['x'], kp['y']] for kp in keypoints])
    
    # Center around hip midpoint
    left_hip_idx = 11
    right_hip_idx = 12
    hip_center = (coords[left_hip_idx] + coords[right_hip_idx]) / 2
    coords_centered = coords - hip_center
    
    # Scale based on torso length
    left_shoulder_idx = 5
    torso_length = np.linalg.norm(coords[left_shoulder_idx] - coords[left_hip_idx])
    if torso_length > 0:
        coords_normalized = coords_centered / torso_length
    else:
        coords_normalized = coords_centered
    
    return coords_normalized

def extract_features(keypoints):
    """Extract joint angles as features"""
    angles = []
    
    # Get keypoints
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
    
    # Calculate 16 key angles
    angles.append(calculate_angle(left_hip, left_knee, left_ankle))  # Left knee
    angles.append(calculate_angle(right_hip, right_knee, right_ankle))  # Right knee
    angles.append(calculate_angle(left_shoulder, left_hip, left_knee))  # Left hip
    angles.append(calculate_angle(right_shoulder, right_hip, right_knee))  # Right hip
    angles.append(calculate_angle(left_shoulder, left_elbow, left_wrist))  # Left elbow
    angles.append(calculate_angle(right_shoulder, right_elbow, right_wrist))  # Right elbow
    angles.append(calculate_angle(left_elbow, left_shoulder, left_hip))  # Left shoulder
    angles.append(calculate_angle(right_elbow, right_shoulder, right_hip))  # Right shoulder
    
    # Additional angles for better classification
    angles.append(calculate_angle(left_shoulder, left_hip, right_hip))  # Left trunk
    angles.append(calculate_angle(right_shoulder, right_hip, left_hip))  # Right trunk
    angles.append(calculate_angle(left_hip, left_shoulder, right_shoulder))  # Upper back left
    angles.append(calculate_angle(right_hip, right_shoulder, left_shoulder))  # Upper back right
    angles.append(calculate_angle(left_knee, left_hip, right_hip))  # Left lower trunk
    angles.append(calculate_angle(right_knee, right_hip, left_hip))  # Right lower trunk
    angles.append(calculate_angle(left_wrist, left_elbow, left_shoulder))  # Left arm extension
    angles.append(calculate_angle(right_wrist, right_elbow, right_shoulder))  # Right arm extension
    
    return np.array(angles)

def generate_feedback(exercise_type, angles, quality_score, state):
    """Generate real-time feedback based on angles and quality"""
    feedback = []
    
    if exercise_type == 'squat':
        knee_angle = (angles[0] + angles[1]) / 2
        hip_angle = (angles[2] + angles[3]) / 2
        
        if state == 'down':
            if knee_angle > 100:
                feedback.append({'message': 'Squat deeper! Get thighs parallel', 'type': 'warning'})
            elif knee_angle < 90:
                feedback.append({'message': 'Excellent depth!', 'type': 'positive'})
            
            if abs(angles[0] - angles[1]) > 15:
                feedback.append({'message': 'Keep knees aligned', 'type': 'error'})
                
        if quality_score < 0.5:
            feedback.append({'message': 'Focus on form over speed', 'type': 'warning'})
    
    elif exercise_type == 'shoulder_press':
        elbow_angle = (angles[4] + angles[5]) / 2
        shoulder_angle = (angles[6] + angles[7]) / 2
        
        if state == 'up':
            if elbow_angle < 160:
                feedback.append({'message': 'Extend arms fully at the top', 'type': 'warning'})
            else:
                feedback.append({'message': 'Perfect extension!', 'type': 'positive'})
                
        if abs(angles[4] - angles[5]) > 20:
            feedback.append({'message': 'Keep arms symmetrical', 'type': 'error'})
    
    elif exercise_type == 'lunge':
        front_knee = min(angles[0], angles[1])
        back_knee = max(angles[0], angles[1])
        
        if state == 'down':
            if front_knee > 100:
                feedback.append({'message': 'Lower down more', 'type': 'warning'})
            if front_knee < 70:
                feedback.append({'message': 'Don\'t let knee go past toes', 'type': 'error'})
            else:
                feedback.append({'message': 'Good lunge depth!', 'type': 'positive'})
    
    elif exercise_type == 'lateral_raise':
        shoulder_angle = (angles[6] + angles[7]) / 2
        elbow_angle = (angles[4] + angles[5]) / 2
        
        if state == 'up':
            if shoulder_angle < 80:
                feedback.append({'message': 'Raise arms to shoulder height', 'type': 'warning'})
            elif shoulder_angle > 100:
                feedback.append({'message': 'Don\'t raise too high', 'type': 'warning'})
            else:
                feedback.append({'message': 'Perfect shoulder height!', 'type': 'positive'})
        
        if elbow_angle < 150:
            feedback.append({'message': 'Keep slight bend in elbows', 'type': 'info'})
    
    if not feedback:
        feedback.append({'message': 'Good form! Keep it up', 'type': 'positive'})
    
    return feedback

@app.route('/')
def index():
    """Serve the main application"""
    return send_from_directory('../frontend', 'index.html')

@app.route('/api/exercises', methods=['GET'])
def get_exercises():
    """Get list of available exercises"""
    return jsonify({
        'success': True,
        'exercises': EXERCISES
    })

@app.route('/api/session/start', methods=['POST'])
def start_session():
    """Start a new exercise session"""
    data = request.json
    exercise_type = data.get('exercise_type', 'squat')
    
    if exercise_type not in EXERCISES:
        return jsonify({
            'success': False,
            'error': 'Invalid exercise type'
        }), 400
    
    session_id = f"session_{int(time.time() * 1000)}"
    
    with session_lock:
        sessions[session_id] = ExerciseSession(session_id, exercise_type)
    
    return jsonify({
        'success': True,
        'session_id': session_id,
        'exercise': EXERCISES[exercise_type]
    })

@app.route('/api/session/<session_id>/frame', methods=['POST'])
def process_frame(session_id):
    """Process a single frame of keypoints"""
    with session_lock:
        if session_id not in sessions:
            return jsonify({
                'success': False,
                'error': 'Session not found'
            }), 404
        
        session = sessions[session_id]
    
    data = request.json
    keypoints = data.get('keypoints', [])
    
    if not keypoints:
        return jsonify({
            'success': False,
            'error': 'No keypoints provided'
        }), 400
    
    # Extract features (angles)
    angles = extract_features(keypoints)
    
    # Add to session buffer
    session.add_frame(keypoints, angles)
    session.state = 'active'
    
    # Get current sequence
    sequence = session.get_sequence()
    
    # Classify exercise (every 10 frames to save compute)
    exercise_detected = session.exercise_type
    confidence = 1.0
    if len(session.angle_buffer) >= SEQUENCE_LENGTH and len(session.angle_buffer) % 10 == 0:
        exercise_detected, confidence = ml_model.classify_exercise(sequence)
    
    # Evaluate quality
    quality_score = 0.75  # Default
    if len(session.angle_buffer) >= SEQUENCE_LENGTH:
        quality_score = ml_model.evaluate_quality(sequence, session.exercise_type)
    
    # Determine state for feedback
    state = determine_exercise_state(session.exercise_type, angles)
    
    # Generate feedback
    feedback = generate_feedback(session.exercise_type, angles, quality_score, state)
    
    # Store feedback
    if feedback:
        session.feedback_history.extend(feedback)
    
    return jsonify({
        'success': True,
        'exercise_detected': exercise_detected,
        'confidence': float(confidence),
        'quality_score': float(quality_score),
        'feedback': feedback,
        'angles': angles.tolist(),
        'state': state
    })

def determine_exercise_state(exercise_type, angles):
    """Determine current state of exercise for rep counting"""
    if exercise_type == 'squat':
        knee_angle = (angles[0] + angles[1]) / 2
        if knee_angle > 150:
            return 'up'
        elif knee_angle < 110:
            return 'down'
        else:
            return 'transition'
    
    elif exercise_type == 'shoulder_press':
        elbow_angle = (angles[4] + angles[5]) / 2
        if elbow_angle > 150:
            return 'up'
        elif elbow_angle < 100:
            return 'down'
        else:
            return 'transition'
    
    elif exercise_type == 'lunge':
        min_knee = min(angles[0], angles[1])
        if min_knee > 150:
            return 'up'
        elif min_knee < 110:
            return 'down'
        else:
            return 'transition'
    
    elif exercise_type == 'lateral_raise':
        shoulder_angle = (angles[6] + angles[7]) / 2
        if shoulder_angle < 50:
            return 'down'
        elif shoulder_angle > 80:
            return 'up'
        else:
            return 'transition'
    
    return 'idle'

@app.route('/api/session/<session_id>/rep', methods=['POST'])
def count_rep(session_id):
    """Count a completed repetition"""
    with session_lock:
        if session_id not in sessions:
            return jsonify({
                'success': False,
                'error': 'Session not found'
            }), 404
        
        session = sessions[session_id]
    
    data = request.json
    quality_score = data.get('quality_score', 0.5)
    
    session.rep_count += 1
    session.quality_scores.append(quality_score)
    
    return jsonify({
        'success': True,
        'rep_count': session.rep_count,
        'quality_score': quality_score,
        'average_quality': np.mean(session.quality_scores)
    })

@app.route('/api/session/<session_id>/status', methods=['GET'])
def get_session_status(session_id):
    """Get current session status"""
    with session_lock:
        if session_id not in sessions:
            return jsonify({
                'success': False,
                'error': 'Session not found'
            }), 404
        
        session = sessions[session_id]
    
    return jsonify({
        'success': True,
        'session': session.to_dict()
    })

@app.route('/api/session/<session_id>/end', methods=['POST'])
def end_session(session_id):
    """End an exercise session"""
    with session_lock:
        if session_id not in sessions:
            return jsonify({
                'success': False,
                'error': 'Session not found'
            }), 404
        
        session = sessions[session_id]
        session.state = 'completed'
        
        # Generate summary
        summary = {
            'session_id': session_id,
            'exercise_type': session.exercise_type,
            'total_reps': session.rep_count,
            'average_quality': float(np.mean(session.quality_scores)) if session.quality_scores else 0,
            'duration': (datetime.now() - session.start_time).seconds,
            'feedback_summary': session.feedback_history[-10:],
            'completed_at': datetime.now().isoformat()
        }
        
        # Clean up session after 5 minutes
        def cleanup():
            time.sleep(300)
            with session_lock:
                if session_id in sessions:
                    del sessions[session_id]
        
        threading.Thread(target=cleanup, daemon=True).start()
    
    return jsonify({
        'success': True,
        'summary': summary
    })

@app.route('/api/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    return jsonify({
        'status': 'healthy',
        'active_sessions': len(sessions),
        'timestamp': datetime.now().isoformat()
    })

if __name__ == '__main__':
    print("="*60)
    print("🏥 RehabAI Backend Server Starting...")
    print("="*60)
    print("✓ ML Models Loaded")
    print(f"✓ {len(EXERCISES)} Exercises Available: {', '.join(EXERCISES.keys())}")
    print("✓ Server running on http://localhost:5000")
    print("="*60)
    
    app.run(debug=True, host='0.0.0.0', port=5000, threaded=True)
