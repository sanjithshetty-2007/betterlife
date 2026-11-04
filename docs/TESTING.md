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
| A | App open | ? | **PASS** (AI, scheduled 11:35:20, received 11:35:20.189, overlay over the app) |
| B | App in background, using Chrome | **PASS** (AI, 11:19:18, overlay over Chrome) | **PASS** (AI, scheduled 11:36:55, received 11:36:55.639, overlay over Chrome) |
| C | App swiped away from recents | **PASS** (AI, process killed, alarm fired 11:20:58 in a fresh process, overlay shown) | **PASS** (AI, card swiped, process killed, not force-stopped; received 11:38:34.617, overlay over the home screen, screenshot) |
| D | Screen off 15+ minutes (Doze) | ? | **PASS** (owner; log read by the AI): scheduled 12:04:04 for 12:19:04 with the phone locked, received 12:19:04.515, Path B full-screen pop-up over the lock screen, Done. Note: 15 minutes may not reach Android's deepest idle state; Prompt 13's multi-day real use covers that. |
| E | Reboot after arming a 5-minute alarm | ? | **PASS** (owner rebooted; log read by the AI): scheduled 11:24:16 for 11:29:16, boot re-armed it at 11:26:18 at the original time, received 11:29:17 while locked, Path B full-screen pop-up shown, Done |
| F | Battery saver on | ? | ? |
| G | Phone clock moved forward 1 hour | ? | ? |
| H | Force stop from App info (expected: does not fire) | ? | ? |

Also seen: an owner-scheduled alarm at 11:22:08 fired on time over the app in use and was dismissed with Done.

Default to use for FULL_SCREEN and ALARM levels: **setAlarmClock** (owner decision 2026-11-04; the only type that passed all gate rows A to E). NORMAL-level reminders use setExactAndAllowWhileIdle (no status-bar alarm icon).

## Prompt 7: Delivery pipeline and scheduler

How: Home > Debug: reminder engine. Each button creates a test task due in 1 or 2 minutes with one reminder at that level. "Scheduled alarms" lists the alarms set right now; "Reminder log" shows every scheduling and firing with its reason. "Remove test tasks" deletes the test tasks and cancels their alarms.

Check the real AlarmManager from the PC (phone connected):
```
adb shell dumpsys alarm | findstr REMINDER_ALARM
```
Each pending reminder shows as one `RTC_WAKEUP #n: Alarm{... tag=*walarm*:com.sanjith.betterlife.action.REMINDER_ALARM ...}` line (plus tag lines and possibly a "Next wake from idle" line pointing at the same alarm). `flags 3` = alarm clock (Full and Alarm levels), `flags 9` = exact while idle (Normal). Tapping "Reschedule all" any number of times must not add lines.

| # | Test | Expected | Result |
|---|---|---|---|
| 1 | +1 min Normal | Notification on the "Reminders" channel at the minute; log FIRED NORMAL NOTIFICATION_ONLY | **PASS** (AI, 2026-11-04: scheduled 13:25:17.153, delivered 13:25:17.270) |
| 2 | +2 min Full | Pop-up with the task title over the app in use; log FIRED FULL_SCREEN OVERLAY | **PASS** (AI, 2026-11-04: scheduled 13:26:21.820, delivered 13:26:21.880, screenshot) |
| 3 | +1 min Alarm, phone on silent | Pop-up plus a loud alarm sound on the alarm stream | |
| 4 | dumpsys alarm, then Reschedule all 3 times, then dumpsys again | Same number of REMINDER_ALARM alarms, no duplicates | AI: exactly 2 alarms for 2 reminders |
| 5 | +2 min Full, swipe the app away from recents | Pop-up still appears | |
| 6 | +2 min Full, restart the phone, unlock and wait | Pop-up still appears (or, if the time passed while off, one "missed" summary notification) | |
| 7 | +1 min Full, lock the phone | Screen wakes with the pop-up over the lock screen | |
| 8 | Create a test reminder, then "Remove test tasks" | Its alarm disappears from the list and from dumpsys | |
| 9 | Change the phone's time zone in Settings and back | Log shows "Reminder engine rescheduled (TIMEZONE_CHANGED)" in Debug: alarm test | |

## Prompt 8: Production pop-up

How: Home > Debug: reminder engine, "+1 min Full" (or Alarm). "Complete test tasks" completes them from outside the pop-up; the "Hide titles on the lock screen" switch is the privacy setting until the Settings screen exists (Prompt 27).

| # | Test | Expected | Result |
|---|---|---|---|
| 1 | +1 min Full, wait | Pop-up with Blip, "Pop-up" badge, ⓘ, due time, title, Snooze / Done / Reschedule; card fades and scales in | **PASS** (AI 2026-11-04, screenshot; fired 13:45:55.941) |
| 2 | Snooze > 10 min | Pop-up closes; log SNOOZED count 1; new alarm 10 minutes later | **PASS** (AI: SNOOZED count 1 until 13:56:28, ALARM_CLOCK alarm set) |
| 3 | Let the snoozed one come back, snooze it twice more, let it come back | Card says "You have snoozed this 3 times. Move it to tomorrow instead?" with a button | |
| 4 | Snooze options at 21:00 | "Tonight" is greyed out | |
| 5 | Snooze > Pick time, enter a time | Comes back at that time | |
| 6 | Tap ⓘ | Item, due time, rule ("At the time"), level, Edit button | UI test passes on the phone; live tap: owner |
| 7 | Done | Task completed, no further alarm for it, log COMPLETED | |
| 8 | Reschedule | App opens on "Edit item" showing the task title; log RESCHEDULED | |
| 9 | Full: leave it unanswered | One gentle sound, a little louder each minute; after 5 minutes it closes, log MISSED, one quiet notification | |
| 10 | Alarm with the phone on silent | Rings in a loop on the alarm stream and vibrates; stops the instant any button is tapped | |
| 11 | Two reminders in the same minute (+1 Full and +1 Alarm) | ONE window with "2 reminders" and two cards | |
| 12 | Pop-up showing, tap "Complete test tasks" in the app behind it (or wait for a completion) | The card closes by itself | |
| 13 | Hide titles ON, lock the phone, +1 min Full | Lock screen shows "Reminder", not the title | |
| 14 | Developer options > animator duration scale off | Pop-up appears and closes instantly, still usable; Blip does not blink | |
| 15 | Back gesture on the pop-up | Top card closes (log DISMISSED) | |

## Prompt 9: Tasks

How: Home > Tasks. Quick-add on top, tabs Today / Upcoming / Overdue / Inbox, the + opens the full editor, Projects and Check-ins in the header. Check scheduled alarms with `adb shell dumpsys alarm | findstr REMINDER_ALARM`.

Defaults chosen (change per task in the editor): a dated, timed task gets "1 day before" (notification) and "at the time" (pop-up); a whole-day task gets one notification at 09:00 on the day. `#tags` stay in the title until the Tag table exists (Prompt 21).

| # | Test | Expected | Result |
|---|---|---|---|
| 1 | Type "call mom tomorrow 5pm !high" | Preview chips Tomorrow, 5:00 PM, High above the keyboard; Add enabled | **PASS** (AI 2026-11-04, screenshot) |
| 2 | Tap Add | Task in Upcoming with a red High flag and "Tomorrow 5:00 PM"; 2 reminder alarms | **PASS** (AI: Upcoming (1), 2 alarms) |
| 3 | Tick it | Strikes through, leaves the list, "Done: call mom" with Undo; its alarms disappear | **PASS** (AI: alarms 2 -> 0, snackbar screenshot) |
| 4 | Tap Undo within 6 seconds | Task back in Upcoming, alarms back | **PASS** (AI: Upcoming (1), alarms 2) |
| 5 | Bin icon | Task deleted with Undo; alarms gone | **PASS** (AI: alarms 0). Owner: try Undo after delete |
| 6 | "meeting at 5" | Asks AM or PM inline; Add only after answering | UI test passes on the phone |
| 7 | Your own 5 real tasks by quick-add | Parsed as you meant (report anything odd) | |
| 8 | + > new task with a date, "1 day before" + "at time", save | Both alarms appear in dumpsys | |
| 9 | Repeating task (Daily), tick it | Next one appears with tomorrow's date; Undo removes it and reopens the first | Device test passes; owner: try in the app |
| 10 | Edit a task's time | Old alarms replaced by new ones (dumpsys) | |
| 11 | Projects > add a project, assign tasks, complete one | "1 of N done" with a progress bar | |
| 12 | Subtasks: save a task, add 2 subtasks, tick one | Row shows "1/2 subtasks" | |
| 13 | Check-ins: pick 3 top tasks | They show first in Today with ★ Top 3; a 4th cannot be picked | |
| 14 | Check-ins: turn on the morning nudge 2 minutes from now | A "Good morning" notification opens Check-ins | |
| 15 | Evening wrap-up "Move all to tomorrow" | Today's and overdue tasks move one day, same time | |
| 16 | Reschedule on a task pop-up | Opens the task editor for that task | |
