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

## Prompt 4: Alarm spike (GATE)

How: Home > Debug: alarm test. Pick the alarm type with the switch (off = setExactAndAllowWhileIdle, on = setAlarmClock), tap "Alarm in 1/5/15 min", then do the test. The log on that screen is saved to a file, so it survives the app being killed; newest lines are on top. Over adb: `adb shell run-as com.sanjith.betterlife.debug tail -n 20 files/debug_log.txt`.

Design decision (asked for by the prompt): the receiver does NOT show the overlay itself. A receiver's process can be killed seconds after onReceive returns, which would remove the window. Instead: exact alarm -> TestAlarmReceiver (logs, starts the service, clears the pending alarm) -> ReminderPopupService, a foreground service of type `systemExempted` -> overlay or full-screen notification. `systemExempted` is allowed because the app holds USE_EXACT_ALARM (Android docs, foreground service types) and has no time limit, unlike `shortService` (~3 minutes). Starting a foreground service from the background is allowed because an exact alarm just fired. If Android refuses anyway, the receiver posts a full-screen notification directly (logged).

Re-arming: the pending alarm is saved in DataStore. RearmReceiver handles BOOT_COMPLETED and MY_PACKAGE_REPLACED; an alarm whose time passed while the phone was off fires 5 seconds after boot and is logged as missed (DEVICE_OFF).

Observations (2026-11-04, checked by the AI over adb):
- Swiping BetterLife Dev away in Vivo recents kills the process but does NOT force-stop the app (`stopped=false`), so the exact alarm survives.
- After a force stop, the next app start delivers BOOT_COMPLETED to the app ("Boot: ..." lines with no real reboot). Not documented behaviour we rely on; noted only.
- Alarms are exact on this phone: fired within 0.1 to 1.0 s of the scheduled second.

Results table (fill in "?" cells). Row H (force stop) is EXPECTED to not fire.

| Row | Test | setExactAndAllowWhileIdle | setAlarmClock |
|---|---|---|---|
| A | App open | ? | ? |
| B | App in background, using Chrome | **PASS** (AI, 11:19:18, overlay over Chrome) | ? |
| C | App swiped away from recents | **PASS** (AI, process killed, alarm fired 11:20:58 in a fresh process, overlay shown) | ? |
| D | Screen off 15+ minutes (Doze) | ? | ? |
| E | Reboot after arming a 5-minute alarm | ? | ? |
| F | Battery saver on | ? | ? |
| G | Phone clock moved forward 1 hour | ? | ? |
| H | Force stop from App info (expected: does not fire) | ? | ? |

Also seen: an owner-scheduled alarm at 11:22:08 fired on time over the app in use and was dismissed with Done.

Default to use for FULL_SCREEN and ALARM levels: (decide after the table is filled in)
