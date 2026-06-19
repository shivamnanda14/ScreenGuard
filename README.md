# 🛡️ ScreenGuard: Cross-Platform Distance & Eye Protection Suite

ScreenGuard is an intelligent, automated digital health utility designed to combat computer vision syndrome and eye strain. By leveraging device cameras and real-time physical proximity estimation, the suite actively monitors user distance and safeguards health across both mobile and desktop environments.

---

### 📥 Downloads & Installation

> 💡 **Important:** You do not need to compile the source code yourself to test the applications. Pre-compiled, production-ready installation files are available directly from the repository.

1. Navigate to the [Releases](https://github.com/shivamnanda14/ScreenGuard/releases) section on the right side of this page.
2. Download the appropriate file for your device:
   * **📱 Android Mobile:** Download `app-debug.apk` 
   * **💻 Windows Desktop:** Download `screen_guard.exe`

#### 🔧 Setup Quick-Start
* **Windows Setup:** Double-click `screen_guard.exe` to launch. Look for the shield icon 🛡️ in your system tray. To close, right-click the icon and select *Quit Application*.
* **Android Setup:** Install the `app-debug.apk` onto your device. Toggle on the *Accessibility Service* permission when prompted to allow the smart overlay window to function seamlessly over your apps.

---

## 🚀 The Dual Architecture

### 📱 1. Android Application (Native)
A low-overhead Android background utility that ensures safe screen distances, especially for children.
* **Core Tech:** Kotlin, Jetpack Compose, Material 3, AndroidX Lifecycle Components.
* **System Integration:** Leverages a custom Android `AccessibilityService` Engine to persistently monitor frame layouts and draw animated overlay constraints safely over any running application without system lag.

### 💻 2. Windows Desktop Utility (Native)
A lightweight background application that resides silently within the Windows system tray and dynamically locks the OS if the user leans too close.
* **Core Tech:** Python 3.13, OpenCV, Pillow.
* **System Integration:** Runs a multi-threaded architecture separating the background Haar Cascade Face Detection Loop from a responsive `Pystray` System Tray Interface. Triggers native OS locks via Windows kernel APIs.

---

## 📊 Core Engineering Problems Solved

* **Zombie Process Blocks:** Resolved OS file-lock exceptions (`WinError 5`) during binary compilation by writing multi-threaded cleanup handlers.
* **Wide-Angle Webcam Calibration:** Calibrated spatial ratios to account for wide-angle laptop webcam lenses versus narrow mobile sensors, reducing false triggers.
* **Asset Portability:** Engineered a self-healing model loader in Python to dynamically pull runtime dependencies offline if PyInstaller boundaries stripped local system paths.

---

---

---
## 📱 Android App Upgrades (v1.1.0 — Child Mode Update)

The Android tracking core has been completely overhauled to turn it into an un-bypassable child safety mechanism. 

* **Dual Biometric Calibration Profiles:** Integrated an interactive runtime preference switch allowing users to cycle between **Adult Mode** and **Child Mode** tracking profiles, precisely tuned to handle the 25 cm eye-to-screen threshold across varying age groups.
* **Immersive Screen Shield Overlay:** Upgraded traditional micro-banners into an absolute, full-screen viewport canvas running on an intensive **85% background dim layer** to pull absolute focus away from media or games.
* **Hardware-Accelerated Window Blur:** For devices running Android 12 (API 31) and above, the system invokes a native `blurBehindRadius` of 35, transforming the background viewport into a gorgeous frosted-glass structural lockdown.
* **Touch Input Absorption:** The root container intercepts and entirely consumes all pointer touch vectors. If a child attempts to frantically click through or around the overlay, the system absorbs the inputs, securing the device until they physically step back.

---

## 📖 Deep-Dive Technical Documentation

For an in-depth breakdown of the underlying cross-platform architecture, multi-threaded concurrency models, CameraX + ML Kit spatial tracking sensor calibration, and Android accessibility layer kernel integrations, please check out our full [Complete Project Documentation PDF](./ScreenGuard_Technical_Architecture_Documentation%20git.pdf).
