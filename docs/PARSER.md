# Quick-add grammar

Quick-add turns one line of text into a task. It uses plain rules, no AI. The preview always shows what it understood before you save. Anything it doesn't recognise stays in the title, so nothing you type is lost.

Code: `app/src/main/java/com/sanjith/betterlife/domain/parser/`. Tests: `QuickAddParserTest.kt`.

## What it recognises

Words can be in any order and any letter case.

| You type | Meaning | Examples |
|---|---|---|
| `today`, `tomorrow`, `tmrw`, `tmr` | That day | `call mom tomorrow` |
| A weekday | Its **next** occurrence. Today's own weekday means **next week**. | `gym thursday`, `fri`, `tues` |
| `dd/mm`, `dd/mm/yyyy`, `dd/mm/yy` (also `-` or `.`) | A date, **day first** | `dentist 12/10` = 12 October |
| A number + month name | A date | `pay rent 15 nov`, `15 november` |
| `5pm`, `5:30pm`, `5 pm`, `5:30 a.m.` | A 12-hour time | `call 5pm` |
| `17:30`, `05:30`, `at 17` | A 24-hour time (hour 0, hour 13 or later, or a leading zero) | `report 17:30` |
| `at 5`, `5:30`, `at 12` | **Could be AM or PM**: the preview asks you | `meeting at 5` |
| `!high` `!h`, `!medium` `!med` `!m`, `!low` `!l` | Priority | `submit report !high` |
| `#word` | A tag (stored lowercase; any script, such as `#काम`) | `#work` |
| `@word` | The project (the first one only) | `@projectx` |

Only the **first** date and the **first** time are used; any later ones stay in the title.

## How the date is chosen

- **Time but no date:** today if the time is still ahead, otherwise **tomorrow**, with the warning "moved to tomorrow". A time exactly equal to now counts as passed.
- **Day and month but no year, already passed this year:** **next year**, with a warning.
- **A date with a year:** kept exactly, even if it is in the past.
- **`today` with a time that has passed:** kept as today, with the warning "already passed".
- **Nothing date-like:** no date, so the task goes to the **Inbox**.

## Warnings and questions shown in the preview

| Warning | When |
|---|---|
| Moved to tomorrow | A time alone had already passed today |
| Moved to next year | A day/month alone had already passed this year |
| Time already passed | `today` + a time that has passed |
| No title yet | Only date, time, tag or priority words were typed (Save stays disabled) |
| AM or PM? | `at 5`, `5:30`, `at 12`: tap AM or PM, and the same text is parsed again with your answer |

## Things to know

- `sun`, `sat` and `wed` are read as weekdays (`buy sun cream` gets a Sunday date). The preview shows this, so remove the date chip or reword if it wasn't meant.
- `tom` is **not** tomorrow, because it's a common name (`call tom`).
- A bare number is not a time: `buy 2 apples` stays as typed. Use `at`, a colon, or am/pm.
- Impossible dates and times stay in the title: `31/02`, `25:99`.
- Emoji, other scripts, empty text, only punctuation and very long text never crash; they become the title.

## Examples (typed on Thursday 8 October 2026 at 18:00)

| Text | Title | Date | Time | Other |
|---|---|---|---|---|
| `call mom tomorrow 5pm` | call mom | Fri 9 Oct | 17:00 | |
| `submit report friday 3pm !high` | submit report | Fri 9 Oct | 15:00 | priority high |
| `gym thursday` | gym | Thu 15 Oct | none | today is Thursday, so next week |
| `buy milk 5pm` | buy milk | Fri 9 Oct | 17:00 | moved to tomorrow |
| `meeting at 5` | meeting | ? | ? | asks AM or PM |
| `dentist 12/10` | dentist | Mon 12 Oct | none | day first |
| `report 17:30 #work @projectx` | report | Fri 9 Oct | 17:30 | tag work, project projectx, moved to tomorrow |
| `anniversary gift` | anniversary gift | none | none | goes to the Inbox |
