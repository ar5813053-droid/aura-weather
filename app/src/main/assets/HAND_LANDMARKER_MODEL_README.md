# Hand Landmarker model file - required, not included in this repo

`HandTracker` (see `handtracking/HandTracker.kt`) loads the MediaPipe Hand
Landmarker model from the app's assets at runtime. The `.task` model file is
a ~7-10MB binary and is **not committed to this repository** - you need to
download it once and place it here yourself:

```
app/src/main/assets/hand_landmarker.task
```

## How to get it

1. Download the "Hand landmarker (float16)" task bundle from Google's
   MediaPipe model index:
   https://ai.google.dev/edge/mediapipe/solutions/vision/hand_landmarker/index#models
2. Save the downloaded file as exactly `hand_landmarker.task`.
3. Place it in this folder: `app/src/main/assets/hand_landmarker.task`.
4. Rebuild the app.

## If the file is missing

`HandTracker.setup()` catches the failure and reports a clear error through
the app's `onError` callback instead of crashing - you'll see a red banner
in the HandDrive UI ("Couldn't load the hand tracking model…") and the
Start button stays disabled until the model is present and the app is
restarted.

