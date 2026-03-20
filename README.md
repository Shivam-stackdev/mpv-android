# mpv-android (Modified)

A modified version of [mpv-android](https://github.com/mpv-android/mpv-android) 
with VLC-style video library features added.

## Original Project Credit

This project is based on **mpv-android** by the mpv-android contributors.  
Original repo: https://github.com/mpv-android/mpv-android  
License: MIT

## Changes Made (by Shivam-stackdev)

### 🎬 All Videos Screen
- VLC-style home screen showing all videos from device storage
- Grid layout with video thumbnails and names
- Video duration badge on each thumbnail
- Red progress bar showing watched position

### ⏱️ Video Resume / Time Preservation
- Automatically saves playback position when you exit
- Resumes from last position when you reopen a video
- Clears saved position when video is watched till end

### 🔍 Video Options (3-dot menu per video)
- Play
- Delete (with confirmation)
- Share
- Info (duration, size, last position, path)

### 👆 Double Tap Seek (default)
- Double tap left side → seek back 10 seconds
- Double tap right side → seek forward 10 seconds
- Works out of the box without changing any settings

### 📱 Permissions Added
- READ_MEDIA_VIDEO (Android 13+)
- READ_MEDIA_IMAGES (Android 13+)
- READ_EXTERNAL_STORAGE (Android 12 and below)

## Building

See `.github/workflows/build.yml` for automated build via GitHub Actions.

## License

Same as original — MIT License
