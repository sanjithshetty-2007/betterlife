# BetterLife UI design reference

The visual target for every screen in the playbook (Prompts 1 to 30 plus S1 to S3). context.md and agent.md still decide behaviour; this decides the look.

| File | What it is |
|---|---|
| `BetterLife_UI_Design_Reference.pdf` | The reference: tokens, components, navigation, motion specs, motion frame strips, skeleton rules, mascot, then every screen labelled with its prompt, layout and motion. |
| `prototype/index.html` | The live, clickable prototype the screenshots come from. Open it in Chrome; `?s=<id>` opens one screen (ids are printed under each screen in the PDF). `&t=<ms>` freezes all animation at that moment; `&reduced=1` shows the reduced-motion version. |
| `shots/` | 2x PNG screenshots of every screen and the motion frames. |
| `reference.html`, `annotations.js` | Source of the PDF (per-screen notes live in `annotations.js`). |

Colours, radii and spacing come from `app/src/main/java/com/sanjith/betterlife/ui/theme`. Values marked **proposed** in the PDF (raised / sunk surfaces, soft tints, motion tokens, the 32 dp pop-up radius) are not in the theme yet: add them there as tokens before using them, never as literals in a screen.

## Rebuilding the PDF (Windows, Git Bash)

```bash
CH="/c/Program Files/Google/Chrome/Application/chrome.exe"
"$CH" --headless=new --hide-scrollbars --force-device-scale-factor=2 --window-size=412,915 --virtual-time-budget=5000 --screenshot="C:/betterlife/docs/design/shots/today.png" "file:///C:/betterlife/docs/design/prototype/index.html?s=today&t=2200"
"$CH" --headless=new --allow-file-access-from-files --no-pdf-header-footer --virtual-time-budget=8000 --print-to-pdf="C:/betterlife/docs/design/BetterLife_UI_Design_Reference.pdf" "file:///C:/betterlife/docs/design/reference.html"
```

Repeat the first command for each screen id that changed (skeleton screens use `t=700`).
