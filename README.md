# English Oral Practice (口语练习)

Android app for English speaking practice with video-based learning.

## Features

### P0 (Current Release)
- **US1: Video Import**
  - Import local videos via SAF (Storage Access Framework)
  - Persistent URI permissions (no file copying)
  - Supported formats: MP4, MKV, WebM
  - Validation: corrupt/invalid files are rejected with clear error messages
  - Room database for library metadata

- **US2: Video Library**
  - Display imported videos with title and duration
  - Tap to open player
  - Graceful handling of inaccessible videos (marked as "bad")
  - Delete videos from library

### Stubbed for P1
- **Subtitle Support**: SRT parsing, external SRT preferred over embedded
- **Speech Recognition**: ASR integration, pronunciation scoring

### Planned for P2
- Learning reports and progress tracking

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Video Player**: Media3 / ExoPlayer
- **Database**: Room
- **File Access**: SAF with `takePersistableUriPermission`
- **Architecture**: MVVM with ViewModels

## Project Structure

```
app/src/main/java/com/example/englishoralpractice/
├── EnglishOralPracticeApp.kt    # Application class
├── MainActivity.kt               # Entry point + Navigation
├── library/                      # Video library module
│   ├── data/                     # Room entities, DAO, database
│   ├── domain/                   # Import errors, result types
│   ├── ui/                       # LibraryScreen, LibraryViewModel
│   └── VideoImporter.kt          # SAF import + validation
├── player/                       # Video player module
│   ├── PlayerScreen.kt
│   └── PlayerViewModel.kt
├── subtitle/                     # Subtitle module (P1 stub)
│   └── SrtParser.kt
├── speech/                       # Speech module (P1 stub)
│   └── SpeechStub.kt
└── ui/theme/                     # Compose theme
    └── Theme.kt
```

## Requirements

- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 34 (compileSdk)
- Minimum Android 8.0 (API 26)
- JDK 17

## Build & Run

### In Android Studio

1. Clone the repository:
   ```bash
   git clone <repo-url>
   cd english-oral-practice
   ```

2. Open the project in Android Studio

3. Wait for Gradle sync to complete

4. Run on emulator or device:
   - Select a device from the toolbar
   - Click Run (▶) or press `Shift + F10`

### Command Line (requires Android SDK)

```bash
# Set ANDROID_HOME if not already set
export ANDROID_HOME=/path/to/Android/Sdk

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# APK location: app/build/outputs/apk/debug/app-debug.apk
```

### Build Without Android SDK

If building on a system without Android SDK, the project structure is complete and ready to open in Android Studio. The IDE will download necessary SDK components automatically.

## Usage

1. **Import a Video**
   - Tap the + FAB button
   - Select a video file (MP4/MKV/WebM)
   - The app will validate and add it to your library

2. **Play a Video**
   - Tap on any video in the library list
   - Use ExoPlayer controls for playback

3. **Delete a Video**
   - Tap the delete icon on a video item
   - Confirm deletion

## Design Decisions

### SAF + Persistent URI
- No file copying: videos stay in their original location
- Permissions persist across app restarts
- Better storage efficiency

### Single ExoPlayer Instance
- Held in PlayerViewModel, released on `onCleared()`
- Never created during recomposition
- Prevents resource leaks

### Import Validation
- Format whitelist (mp4/mkv/webm)
- MediaMetadataRetriever for metadata extraction
- MediaExtractor to verify video track exists
- Clear error messages for all failure cases

### Error Handling
- Bad library items marked (not crashed)
- URI accessibility checked before playback
- Graceful degradation throughout

## License

MIT License
