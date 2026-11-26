# Bulk WA Sender (Android, Kotlin + Jetpack Compose)

1. Open this folder in Android Studio (Koala or newer) and let Gradle sync.
2. Run on a device/emulator.

## Meta setup (one time)
- developers.facebook.com -> create Business app -> add WhatsApp product.
- Add and verify your business phone number; note the **Phone Number ID**.
- Create a **System User** in Business Settings, give it the app + WhatsApp account, generate a **permanent token**
  with `whatsapp_business_messaging` and `whatsapp_business_management`.
- In WhatsApp Manager -> Message templates, create a MARKETING/UTILITY template (e.g. body: "Hello {{1}}, ...") and wait for approval.

## Use
Enter Phone Number ID, token, template name + language code, paste or import contacts
(`9447xxxxxx,Name` per line; 10-digit numbers get the country code), tap Start sending.

## Build on GitHub
1. Create a repo and push this folder to `main`.
2. Open the **Actions** tab -> **Build APK** runs automatically (or use *Run workflow*).
3. Download `BulkWA-debug-apk` from the run's Artifacts, unzip, install `app-debug.apk` on your phone.
4. To publish a downloadable release: `git tag v1.0 && git push origin v1.0` - the APK is attached to the GitHub Release.

The workflow installs Gradle 8.7 itself, so no Gradle wrapper files are needed.
