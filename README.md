# DualCam 📷🎥

Dual-camera split-screen video recording app for Android with simultaneous front and back camera preview, customizable split layouts, and a built-in video library.

## ✨ Features

- **Concurrent Dual Camera Capture**: Record using front and rear cameras simultaneously (on supported Android devices).
- **Multiple Layout Modes**: Side-by-side, picture-in-picture (PiP), and top/bottom split view.
- **Pinch-to-Zoom**: Interactive pinch-to-zoom controls in the camera viewfinder with live HUD zoom level feedback.
- **Audio & Video Metering**: Real-time dual stream recording with live audio level tracking.
- **Video Library**: Integrated gallery and player for browsing and previewing recorded dual-stream clips.
- **Modern Jetpack Compose UI**: Material 3 theming with support for edge-to-edge display and dynamic colors.

---

## 🚀 Automated APK Releases (GitHub Actions)

This repository includes a fully automated GitHub Actions workflow located at [`.github/workflows/release.yml`](.github/workflows/release.yml).

### 1. Triggering an APK Release

You can trigger a release in two ways:

#### Option A: Push a Version Tag (Recommended for releases)
```bash
git tag v1.0.0
git push origin v1.0.0
```
Pushing any tag matching `v*` (e.g. `v1.0.0`, `v0.1.0`) will automatically:
1. Build the production release APK.
2. Sign the APK so it can be installed on Android devices immediately.
3. Compute SHA-256 checksums for verification.
4. Create a new GitHub Release with release notes and attach the `.apk` and `.sha256` files.

#### Option B: Manual Trigger (One-click in GitHub)
1. Go to the **Actions** tab on your GitHub repository: [Actions](https://github.com/zerokid/Android-Dual-Camera/actions).
2. Click on **Build & Release APK** in the left sidebar.
3. Click the **Run workflow** dropdown button.
4. Enter the release version tag (e.g., `v1.0.0`) and click **Run workflow**.

#### Option C: Normal Push to `main`
Whenever you push commits to `main`, the workflow will compile and test-build the APK, uploading it as a workflow artifact in the GitHub Actions summary page without creating an official release.

---

## ⚙️ GitHub Repository Configuration

### Public Repository Status
This repository is configured as **Public** (`visibility: public`), allowing anyone to view the repository and download APK releases without needing GitHub authentication.

To verify or change repository visibility:
1. Go to repository **Settings** -> **General**.
2. Scroll to the bottom to the **Danger Zone** section.
3. Check the **Change repository visibility** setting.

### Workflow Permissions (Required for Releases)
For GitHub Actions to publish releases, ensure workflow write permissions are granted:
1. Go to repository **Settings** -> **Actions** -> **General**.
2. Under **Workflow permissions**, select **Read and write permissions**.
3. Check **Allow GitHub Actions to create and approve pull requests** (if applicable) and click **Save**.

---

## 🔑 Custom Release Signing (Optional)

By default, the GitHub Actions workflow automatically generates a release signing keystore if no custom key is configured, so release builds succeed and are immediately installable on devices out-of-the-box.

To sign releases with your own production keystore:
1. Base64-encode your keystore file:
   ```bash
   base64 -i my-upload-key.jks | pbcopy   # macOS
   base64 -w 0 my-upload-key.jks         # Linux
   ```
2. In your repository on GitHub, navigate to **Settings** -> **Secrets and variables** -> **Actions**.
3. Add the following repository secrets:
   - `RELEASE_KEYSTORE_BASE64`: The base64 output of your `.jks` file.
   - `STORE_PASSWORD`: Keystore password.
   - `KEY_ALIAS`: Keystore alias (defaults to `upload` if omitted).
   - `KEY_PASSWORD`: Key password.

### Optional Integration Secrets
- `GEMINI_API_KEY`: API key for Gemini / AI Studio integration (if enabled).
- `GOOGLE_SERVICES_JSON`: Contents of `google-services.json` if using Firebase features.
