<div align="center">
<img width="30%" alt="Screenshot 1" src="src/Screenshot_20260907_175125_Goo-goo%20ga-ga.jpg" />
<img width="30%" alt="Screenshot 2" src="src/Screenshot_20260907_175159_Goo-goo%20ga-ga.jpg" />
<img width="30%" alt="Screenshot 3" src="src/Screenshot_20260907_175206_Goo-goo%20ga-ga.jpg" />
</div>

# Googoo-gaga

An Android app originally prototyped in **AI Studio**, now refined and enhanced with custom development.

## About This Project

This project started as a prototype generated through [Google AI Studio](https://ai.studio/apps/e3c8a1ca-a96a-472b-8663-801c84376ede) and has since been developed and refined with additional features [...]

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)

1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
7. If you have already published your app in AI Studio, please [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset)
