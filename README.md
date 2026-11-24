# Smart Cane Obstacle Detection

Smart Cane is an offline Android prototype that uses the rear camera to find common objects and announce where they appear. It draws a box around each detection and speaks short alerts such as “Obstacle, center.”

The current prototype is **v0.3**. The camera preview fills the screen, with an orientation menu in the bottom-right corner. Detection runs on the phone with a bundled model. Spoken alerts use an installed English offline text-to-speech voice.

## Use the app

1. Open **Smart Cane** and allow camera access.
2. Tap the **three-line menu** in the bottom-right corner and choose an orientation. The app remembers your selection.
3. Swipe inward from a screen edge to temporarily show the Android status and navigation bars.

| Mode | Detection zones |
| --- | --- |
| **Landscape** | Five equal vertical columns labeled Far Left, Left, Center, Right, and Far Right |
| **Portrait** | Three equal horizontal rows labeled Top, Center, and Bottom |
| **Auto** | Switches between the two layouts according to the phone’s rotation settings |

The center of each visible box determines its zone. The first eligible detection can trigger speech on the first analyzed frame. When several zones are occupied, Center has priority and alerts play one at a time. Each occupied zone speaks once and can alert again after it stays clear for 800 ms.

## Build the APK

Use JDK 17, Android SDK Platform 35, and Android SDK Build-Tools 35.0.0. Open the repository in Android Studio, or run the Gradle wrapper from the repository root. The first build downloads Gradle and its dependencies.

**Windows PowerShell**

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

**macOS or Linux**

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

The debug installer is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Install on a phone

Use an ARM64 phone running Android 7.0 or later with a rear camera.

1. Copy `app-debug.apk` to **Downloads** on the phone. For USB copying, unlock the phone and select **File transfer** from the USB notification.
2. In the phone’s **Files** app, open **Downloads**, then tap the APK. If Android prompts for permission, allow installation from that Files app and return to the installer.
3. Tap **Install** or **Update**, then open **Smart Cane** and allow camera access.
4. For spoken alerts, install an English offline voice in the phone’s text-to-speech settings. Complete the voice download before testing in airplane mode.

With USB debugging enabled and Android SDK Platform-Tools installed, you can also install from the repository root:

```sh
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Test offline on a phone

Use a clear indoor area and stay stationary during testing. Keep using your usual mobility aid.

| Check | Expected behavior |
| --- | --- |
| Start in airplane mode | The rear-camera preview, detection boxes, and speech work with an installed offline voice. |
| Rotate in Landscape and try Portrait | The preview stays upright and the grid follows the selected layout. |
| Move a recognizable object across the grid | The box follows the object, and the spoken direction matches the zone containing the box center. |
| Hold an object still, remove it for at least one second, and return it | The occupied zone speaks once, then speaks again after the object returns. |
| Hold two recognizable objects in separate zones | Both display boxes and their alerts play one at a time. |

The local build and four unit tests passed for v0.3. The tests cover both grid layouts, alert timing, and repeat suppression.

## Implementation

- **Camera** uses CameraX and keeps the latest frame if analysis is busy. MediaPipe runs one CPU detection pass for each analyzed frame.
- **Detection** uses the bundled EfficientDet-Lite0 int8 model with a confidence threshold of 0.50. CameraX transforms map detections from the analysis frame onto the displayed preview.
- **Speech** uses Android text-to-speech with an installed offline voice, preferring lower reported latency. The APK has no Android Internet permission.

The pretrained model recognizes common COCO object categories. Custom pothole detection is planned for the next milestone. [MediaPipe Object Detector documentation](https://developers.google.com/edge/mediapipe/solutions/vision/object_detector)

The bundled model is distributed under the Apache License, Version 2.0. [Model card and checksum](https://huggingface.co/litert-community/MediaPipe-ObjectDetector-EfficientDet-Lite0) · [Bundled model license](app/src/main/assets/efficientdet_lite0-LICENSE.txt)
