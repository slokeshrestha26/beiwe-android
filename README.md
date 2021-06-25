## Compiling and running the Beiwe Android app

### Compiling
1. To compile and sign the app, you must add a directory called `private` (`beiwe-android`'s `.gitignore` file keeps the `private` directory out of Git), and a file called `private/keystore.properties`, containing these lines (no quotes around the values that you fill in):
    ```
    storePassword=KEYSTORE_PASSWORD
    keyPassword=KEY_PASSWORD
    keyAlias=KEY_ALIAS
    storeFile=KEYSTORE_FILEPATH
    ```

2. If it's your first time running the project, **open Android Studio and click "Sync Project with Gradle Files"**.  If you run into errors, open Android Studio's "SDK Manager" and make sure you have the correct SDK Platform installed (the "API level" should match the one specified in app/build.gradle's `compileSdkVersion`).

3. You'll need to **generate a key file** by running Build -> Generate Signed Bundle/APK, and then, inside `private/keystore.properties`, update the four values with the information from your newly-generated key and keystore.

4. (Optional) You can also configure a Sentry DSN for each build type inside your `private/keystore.properties` file.
    ```
    releaseDSN=https://publicKey:secretKey@host:port/1?options
    betaDSN=https://publicKey:secretKey@host:port/1?options
    developmentDSN=https://publicKey:secretKey@host:port/1?options
    ```

### Setting up Firebase
To receive push notifications, add Firebase to your Android Project following [these steps](https://firebase.google.com/docs/android/setup) up through Step 3. 


### Build Variants and Product Flavors
When you build the Android app, you choose one of the Build Variants and one of the Product Flavors.  There are three of each, specified in the `buildTypes` section of `app/build.gradle`.  To select which Build Variant the app compiles as, go to **Build** > **Select Build Variant** in the menu bar [(see the documentation)](https://developer.android.com/studio/run/index.html#changing-variant).

#### The three Build Variants are:

| Build Variant | App name | Password requirements | Debug interface |
| --- | --- | --- | --- |
| **release** | "Beiwe" or "Beiwe2" | At least 6 characters | No |
| **beta** | "Beiwe-beta" or "Beiwe2-beta" | At least 1 character | Yes |
| **development** | "Beiwe-development" or "Beiwe2-development" | At least 1 character | Yes, plus extra buttons only useful for develpers, like buttons to crash the app.  Also includes some extra logging statements that write to LogCat, but not to app log files that get uploaded. |

#### The three Product Flavors are:

| Product Flavor | App Name | Intended for | Server URL | Record text message and call log stats | Request background location permission |
| --- | --- | --- | --- | --- | --- |
| **googlePlayStore** | Beiwe2 | Distribution via Google Play Store | Customizable at registration | No (forbidden by Play Store policies) | No (forbidden by Play Store policies) |
| **onnelaLabServer** | Beiwe | Download APK from studies.beiwe.org/download | Hardcoded to studies.beiwe.org | Yes | Yes |
| **commStatsCustomUrl** | Beiwe | Download APK from `/download` link on other Beiwe deployments | Customizable at registration | Yes | Yes |
