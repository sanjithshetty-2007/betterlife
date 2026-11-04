# Manual test notes

Results of manual tests on the Vivo V50e (OriginOS 6, Android 16). Each prompt adds its matrix here.

| Date | Prompt | Test | Expected | Result |
|---|---|---|---|---|

## Prompt 2: Setup Checklist

Phone facts found while building it (OriginOS 6, checked over adb on 2026-11-04):
- `canScheduleExactAlarms()` is **true** on a fresh install with `USE_EXACT_ALARM` alone. No SCHEDULE_EXACT_ALARM fallback is needed.
- `canUseFullScreenIntent()` is **true** on a fresh install (sideloaded over USB).
- Notifications and Display over other apps start **off**.
- Autostart for BetterLife Dev was already **Allowed** (Settings > Apps > Special app access > Autostart).
- Battery: App info > Battery usage > Background power control > **Allow background power usage**.
- Special app access also lists **Turn screen on**, **Full-screen notifications** and **Alarms & reminders** (relevant to Prompts 3 and 4).
- USB debugging lives at Settings > System management > Developer options.

| # | Step | Expected | Result |
|---|---|---|---|
| 1 | Home > Setup checklist | 5 detectable rows + 4 Vivo rows, each with icon, chip (symbol + word), why line | |
| 2 | Notifications > Open settings > turn on > Back | Lands back on the checklist; row turns green OK | |
| 3 | Display over other apps > Open settings > allow > Back | Row turns green OK | |
| 4 | Background battery use > Open settings > Battery usage > Background power control > Allow > Back | Row turns green OK (if it stays yellow, note it: Vivo's switch may not be the Android one) | |
| 5 | Tick all four Vivo rows, close the app from recents, reopen the checklist | Ticks are still there | |
| 6 | Turn Display over other apps off again in system settings, return to the app | Row goes back to red Fix | |
| 7 | Every Open settings button | Opens the matching page (App info for battery and Vivo rows); Back returns to the checklist | |

## Prompt 3: Overlay spike (GATE)

How: Home > Debug: pop-up test > "Show test pop-up in 10 seconds", then do the action within 10 seconds. The log on that screen (newest first) shows the decision and outcome; it is also in logcat under the tag `BetterLifeDebug`.

Design choices:
- Path A (phone in use): our own `TYPE_APPLICATION_OVERLAY` window hosting Compose, owned by a short-lived `shortService` foreground service. The service keeps the process alive during the 10 s wait and while the overlay shows, and removes the overlay in onDestroy. Android stops short services after about 3 minutes; the overlay is removed then (logged).
- Path B (locked or screen off): an IMPORTANCE_HIGH notification with a full-screen intent opening FullScreenReminderActivity (showWhenLocked, turnScreenOn).
- Back on the overlay: OnBackInvokedCallback (Android 16 predictive back) plus a KEYCODE_BACK fallback.
- Background foreground-service start (docs/PLAN_CHANGES.md, Prompt 3 note): not tested here, because this spike starts the service from the visible app. Prompt 4 tests the start from an alarm in the background.

| # | Test | Expected | Result (2026-11-04) |
|---|---|---|---|
| 1 | Stay in the app | Overlay over BetterLife; Done closes it | PASS (AI over adb): OVERLAY (PHONE_IN_USE), dismissed with DONE |
| 2 | Open Chrome | Overlay over Chrome | **PASS (AI over adb, screenshot)**: OVERLAY (PHONE_IN_USE) |
| 3 | Open a video app (play a video) | Overlay over the video; video may pause or keep playing | |
| 4 | Lock the phone and wait | Screen turns on, pop-up over the lock screen | **PASS (AI over adb, screenshot)**: FULL_SCREEN_INTENT (LOCKED_OR_SCREEN_OFF), screen woke, keyguard still showing, Done closed it and cleared the notification |
| 5 | Rotate the phone while it shows | Pop-up re-lays out; no stuck or duplicate window | |
| 6 | Press Back | Overlay closes, nothing stuck | PASS (AI over adb, key event): dismissed with BACK, no overlay window left. Owner: also try the back swipe gesture |
| 7 | Tap Done within the first half second | Tap ignored ("tap ignored (tap guard)" in the log), button works afterwards | |
