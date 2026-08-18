# CHANGE_REQUEST_003 — OCR regression, background notifications, blood-pressure scale and mascot animations

## 0. Baseline

Этот change request применяется поверх текущей рабочей версии после CR-002.

Текущая версия пользователя устраивает и является known-good baseline для всех функций, кроме явно перечисленных в CR-003 проблем.

Не переписывать приложение с нуля.

Это следующая версия того же Android-приложения.

Сохранить:

- applicationId;
- package identity;
- Room database;
- пользовательские данные;
- существующие функции CR-001 и CR-002;
- текущий UI, если он явно не изменяется этим документом;
- существующие invariants из `AGENTS.md`.

Baseline commit:

```text
<CR003_BASELINE_COMMIT>
```

Перед изменениями:

1. прочитать `AGENTS.md`;
2. прочитать CR-001 и CR-002;
3. запустить существующие tests;
4. выполнить debug build;
5. проверить текущую работу приложения;
6. только после этого начинать изменения.

Если требуется Room migration:

- только non-destructive;
- сохранить все данные;
- добавить migration test.

---

# 1. Scope CR-003

## REQUIRED

1. Исправить regression качества OCR артериального давления.
2. Создать воспроизводимую проверку OCR на dataset `app/src/test/pressure/`.
3. Добиться field accuracy > 80% на предоставленном test set.
4. Устранить случаи, когда на предоставленных test images OCR pipeline возвращает полностью пустой результат.
5. Сделать ось Y графика артериального давления базово `40..150`.
6. Расширять эту шкалу только при наличии отображаемых значений вне `40..150`.
7. Исправить уведомления так, чтобы они не зависели от того, открыто ли приложение.
8. DAY/EVENING reminder должен срабатывать, когда приложение не находится foreground и его Activity/process не используется пользователем.
9. Увеличить визуальный размер маскотов примерно в `1.3x` относительно текущего rendered size.
10. Поддержать новые stationary animations `tea` и `fun`.
11. Stationary animation frames должны переключаться примерно в 3 раза медленнее walk animation.
12. Игнорировать directories `scratches`.
13. Добавить mascot `scratches` в `.gitignore`.
14. Не включать scratches assets в APK/resources.

---

# 2. Critical invariants

```text
OCR is never allowed to autosave

OCR photo remains temporary

test fixtures must not affect production inference

no filename-based OCR cheating

no ground-truth lookup in production code

pressure graph scale is presentation only

pressure Y-axis changes must not modify stored values

notification delivery must not depend on opening MainActivity

no permanent foreground service for two daily reminders

existing DAY / EVENING notification semantics stay unchanged

mascot source pixel dimensions must not determine logical on-screen size

stationary mascot animation must not move mascot position

scratches are never runtime assets
```

---

# 3. OCR regression — context

OCR pressure recognition worked substantially better in the original MVP baseline.

After later changes it became unreliable and often returns no usable recognized result from photographs.

The goal is to identify and fix the regression rather than blindly layer additional heuristics on top of the current implementation.

---

# 4. Compare against original working OCR

Before changing OCR logic:

1. inspect current OCR implementation;
2. locate the original MVP baseline commit recorded in CR-001;
3. inspect/diff OCR-related files between that baseline and current HEAD;
4. identify changes in:
   - image loading;
   - URI handling;
   - image orientation;
   - image scaling;
   - preprocessing;
   - ML Kit invocation;
   - reading-order extraction;
   - numeric candidate parsing;
   - mapping into SYS/DIA/Pulse.

Do not revert unrelated application changes.

Use the original baseline only as an OCR regression reference.

Document the identified regression cause in `DECISIONS.md` or final report.

---

# 5. OCR test dataset

A small regression dataset is located at:

```text
app/src/test/pressure/
```

It contains:

```text
test_pressures.csv
1.jpeg
2.jpeg
3.jpeg
4.jpeg
5.jpeg
```

Do not delete, rename or replace these fixtures unless explicitly required.

---

# 6. Ground truth CSV

CSV columns:

```text
name
sys
dia
pul
```

Expected data:

```text
1.jpeg,100,63,100
2.jpeg,111,62,87
3.jpeg,101,61,78
4.jpeg,89,58,83
5.jpeg,100,67,71
```

The column is currently named:

```text
pul
```

not `pulse`.

Test tooling must support the existing file as-is.

Do not require the user to manually edit the CSV.

---

# 7. OCR quality metric

Primary acceptance metric:

```text
field_accuracy =
correctly recognized individual fields
/
total expected fields
```

There are:

```text
5 images × 3 fields = 15 fields
```

Required:

```text
field_accuracy > 0.80
```

Because the dataset contains 15 fields, this means at least:

```text
13 / 15 correct
= 86.7%
```

A field is correct only on exact integer match.

Example:

```text
expected SYS = 100
predicted SYS = 100
→ correct

expected SYS = 100
predicted SYS = 101
→ incorrect
```

---

# 8. Additional OCR acceptance

In addition to field accuracy:

Each of the five provided images must produce a non-empty OCR result.

For every test image, the final pressure parser should at minimum produce usable SYS and DIA candidates.

Target:

```text
5 / 5 complete SYS/DIA/Pulse triplets
```

but the hard numerical acceptance threshold remains:

```text
>= 13 / 15 correct individual fields
```

---

# 9. No test-set cheating

Strictly prohibited:

```text
if filename == "1.jpeg" return ...
```

or any equivalent mechanism.

Production OCR/parser code must not:

- know test filenames;
- read `test_pressures.csv`;
- contain hardcoded expected answers from the test set;
- branch specifically for these five images.

The dataset is validation data, not production configuration.

---

# 10. OCR diagnostic separation

The OCR pipeline must be diagnosable in stages.

Conceptually:

```text
image input
→ orientation / image preparation
→ ML Kit text recognition
→ raw recognized blocks/elements
→ numeric candidate extraction
→ ordering
→ SYS / DIA / Pulse mapping
```

When a test fails it should be possible to determine whether:

1. ML Kit did not recognize digits at all;
2. digits were recognized but parser discarded them;
3. parser found digits but ordered them incorrectly;
4. image orientation/input preparation was incorrect.

Do not collapse everything into one opaque function.

---

# 11. OCR test report

Create a reproducible test/verifier which reports per image:

```text
image
expected SYS/DIA/Pulse
predicted SYS/DIA/Pulse
field matches
raw recognized text or diagnostic representation
PASS / FAIL
```

And summary:

```text
Correct fields: 14 / 15
Field accuracy: 93.3%

Complete triplets: 4 / 5
Images with usable SYS+DIA: 5 / 5
```

The report does not have to appear in production UI.

---

# 12. Test execution environment

The verifier must exercise the same production OCR preparation and parsing logic as closely as technically possible.

Do not create a fake OCR implementation solely to make `./gradlew test` pass.

If ML Kit cannot run in ordinary JVM local tests:

use an appropriate:

- instrumented test;
- Android test fixture;
- dedicated debug verification harness;
- or other reproducible Android test setup.

The agent may move/copy fixtures into an Android-test-compatible asset location if technically required, but:

- `app/src/test/pressure/` remains the source-of-truth dataset;
- do not silently replace its data.

---

# 13. Parser unit tests remain useful

Pure parser logic should still have JVM unit tests where possible.

Separate:

```text
raw OCR result → parser
```

from:

```text
image → ML Kit raw OCR result
```

This allows parser regressions to be tested cheaply.

But parser-only tests are not sufficient for CR-003 acceptance.

The supplied images must actually be exercised through the OCR pipeline.

---

# 14. OCR image handling

Pay special attention to image orientation and source handling.

Camera capture and Photo Picker must ultimately feed equivalent correctly-oriented visual content into the OCR stage.

Do not assume:

```text
bitmap rotation = 0
```

for every camera image unless this is actually correct for the current input API.

Do not aggressively downscale images if doing so destroys LCD digit readability.

Any preprocessing added should be justified by measured results on the supplied regression set.

---

# 15. OCR candidate extraction

Do not rely solely on one giant raw-text string if structured ML Kit blocks/elements provide better reading-order information.

The parser may use:

- recognized elements;
- line grouping;
- bounding boxes;
- visual top-to-bottom / left-to-right order;
- recognized numeric strings.

Do not use medical interpretation as the primary mechanism.

The application is recognizing displayed numbers, not diagnosing whether those values are healthy.

---

# 16. OCR fallback behavior

If a real user image still cannot be recognized:

existing UI behavior remains:

```text
OCR fails
→ editable manual pressure form remains available
→ show understandable message
→ no automatic save
```

Never block manual input.

---

# 17. Pressure graph Y-axis — default scale

The dedicated SYS/DIA blood-pressure chart should normally use:

```text
Y minimum = 40
Y maximum = 150
```

This is a visualization preference only.

Do not treat these numbers as medical normal ranges.

Do not validate or reject measurements based on this display range.

---

# 18. Pressure graph scale expansion

Only expand the Y-axis when visible values exceed the default range.

If all visible SYS/DIA values satisfy:

```text
40 <= value <= 150
```

the axis must remain exactly:

```text
40..150
```

Do not autoscale to the visible data such as:

```text
58..121
```

---

# 19. Lower-bound expansion

If at least one currently visible SYS/DIA value is:

```text
< 40
```

expand the lower bound enough to include it.

Preferred readable behavior:

```text
yMin =
min(
    40,
    floor(minVisible / 10) * 10
)
```

Example:

```text
min visible = 37
→ yMin = 30
```

---

# 20. Upper-bound expansion

If at least one currently visible SYS/DIA value is:

```text
> 150
```

expand the upper bound enough to include it.

Preferred:

```text
yMax =
max(
    150,
    ceil(maxVisible / 10) * 10
)
```

Example:

```text
max visible = 157
→ yMax = 160
```

---

# 21. Both directions

Example:

```text
visible values include 36 and 164
```

Expected:

```text
Y range = 30..170
```

Use readable tick values.

---

# 22. Scale based on visible data

Scale computation must use values that are actually part of the current chart after:

- selected date range;
- grouping;
- filters;
- aggregation mode.

It should not expand because of a historical value outside the currently viewed period.

---

# 23. Empty pressure chart

If there is no visible BP data:

keep logical default range:

```text
40..150
```

while still showing the existing empty-data UI.

---

# 24. Pulse chart

Do not apply `40..150` pressure-axis rules to the separate Pulse chart.

Pulse remains its own chart with its own existing scale behavior.

---

# 25. Background reminder regression — problem

Currently notifications may only appear when the user opens the application after the scheduled time.

This is incorrect.

Opening the application must not be the mechanism that causes an overdue DAY/EVENING reminder to be posted.

---

# 26. Required notification behavior

If DAY has not been filled:

the DAY notification scheduled around 13:00 should be posted by the Android system even when:

- the application UI is not open;
- MainActivity is stopped/destroyed;
- the app is not the foreground application;
- the process was reclaimed and is not currently running.

Likewise for EVENING around 19:00.

---

# 27. Do not use persistent foreground service

Do not keep a permanent foreground service running solely to deliver two daily reminders.

Do not show a permanent “Pressure and Condition is running” notification.

Use Android's scheduled/background primitives appropriate for a user-facing time-based notification.

---

# 28. Alarm receiver

The reminder trigger must not depend on:

```text
Activity.onResume
Compose screen lifecycle
ViewModel lifetime
in-process Timer
Handler
Coroutine delay living in app process
```

Use a system scheduled trigger whose PendingIntent targets an application component capable of running without an already-open Activity.

A manifest-declared `BroadcastReceiver` is the preferred architecture unless the current project has an equally robust existing solution.

---

# 29. Receiver behavior

When the alarm fires:

1. determine the exact intended target date and slot from the scheduled alarm;
2. check whether that slot has already been filled;
3. if filled: do not post notification;
4. if not filled: post the notification;
5. preserve existing notification deep-link behavior;
6. schedule/ensure future reminders as appropriate.

Do not determine DAY/EVENING from current clock at notification click.

Existing slot semantics remain unchanged.

---

# 30. App launch must not be the trigger

The application may reconcile or repair future schedules when launched.

However:

```text
open app at 14:00
→ notification suddenly appears because onResume checked current time
```

must not be the normal production mechanism.

Notification posting belongs to the scheduled background path.

---

# 31. Doze / device idle

Use a scheduling method appropriate for a user-facing reminder when the device may be idle.

Exact-to-the-minute delivery is still not required.

A modest Android-controlled delay is acceptable.

The important acceptance requirement is:

```text
delivery does not require opening the app
```

---

# 32. Boot and rescheduling

Ensure future reminders are restored when needed after relevant system events, including device reboot.

Also preserve correct scheduling after timezone / clock changes where supported by the existing notification architecture.

Do not create duplicate alarms.

---

# 33. App upgrade

An app update should not permanently disable reminders.

After package replacement / next appropriate scheduling opportunity, valid future DAY/EVENING reminders must exist.

---

# 34. Notification permission

Existing permission behavior remains.

If POST_NOTIFICATIONS is denied:

do not crash.

If permission is granted:

the scheduler must not additionally require the Activity to remain alive.

---

# 35. Explicit force-stop is outside acceptance

CR-003 background notification acceptance concerns normal background use:

- app not visible;
- app removed from recent UI;
- process not running normally.

Do not design a permanent foreground service solely to bypass an explicit user/system force-stop.

---

# 36. Notification testability

Keep scheduling logic behind a testable abstraction.

Tests should be able to verify:

```text
DAY alarm scheduled
EVENING alarm scheduled
correct target date
correct slot
filled slot suppresses notification
unfilled slot posts notification path
```

If useful, add a debug-only mechanism to schedule a reminder a few minutes in the future for manual background verification.

Do not expose debug scheduling controls in release UI.

---

# 37. Manual background acceptance test

Before completing CR-003, perform or document a reproducible manual verification:

```text
1. notification permission = granted
2. DAY/EVENING target slot = unfilled
3. schedule near-future test reminder in debug build
4. leave the application
5. app is no longer foreground
6. reminder appears without reopening app
```

Prefer also test with screen off/device idle if practical.

---

# 38. Mascot source assets changed

Mascot assets have been replaced.

Previous source canvases were approximately:

```text
800 × 1300
```

Current source frames are:

```text
1254 × 1254
```

Do not infer desired runtime size directly from source pixel dimensions.

The runtime mascot size is a UI layout decision in dp/Compose dimensions.

---

# 39. Mascot visual size

Increase the currently rendered mascots by approximately:

```text
1.3x
```

relative to their CR-002 on-screen visual size.

Example conceptual change:

```text
old rendered size = S
new rendered size ≈ 1.3 * S
```

Do not multiply source pixel dimensions.

---

# 40. Mascot sizing quality

After scaling:

- preserve aspect ratio;
- do not stretch;
- do not clip heads/feet;
- keep character inside decorative area;
- characters must still not obscure functional controls;
- allow adjustment of decorative bounds if necessary.

Because the new source canvas is square, inspect actual transparent padding rather than assuming the visible character fills all 1254 pixels.

---

# 41. Mascot scale configuration

Prefer keeping runtime scale in one central mascot/layout configuration.

Do not scatter arbitrary `1.3f` transforms across multiple composables.

Future mascot replacements should make size adjustment straightforward.

---

# 42. Existing mascot animations

Existing walking behavior remains:

```text
walk_1
walk_2
...
```

Walking:

- changes frames;
- changes X position;
- mirrors according to direction.

Existing idle/static behavior remains supported.

---

# 43. New stationary tea animation

Mascot folders may now contain:

```text
tea_1.png
tea_2.png
```

If these frames exist for a mascot, the mascot engine must support a Tea stationary animation.

During Tea:

```text
position remains fixed
tea_1
→ tea_2
→ tea_1
→ tea_2
```

The character must not walk while Tea is playing.

---

# 44. New stationary fun animation

Some mascot folders may contain:

```text
fun_1.png
fun_2.png
```

If these frames exist, support a Fun stationary animation.

During Fun:

```text
position remains fixed
fun_1
→ fun_2
→ fun_1
→ fun_2
```

No movement occurs.

---

# 45. Optional animations by availability

Not every mascot has every animation.

Required behavior:

```text
tea frames present
→ Tea is available

tea frames absent
→ no Tea for that mascot

fun frames present
→ Fun is available

fun frames absent
→ no Fun for that mascot
```

Missing optional animation files must not crash the application.

Do not require placeholder `fun` frames for mascots that do not have them.

---

# 46. Generic stationary animation model

Do not hardcode Fun as “girl number 2 does this” throughout UI code.

Represent optional stationary animations through generic configuration.

Conceptually:

```text
MascotAnimation
- name
- frames
- frameDuration
- movesCharacter
```

For example:

```text
walk
movesCharacter = true

tea
movesCharacter = false

fun
movesCharacter = false
```

Exact implementation is up to the agent.

---

# 47. Stationary animation speed

New Tea/Fun animation frame changes should be approximately:

```text
3x slower
```

than walking frame changes.

Define speed relative to the current actual walking frame duration.

Example:

```text
walk frame duration = 200 ms
stationary frame duration ≈ 600 ms
```

The exact absolute milliseconds may differ if the existing animation uses another value.

The relationship matters:

```text
stationaryFrameDuration ≈ walkFrameDuration * 3
```

---

# 48. Stationary animation position

During:

```text
tea
fun
other future stationary animation
```

the mascot's logical X/Y position must remain unchanged.

Frame dimensions or transparent padding must not cause visible positional jumps if they can reasonably be avoided.

---

# 49. Stationary behavior selection

When a mascot stops walking, it may choose among its available stationary behaviors.

For example:

```text
idle
tea
fun
```

depending on frames available for that mascot.

A simple random choice is sufficient.

Do not require sophisticated AI behavior.

---

# 50. Stationary duration

A mascot should remain stationary for a noticeable period rather than switching immediately back to walking after one frame.

Exact duration can be simple/random within reasonable bounds.

The new Tea/Fun frame animation should visibly play for at least one meaningful cycle before movement resumes.

---

# 51. No movement during stationary frames

Critical:

Do not continue incrementing mascot X position while Tea/Fun frames are displayed.

A stationary animation is not merely alternate walking artwork.

---

# 52. Scratches directories

Mascot directories may contain:

```text
scratches/
```

These are user working drafts.

They are not production assets.

They must be completely ignored by the app/build/import pipeline.

---

# 53. Gitignore scratches

Add an appropriate rule to `.gitignore`.

Expected concept:

```gitignore
design/mascots/**/scratches/
```

or equivalent matching the real directory structure.

Do not ignore the actual production mascot frames.

---

# 54. Already tracked scratches

Remember:

`.gitignore` does not automatically untrack files already committed.

If scratch files are already tracked:

remove them from the Git index without deleting the user's local working copies.

Do not destroy the user's scratch artwork.

---

# 55. Scratches not copied to Android resources

Do not:

- copy scratch images into `res/drawable`;
- reference them from runtime code;
- include them in mascot frame lists;
- package them intentionally into the APK.

Only production animation frames should be used.

---

# 56. Mascot animation lifecycle remains

Existing CR-002 lifecycle requirements remain:

animation runs only when:

```text
Today screen visible
AND
application foreground
```

Do not run mascot animation in Android background.

This is separate from background notification scheduling.

---

# 57. Tests — OCR dataset

Create automated/reproducible validation for the supplied 5-image dataset.

Required final report:

```text
Image    Expected         Predicted        Correct fields
1.jpeg   100/63/100       ...              x/3
2.jpeg   111/62/87        ...              x/3
3.jpeg   101/61/78        ...              x/3
4.jpeg   89/58/83         ...              x/3
5.jpeg   100/67/71        ...              x/3

Total: x/15
Accuracy: xx.x%
```

Required:

```text
> 80%
```

i.e. at least 13/15 fields.

---

# 58. Tests — OCR regression stages

Where practical, tests/diagnostics should cover:

```text
image can be loaded
orientation is usable
ML Kit returns structured result
numeric candidates extracted
candidate order deterministic
SYS/DIA/Pulse mapping deterministic
```

---

# 59. Tests — pressure chart scale

Required examples:

```text
visible values = 63, 76, 100, 121
→ 40..150
```

```text
visible values = 39, 100
→ lower bound <= 39
→ preferred 30..150
```

```text
visible values = 100, 151
→ upper bound >= 151
→ preferred 40..160
```

```text
visible values = 34, 167
→ preferred 30..170
```

Data outside selected date range must not influence current scale.

---

# 60. Tests — notifications

Test scheduler/receiver logic for:

```text
unfilled DAY
→ notification path executes

filled DAY
→ suppressed

unfilled EVENING
→ notification path executes

filled EVENING
→ suppressed

receiver target slot/date survives process-independent PendingIntent

opening MainActivity is not required to invoke notification path
```

---

# 61. Tests — mascot sizing

Verify visually / with layout constraints:

```text
new rendered scale ≈ 1.3 × CR-002 scale
```

and:

```text
no clipping
no functional UI overlap
```

---

# 62. Tests — mascot optional animations

Required:

```text
tea_1 + tea_2
→ Tea available

no tea files
→ no crash

fun_1 + fun_2
→ Fun available

no fun files
→ no crash
```

---

# 63. Tests — stationary speed

Verify configuration relationship:

```text
stationary frame interval ≈ walk interval × 3
```

Do not require screenshot/pixel timing tests if fragile.

Prefer testing animation configuration/state logic.

---

# 64. Tests — stationary movement

During Tea/Fun state:

```text
position before frame tick == position after frame tick
```

Walking state should continue to update position normally.

---

# 65. Acceptance criteria — OCR

```text
[ ] supplied 5-image dataset is exercised by a reproducible verifier
[ ] expected CSV is read as name/sys/dia/pul
[ ] every image produces a non-empty OCR result
[ ] every image produces usable SYS+DIA candidates
[ ] field accuracy >= 13/15 = 86.7%
[ ] no filename/ground-truth cheating exists in production code
[ ] regression cause was investigated against original working MVP
[ ] camera OCR still works
[ ] Photo Picker OCR still works
[ ] manual fallback still works
[ ] OCR never autosaves
```

---

# 66. Acceptance criteria — Pressure chart

```text
[ ] default visible SYS/DIA Y-axis = 40..150
[ ] values inside bounds do not cause autoscaling
[ ] value < 40 expands lower bound
[ ] value > 150 expands upper bound
[ ] expansion includes all visible values
[ ] only current visible/filter data affects bounds
[ ] pulse chart is not forced to 40..150
[ ] no stored BP values are modified
```

---

# 67. Acceptance criteria — Background notifications

```text
[ ] DAY reminder does not require app UI to be open
[ ] EVENING reminder does not require app UI to be open
[ ] app process/activity lifetime is not the reminder timer
[ ] manifest/system scheduled component can post reminder
[ ] filled slot still suppresses reminder
[ ] notification deep link still opens correct target date + slot
[ ] reboot does not permanently lose future reminders
[ ] scheduling does not create duplicates
[ ] no permanent foreground service was introduced
[ ] normal background manual test succeeds without reopening app
```

---

# 68. Acceptance criteria — Mascot size and animations

```text
[ ] current mascot assets 1254×1254 are handled without source-size assumptions
[ ] mascots appear approximately 1.3x larger than CR-002
[ ] mascots remain inside usable decorative area
[ ] tea_1/tea_2 are supported when present
[ ] fun_1/fun_2 are supported when present
[ ] Tea/Fun do not move mascot position
[ ] Tea/Fun frame changes are about 3x slower than walking
[ ] missing optional animations do not crash
[ ] stationary action is selected only from animations available to that mascot
```

---

# 69. Acceptance criteria — Scratches

```text
[ ] mascot scratches directories are ignored
[ ] .gitignore contains appropriate scratches rule
[ ] scratch files are not copied to Android resources
[ ] scratch files are not runtime animation frames
[ ] if previously tracked, scratches are untracked without deleting local originals
```

---

# 70. Suggested implementation order

```text
1. Baseline verification

2. OCR regression investigation
   - diff against original working baseline
   - create test-set verifier
   - establish current accuracy
   - identify failing stage

3. OCR fix
   - image preparation/orientation
   - ML Kit path
   - parser/order if necessary
   - rerun five-image benchmark
   - do not stop until acceptance threshold passes

4. Pressure chart scale
   - fixed 40..150 default
   - out-of-range expansion
   - tests

5. Background reminder fix
   - inspect current scheduler
   - decouple from Activity lifecycle
   - manifest/system alarm receiver
   - reboot/rescheduling
   - suppression logic
   - manual background verification

6. Mascot asset update
   - 1.3x rendered size
   - adjust bounds

7. Mascot animation state extension
   - Tea
   - Fun
   - generic stationary animations
   - slower frame timing
   - no movement

8. Scratches ignore
   - .gitignore
   - remove tracked scratch assets from index if needed
   - verify not packaged

9. Full regression

10. Final test/build
```

OCR and notification correctness have higher priority than mascot visual polish.

---

# 71 OCR benchmark history

OCR benchmark results must be persisted in the repository, not only printed in the final agent report.

Maintain an append-only file:

`app/src/test/pressure/benchmark_history.csv`

Columns:

```text
date
git_commit
version
dataset
correct_fields
total_fields
field_accuracy
complete_triplets
usable_sys_dia
notes
```

Rules:

never overwrite or delete previous benchmark rows;
append a new row whenever OCR behavior is intentionally changed and the pressure dataset is evaluated;
git_commit identifies the tested implementation;
use the current git tag/version when available; otherwise leave version empty;
for the current fixture set use dataset identifier pressure-v1;
correct_fields / total_fields is the exact SYS/DIA/Pulse field score;
complete_triplets is the number of images where all three fields are correct;
usable_sys_dia is the number of images where usable SYS and DIA candidates were produced;
notes should briefly identify the change, e.g. before CR-003, orientation fix, parser ordering fix.

For CR-003 specifically, record at least:

benchmark result for the current baseline before the OCR fix;
benchmark result for the final CR-003 implementation.

Do not modify ground-truth values in test_pressures.csv to improve the benchmark.

Also maintain:

app/src/test/pressure/BENCHMARK.md

It should document:

dataset location;
ground-truth CSV location;
dataset identifier (pressure-v1);
definition of field accuracy;
how to run the benchmark;
acceptance threshold;
meaning of each benchmark_history.csv column.

The historical CSV is part of the project and should be committed to git.

---

# 72. Regression requirements

CR-003 must not break:

- DAY/EVENING/EXTRA;
- partial check-ins;
- linked blood pressure;
- manual BP input;
- camera capture;
- Photo Picker;
- editable OCR result;
- calendar;
- cycle days;
- cycle overlay;
- chart bucket selection;
- empty-day selection;
- boolean charts;
- CSV export;
- themes;
- mascot walking;
- existing health data;
- notification deep links.

---

# 73. Final validation

Run at minimum:

```bash
./gradlew test
./gradlew assembleDebug
```

Also run the actual OCR image-set verifier.

If it is an instrumented test, run it on emulator/device and include its result.

Do not claim OCR requirement completed based only on parser unit tests.

---

# 74. Final report

Return:

```text
Implemented:
- ...

OCR regression cause:
- ...

OCR test set:
1.jpeg expected ... predicted ...
...
Accuracy: x/15 = xx.x%

Pressure graph:
- resulting scale behavior

Background reminders:
- scheduling architecture
- manual background test result

Mascots:
- rendered size change
- supported animations
- frame timings

Scratches:
- ignore pattern
- whether any files were untracked

Database migration:
- yes/no

Tests:
- ...

Build:
- ...

Not implemented / remaining:
- ...
```

Do not report a REQUIRED item as completed if its acceptance criterion does not pass.
