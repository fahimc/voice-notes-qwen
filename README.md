# Voice Notes

An Android app for recording a voice sample, creating a local voice profile, and turning text into audio notes. Generated WAV notes stay on the phone until you choose to export or share one.

## What it does

- Record a voice sample and create a reusable voice profile on the device.
- Write or paste text and generate speech in the selected voice.
- Listen to results, then find them later in History.
- Share a WAV through Android's share sheet (including WhatsApp when installed), or save it with Android's file picker.
- Download and run the model locally; synthesis does not upload your text or voice recordings.
- Qwen voice cloning installs automatically on first launch. Add the optional 16-speaker Bengali voice model from Studio or Settings and switch between installed models without downloading them again.
- Choose male or female Bengali voices, or pick from the other built-in Bengali speakers.
- Enter Bengali directly or translate English to Bengali on-device before speech generation.

## Model

The default option is **Qwen3-TTS 0.6B Base Q4_K_M** through [`qwen3-tts.cpp`](https://github.com/Danmoreng/qwen3-tts.cpp):

- `qwen-talker-0.6b-base-Q4_K_M.gguf`
- `qwen-tokenizer-12hz-Q4_K_M.gguf`

The two files total about 884 MB and are downloaded from [Serveurperso/Qwen3-TTS-GGUF](https://huggingface.co/Serveurperso/Qwen3-TTS-GGUF) to app-private storage on first launch. This model supports voice cloning. Installed model files are reused on later launches and app updates.

The optional Bengali model is [Mimic 3 Bengali multi-speaker VITS](https://huggingface.co/csukuangfj/vits-mimic3-bn-multi_low), run locally with [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx). It provides 16 built-in speaker IDs, including female and male choices in the app. It does not support voice cloning. Choosing Bengali installs it once. If you use English-to-Bengali translation, the roughly 30 MB [Google ML Kit on-device model](https://developers.google.com/ml-kit/language/translation/android) downloads on first use. Speech and translation run on the phone after their models are installed. The APK does not bundle model weights. Ensure the phone has enough free storage and memory before downloading and loading either model.

## Requirements

- Android 12 or newer, on an `arm64-v8a` device.
- Around 900 MB free for Qwen or at least 200 MB for Bengali speech and translation setup, plus additional working memory/storage while running the selected model.
- Microphone permission to record a voice profile.
- Android Studio or Android SDK, NDK, CMake, and JDK 17 to build.

## Build locally

Initialize the native runtime submodules and build the release APK:

```powershell
git submodule update --init --recursive
./gradlew.bat :app:assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`. The release build is signed with the local Android debug key by default so it can be installed directly; create and configure your own release signing key before distributing an update that must preserve an existing installation.

## GitHub release

Build the APK locally with the command above, create a version tag, and attach the APK to a GitHub release. This repository does not use GitHub Actions.

## Project notes

- `app/` contains the Android UI, recorder, local history, share/export flow, and JNI bridge.
- `external/qwen3-tts.cpp/` is a Git submodule; its `ggml` dependency is nested below it.
- App source is MIT licensed. The model weights and native runtime retain their own upstream licenses and terms; see their linked repositories and model card before redistribution.

The app began from [Danmoreng/qwen3-tts-android](https://github.com/Danmoreng/qwen3-tts-android), with attribution retained under its MIT license.
