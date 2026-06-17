# 🛡️ ScreenGuard: Cross-Platform Distance & Eye Protection Suite

ScreenGuard is an intelligent, automated digital health utility designed to combat computer vision syndrome and eye strain. By leveraging device cameras and real-time physical proximity estimation, the suite actively monitors user distance and safeguards health across both mobile and desktop environments.

---

## 🚀 The Dual Architecture

### 📱 1. Android Application (Native)
A low-overhead Android background utility that ensures safe screen distances, especially for children.
* **Core Tech:** Kotlin, Jetpack Compose, Material 3, Android X Lifecycle Components.
* **System Integration:** Leverages a custom **Android AccessibilityService Engine** to persistently monitor frame layouts and draw animated overlay constraints safely over any running application without system lag.

### 💻 2. Windows Desktop Utility (Native)
A lightweight background application that resides silently within the Windows system tray and dynamically locks the OS if the user leans too close.
* **Core Tech:** Python 3.13, OpenCV, Pillow.
* **System Integration:** Runs a multi-threaded architecture separating the background **Haar Cascade Face Detection Loop** from a responsive **Pystray System Tray Interface**. Triggers native OS locks via Windows kernel APIs.

---

## 🛠️ Installation & Usage

### Windows Setup
1. Download `screen_guard.exe` from the Releases section.
2. Double-click to launch. Look for the shield icon 🛡️ in your system tray.
3. To close, right-click the icon and select **Quit Application**.

### Android Setup
1. Install the `app-debug.apk` onto your device.
2. Toggle on the **Accessibility Service** permission when prompted to allow the smart overlay to function.

---

## 📊 Core Engineering Problems Solved
* **Zombie Process Blocks:** Resolved OS file-lock exceptions (`WinError 5`) during binary compilation by writing multi-threaded cleanup handlers.
* **Wide-Angle Webcam Calibration:** Calibrated spatial ratios to account for wide-angle laptop webcam lenses versus narrow mobile sensors, reducing false triggers.
* **Asset Portability:** Engineered a self-healing model loader in Python to dynamically pull runtime dependencies offline if PyInstaller boundaries stripped local system paths.
