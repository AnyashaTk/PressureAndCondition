# Технические решения

## 2026-08-17 — compileSdk 36

Context: стабильный Health Connect Client 1.1.0 требует Android API 36 для компиляции.

Decision: использовать compileSdk/targetSdk 36 при сохранении minSdk 28.

Reason: это актуальная стабильная интеграция и она совместима с Samsung Galaxy S24.

Alternatives considered: устаревшие pre-release версии Health Connect отклонены.

## 2026-08-17 — графики на Compose Canvas

Context: ТЗ разрешает подходящую библиотеку, но фиксированная библиотека не обязательна.

Decision: для MVP рисовать простые line charts штатным Compose Canvas, а агрегацию держать в отдельном тестируемом классе.

Reason: меньше зависимостей и полностью прозрачная шкала без неявной нормализации.

Alternatives considered: сторонние chart libraries.

## 2026-08-17 — расширение Canvas-графиков для CR-001

Context: существующий MVP использовал штатный Compose Canvas без сторонней библиотеки.

Decision: сохранить renderer и добавить axes, legend, tap marker, раздельные series и выделение min/max поверх Canvas.

Reason: требуемые возможности реализуются без замены архитектуры или новой зависимости.

Alternatives considered: миграция на стороннюю chart library, отклонена как ненужная для текущего объёма.

## 2026-08-17 — тема без изменения Room

Context: CR-001 требует persistent theme override и сохранения пользовательских данных.

Decision: хранить SYSTEM/LIGHT/DARK в Preferences DataStore с default SYSTEM.

Reason: настройка не является историческим наблюдением и не требует миграции Room.

Alternatives considered: поле в Room, отклонено как несоответствующее назначению данных.

## 2026-08-17 — единый временной домен Analytics для CR-002

Context: позиция и выбор X в CR-001 частично зависели от имеющихся observations, что не позволяло корректно выбирать пустые периоды.

Decision: ввести общий `TimeBucket` domain для Raw/Day/Week/Month и хранить observations отдельно как marks внутри bucket. Tap сначала выбирает bucket, независимо от hit testing точек.

Reason: missing periods сохраняют место и остаются selectable, а grouping действительно меняет boundaries, labels и interaction semantics.

Alternatives considered: snapping к ближайшей точке и категориальная ось только по данным отклонены как нарушающие CR-002.

## 2026-08-17 — системная камера через temporary FileProvider URI

Context: нужен прямой capture без permanent photo history.

Decision: использовать Activity Result `TakePicture`, cache-файл и существующий FileProvider; camera и gallery сходятся в одну OCR-функцию.

Reason: системная камера не требует custom camera UI, а файл удаляется после OCR, cancel, ошибки или ухода с экрана.

Alternatives considered: CameraX preview отклонён как избыточный; permanent MediaStore image запрещён privacy requirements.

## 2026-08-17 — generic mascot engine

Context: CR-002 переводит mascots из backlog в REQUIRED.

Decision: централизованный `MascotDefinition` описывает idle/walk resources, а общий actor управляет frames, движением, остановками и mirroring.

Reason: набор персонажа можно заменить без изменения Today/business logic; lifecycle coroutine прекращается вместе с экраном или Activity pause.

Alternatives considered: character-specific logic и background service отклонены.
