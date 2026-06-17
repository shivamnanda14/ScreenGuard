import threading
import time
import os
import sys
import cv2
import pystray
import urllib.request
from PIL import Image, ImageDraw

# Global control flag to safely stop the background threads
is_running = True
too_close_start_time = None
lock_threshold_duration = 5.0  # 5 seconds limit

def get_cascade_filepath():
    """
    Locates or downloads the face detection model.
    This prevents PyInstaller compilation packaging failures.
    """
    filename = "haarcascade_frontalface_default.xml"
    
    # Place file in AppData to prevent write-permission blocks
    appdata_dir = os.path.join(os.environ.get("LOCALAPPDATA", os.path.expanduser("~")), "ScreenGuard")
    if not os.path.exists(appdata_dir):
        os.makedirs(appdata_dir)
        
    local_path = os.path.join(appdata_dir, filename)
    
    # If the file is missing, fetch it silently from OpenCV's official GitHub
    if not os.path.exists(local_path):
        url = "https://raw.githubusercontent.com/opencv/opencv/4.x/data/haarcascades/haarcascade_frontalface_default.xml"
        try:
            urllib.request.urlretrieve(url, local_path)
        except Exception as e:
            # Fallback path if offline (tries current directory)
            return filename
            
    return local_path

def lock_workstation():
    """Triggers the native Windows workstation lock command."""
    if os.name == 'nt':
        os.system("rundll32.exe user32.dll,LockWorkStation")

def monitor_distance_worker():
    """
    Background worker thread running the OpenCV face detection engine.
    Uses lightweight, offline Haar Cascades to check face proximity.
    """
    global is_running, too_close_start_time
    
    cascade_path = get_cascade_filepath()
    face_cascade = cv2.CascadeClassifier(cascade_path)
    
    # Fallback to default path if AppData failed
    if face_cascade.empty():
        face_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_frontalface_default.xml')
        
    cap = cv2.VideoCapture(0)
    
    if not cap.isOpened():
        return

    while is_running:
        ret, frame = cap.read()
        if not ret:
            time.sleep(0.1)
            continue
            
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        frame_width = frame.shape[1]
        
        # Detect faces in the frame
        faces = face_cascade.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=5, minSize=(30, 30))
        
        is_anyone_too_close = False
        
        for (x, y, w, h) in faces:
            # Calculate face width relative to frame width
            width_ratio = float(w) / float(frame_width)
            
            # CALIBRATION UPGRADE: Laptop webcams are wide-angle. 
            # 0.28 is the optimal threshold for natural laptop seating distances
            if width_ratio > 0.28:
                is_anyone_too_close = True
                break

        if is_anyone_too_close:
            if too_close_start_time is None:
                too_close_start_time = time.time()
            elif time.time() - too_close_start_time >= lock_threshold_duration:
                lock_workstation()
                too_close_start_time = None  # Reset tracking state after locking
        else:
            too_close_start_time = None  # Reset if they pull back

        # Prevent high CPU consumption (targets ~30 FPS analysis cycles)
        time.sleep(0.03)
        
    cap.release()

def create_tray_icon_image():
    """Generates a simple, custom visual red shield icon for the Windows system tray."""
    image = Image.new('RGB', (64, 64), color='#0F172A')  # Slate-900 background
    dc = ImageDraw.Draw(image)
    dc.ellipse((16, 16, 48, 48), fill='#DC2626', outline='#FDA4AF', width=2) 
    return image

def on_exit_clicked(icon, item):
    """Gracefully shuts down camera loops and removes the system tray icon."""
    global is_running
    is_running = False
    icon.stop()

def setup_system_tray():
    """Registers and launches the multi-threaded system tray application."""
    # 1. Spin up face detection engine on background thread
    bg_thread = threading.Thread(target=monitor_distance_worker, daemon=True)
    bg_thread.start()
    
    # 2. Configure System Tray Context Menu using CORRECT MenuItem objects
    icon_menu = pystray.Menu(
        pystray.MenuItem("🛡️ ScreenGuard Active", lambda: None, enabled=False),
        pystray.MenuItem("Quit Application", on_exit_clicked)
    )
    
    # 3. Create the tray icon
    tray_icon = pystray.Icon(
        name="screenguard",
        icon=create_tray_icon_image(),
        title="ScreenGuard - Eye Protection",
        menu=icon_menu
    )
    
    tray_icon.run()

if __name__ == "__main__":
    setup_system_tray()