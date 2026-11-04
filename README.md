# BetterLife

A private Android app for one person: reminders with full-screen pop-ups, tasks, habits, calendar, notes and body tracking. Android 14+ (minSdk 34, targetSdk 36). Package `com.sanjith.betterlife`.

The rules for building it are in [agent.md](agent.md), what to build is in [context.md](context.md), and the current status is in [PROGRESS.md](PROGRESS.md).

## One-time setup on Windows

1. Install **Android Studio** (already done on this PC). It includes the Java runtime (JDK) and the Android SDK.
2. Open a terminal in this folder (`C:\betterlife`) and point Gradle at Android Studio's Java:
   ```
   set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
   ```
   In PowerShell use `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` instead. (Opening the project in Android Studio does this for you.)
3. `local.properties` (never committed) must contain the SDK path. It also holds the Supabase settings filled in at Prompt S1:
   ```
   sdk.dir=C\:\\Users\\SANJITH\\AppData\\Local\\Android\\Sdk
   supabase.url=
   supabase.anonKey=
   ```
   Only the publishable (anon) key ever goes here. The service_role key must never be in the app.

## Phone setup (Vivo V50e, OriginOS 6)

1. **Developer options:** Settings > About phone (on some versions: Settings > System > About phone) > tap **Software version** 7 times until it says you are a developer. If the wording is different, look for "Build number" or "Version" in the same screen.
2. **USB debugging:** Settings > System management > Developer options > turn on **USB debugging** (path checked on OriginOS 6). While there, turn on **Install via USB** if your phone shows it (Vivo can block USB installs without it).
3. Connect the phone to the PC with a data USB cable. On the phone, choose **File transfer** if asked, and tap **Allow** on the "Allow USB debugging?" prompt (tick "Always allow from this computer").
4. Check the PC sees the phone:
   ```
   %LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe devices
   ```
   You should see one line ending in `device`. `unauthorized` means you have not tapped Allow yet.

I am not certain of every OriginOS 6 menu name; if one differs, note the real name in PROGRESS.md so this file can be corrected.

## Commands (run in `C:\betterlife`)

| What | Command |
|---|---|
| Build the debug app | `gradlew.bat assembleDebug` |
| Build and install on the connected phone | `gradlew.bat installDebug` |
| Unit tests on the PC | `gradlew.bat testDebugUnitTest` |
| Tests on the connected phone | `gradlew.bat connectedDebugAndroidTest` |
| List scheduled alarms | `adb shell dumpsys alarm \| findstr betterlife` |

The debug build installs as **BetterLife Dev** (`com.sanjith.betterlife.debug`), so it never replaces the real release app or its data.
