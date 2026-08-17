# CHANGE_REQUEST_001 — UI compactness, blood pressure UX, analytics and theming

## 0. Baseline

Этот change request применяется **поверх уже существующего рабочего MVP**.

Существующее приложение считать baseline.

Не переписывать проект с нуля.

Не менять существующую архитектуру, схему данных или библиотеку графиков без необходимости.

Перед началом:

1. изучить существующий код;
2. запустить существующие tests;
3. выполнить debug build;
4. убедиться, что baseline работает;
5. только после этого вносить изменения.

Baseline commit:

```text
e82b90ea2d7e5a7701508d6a4d1a2ad63a15aa65
```

Если для реализации всё же требуется изменение Room schema:

- сохранить все существующие пользовательские данные;
- сделать нормальную migration;
- не использовать destructive migration;
- добавить migration test.

Существующие invariants из AGENTS.md остаются обязательными.

Особенно:

```text
NULL != 0
NULL != false

raw observations must be preserved

OCR never saves automatically

photos are not persisted

DAY / EVENING / EXTRA semantics remain unchanged
```

---

# 1. Приоритеты

## REQUIRED — реализовать в этой итерации

1. Более компактный check-in UI.
2. Якоря шкал по нажатию вместо постоянного отображения.
3. Компактное отображение выбранного значения шкалы.
4. Отображение и редактирование давления внутри check-in.
5. Изменение поведения OCR давления.
6. Существенное улучшение Analytics.
7. Отдельный график давления и отдельный график пульса.
8. Более компактный selector метрик в Analytics.
9. Интерактивные значения при нажатии на график.
10. Нормальные X/Y axes и legend.
11. Отображение min/max.
12. Theme override внутри приложения.
13. Переименование приложения в `Pressure and Condition`.
14. Новая pink/cherry theme при сохранении существующих зелёных accent/buttons.

## OPTIONAL / низкий приоритет

1. Номер дня цикла внутри календарной клетки.
2. Пользовательская launcher icon, если asset уже предоставлен.

## DEFERRED

Анимированные маскоты / персонажи на Today screen описаны в конце документа как future feature.

Не реализовывать их в этой итерации, даже если assets уже присутствуют в design/mascots/.

---

# 2. Компактный Check-in UI

## Проблема

Сейчас якоря шкал постоянно занимают место под каждым параметром.

Из-за этого check-in становится слишком длинным и требует большого количества scrolling.

## Требуемое поведение

В collapsed/default состоянии якоря вообще не показывать.

Строка числовой метрики должна выглядеть примерно так:

```text
Энергия                              ( 7 )
[────────────●────────────]
0                                10
```

Точное визуальное оформление может отличаться, но layout должен быть компактным.

Основное требование:

- название;
- компактное текущее значение;
- slider;
- никаких постоянно отображаемых многострочных anchor descriptions.

---

# 3. Отображение выбранного значения

Для scale metric `0..10` текущее значение должно быть визуально заметно, но не занимать отдельную большую строку.

Предпочтительный вариант:

```text
Энергия                         [ 7 ]
```

где `7` находится в небольшом круглом или rounded badge.

Badge должен:

- быть небольшим;
- располагаться в той же header row, что и название;
- обновляться сразу при движении slider;
- отображать `—`, если значение NULL.

Допустима реализация value label непосредственно около slider thumb, если она получается визуально чистой.

Не делать одновременно:

- большой value text;
- badge;
- ещё один value label.

Нужен один компактный способ отображения текущего значения.

---

# 4. Anchors по нажатию

Название metric с anchors должно быть interactive.

Например:

```text
Энергия ⓘ
```

или вся строка `Энергия` clickable.

При tap открыть небольшой:

- dialog;

или

- modal bottom sheet.

Не переходить на отдельный screen.

---

# 5. Energy anchors

При tap на `Энергия` показать:

```text
Энергия

0
Вообще нет ощущения, что могу что-то делать:
ни есть, ни встать с кровати.

5
Получается что-то потыкать,
быт примерно ок.

10
Постоянно что-то делаю весь день.
```

Закрывается:

- tap outside;
- swipe down, если bottom sheet;
- кнопкой Close/OK.

---

# 6. Anxiety anchors

При tap на `Тревога` показать:

```text
Тревога

0
Чувствую и веду себя спокойно или счастливо,
не нервничаю, не ною.

5
Ною как обычно,
но физически всё ок.

10
Всё тело напрягается,
психую,
есть яркий комок тревоги.
```

---

# 7. Метрики без anchors

Для:

```text
Хочется плакать
Пореветь
```

дополнительный anchor popup пока не нужен.

Если у MetricDefinition уже существует generic support anchors, оставить его.

Но не придумывать новые тексты самостоятельно.

---

# 8. Давление внутри Check-in

## Проблема

После добавления давления из check-in сейчас непонятно:

- добавилось ли оно;
- какое значение было сохранено;
- как его изменить.

Это необходимо исправить.

---

# 9. Blood pressure section в Check-in screen

Если к текущему check-in давление не привязано:

```text
Артериальное давление

[ + Добавить давление ]
```

Если измерение уже существует:

```text
Артериальное давление

118 / 76
Пульс 68

[ Изменить ]
```

Если pulse отсутствует:

```text
118 / 76

[ Изменить ]
```

Не показывать пустой `Пульс —`.

---

# 10. Редактирование linked blood pressure

Tap:

```text
Изменить
```

открывает существующий BloodPressure Editor с заполненными текущими значениями.

После save:

- обновить существующую запись;
- не создавать accidental duplicate;
- вернуться в check-in;
- сразу показать новые значения внутри check-in.

---

# 11. Несколько pressure measurements

Не вводить новое ограничение базы без необходимости.

Если существующая data model допускает несколько `BloodPressureMeasurement` с одним `checkin_id`, сохранить эту возможность.

Если linked measurements несколько, показать компактный список:

```text
118 / 76 · пульс 68 · 13:14
121 / 79 · пульс 72 · 13:22
```

Каждое измерение editable отдельно.

При обычном сценарии с одной записью UI остаётся компактным.

---

# 12. Independent blood pressure

Существующая возможность добавить давление вне check-in должна сохраниться.

То есть по-прежнему допустимо:

```text
BloodPressureMeasurement.checkin_id = NULL
```

Не заставлять пользователя создавать check-in ради давления.

---

# 13. Фото давления

Предыдущие privacy requirements остаются без изменений.

Фотография:

- используется только как input OCR;
- не сохраняется в permanent storage;
- не хранится рядом с BloodPressureMeasurement;
- не экспортируется;
- удаляется из temporary cache, когда больше не нужна.

---

# 14. Новое OCR поведение

## Цель

После OCR пользователь должен сразу получить заполненную форму давления.

Не нужен промежуточный UI вида:

```text
Распознано:
118
76
68
```

с последующим ручным переносом.

---

# 15. OCR mapping

Из результата OCR извлечь числовые значения.

Для обычного сценария взять первые три распознанных числовых значения в визуальном reading order.

Mapping:

```text
first  numeric value → systolic / SYS
second numeric value → diastolic / DIA
third  numeric value → pulse
```

После OCR сразу открыть editor:

```text
Верхнее / SYS
[ 118 ]

Нижнее / DIA
[ 76 ]

Пульс
[ 68 ]
```

Все три поля editable.

---

# 16. OCR incomplete values

Если найдено только:

```text
118
76
```

заполнить:

```text
SYS   = 118
DIA   = 76
pulse = empty
```

Если найдено одно значение:

```text
SYS = first value
```

остальные оставить пустыми.

Если значений нет:

показать пустую форму и сообщение:

```text
Не удалось уверенно распознать значения.
Введи их вручную.
```

---

# 17. OCR never autosaves

Даже если найдены все три числа:

```text
118
76
68
```

не сохранять автоматически.

Flow остаётся:

```text
photo
→ OCR
→ prefilled editable form
→ user checks/corrects
→ explicit Save
```

---

# 18. Analytics screen — общий UX

Analytics сейчас необходимо сделать более информативным и одновременно более компактным.

Основной screen должен отдавать большую часть вертикального пространства самим графикам.

Controls не должны постоянно занимать половину экрана.

---

# 19. Compact metric selector

Не показывать длинный вертикальный список всех metric checkbox непосредственно над графиком.

Предпочтительный UI:

```text
Метрики
[ Энергия ] [ Тревога ] [ +3 ]
                         [ Изменить ]
```

или другой компактный chip-based summary.

Tap `Изменить` открывает:

```text
ModalBottomSheet
```

с полным списком metrics:

```text
[x] Энергия
[x] Тревога
[ ] Хочется плакать
[ ] Пореветь
...
```

После закрытия sheet график обновляется.

---

# 20. Compact analytics controls

Следующие advanced settings также не должны постоянно занимать большой вертикальный блок:

```text
date range
grouping
aggregation
slot selection
chart layout
```

Допустимо использовать:

- compact chips;
- dropdown;
- bottom sheet;
- expandable `Настройки графика`.

Главное:

сам график должен оставаться центральной частью screen.

---

# 21. Interactive chart values

Пользователь должен иметь возможность tap/press на конкретную точку графика.

После tap показать marker/tooltip.

Например:

```text
Пн, 17 августа

Энергия · День
7
```

Для нескольких series на одном X:

```text
Пн, 17 августа

Энергия · День       7
Энергия · Вечер      5
Тревога · День       4
```

Marker должен показывать реальные значения выбранной X-position.

---

# 22. Raw multiple observations

Если в один день существует несколько raw observations:

```text
DAY
EVENING
EXTRA
```

они не должны исчезать.

Tooltip должен позволять различить series/slot.

Пример:

```text
Пн, 17

День       4
Вечер      7
Extra      5
```

---

# 23. Legend

На графиках с несколькими series должна быть нормальная legend.

Например:

```text
● Энергия · День
● Энергия · Вечер
```

или:

```text
● SYS
● DIA
```

Legend должна соответствовать фактическим series colors/styles.

Существующая setting:

```text
Показывать легенду
```

должна работать.

---

# 24. X axis

На X-axis должны быть читаемые date labels.

## Для короткого daily диапазона

Предпочтительный формат:

```text
Пн, 17
Вт, 18
Ср, 19
```

То есть:

```text
short weekday + day of month
```

Для русского locale:

```text
Пн, 17
Вт, 18
Ср, 19
Чт, 20
Пт, 21
Сб, 22
Вс, 23
```

---

# 25. Dense X axis

На диапазоне 30 дней или больше нельзя пытаться подписать каждую дату, если labels начинают overlapping.

Использовать adaptive tick density.

Например:

```text
17 авг
21 авг
25 авг
29 авг
...
```

При этом tap marker всё равно должен показывать точную дату конкретной observation.

---

# 26. Y axis для scale metrics

Для 0..10 metrics Y-axis должен явно отображать шкалу:

```text
10
8
6
4
2
0
```

или более частые ticks, если UI остаётся читаемым.

Range для этих metrics:

```text
0..10
```

Не autoscale до, например:

```text
4..7
```

если это делает визуальное изменение искусственно более сильным.

---

# 27. Highlight min/max

Для отображаемой scale series определить:

```text
visible minimum
visible maximum
```

на текущем выбранном date range/filter.

Эти точки должны визуально отличаться от обычных.

Например:

- слегка увеличенный point;
- дополнительный outline;
- маленький marker.

Не нужно постоянно выводить огромные текстовые labels около каждой extreme point.

Допустим небольшой summary над/под chart:

```text
min 2
max 9
```

Tap на extreme point должен работать так же, как обычный marker.

---

# 28. Несколько series и min/max

Min/max считать **отдельно для каждой фактической series**.

Например:

```text
Energy DAY
Energy EVENING
```

имеют независимые min/max.

Не вычислять общий min/max после смешивания DAY и EVENING.

---

# 29. Boolean metrics — RAW visualization

Для raw boolean data использовать categorical two-level chart.

Y-axis:

```text
Да
Нет
```

Mapping:

```text
true  → Да
false → Нет
NULL  → no point
```

Предпочтительный renderer:

```text
points + step-like connection
```

или только хорошо видимые points.

Основная семантика:

```text
false != missing
```

Поэтому `false` обязательно должен быть видимой observation.

---

# 30. Не использовать half bar для false

Не реализовывать схему:

```text
true  = full column
false = half column
```

потому что она визуально превращает `false` в промежуточное количественное значение.

Если используется column renderer для raw boolean, false всё равно должен иметь явный baseline marker/символ.

Предпочтителен categorical point/step renderer.

---

# 31. Boolean aggregation

После группировки нескольких observations:

```text
day
week
month
```

boolean metric превращать в:

```text
share_true =
count(true) / count(non-null)
```

На aggregated chart использовать columns.

Y-axis:

```text
100%
75%
50%
25%
0%
```

Название/tooltip должен ясно говорить:

```text
Доля ответов «Да»
```

Пример:

```text
Неделя 33
Физическая усталость
67% «Да»
6 ответов
```

Missing observations не входят в denominator.

---

# 32. Blood pressure analytics

Артериальное давление и pulse разделить на два charts.

Не показывать pulse третьей линией на том же pressure chart.

---

# 33. Blood pressure chart

Один chart:

```text
Артериальное давление
```

Две series:

```text
SYS
DIA
```

У них должны быть визуально различимые colors.

Legend:

```text
● SYS
● DIA
```

Обе series находятся на одной Y-axis.

---

# 34. Raw blood pressure

Если за день было несколько measurements:

```text
13:00 118 / 76
19:00 125 / 81
```

в Raw mode показать оба.

Не сводить автоматически в одно значение.

Tooltip:

```text
Пн, 17 · 13:04

SYS 118
DIA 76
```

---

# 35. Pulse chart

Отдельный chart:

```text
Пульс
```

Одна series:

```text
pulse
```

Если pulse NULL для measurement:

точка отсутствует.

Не считать NULL как 0.

---

# 36. Pressure aggregation controls

Если пользователь переходит к grouped view:

```text
По дням
По неделям
По месяцам
```

должна существовать настройка:

```text
Агрегация
```

Options:

```text
Среднее
Минимум
Максимум
```

Эту настройку не держать постоянно большим control block на основном screen.

Поместить в:

- dropdown;
- bottom sheet;
- expandable graph settings.

---

# 37. Pressure aggregation semantics

Для:

```text
SYS
DIA
pulse
```

агрегировать series независимо.

Например при `Максимум`:

```text
SYS = max(SYS values)
DIA = max(DIA values)
pulse = max(non-null pulse values)
```

При `Минимум` аналогично.

При `Среднее` arithmetic mean non-null values.

---

# 38. Analytics grouping compatibility

Сохранить существующую возможность:

```text
Raw
Дни
Недели
Месяцы
```

И существующие:

```text
DAY
EVENING
EXTRA
```

filters.

Не уничтожать raw data ради нового rendering.

---

# 39. Empty analytics states

Если для chart нет данных:

не показывать пустой coordinate plane без объяснения.

Показывать понятное:

```text
За выбранный период данных нет.
```

---

# 40. Calendar cycle day — OPTIONAL

Это изменение можно сделать после REQUIRED части.

В monthly calendar внутри каждого дня, для которого известен `cycle_day`, показать маленькое дополнительное число.

Например:

```text
┌────────┐
│  17    │
│     8  │
└────────┘
```

где:

```text
17 = календарная дата
8  = день цикла
```

Cycle day font должен быть приблизительно:

```text
~50–60% размера day-of-month font
```

и визуально вторичным.

---

# 41. Cycle day semantics

Использовать уже существующий calculation:

```text
cycle_day =
date - latest_period_start_date + 1
```

Не добавлять новое persisted поле только ради Calendar UI.

Если cycle day неизвестен:

ничего не показывать.

---

# 42. Application name

Изменить display name приложения:

```text
Pressure and Condition
```

В launcher и system app label должно показываться именно:

```text
Pressure and Condition
```

Не требуется менять:

- package name;
- applicationId;
- Room database name;

если только это не необходимо.

Особенно не делать изменения, способные создать на телефоне второе отдельное приложение вместо upgrade существующего.

---

# 43. Launcher icon — ASSET-DRIVEN

Если в проекте присутствует:

design/app_icon.png

использовать этот asset как source для launcher/adaptive icon.

Сгенерировать необходимые Android launcher icon resources из предоставленного source asset.

Не менять applicationId/package name.

Если asset отсутствует или повреждён:
- оставить текущую launcher icon;
- явно указать это в final report;
- не блокировать остальные изменения.

---

# 44. Theme modes

В приложении добавить собственную настройку темы:

```text
Тема

○ Как в системе
○ Светлая
○ Тёмная
```

Это должно позволять, например:

```text
phone = dark
app = light
```

---

# 45. Theme persistence

Выбранный mode хранить в DataStore.

При следующем запуске приложение должно использовать выбранную тему.

Default для существующих пользователей:

```text
SYSTEM
```

если раньше override не существовал.

---

# 46. Общая цветовая идея

Существующие зелёные кнопки/accent elements пользователю нравятся.

Их не нужно полностью заменять розовыми.

Новая visual direction:

```text
pink / cherry background
+
existing green action accents
```

---

# 47. Light theme

Светлая тема:

- мягкий светло-розовый основной background;
- карточки чуть нейтральнее/светлее background;
- readable dark text;
- зелёные action buttons оставить визуально близкими к текущим.

Не использовать чрезмерно яркий bubblegum pink.

Цель:

```text
soft dusty / warm pink
```

---

# 48. Dark theme

Тёмная тема:

основной background:

```text
dark cherry / wine / burgundy family
```

а не просто charcoal gray.

Cards/surfaces должны быть различимы на фоне.

Text должен иметь достаточный contrast.

Существующие green actions сохранить, если они остаются читаемыми.

---

# 49. Theme implementation

Цвета определить через Material theme tokens.

Не hardcode arbitrary colors непосредственно внутри отдельных screens.

Использовать единый:

```text
LightColorScheme
DarkColorScheme
```

или существующую эквивалентную систему проекта.

---

# 50. UI regression

После изменений проверить:

```text
Today
Check-in
Calendar
Analytics
Settings
Blood Pressure Editor
OCR Confirmation/Edit
```

в обеих темах.

Особенно проверить:

- readable labels;
- slider;
- chart axes;
- chart legend;
- dialog/bottom sheet anchors;
- input fields;
- selected/unselected states.

---

# 51. Accessibility

Clickable metric title для anchors должен иметь понятную semantics label.

Например:

```text
Энергия. Показать описание шкалы.
```

Не делать anchors доступными только через очень маленькую icon без нормального tap target.

---

# 52. Analytics library

Сначала изучить библиотеку графиков, уже используемую текущим MVP.

Не менять её просто ради preference.

Оставить текущую library, если она позволяет реализовать:

```text
line series
column series
axes
custom axis labels
multiple series
legend
tap/press marker
custom min/max point styles
```

Если существующая библиотека объективно не позволяет выполнить REQUIRED functionality без большого количества custom hacks:

только тогда допустимо заменить её.

Такое решение описать в `DECISIONS.md`.

---

# 53. Tests — Check-in UI/business

Добавить/обновить tests так, чтобы подтвердить:

```text
anchors do not affect persisted metric data

editing linked BP updates existing measurement

deleting/editing check-in does not lose unrelated BP

partial check-in still works

NULL scale still remains NULL

NULL boolean still remains NULL
```

UI screenshot tests необязательны.

---

# 54. Tests — OCR

Добавить tests для mapping:

```text
"118 76 68"
→ SYS 118
→ DIA 76
→ pulse 68
```

```text
"118 76"
→ SYS 118
→ DIA 76
→ pulse NULL
```

```text
"118"
→ SYS 118
→ others NULL
```

No values:

```text
→ all NULL
```

Обязательно:

```text
OCR result does not persist until explicit Save
```

---

# 55. Tests — Analytics

Проверить:

```text
scale Y range = 0..10

boolean false remains visible data

boolean NULL remains missing

share_true ignores NULL

DAY / EVENING remain separate series

multiple blood pressure measurements survive Raw mode

SYS and DIA remain separate series

pulse NULL does not become 0

mean/min/max aggregation works independently per series
```

---

# 56. Acceptance criteria — Check-in

REQUIRED часть считается выполненной, если:

```text
[ ] anchors no longer take permanent vertical space
[ ] tap Energy shows its anchors
[ ] tap Anxiety shows its anchors
[ ] current scale value is compactly visible
[ ] check-in requires noticeably less scrolling than baseline
[ ] linked blood pressure is visible inside check-in
[ ] linked blood pressure can be edited from check-in
[ ] edited BP updates immediately after returning
```

---

# 57. Acceptance criteria — OCR

```text
[ ] photo is not persisted
[ ] OCR extracts numeric candidates
[ ] first candidate prefills SYS
[ ] second prefills DIA
[ ] third prefills pulse
[ ] fields are editable
[ ] missing candidates leave fields empty
[ ] explicit Save is still required
```

---

# 58. Acceptance criteria — Analytics

```text
[ ] metric selection no longer consumes a large permanent vertical block
[ ] chart has readable X-axis
[ ] scale chart has explicit 0..10 Y-axis
[ ] chart values can be inspected by tap/press
[ ] multiple series have legend
[ ] visible min/max are visually highlighted
[ ] raw boolean distinguishes true / false / missing
[ ] aggregated boolean shows percentage of "Да"
[ ] pressure has dedicated SYS+DIA chart
[ ] pulse has separate chart
[ ] multiple BP measurements per day remain visible in Raw mode
[ ] mean/min/max aggregation exists for grouped BP
```

---

# 59. Acceptance criteria — Theme

```text
[ ] app display name = Pressure and Condition
[ ] Settings supports System / Light / Dark
[ ] mode survives restart
[ ] Light theme uses soft pink background family
[ ] Dark theme uses cherry/wine background family
[ ] existing green action visual direction remains recognizable
[ ] main screens are readable in both themes
```

---

# 60. OPTIONAL Future feature — animated mascots / characters

DO NOT make this feature a blocker for this change request.

It is documented here so the idea is not lost.

Concept:

On the lower currently-unused area of Today screen display 1–3 small locally-rendered animated mascots.

These mascots may be:

* dogs;
* humans;
* chibi humans;
* pixel-art characters;
* other small cute character sprites.

Assets will be supplied by the user.

No network assets.

The implementation must not be hardcoded specifically for dogs.

It must support a generic local character asset set.

---

# 61. Mascot asset model

Each mascot should be loaded from its own folder.

Recommended project structure:

```text id="p5e4vm"
design/mascots/
  dog_1/
    idle.png
    walk_1.png
    walk_2.png

  human_1/
    idle.png
    walk_1.png
    walk_2.png

  mascot_3/
    idle.png
    walk_1.png
    walk_2.png
```

Required minimum set per mascot:

```text id="l7cf6j"
idle.png
walk_1.png
walk_2.png
```

Optional extended set:

```text id="w5wdn7"
idle_1.png
idle_2.png
walk_1.png
walk_2.png
walk_3.png
walk_4.png
```

The implementation should support the minimum 3-file variant first.

No sprite sheet is required.

Individual PNG files are sufficient.

---

# 62. Mascot asset requirements

All frames for one mascot must:

* have the same canvas size;
* use transparent background;
* depict the same character at approximately the same scale;
* keep the feet/base aligned to approximately the same baseline;
* face the same direction in source assets.

Do not require separate left-facing and right-facing asset files.

The app should mirror the same assets horizontally when changing direction.

Preferred format:

```text id="pgqihp"
PNG with alpha
```

Recommended source canvas size per frame:

```text id="u0dsrt"
512 × 512 px
```

Acceptable alternative source sizes:

```text id="yaimrb"
256 × 256 px
```

Larger source files are allowed, but the app should scale the mascot down for display.

The runtime displayed mascot size should be small and decorative.

---

# 63. Mascot animation

While moving, a mascot may alternate frames like:

```text id="5rki5g"
walk_1
walk_2
walk_1
walk_2
```

If more walk frames are provided later, the implementation may support them, but this is not required now.

While idle, the mascot may:

* display `idle.png`; or
* alternate `idle_1.png` and `idle_2.png`, if such files are provided.

Exact advanced animation system is not required.

A simple timer-based frame swap is sufficient.

---

# 64. Mascot movement and boundaries

Mascots must stay inside a dedicated decorative area on the Today screen.

They must not:

* cover DAY / EVENING cards or buttons;
* overlap bottom navigation;
* block essential text or input controls;
* move across the whole application.

The decorative area should use currently free space near the bottom of Today.

The feature is decorative and must not reduce usability of the main app.

---

# 65. Mascot interaction — optional

Optional enhancement:

User may be able to:

```text id="73m2e6"
touch mascot
→ drag
→ release
```

After release, the mascot may continue its usual autonomous movement from the new position.

This interaction is nice-to-have and not a blocker.

---

# 66. Mascot performance and lifecycle

Animation should run only while Today screen is visible.

Do not keep animation active:

* when the app is backgrounded;
* when another screen is open;
* via a permanent background worker.

Mascot state is decorative UI state.

Do not store continuous animation positions in Room.

Persistent per-user mascot configuration is not required in this iteration.

---

# 67. Mascot customization scope

Do not build a full editor or content-management system.

For this feature, it is enough to support local user-supplied asset folders.

Do not implement:

* server downloads;
* online galleries;
* marketplace;
* cloud asset sync;
* network character packs;
* advanced editor.

The goal is simply to support a few local decorative mascots, regardless of whether they are dogs, humans, or other cute character sprites.

---

# 68. Suggested implementation order

Работать в следующем порядке:

```text
1. Baseline verification

2. Compact check-in
   - hide anchors
   - anchor popup
   - value badge

3. Blood pressure inside check-in

4. OCR prefill changes

5. Analytics data/rendering changes
   - marker
   - axes
   - legend
   - min/max
   - compact controls

6. Boolean chart

7. Pressure + pulse charts

8. Theme selector + new colors

9. Rename application

10. Optional calendar cycle day

11. Optional app icon if asset exists

12. Run tests + full regression

13. Animated mascots / characters:
    DO NOT IMPLEMENT in CR-001.
    Assets in design/mascots/ are future-use assets only.
```

Не начинать visual polish следующей части, пока текущая часть не компилируется и не проходит соответствующие tests.

---

# 69. Final validation

В конце выполнить минимум:

```bash
./gradlew test
./gradlew assembleDebug
```

Если в проекте существуют instrumented/UI tests, запустить релевантные доступные tests также.

Не завершать задачу с заведомо failing tests.

---

# 70. Final report from agent

После работы дать короткий summary:

```text
Implemented:
- ...

Changed files:
- ...

Database migration:
- yes/no

Tests:
- ...

Build:
- ...

Deferred:
- ...
```

Если какое-либо REQUIRED требование не реализовано, явно указать его.

Не выдавать его молча за выполненное.
