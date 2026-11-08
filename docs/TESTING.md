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

## Prompt 10: Events, meetings, birthdays and early reminders

How: Home > Agenda. The + adds an event, a birthday or a "Remind me"; Defaults sets the reminders new items start with and the birthday time. Tap a row to edit it. Check scheduled alarms with `adb shell dumpsys alarm | findstr REMINDER_ALARM`.

Defaults chosen (editable in Agenda > Defaults): birthdays and timed tasks get "1 day before" (Reminder) + "on the day / at the time" (Pop-up); events, meetings and "Remind me" get "at the time" (Pop-up). A new birthday starts with "I don't know the year" on.

| # | Test | Expected | Result |
|---|---|---|---|
| 1 | Add a birthday for tomorrow | Reminders today 09:00 (1 day before) and tomorrow 09:00 | **PASS** (AI 2026-11-04 at 16:00: tomorrow 09:00 alarm set; today 09:00 had passed so it was correctly skipped; a unit test covers both) |
| 2 | Edit an event's time | Old alarm disappears, new one appears | **PASS** (AI: 17:00 -> 19:00, one alarm per time in dumpsys) |
| 3 | Two overlapping events and an overlapping task | Clash badge on both events, not on the task | **PASS** (AI, screenshot) |
| 4 | Weekly meeting | Agenda lists every week; dumpsys shows only the next one | **PASS** (AI: one alarm) |
| 5 | Birthday for tomorrow added before 09:00 (or set the birthday time 2 minutes ahead in Defaults) | The "1 day before" reminder fires as a notification | |
| 6 | Birthday with a known year | Agenda shows "Turns N" | |
| 7 | Birthday on 29 February, year unknown | Shows on 28 Feb 2027 (60-day agenda: check in Prompt 18's calendar, or trust the unit test) | |
| 8 | Remind me in 3 minutes, "at the time" | Pop-up shows its title; Done removes it from the agenda | |
| 9 | Event with a new colour category | Coloured stripe on the agenda row, category name in the details | |
| 10 | Change a default in Agenda > Defaults, then add a new event | The new event starts with your default | |
| 11 | Reschedule on an event pop-up | Opens the event editor for that event | |
| 12 | Delete an event | Gone from the agenda; its alarms gone from dumpsys | |

## Prompt 11: Reminder Center, Reminder Health and test reminders

How: Home > Reminder Health (the Setup Checklist became this screen) and Home > Reminder Center. Health re-checks every time the app comes back to the front.

Reason codes and the sentence the Reminder Center shows (a unit test checks every code has one and that each is a full sentence):

| Code | Sentence |
|---|---|
| QUIET_HOURS | Held because Quiet Hours were active. |
| PAUSED | Held because you paused reminders. |
| FATIGUE_GROUPED | Grouped with other reminders that came close together. |
| FATIGUE_DOWNGRADED | Shown as a normal notification because several pop-ups in a row were ignored. |
| IN_CALL | You were on a call, so it came as a normal notification. |
| PERMISSION_MISSING | A permission was missing, so it could not be shown the way you chose. Check Reminder Health. |
| ITEM_COMPLETED | Not shown because the item was already done. |
| ITEM_DELETED | Not shown because the item was deleted. |
| DEVICE_OFF | The phone was off or the app was stopped when this was due. |
| EXACT_ALARM_DENIED | Exact alarms were not allowed, so it may have come late. Check Reminder Health. |
| (Missed, no code) | It was not answered before the pop-up closed. |
| (Failed, no code) | Nothing could be shown. Check Reminder Health. |
| (Held, no code) | Held for later. |

| # | Test | Expected | Result |
|---|---|---|---|
| 1 | Reminder Health with everything allowed | 10/10, "All set", every Android row OK, Vivo rows ticked | **PASS** (AI 2026-11-06, screenshot) |
| 2 | Test in 10 s | The real pop-up "Test reminder" about 10 s later | **PASS** (AI: pop-up at 3:54 PM) |
| 3 | Snooze the test pop-up, open Reminder Center | Rows Scheduled, Fired, Snoozed ×1 and the new Scheduled time | **PASS** (AI, screenshot) |
| 4 | Test after closing, then leave and kill the app | Pop-up arrives on its own; Health shows "arrived at …, N s after it was due" | **PASS** (AI: process killed, pop-up at 3:57 PM, "0 s after it was due") |
| 5 | Turn off "Display over other apps" for BetterLife Dev in phone settings, come back | Red banner on Home naming it; Health row red with a Fix button; score drops | |
| 6 | Fix all with two rows red | Opens the first row's settings; after Back, asks "Next: …"; ends with a summary | |
| 7 | Turn the Pop-up channel off (long-press a BetterLife notification > turn off "Full-screen reminders") | "Reminder channels not turned down" turns red | |
| 8 | Complete a task from its pop-up, snooze another | Completed and Snoozed rows with the right item names | |
| 9 | Tap a Reminder Center row | Opens that task / event / reminder; a deleted item says it was deleted | |
| 10 | Real swipe-away: Test after closing, then swipe BetterLife away in recent apps | Pop-up arrives within a few seconds of the due time | |

## Prompt 12: Quiet Hours, Pause, fatigue protection and in-call handling

How: Home > I'm busy (the pause sheet; a banner with Resume appears while paused) and Home > Quiet & calm (Quiet Hours, ALARM bypass, Pause default, fatigue settings). The Reminder Center shows Held rows with the reason. Defaults: Quiet Hours off (22:00 to 07:00 when on, every day), ALARM ignores Quiet Hours, Pause leaves calendar items and ALARM alone, max 3 pop-ups per hour, group within 10 min, 3 ignored in a row -> calm for 24 h.

Rules in short: Pause beats Quiet Hours beats fatigue. Held reminders come back as ONE digest notification when the pause or Quiet Hours end. A pop-up within 10 minutes of the last one joins it (Grouped) and does not count toward the hourly limit. "Ignored" means closed with Back or left to time out.

| # | Test | Expected | Result |
|---|---|---|---|
| 1 | I'm busy > 30 min > Pause | Banner "Paused until …" with Resume; a pause-end alarm is set | **PASS** (AI 2026-11-06: banner, alarm at 4:41:37 PM) |
| 2 | While paused, Reminder Health > Test in 10 s | No pop-up; Reminder Center row Held: "Held because you paused reminders." | **PASS** (AI) |
| 3 | Resume | Banner gone; ONE notification "1 reminder waited for you" listing Test reminder | **PASS** (AI, notification read with dumpsys) |
| 4 | Pause 30 min with a birthday reminder due inside it | The birthday still fires (calendar items are exempt by default) | |
| 5 | Pause 30 min and do nothing | At the end the banner goes and anything held arrives as one digest | |
| 6 | Quiet Hours: switch on, set From = now + 2 min, To = now + 6 min; add a task reminder for now + 3 min | Held (Reminder Center says Quiet Hours); one digest at the To time | |
| 7 | Same, with an ALARM level reminder | It rings anyway (switch "ALARM level ignores Quiet Hours" on) | |
| 8 | 4 pop-up reminders 12 minutes apart within one hour | The 4th arrives as a normal notification; Center says why | |
| 9 | 3 pop-up reminders 3 minutes apart | One pop-up window with the cards stacked; the 2nd and 3rd show "Grouped" in the Center | |
| 10 | Close 3 pop-ups in a row with Back | A one-time notice "Pop-ups are taking a break"; Quiet & calm shows "Calm until …" with End now | |
| 11 | Call yourself from another phone while a pop-up reminder fires | Notification only; Center reason "You were on a call…" | |
| 12 | Pause, then restart the phone before it ends | The pause still ends on time and the digest arrives | |

## Prompt 13: Phase 1 regression checklist (GATE)

Run every row on the V50e with the latest debug build before Phase 2 starts. Tick Result with PASS, or write what happened. Anything that fails goes through Template 4 first. Times are phone time. "Pop-up" means a reminder at the Pop-up level; create test reminders in Tasks (+), Agenda (+ > Remind me) or with Reminder Health's test buttons.

Before you start: Home > Reminder Health shows 10/10 (fix any red row first), and Home > Backup > Back up now, saved somewhere safe.

| # | Area | Steps | Expected | Result |
|---|---|---|---|---|
| R1 | Overlay over an app | Remind me in 2 min (Pop-up). Open Chrome and keep using it | The pop-up appears on top of Chrome with sound; Done closes it; Chrome is untouched | |
| R2 | Locked screen | Remind me in 2 min (Pop-up). Lock the phone, screen off | The screen wakes and the pop-up shows over the lock screen; Done works after unlocking if asked | |
| R3 | Alarm level on silent | Ringer on silent. Remind me in 2 min at Alarm level | Loud alarm sound plays anyway; Done stops it at once | |
| R4 | After swipe-away | Reminder Health > Test after closing. Swipe BetterLife away in recent apps | Pop-up arrives on its own; Health says "arrived … 0-2 s after it was due" | AI PASS 2026-11-06 (process killed with adb, 0 s) |
| R5 | After reboot | Remind me in 10 min (Pop-up). Restart the phone, unlock, wait | It fires on time after the reboot | |
| R6 | Missed while off | Remind me in 3 min. Turn the phone OFF for 5 min, turn on | ONE "missed" notification (count only); Reminder Center lists it as Missed: "The phone was off…" | |
| R7 | Early reminder 1 day before | Add a birthday for tomorrow | Alarm for today 09:00 (if still ahead) and tomorrow 09:00 in the alarm list | AI PASS 2026-11-04 (tomorrow 09:00; today's 09:00 had passed) |
| R8 | Repeating task | Daily task at now + 3 min with a Pop-up reminder; tap Done on the pop-up | Tomorrow's copy appears in Upcoming with its reminder | |
| R9 | Quiet Hours | Quiet & calm: switch on, From = now + 2 min, To = now + 6 min. Task reminder at now + 3 min | Nothing at +3 min; Center: Held "Held because Quiet Hours were active."; ONE digest at +6 min | |
| R10 | Pause | I'm busy > 30 min, then Reminder Health > Test in 10 s | No pop-up; Center Held (paused); Resume gives ONE digest | AI PASS 2026-11-06 |
| R11 | Pause exemptions | Pause 30 min, Agenda event at now + 2 min | The event still pops up (calendar is exempt by default) | |
| R12 | Fatigue: 4 per hour | 4 Pop-up reminders 12 min apart | The 4th is a normal notification; Center reason says several pop-ups | |
| R13 | Grouping | 3 Pop-up reminders 3 min apart, do not answer the first | One pop-up window with the cards stacked; Center shows Grouped for the 2nd and 3rd | |
| R14 | Ignored 3 times | Close 3 pop-ups in a row with Back | A one-time notice "Pop-ups are taking a break"; Quiet & calm shows Calm until … | |
| R15 | In a call | Call the phone from another phone; while it rings or you talk, a Pop-up reminder fires | Normal notification only; Center: "You were on a call…" | |
| R16 | Health banner | Phone settings: turn off "Display over other apps" for BetterLife Dev, come back | Red banner on Home naming it; Health row red with Fix; Fix opens the right page | |
| R17 | Snooze | On a pop-up: Snooze > 10 min | It returns 10 min later; Center shows Snoozed ×1 | AI PASS 2026-11-06 (row seen) |
| R18 | Edit and delete | Edit a task's time; then delete it | Alarm moves to the new time; after delete no alarm is left | AI PASS 2026-11-04 |
| R19 | Export and import | Backup > Back up now (save the file). Add one test task. Backup > Restore > pick the file > Restore this backup > Replace and restart | App restarts; the test task is gone, everything from the backup is back, reminders still fire; Debug: database > Check says No broken references | AI PASS 2026-11-06 (counts identical, integrity clean) |
| R20 | Refuse a bad file | Backup > Restore > pick any other file (a photo) | "This file is not a BetterLife backup." Nothing changes | |
| R21 | Airplane mode | Airplane mode on for a day of normal use | Everything works; reminders fire (Supabase is not used yet) | |

Owner gate: also use the app normally for 2 to 3 days with real tasks and write anything missed or annoying under Known issues in PROGRESS.md. Keep a backup somewhere safe before Phase 2.

## Owner's change 2026-11-08: floating bar, smoother app, profile build

| # | Check | Expected | Result |
|---|---|---|---|
| 1 | Look at the bar on each tab, then scroll Habits and More under it | A floating rounded bar you can see the screen through, no blur; labels still readable | **PASS** (AI: seen on the phone; whether it is see-through enough is yours to judge) |
| 2 | Tap between tabs quickly | The pill slides, screens swap at once; no back arrows on Tasks, Habits, Calendar | **PASS** (AI: 50 tab taps, smoothness is yours to judge) |
| 3 | Open a task or a habit, then go back | A short slide and fade both ways; the bar leaves and returns | |
| 4 | Scroll to the very bottom of every tab | The last row clears the bar | **PASS** (AI on More; code for the rest) |
| 5 | Tick a habit on Habits, or a task on Tasks | The Undo snackbar shows above the bar | |
| 6 | More > Your name: tap the field | It scrolls above the keyboard | |
| 7 | The build on the phone | "BetterLife Dev", same habits, signed in, Reminder Health 10/10, no Debug tools on More | **PASS** (AI) |
| 8 | Reminder Health > Test in 10 s on this build | Pop-up, ring on the alarm stream, Done stops it | **PASS** (AI) |

## Owner's change 2026-11-08: stronger alarm, habits locked to today, stronger done feedback

| # | Check | Expected | Result |
|---|---|---|---|
| 1 | More > Alarm: Choose alarm sound, pick one; Reminder Health > Test in 10 s | Your sound rings | |
| 2 | More > Alarm: Use my own audio file, pick a song or tone; test again; restart the phone; test again | Your file rings both times | |
| 3 | Lock the phone, Test in 10 s, leave it in a pocket or face down | Screen wakes to the full pop-up at full brightness and stays lit; loud ring and strong vibration, on silent too | **PASS** (AI: pop-up took focus on the locked phone, bright wake lock held, ALARM stream at 15 of 15, vibrating; feel and loudness are yours to judge) |
| 4 | Do not answer | After 5 min it stops; 10 min later it rings again; the Reminder Center shows Snoozed | **PASS** (AI with 1 and 1 minutes: rang, snoozed, rang again as a repeat; 5 and 10 restored) |
| 5 | Done on the pop-up | Ring and vibration stop at once, a check pops on the button, a short firm buzz | **PASS** (AI: ring stopped, 269 ms buzz played; the check animation is yours to see) |
| 6 | Test in 10 s while using another app | The same over the app | **PASS** (AI: overlay window, ringing, vibrating) |
| 7 | Volume check: note your alarm volume, let a test ring, answer it | The alarm volume is back where it was | |
| 8 | A phone call while a reminder is due | A quiet notification now, and it rings again 10 min later | **PASS** (unit test) |
| 9 | Habits: tap a past box (done or not) | Nothing happens; only today's box reacts | **PASS** (AI on the phone: days 5 and 7 unchanged, no Undo) |
| 10 | Tick today's box, then tick a task, on Habits, Today and Tasks | A firm buzz, a burst of colour, the title struck through before the row leaves | **PASS** (AI: burst and strike-through seen, buzz logged; feel is yours to judge) |
| 11 | Habits > ⋮ > Vibrate on done off | Ticks are silent to the hand; the alarm still vibrates | |
| 12 | A reminder at the plain Reminder level, or a habit's daily reminder | It rings, vibrates and lights the screen like a pop-up, and comes back if unanswered | **PASS** (AI: a Normal test reminder from Debug: reminder engine showed the pop-up and rang) |
| 13 | More > Alarm > Reminder level rings like a pop-up: off, then a Reminder-level item | A plain notification again | **PASS** (unit test) |

## Prompt 17: Today, nudges, comeback, Minimum Day, pause mode (GATE)

Today is the home screen now, with the bottom bar (Today, Tasks, Habits, Calendar, More). Everything that was on the old Home is on More.

| # | Check | Expected | Result |
|---|---|---|---|
| 1 | GATE: use Today as your real home screen for 3 days | It truthfully answers "what should I do right now?" | |
| 2 | First launch with no name | Blip asks your name; Today greets you with it; More > Your name changes it | **PASS** (AI entered the placeholder "Friend": change it to your name in More) |
| 3 | Today with a task due today, an overdue high task, a meeting in the next 2 hours and habits | Order: pop-up/alarm reminders due now, overdue high tasks, events soon, today's tasks by priority then time (same minute = one "2:00 PM, 2 items" row), habits | **PASS** (unit tests for every tier and tie-break) |
| 4 | Bad day? | Three things (top task, Essential habit or the longest streak at risk, a different Self-care habit); ticking all three keeps every other habit as a KEPT day and Blip says "That's enough for today." | **PASS** (device test for keeping the rest; on the phone it picked Meditation and Skincare: no tasks, Creatine already done) |
| 5 | More > Pause habits: a range starting tomorrow | Habits show paused on those days; task reminders inside the range are held and arrive together when it ends; birthdays, events and alarm-level reminders still fire | **PASS** (engine tests: task held, alarm rings, one digest at the end) |
| 6 | Today banner > End pause | The range ends yesterday (or disappears if it started today); held reminders arrive at once | **PASS** (device test) |
| 7 | Time Machine 3 days ahead without ticking | Comeback opens once that day: "Welcome back!", two habits and one task, no list of missed days | **PASS** (unit and device tests) |
| 8 | Leave a habit unticked at 21:00 | "A habit is still waiting" notification; none if all are done; switch and time on More | |
| 9 | After the gate: Backup > Back up now; the AI tags phase-2 in git | Backup saved; tag pushed | |

## Prompt 16: Celebrations and mascot v1

Blip lives on the celebration screen, the reminder pop-up, the quiet banner and Debug: mascot. Your own messages: Habits > ⋮ > Celebration messages (Blip's name is at the top of that screen).

| # | Check | Expected | Result |
|---|---|---|---|
| 1 | Debug: Time Machine. Create a test habit, then tick it on 7 days in a row, moving the Time Machine forward one day each time (tick on that day's box) | On day 7 the full-screen celebration appears once: confetti, Blip cheering, "7", "days of <habit>!", one message | **PASS** (device test: 7 single-day ticks give one celebration) |
| 2 | Untick and tick day 7 again | No second celebration | **PASS** (device test) |
| 3 | Replaced 2026-11-08: past days can no longer be ticked | A past box does nothing, and nothing is celebrated | **PASS** (device test) |
| 4 | Habits > ⋮ > Celebration messages: add two of your own; Debug: mascot > Show a test celebration a few times | One of YOUR lines shows (the built-in ones only while you have none) | |
| 5 | Debug: mascot: watch it cycle; tap each mood and Pod / Baby | Blip looks good and cute in all five moods; Down is sad, never blaming | **PASS** (AI looked at Proud, Down and the Pod on the phone; your eye decides "cute") |
| 6 | Developer options > Animator duration scale off; open Debug: mascot and a test celebration | Blip stands still in each mood's pose; the confetti does not move | |
| 7 | A habit with a reminder: press Done on its pop-up on the day that makes 7 | The celebration shows when the app is next in front | |
| 8 | Change Blip's name on the Celebrations screen, Save | The name sticks after a restart | |

## Prompt 15: Habit grids UI

Home > Habits. Each card: icon, name, consistency on the right ("New" for the first 7 days), the streak line, and 31 boxes in rows of 7 (every box is a 48 dp target). Tap a box to tick or clear it; Undo stays for about 6 seconds. Tap a habit's name for its page (numbers, last 12 months, this month). + adds a habit; the pencil on a habit's page edits it.

| # | Check | Expected | Result |
|---|---|---|---|
| 1 | Tick Creatine for today (past days are locked since 2026-11-08) | Boxes fill from the left with a pop and a check mark, the phone gives a short buzz, the streak line updates at once, Undo appears | **PASS** (AI 2026-11-06: 3 to 6 Nov ticked, "4-day streak", Undo shown; the buzz needs a hand on the phone) |
| 2 | Undo | The day goes back to how it was | **PASS** (device test) |
| 3 | ⋮ > Vibrate on tick off, tick a box | No buzz | |
| 4 | Previous month arrow; a 30-day month and February | Day 31 (and 29 to 31 in February) greyed; "Back to this month" chip; the next arrow stops at this month | **PASS** (unit and device tests; September and October seen on the phone in tests) |
| 5 | + > create "Read 10 pages" today | Days before today are greyed and cannot be tapped (changed 2026-11-08) | **PASS** (unit/device tests) |
| 6 | Edit a habit: colour, icon, category, Essential, reminder 21:30, Save | The card shows the new look; Reminder Center later shows the habit reminder | |
| 7 | Let the 21:30 habit reminder fire once without ticking, once after ticking today | Fires when not done; does NOT fire when today is already ticked; Done on the pop-up ticks today | |
| 8 | Archive, then delete a test habit | Archive hides it; delete asks first and removes it | **PASS** (device test for archive) |
| 9 | Debug: database > Seed, open Habits, scroll fast up and down | No stutter you can feel | **MEASURED** (AI, debug build, 16 habits incl. 12 seeded with 3,491 logs, 12 fast swipes: 610 frames, 1.6% janky, median 12 ms, 90th percentile 44 ms; Wipe removed exactly 5,449 seeded rows) |
| 10 | Debug: Time Machine to 01:00 tomorrow, tick today's box | It lands on today's (the previous day's) box; switch the Time Machine off afterwards | |
| 11 | TalkBack on, move across the grid | Each box says its day and state ("Day 6, today, not done yet") | |

## Prompt 14: Habit data and streak logic

No habit screens yet (they come in Prompt 15). The rules are pure code with unit tests; the habit tables are on the phone.

| # | Check | Expected | Result |
|---|---|---|---|
| 1 | Install over the old app (database 4 -> 5) | Nothing lost: tasks, events, reminders, remind-me items and the Reminder Center history are all still there | **PASS** (AI 2026-11-06: copy taken first; after the upgrade version 5, integrity ok, same 12 tasks / 4 events / 23 reminders / 3 remind-me / 39 log rows) |
| 2 | Starter habits | Creatine (Essential, Health), Meditation, Skincare routine, Haircare routine exist once, starting today | **PASS** (AI: read from the phone's database; shown on screen from Prompt 15) |
| 3 | `gradlew.bat testDebugUnitTest`, then open app/build/reports/tests/testDebugUnitTest/index.html > com.sanjith.betterlife.domain.habit | Every rule from context.md 5.4 has a test name you can read | **PASS** (AI: 25 habit tests, all green) |
| 4 | Ask the AI to explain the backfill merge in plain words | It matches what you expect | |
| 5 | Debug > Time Machine: jump to 02:00 tomorrow | (from Prompt 15) a tick still lands on today's box | |

## Prompt S1: Supabase project, schema, row-level security and sign-in

Project: betterlife, Mumbai, ref `qprtltwuuihbixbfgspm` (found with the Supabase connection, created by the owner on 2026-11-04).

| # | Test | Expected | Result |
|---|---|---|---|
| 1 | Supabase dashboard > Table Editor | projects, tasks, events, reminders, standalone_reminders exist, each "RLS enabled" (habits, habit_logs, pause_ranges come in S3) | **PASS** (AI: tables created by migration, RLS on) |
| 2 | Security advisor | No warnings | **PASS** (AI 2026-11-06: 0 security lints; performance only notes the new pull indexes are unused yet) |
| 3 | SQL editor: run `supabase/tests/rls_test.sql` | Ends with `RLS test passed`, leaves nothing behind | **PASS** (AI, 0 rows left) |
| 4 | Home > Account & sync > enter your email and a password > Create account | "Almost done: open the email…" | |
| 5 | Open the email from Supabase on the phone, tap the link (the page may look broken), back in the app > Sign in | "Signed in as …" | |
| 6 | Close the app from recent apps, reopen > Account & sync | Still signed in | |
| 7 | Sign in with a wrong password | "Wrong email or password." | |
| 8 | Airplane mode on, Sign in | "No internet connection…"; the rest of the app and reminders keep working | |
| 9 | Sign out | Signed out; all tasks and events are still on the phone | |
| 10 | Search the repo on GitHub for your publishable key and for "service_role" | The key is not in any committed file; service_role appears only in docs that warn against it | |

About the confirmation email: Supabase's email link goes to the project's Site URL (localhost by default), so the browser page may fail to load. The account is confirmed anyway. To change that page later: Supabase dashboard > Authentication > URL Configuration.

## Prompt S2: Offline-first sync engine

Sync runs when the app opens, about 5 s after a change, every 6 hours, and on Account & sync > Sync now. It waits for internet and retries by itself. The phone is the source of truth: the newest edit wins (a delete wins a tie). Debug seed rows never leave the phone.

| # | Test | Expected | Result |
|---|---|---|---|
| 1 | Sign in, open Account & sync | Last sync shows a time, Changes waiting 0, all your tasks and events are on Supabase (Table Editor) | **PASS** (AI 2026-11-06: first sync pushed 38 rows) |
| 2 | Add a task on the phone, wait 10 s, look in Supabase > tasks | The task is there | **PASS** (AI: arrived in about 5 s) |
| 3 | Edit a task's title in Supabase (set a newer updated_at), then reopen the app or tap Sync now | The new title shows on the phone | **PASS** (AI: "S2 sync test (edited in cloud)" arrived on reopen) |
| 4 | Delete that task on the phone, wait 10 s, look in Supabase | The row is still there with deleted_at filled in (soft delete) | **PASS** (AI: deleted_at = updated_at = 1793986894942) |
| 5 | A future task with a reminder; move its due time in Supabase; reopen the app | `adb shell dumpsys alarm` shows ONE BetterLife reminder alarm, at the new time | **PASS** (AI: task for 09:00 moved to 09:30 in the cloud; one alarm, now 09:30; deleting the task left none) |
| 6 | Airplane mode on, add a task, reopen the app | Home says offline / changes waiting; reminders still fire | |
| 7 | Airplane mode off | Within a minute the waiting change is pushed; Changes waiting 0 | |
| 8 | Edit the same task on the phone while offline and in Supabase, then go online | The edit with the later updated_at wins on both sides | |
| 9 | Second phone or a reinstall: sign in | Everything comes down; reminders are scheduled once each | |
| 10 | Account & sync > Delete my cloud data (confirm twice) | Signed out; Supabase tables have none of your rows; everything is still on the phone | |
| 11 | Leave the phone with the app untouched for 6+ hours online | Last sync moved forward without opening the app | |

Seen during the AI check: right after a force stop, the first sync request timed out after about 47 s and reported "offline"; WorkManager retried a minute later and it worked. Nothing was lost.
