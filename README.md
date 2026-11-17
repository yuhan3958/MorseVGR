# MorseVGR

An Android app that sends **VGR** in Morse code using the rear camera flash.

## Features

- Start and stop transmission with a single button.
- Send once or repeat continuously.
- Set the base dot duration in milliseconds; pulse lengths and gaps scale automatically.
- Highlight the current letter and symbol during transmission.
- Keep the screen awake while sending.
- Stop transmission and attempt to turn off the flash when the app leaves the foreground.
- Display permission, missing hardware, and camera error messages in English.

## Requirements

- Android 11 (API 30) or later.
- A rear-facing camera with an available flash.
- Camera permission, requested when starting transmission.

A physical device with a rear flash is required to check actual light output.

## Usage

1. Open the app.
2. Enter a **Dot duration (ms)** from **1 to 60,000** in whole milliseconds (default: **400**). The timing preview updates immediately. Empty, nonnumeric, and out-of-range values disable Start.
3. Enable **Repeat** if you want continuous transmission. Timing and Repeat can only be changed while stopped; stop first to adjust them.
4. Tap **Start VGR** and grant camera permission if prompted. If the app asks you to start again after permission is granted, tap **Start VGR** again.
5. Watch the highlighted letter and symbol to follow transmission.
6. Tap **Stop** to cancel. Leaving the app also stops transmission.

The dot duration is retained across screen rotation and normal saved-state recreation. Each transmission uses the duration selected when it starts.

If permission was denied, allow camera access in the app's Android settings. If the flash is unavailable, close other camera apps and try again. Keep the flash away from eyes.

## Morse Code and Timing

| Letter | Pattern |
| --- | --- |
| V | `...-` |
| G | `--.` |
| R | `.-.` |

All timings are derived from the selected dot duration, **T**:

| Event | Duration | Default (T = 400 ms) |
| --- | --- | --- |
| Dot: flash on | T | 400 ms |
| Dash: flash on | 3T | 1,200 ms |
| Gap between symbols within a letter: flash off | T | 400 ms |
| Gap between letters: flash off | 3T | 1,200 ms |
| Gap between repeated VGR messages: flash off | 7T | 2,800 ms |

Letter and repeat gaps replace the symbol gap at those boundaries; they are not added to it. A single VGR transmission takes **30T**, excluding camera calls and scheduling overhead: 12 seconds at the default 400 ms. For example, setting T to 200 ms produces 600 ms dashes and letter gaps, 200 ms symbol gaps, and a 1,400 ms repeat gap.

Actual flash timing can vary with Android scheduling and hardware latency, especially at very short dot durations. The input range is not a guarantee that the hardware can reproduce every selected duration accurately.

## How It Works

`MainActivity.kt` displays the Jetpack Compose UI, requests camera permission, and owns the sender. It keeps the screen awake during transmission and calls `stop()` from `onStop()` when the activity leaves the foreground.

`TorchSender.kt` uses `CameraManager` to select a rear-facing camera with a flash. A coroutine reads each Morse pattern, calls `setTorchMode()` to switch the flash on or off, and uses `delay()` for each pulse and gap without blocking the UI thread. Compose state tracks the current letter, symbol, and transmission status.

Pressing Stop cancels the coroutine and attempts to turn off the flash immediately. The coroutine's `finally` block also performs cleanup on completion, cancellation, or camera errors. Transmission does not continue in the background.

The UI validates the dot-duration input before starting. `TorchSender.start(repeat, dotMs)` also validates the range, snapshots the selected duration, and derives all delays from it. The default and allowed dot-duration range and the VGR patterns are defined in the `TorchSender` companion object.

## Project Structure

```text
app/src/main/
  AndroidManifest.xml                  Camera permission and hardware declarations
  java/me/yuhan8954/morsevgr/
    MainActivity.kt                    UI, permission handling, and activity lifecycle
    TorchSender.kt                     Flash control, Morse patterns, and timing
    ui/theme/                          Compose theme
  res/                                 Android resources
```

## Development

Open the repository in Android Studio and let Gradle sync using the project's checked-in configuration. The app uses Kotlin, Jetpack Compose Material 3, Android Camera2 APIs, and lifecycle coroutines. The minimum supported API level is 30; the project currently targets and compiles against API 37.

For device validation, check permission approval and denial, single and repeated transmission, manual stopping during a pulse, backgrounding, missing flash hardware, and camera contention. Confirm pulse durations with suitable measurement equipment if timing accuracy matters.
