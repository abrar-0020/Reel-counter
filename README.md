<img width="647" height="631" alt="image" src="https://github.com/user-attachments/assets/cd298677-1136-49c4-bc5d-1ea73c199e54" /><div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://aistudio.google.com/_/upload/7f09b80c-dc2d-44fd-8136-d8ea5a6f804c/attachment/1783450010.599765000/blobstore/prod/makersuite/spanner_managed/global::000054e2ea70026d:0000015f:2:000054e2ea70026d:0000000000000001::70331411cef938ee:000001f7d0f018bc:00065609d0537bdf" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/7f09b80c-dc2d-44fd-8136-d8ea5a6f804c

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
