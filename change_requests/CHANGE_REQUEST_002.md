# CHANGE_REQUEST_002 — Camera capture, correct temporal chart axis, empty-period inspection, cycle overlays and mascots

## 0. Baseline

Этот change request применяется **поверх текущей рабочей версии приложения после CR-001**.

Не переписывать приложение с нуля.

Это следующая версия **того же самого Android-приложения**.

Сохранить:

- applicationId;
- package identity;
- существующую Room database;
- существующие пользовательские данные;
- существующие working features;
- существующие invariants из `AGENTS.md`;
- поведение, реализованное в `CHANGE_REQUEST_001.md`, если оно явно не изменяется данным документом.

Baseline commit:

```text
bf215fa1ac7e781b86349c6d0279905f290c87ce
```

Перед изменениями:

1. изучить текущий код;
2. выполнить существующие tests;
3. выполнить debug build;
4. убедиться, что baseline работает;
5. только после этого начинать CR-002.

Если требуется изменение Room schema:

- только non-destructive migration;
- сохранить существующие данные;
- добавить migration;
- добавить migration test.

---

# 1. Scope CR-002

## REQUIRED

1. Добавить возможность сразу сфотографировать тонометр из приложения.
2. Сохранить существующую возможность выбрать фото из галереи / Photo Picker.
3. Исправить семантику X-axis во всех analytics charts.
4. X-domain должен зависеть от выбранной группировки.
5. Отсутствующие даты / недели / месяцы должны существовать в X-domain.
6. Пользователь должен иметь возможность выбрать tap'ом период, в котором данных нет.
7. `NULL / no data` не должен рисоваться как точка, столбик или другое значение.
8. Добавить глобальное отображение первого дня цикла поверх всех analytics charts.
9. Добавить один общий toggle для cycle overlay.
10. Показывать номер дня цикла в Calendar.
11. Реализовать базовую систему локальных animated mascots из `design/mascots/`.

## OPTIONAL

1. Drag & drop маскотов пальцем.
2. Более сложные idle animations, если assets их поддерживают.

Не добавлять новые health metrics в рамках CR-002.

---

# 2. Критические invariants

Существующие invariants сохраняются.

Дополнительно:

```text
NULL != 0
NULL != false
NULL != a rendered chart point

missing date != removed date

chart domain != list of existing observations

changing grouping must change X-domain

tap on empty period must not snap to nearest observation

cycle overlay is visual context only

cycle overlay must not affect aggregation

cycle overlay must not create fake observations

camera photo is temporary

OCR still requires explicit Save
```

---

# 3. Blood pressure input — available actions

На экране добавления / редактирования давления должны быть доступны:

```text
[ Сфотографировать ]
[ Выбрать фото ]

или

ручной ввод значений
```

`Сфотографировать` должен быть непосредственным action из приложения.

Пользователь не должен сначала:

- открывать галерею;
- самостоятельно запускать Camera;
- сохранять фото;
- возвращаться в приложение;
- искать фотографию.

---

# 4. Camera capture flow

Требуемый flow:

```text
Blood Pressure Editor

→ Сфотографировать
→ открывается системная камера
→ пользователь делает фото
→ фото передаётся в существующий OCR pipeline
→ OCR извлекает числа
→ SYS / DIA / Pulse prefilled
→ пользователь проверяет / исправляет
→ explicit Save
```

Не создавать отдельную сложную custom camera UI без необходимости.

Предпочесть наиболее простой современный Android camera capture flow.

---

# 5. Camera + existing gallery flow

Существующая функция:

```text
Выбрать фото
```

должна продолжать работать.

Оба источника:

```text
CAMERA
PHOTO_PICKER
```

должны сходиться в один общий pipeline:

```text
Image
→ OCR
→ numeric candidate extraction
→ prefilled Blood Pressure Editor
```

Не дублировать OCR logic для камеры и галереи.

---

# 6. Camera temporary image privacy

Снятая фотография является temporary input.

Не сохранять её как часть `BloodPressureMeasurement`.

Не сохранять путь к ней в Room.

Не экспортировать её.

Не добавлять permanent photo history.

После того как изображение больше не требуется, temporary file/cache должен быть удалён.

Это относится также к:

- успешному OCR;
- cancel;
- OCR error;
- возврату пользователя без сохранения измерения.

---

# 7. Camera cancellation

Если пользователь:

```text
Сфотографировать
→ открыл Camera
→ Cancel
```

то:

- приложение не должно crash;
- существующие поля Blood Pressure Editor не должны очищаться;
- measurement не создаётся;
- пользователь остаётся в editor.

---

# 8. Camera unavailable

Если подходящее camera application отсутствует или capture не удалось:

показать понятное сообщение.

Например:

```text
Не удалось открыть камеру.
Можно выбрать фотографию или ввести значения вручную.
```

Остальная функциональность должна продолжать работать.

---

# 9. OCR semantics remain unchanged

После фотографии:

первые распознанные числовые кандидаты в visual reading order:

```text
1 → SYS
2 → DIA
3 → Pulse
```

Prefill fields immediately.

Пользователь всегда может изменить значения.

OCR никогда не выполняет automatic Save.

---

# 10. Main Analytics issue

Текущая реализация X-axis ведёт себя неправильно.

Положение X сейчас не должно определяться только существующими observations.

Необходимо разделить:

```text
X DOMAIN
```

и:

```text
ACTUAL OBSERVATIONS
```

X-domain определяется выбранным:

- date range;
- grouping.

Observations только размещаются внутри этого domain.

---

# 11. Fundamental X-axis rule

Главное правило:

> X-axis является временной осью выбранного диапазона, а не списком периодов, для которых существуют данные.

Пример.

Выбран диапазон:

```text
17–23 августа
```

Данные существуют только:

```text
17
20
23
```

Логический daily X-domain всё равно:

```text
17 18 19 20 21 22 23
```

Нельзя превращать его в:

```text
17 20 23
```

с тремя равномерно расположенными категориями.

---

# 12. Missing dates preserve space

Для daily grouping:

```text
17 → value
18 → NULL
19 → NULL
20 → value
21 → NULL
22 → NULL
23 → value
```

Точки 17, 20 и 23 должны находиться в местах, соответствующих их реальному положению во временном диапазоне.

Пустые даты сохраняют своё место на оси.

---

# 13. NULL rendering

Это критическое требование.

Если значение отсутствует:

```text
value = NULL
```

не рисовать:

- точку;
- столбик;
- marker;
- значение 0;
- значение false;
- fake observation.

На самом chart в этой позиции отсутствуют data marks.

---

# 14. Missing values in line charts

Если между двумя observations существует missing bucket, не интерполировать его как существующее значение.

Предпочтительно line series должна иметь gap через missing period.

Например:

```text
17 = 5
18 = NULL
19 = 7
```

не должно визуально означать, что на 18-е существовало вычисленное промежуточное значение.

Никакой automatic interpolation.

---

# 15. Interactive empty dates

При этом пользователь должен иметь возможность tap'нуть область X-axis, соответствующую дате, даже если там нет data point.

Пример:

```text
17 → Energy = 5
18 → no data
19 → Energy = 7
```

Tap на область 18-го должен выбрать:

```text
18 августа
```

а не ближайшую существующую точку.

---

# 16. Do not snap empty date to nearest observation

Запрещено:

```text
tap 18 August
→ nearest data point = 17 August
→ show 17 August
```

или:

```text
tap 18 August
→ nearest data point = 19 August
```

Tap selection должна происходить по X-domain/bucket, а не только по hit testing существующих marks.

При необходимости реализовать отдельный invisible interaction layer / bucket hit areas.

---

# 17. Empty-date tooltip

Если выбран день, в котором ни для одной selected series нет данных:

показать, например:

```text
Вт, 18 августа

Данных нет
```

Это selection UI.

Это **не data point**.

Допустимо одновременно показывать:

- vertical crosshair;
- subtle selected-day background;
- tooltip/card.

Но не рисовать фальшивую observation.

---

# 18. Partially missing date

Если на выбранную дату часть series имеет данные, а часть нет:

пример:

```text
Вт, 18 августа

Энергия · День        4
Энергия · Вечер       Нет данных

Тревога · День        7
Тревога · Вечер       5
```

Не скрывать series только потому, что её значение NULL.

В tooltip явно показывать:

```text
Нет данных
```

---

# 19. Empty state versus NULL series

Различать:

## Full empty bucket

Никаких selected data series нет:

```text
Данных нет
```

## Partial empty bucket

Какие-то series существуют:

```text
SYS 118
DIA 76
Pulse — Нет данных
```

---

# 20. Grouping must change X domain

Текущая проблема:

при выборе:

```text
Дни
Недели
Месяцы
```

не должна оставаться одна и та же X-axis с переставленными точками.

Grouping меняет:

1. aggregation;
2. X-domain;
3. X labels;
4. hit regions;
5. tooltip semantics.

---

# 21. Daily grouping

При:

```text
По дням
```

X-domain состоит из календарных дней выбранного диапазона.

Пример:

```text
Пн, 17
Вт, 18
Ср, 19
Чт, 20
...
```

Каждый календарный день является отдельным selectable X bucket.

---

# 22. Weekly grouping

При:

```text
По неделям
```

X-domain состоит из недельных buckets.

Например:

```text
3–9 авг
10–16 авг
17–23 авг
24–30 авг
```

Позиции данных соответствуют этим week buckets.

Не использовать daily X positions для weekly aggregation.

---

# 23. Monthly grouping

При:

```text
По месяцам
```

X-domain состоит из месяцев:

```text
Май
Июн
Июл
Авг
```

или:

```text
Май 2026
Июн 2026
...
```

если диапазон пересекает годы.

Не использовать daily/month-independent point positions.

---

# 24. Raw mode

В `Raw` mode сохранять реальное время отдельных observations.

Например:

```text
17 Aug 13:04 DAY
17 Aug 19:12 EVENING
18 Aug 13:18 DAY
```

Несколько observations одного дня не должны сливаться.

При этом взаимодействие должно по-прежнему позволять выбрать календарный день.

Tooltip может показать все relevant raw observations внутри выбранного дня.

---

# 25. Raw tooltip

Пример:

```text
Пн, 17 августа

DAY · 13:04
Энергия 4

EVENING · 19:12
Энергия 7
```

Если выбран день без observations:

```text
Вт, 18 августа

Данных нет
```

---

# 26. Weekly tooltip

Tap weekly bucket:

```text
11–17 августа
```

Если data exists:

```text
Энергия · День
Среднее: 5.4
5 наблюдений
```

Использовать фактически выбранную aggregation mode.

Например:

```text
Максимум: 8
```

если выбран Max.

---

# 27. Empty weekly bucket

Если ни одного значения нет:

```text
11–17 августа

Данных нет
```

Не snap к соседней неделе.

Не убирать пустую неделю из X-domain.

---

# 28. Monthly tooltip

Tap month:

```text
Август 2026

Энергия · День
Среднее: 5.8
```

Если данных нет:

```text
Август 2026

Данных нет
```

---

# 29. Missing aggregated bucket

Если после aggregation для bucket нет non-null values:

результат:

```text
NULL
```

а не:

```text
0
```

Никакой point/bar для такого bucket не рисуется.

Но bucket остаётся selectable.

---

# 30. Applies to all chart types

Новая X-domain / missing-selection semantics относится ко всем temporal charts:

- scale metrics;
- boolean metrics;
- SYS/DIA;
- pulse;
- future compatible temporal charts.

Не исправлять только Energy chart.

---

# 31. Boolean charts and missing data

Для raw boolean:

```text
true  → observable "Да"
false → observable "Нет"
NULL  → no mark
```

NULL:

- не рисуется как `Нет`;
- не рисуется как baseline point;
- остаётся selectable через X-domain.

Tap пустого дня:

```text
Физическая усталость
Нет данных
```

---

# 32. Blood pressure and missing dates

Для BP:

если 18 августа measurement отсутствует:

не рисовать:

```text
SYS = 0
DIA = 0
```

и не создавать points.

Но tap на 18 августа должен показать:

```text
18 августа

Артериальное давление
Нет данных
```

---

# 33. One shared period-selection model

По возможности вынести X period/bucket logic в общий analytics layer, а не реализовывать независимо внутри каждого chart composable.

Например conceptual model:

```text
TimeBucket
- start
- end
- label
- groupingType
```

Charts должны использовать один и тот же ordered bucket list.

Это уменьшит риск, что Energy, BP и boolean charts будут иметь разные X positions.

---

# 34. Cycle start overlay — goal

Добавить возможность визуально видеть первый день цикла поверх графиков.

Это context overlay, а не отдельная health metric.

Пользователь должен одной настройкой включать или выключать его **сразу для всех графиков Analytics**.

---

# 35. Global toggle

На Analytics screen добавить компактный control:

```text
Показывать начало цикла
[ ON / OFF ]
```

или короткий chip/action с аналогичной семантикой.

Toggle является глобальным для текущего Analytics screen.

При изменении:

все отображаемые charts обновляются одновременно.

Не создавать отдельный toggle внутри каждого chart.

---

# 36. Cycle overlay persistence

Сохранять preference в DataStore.

Например:

```text
analytics_show_cycle_start = true / false
```

Default:

```text
false
```

если настройка ранее не существовала.

---

# 37. Cycle overlay visual

Первый день цикла отображать как semi-transparent cherry / burgundy vertical area.

Visual direction:

```text
transparent cherry band
```

Полоса должна находиться **под data series**, а не перекрывать их непрозрачным цветом.

Она не должна ухудшать читаемость:

- points;
- lines;
- columns;
- axes;
- tooltip;
- labels.

---

# 38. Cycle overlay color

Не hardcode color в chart implementation.

Добавить semantic theme color/token, например:

```text
cycleStartOverlay
```

Light theme:

вишнёвая/бордовая semi-transparent область.

Dark theme:

подходящий читаемый вариант той же визуальной семантики.

Использовать low alpha.

---

# 39. Daily cycle overlay

В Daily / Raw temporal representation начало цикла должно занимать визуально область одного календарного дня.

Пример:

```text
17 18 19 20 21
      ▒
      ▒ = cycle start on 19
```

То есть это скорее вертикальная day band / highlighted day region.

Не отображать cycle start как обычную circular data point.

---

# 40. Multiple cycle starts

Если выбранный диапазон содержит несколько CycleEvent:

показать overlay для каждого start date.

Например:

```text
4 July
1 August
29 August
```

каждый получает собственную cherry band.

---

# 41. Weekly grouping cycle overlay

При weekly grouping cycle start остаётся событием конкретной даты.

Не закрашивать всю неделю полностью как «начало цикла».

Предпочтительный порядок:

1. если chart library позволяет позиционировать overlay внутри weekly bucket по exact date — использовать exact position;
2. иначе использовать узкую cherry strip/marker внутри соответствующего weekly bucket;
3. tooltip/selection должен сохранять exact cycle start date.

Не превращать целую неделю в cycle-start period.

---

# 42. Monthly grouping cycle overlay

Аналогично weekly.

Не закрашивать весь месяц.

Если возможно:

показать narrow strip внутри month bucket приблизительно/точно в позиции cycle start date.

Если library не поддерживает внутреннее positioning:

показать компактный vertical cherry marker внутри month bucket.

Точная дата должна быть доступна через tooltip/selection.

---

# 43. Cycle information in selected tooltip

Если пользователь выбирает дату, являющуюся первым днём цикла:

добавить:

```text
Начало цикла
```

Например:

```text
18 августа

Начало цикла

Энергия · День
Нет данных
```

CycleEvent существует независимо от наличия metric data.

---

# 44. Cycle info in weekly/monthly tooltip

Если selected weekly/monthly bucket содержит CycleEvent:

добавить exact date.

Например:

```text
11–17 августа

Начало цикла: 14 августа

Энергия
Среднее: 5.2
```

Если несколько:

показать все start dates внутри bucket.

---

# 45. Cycle overlay does not affect data

Cycle overlay:

- не участвует в min/max;
- не участвует в mean;
- не участвует в axis scale;
- не считается observation;
- не создаёт fake chart point;
- не меняет SYS/DIA;
- не меняет boolean share.

Только визуальный temporal context.

---

# 46. Cycle overlay across charts

При включённом toggle overlay должен отображаться одновременно как минимум на:

- scale metric charts;
- boolean temporal charts;
- blood pressure chart;
- pulse chart.

То есть vertical cycle marker должен быть temporal alignment reference между разными graphs.

---

# 47. Calendar cycle day — now REQUIRED

Функция из backlog CR-001 теперь входит в REQUIRED.

В каждой calendar date cell, для которой известен cycle day, показать маленькое дополнительное число.

Пример:

```text
┌──────────┐
│ 17       │
│       8  │
└──────────┘
```

где:

```text
17 = day of month
8 = cycle day
```

---

# 48. Calendar cycle day appearance

Cycle-day number:

- приблизительно 50–60% размера основной цифры даты;
- visual secondary;
- не должен мешать существующим indicators DAY/EVENING/BP;
- должен читаться в Light и Dark theme.

---

# 49. Calendar cycle calculation

Не добавлять новое persisted поле.

Использовать existing CycleEvent data.

Для каждой даты:

найти latest cycle start not after selected date.

```text
cycle_day =
date - latest_cycle_start + 1
```

На следующем CycleEvent счёт снова начинается с 1.

---

# 50. Calendar missing cycle information

Если ни одного предыдущего known CycleEvent нет:

cycle day не показывать.

Не угадывать.

Не прогнозировать цикл.

---

# 51. Mascots — now implement base feature

Система animated mascots из CR-001 backlog теперь должна получить базовую рабочую реализацию.

Assets находятся в:

```text
design/mascots/
```

Mascot может быть:

- собакой;
- человеком;
- chibi;
- pixel-art character;
- другим персонажем.

Не hardcode поведение под dog-specific model.

---

# 52. Mascot implementation scope

REQUIRED:

- локальные assets;
- 1–3 персонажа;
- Today screen only;
- idle frame;
- walk animation;
- horizontal movement;
- random/periodic stops;
- direction change;
- horizontal mirroring;
- ограниченная decorative area;
- pause when Today screen not visible.

OPTIONAL:

- drag & drop;
- extended idle frames;
- more advanced randomness.

---

# 53. Mascot source assets

Ожидаемая структура:

```text
design/mascots/
  mascot_name/
    idle.png
    walk_1.png
    walk_2.png
```

Дополнительно могут существовать:

```text
idle_1.png
idle_2.png
walk_3.png
walk_4.png
```

Минимальная реализация обязана поддерживать:

```text
idle.png
walk_1.png
walk_2.png
```

---

# 54. Mascot architecture

Не разносить dog/person-specific code по Today screen.

Создать generic model/config, например conceptual:

```text
MascotDefinition
- id
- idle frames
- walk frames
- scale
```

Exact implementation agent выбирает сам.

Главное:

замена одного набора character assets в будущем не должна требовать переписывать movement engine.

---

# 55. Mascot movement

Персонаж двигается внутри dedicated decorative bounds.

При движении:

```text
walk_1
walk_2
walk_1
walk_2
```

При остановке:

```text
idle
```

Через некоторый interval персонаж может снова начать движение.

Не требуется сложная physics simulation.

---

# 56. Direction

Source assets могут смотреть в одну сторону.

При движении в противоположную:

использовать horizontal mirroring.

Не требовать отдельные:

```text
walk_left
walk_right
```

---

# 57. Mascot screen area

Использовать свободное декоративное пространство Today screen.

Mascots не должны:

- закрывать DAY;
- закрывать EVENING;
- закрывать давление;
- закрывать важные buttons;
- перекрывать bottom navigation;
- мешать scroll;
- перехватывать touch важных UI controls.

---

# 58. Mascot lifecycle

Animation должна работать только когда:

```text
Today screen visible
AND
app in foreground
```

При переходе на Calendar / Analytics / Settings:

остановить animation loop.

Не использовать background service.

Не использовать WorkManager.

Не хранить continuous mascot position в Room.

---

# 59. Mascot asset changes later

Пользователь будет менять mascot assets в будущих версиях.

Поэтому implementation должна быть максимально asset-driven.

Не связывать application business logic с конкретным персонажем.

Если текущие assets имеют разные названия/наборы, адаптировать их через один central config, а не через scattered conditions.

---

# 60. Dragging — OPTIONAL

Если реализуется:

```text
touch mascot
→ drag inside mascot area
→ release
→ mascot resumes movement
```

Не позволять утащить character за границы decorative area.

Drag не должен влиять на persisted health data.

---

# 61. Existing themes

Все новые UI elements должны работать в:

```text
SYSTEM
LIGHT
DARK
```

Cycle overlay должен корректно выглядеть в обеих app themes.

Mascot area не должна иметь hardcoded background, конфликтующий с pink/cherry theme.

---

# 62. Tests — Camera

Добавить tests/fakes там, где это разумно.

Проверить минимум:

```text
camera cancel → no measurement created

camera OCR → fields prefilled

camera image does not cause automatic Save

gallery OCR still works

existing form values survive camera cancel

temporary photo is not stored in Room
```

---

# 63. Tests — X-domain

Обязательно протестировать analytics domain logic отдельно от rendering.

Пример:

```text
range = Aug 17..Aug 23
data = Aug 17, Aug 20, Aug 23
grouping = DAY
```

Expected buckets:

```text
17
18
19
20
21
22
23
```

а не:

```text
17
20
23
```

---

# 64. Tests — grouping

Проверить:

```text
DAY grouping generates daily buckets

WEEK grouping generates weekly buckets

MONTH grouping generates monthly buckets

changing grouping changes bucket boundaries

empty buckets remain in result
```

---

# 65. Tests — missing data

Проверить:

```text
NULL scale does not become 0

NULL boolean does not become false

empty bucket has no rendered value

empty bucket is still present/selectable

empty bucket is not replaced with neighbor's data
```

---

# 66. Tests — selection

При dataset:

```text
17 = 4
18 = NULL
19 = 7
```

selection 18 должна возвращать:

```text
selected bucket = 18
value = NULL
```

а не 17/19.

---

# 67. Tests — Cycle overlay

Проверить:

```text
cycle overlay OFF → no overlays

cycle overlay ON → CycleEvents returned for chart decoration

multiple CycleEvents → multiple overlays

CycleEvent does not affect aggregate values

CycleEvent does not create observation

cycle start date appears in selected tooltip
```

---

# 68. Tests — Calendar cycle day

Проверить:

```text
cycle start = Aug 10

Aug 10 → cycle day 1
Aug 11 → cycle day 2
Aug 17 → cycle day 8
```

При новом start:

```text
Sep 7 → cycle day 1
```

Дата до первого known start:

```text
unknown
```

---

# 69. Acceptance criteria — Camera

CR-002 camera считается выполненным, если:

```text
[ ] Blood Pressure Editor has "Сфотографировать"
[ ] existing "Выбрать фото" still works
[ ] camera opens directly
[ ] captured image goes through OCR
[ ] SYS/DIA/Pulse are prefilled
[ ] all fields remain editable
[ ] explicit Save is required
[ ] camera cancel is safe
[ ] photo is not permanently stored
```

---

# 70. Acceptance criteria — X-axis

```text
[ ] missing dates preserve horizontal space
[ ] data points are positioned according to actual temporal bucket
[ ] DAY / WEEK / MONTH use different X-domains
[ ] X labels correspond to current grouping
[ ] empty day/week/month remains selectable
[ ] selection of empty bucket does not snap to nearest data point
[ ] NULL does not render as point/bar
[ ] line charts do not interpolate fake missing values
```

---

# 71. Acceptance criteria — Empty-period interaction

```text
[ ] tap day with no data selects that exact day
[ ] tooltip can show "Данных нет"
[ ] partial missing series show "Нет данных"
[ ] no fake point is drawn for NULL
[ ] selected-day crosshair/highlight may exist without a data point
[ ] same behavior works for pressure and boolean charts
```

---

# 72. Acceptance criteria — Cycle overlay

```text
[ ] one global toggle controls cycle overlay on all charts
[ ] setting persists
[ ] overlay is semi-transparent cherry/burgundy
[ ] overlay appears behind data
[ ] daily view highlights one-day region
[ ] weekly/monthly view does not color entire bucket as cycle start
[ ] multiple cycle starts are supported
[ ] cycle start is included in selection tooltip
[ ] cycle overlay does not alter aggregation or Y-axis
```

---

# 73. Acceptance criteria — Calendar

```text
[ ] cycle day appears as small secondary number
[ ] cycle start = day 1
[ ] next dates increment correctly
[ ] next start resets to day 1
[ ] no cycle number before first known start
[ ] existing calendar indicators still work
```

---

# 74. Acceptance criteria — Mascots

```text
[ ] mascot system is generic, not dog-specific
[ ] assets come from provided local mascot sets
[ ] at least one mascot can idle and walk
[ ] walking animation uses provided frames
[ ] character can change direction by mirroring
[ ] character stays inside decorative Today area
[ ] character does not block functional controls
[ ] animations stop when Today is not visible
[ ] no background service is created for mascot animation
```

---

# 75. Suggested implementation order

Работать в следующем порядке:

```text
1. Baseline verification

2. Camera capture
   - system camera action
   - temporary image
   - shared OCR pipeline
   - cleanup

3. Analytics domain refactor
   - explicit time buckets
   - daily domain
   - weekly domain
   - monthly domain
   - missing buckets

4. Analytics interaction
   - select empty bucket
   - no snapping to nearest point
   - tooltip "Нет данных"
   - partial missing series

5. Cycle overlay
   - global toggle
   - daily band
   - week/month marker semantics
   - tooltips

6. Calendar cycle-day numbers

7. Mascot engine
   - asset/config model
   - idle/walk
   - movement
   - mirroring
   - lifecycle

8. Optional mascot dragging

9. Regression tests

10. Full build
```

Не начинать mascot polish, пока camera и Analytics fixes не работают.

---

# 76. Regression requirements

CR-002 не должен ломать уже работающие:

- partial check-ins;
- DAY/EVENING/EXTRA;
- notifications;
- notification deep links;
- blood pressure editing inside check-in;
- gallery OCR;
- manual blood pressure;
- Health Connect;
- Calendar navigation;
- existing graph metric selection;
- existing min/max;
- legend;
- Light/Dark/System themes;
- CSV export;
- existing launcher identity;
- Room data.

---

# 77. Final validation

В конце выполнить минимум:

```bash
./gradlew test
./gradlew assembleDebug
```

Если существуют доступные instrumented/UI tests:

запустить релевантные tests.

Не завершать работу с failing build/tests без явного объяснения.

---

# 78. Final report from agent

После реализации вернуть summary:

```text
Implemented:
- ...

Analytics X-domain fix:
- ...

Empty-bucket interaction:
- ...

Camera:
- ...

Cycle overlay:
- ...

Calendar cycle day:
- ...

Mascots:
- ...

Database migration:
- yes/no

Tests:
- ...

Build:
- ...

Deferred / not implemented:
- ...
```

Если REQUIRED requirement не реализован:

указать это явно.

Не считать его выполненным молча.
