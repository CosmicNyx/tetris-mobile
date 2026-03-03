# Tetris

A clean, offline Tetris game for Android. No ads. No accounts. Just Tetris.

## Features

- Classic and Fixed Speed game modes
- Ghost piece and hold piece
- Lock delay with visual indicator
- Screen shake on line clears
- Haptic feedback
- High contrast mode
- Settings panel with toggles
- Game over stats screen (score, level, lines, time)
- Smooth 60fps animations

## Controls

| Gesture | Action |
|---|---|
| Tap | Rotate |
| Drag left / right | Move |
| Drag down | Soft drop |
| Fast swipe down | Hard drop |
| Fast swipe up | Hold |
| Top-right button | Pause |

## Building

Requires Android Studio or the Android SDK with Gradle.

```
gradlew assembleDebug
```

APK will be at `app/build/outputs/apk/debug/app-debug.apk`

## Requirements

- Android 5.0+ (API 21)
- No internet permission required
