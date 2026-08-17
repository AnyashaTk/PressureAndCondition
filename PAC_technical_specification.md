# State Tracker — полное ТЗ для coding agent

## 0. Роль этого документа

Этот файл является основным источником продуктовых и технических требований к приложению.

Проект начинается с пустой директории. Необходимо создать полностью рабочее Android-приложение, которое можно собрать в APK и установить на физический Android-телефон.

Если какое-либо техническое решение не зафиксировано явно, выбирать **самое простое решение, которое сохраняет данные, тестируемость и возможность дальнейшего расширения**.

Не добавлять продуктовые фичи, которых нет в этом документе.

Если при реализации обнаруживается неоднозначность:

1. не менять существующую продуктовую семантику;
2. выбрать минимально сложный вариант;
3. записать решение в `DECISIONS.md`;
4. продолжить реализацию.

Главный приоритет: **не потерять пользовательские данные**.

---

# 1. Назначение приложения

Локальное Android-приложение для личного отслеживания состояния пользователя во времени.

Основные сценарии:

- два регулярных check-in в день;
- дополнительные ручные check-in;
- напоминания о дневном и вечернем check-in;
- шкалы субъективного состояния;
- булевы симптомы;
- комментарий к check-in;
- артериальное давление;
- распознавание показаний давления с фотографии;
- первый день менструации;
- импорт первого дня менструации через Android Health Connect;
- просмотр истории в календаре;
- графики;
- CSV export для дальнейшего анализа данных в Python/pandas.

Приложение является **трекером данных**, а не медицинским приложением.

Оно не должно:

- ставить диагнозы;
- интерпретировать состояние;
- давать медицинские рекомендации;
- оценивать, нормальное ли давление;
- выдавать предупреждения на основании значений;
- предсказывать психическое или физическое состояние.

---

# 2. Scope MVP

В MVP должны работать:

1. Android-приложение на Kotlin.
2. Локальная база данных.
3. DAY check-in.
4. EVENING check-in.
5. EXTRA check-in.
6. Частично заполненные check-in.
7. Редактирование check-in.
8. Удаление check-in.
9. Внесение записей задним числом.
10. Комментарий к check-in.
11. Уведомление DAY около 13:00.
12. Уведомление EVENING около 19:00.
13. Отмена уведомления, если соответствующий check-in уже заполнен.
14. Ручной ввод давления.
15. Добавление давления внутри check-in.
16. Фото давления → OCR → подтверждение пользователем.
17. Ручная отметка первого дня менструации.
18. Импорт менструации из Health Connect.
19. Календарь.
20. Экран выбранного дня.
21. Графики.
22. Настройка отображаемых метрик.
23. CSV export.
24. Редактирование времени для уведомлений

---

# 3. Не входит в MVP

Не реализовывать без отдельного требования:

- iOS;
- Flutter;
- backend;
- API server;
- пользовательские аккаунты;
- login/password;
- Firebase;
- cloud sync;
- Google Drive sync;
- автоматический backup;
- импорт CSV;
- социальные функции;
- sharing между пользователями;
- LLM;
- AI-интерпретацию состояния;
- корреляционные советы;
- прогнозирование;
- статистическую диагностику;
- лекарства;
- пользовательское создание произвольных новых метрик;
- виджеты Android home screen;
- Wear OS;
- push notifications с сервера;
- публикацию в Google Play;
- хранение фотографий тонометра после OCR;
- историю всех версий одной записи;
- медицинские alerts;
- biometric lock.

---

# 4. Платформа и стек

Использовать:

- Kotlin;
- native Android;
- Jetpack Compose;
- Material 3;
- Gradle Kotlin DSL;
- version catalog;
- Room;
- Kotlin Coroutines;
- Flow / StateFlow;
- ViewModel;
- Navigation Compose;
- DataStore для небольших пользовательских настроек;
- Android Health Connect client;
- Google ML Kit Text Recognition для OCR;
- Android system notification APIs;
- AlarmManager с inexact alarms;
- Android Photo Picker / Activity Result APIs;
- подходящую современную Compose-compatible библиотеку графиков.

## SDK

Использовать:

```text
minSdk = 28
compileSdk = latest stable available
targetSdk = latest stable available
```

Не фиксировать в этом документе номера версий AndroidX-библиотек.

При создании проекта найти текущие stable/recommended версии по официальной документации и использовать их.

Не использовать alpha/beta зависимости без необходимости.

Исключение допустимо только если нужный API не имеет stable-версии и без него невозможно реализовать обязательную фичу.

В таком случае решение описать в `DECISIONS.md`.

Важно - программа должна 100% корректно работать на samsung s24. брать его характеристики как главный ориентир.

---

# 5. Общая архитектура

Приложение должно быть offline-first и работать без backend.

Минимальная архитектура:

```text
Compose UI
    ↓
ViewModel
    ↓
Repositories
    ↓
Room / Health Connect / OCR / Notifications / Export
```

Не хранить долгоживущие данные внутри Activity, Fragment или composable.

Использовать repository abstraction для доступа к данным.

Не делать чрезмерную Clean Architecture с десятками бесполезных abstraction layers.

Для MVP достаточно одного Android `app` module.

## Dependency injection

Не обязательно использовать Hilt.

Предпочтительно сделать простой `AppContainer` / manual dependency injection, если этого достаточно.

---

# 6. Рекомендуемая структура проекта

Пример:

```text
app/
  src/main/java/.../

    app/
      StateTrackerApplication.kt
      MainActivity.kt
      AppContainer.kt

    data/
      local/
        AppDatabase.kt

        entity/
          CheckInEntity.kt
          MetricDefinitionEntity.kt
          ObservationEntity.kt
          BloodPressureMeasurementEntity.kt
          CycleEventEntity.kt

        dao/
          CheckInDao.kt
          MetricDao.kt
          ObservationDao.kt
          BloodPressureDao.kt
          CycleEventDao.kt

      repository/
        CheckInRepository.kt
        MetricRepository.kt
        BloodPressureRepository.kt
        CycleRepository.kt

      healthconnect/
        HealthConnectDataSource.kt

      ocr/
        BloodPressureOcrService.kt
        BloodPressureParser.kt

      notifications/
        NotificationScheduler.kt
        CheckInAlarmReceiver.kt

      export/
        CsvExporter.kt

      settings/
        SettingsRepository.kt

    domain/
      model/
      aggregation/

    feature/
      today/
      checkin/
      calendar/
      daydetails/
      analytics/
      bloodpressure/
      settings/

    navigation/
      AppNavigation.kt

    ui/
      components/
      theme/
```

Это ориентир, а не обязательное буквальное дерево.

---

# 7. Язык интерфейса

MVP интерфейс на русском языке.

Все пользовательские строки вынести в resources.

Не хардкодить пользовательский текст непосредственно в composable.

---

# 8. Модель check-in

Есть три типа check-in:

```text
DAY
EVENING
EXTRA
```

В UI:

```text
DAY      → День
EVENING  → Вечер
EXTRA    → Дополнительное
```

## Ограничения

Для каждой календарной даты:

```text
DAY      максимум 1
EVENING  максимум 1
EXTRA    0..N
```

Нельзя создать два DAY check-in на одну дату.

Нельзя создать два EVENING check-in на одну дату.

EXTRA можно создавать сколько угодно.

---

# 9. Временная семантика check-in

У check-in необходимо различать:

```text
target_date
reported_for_at
created_at
updated_at
```

## target_date

Календарная дата, к которой относится состояние.

Например:

```text
2026-08-16
```

## reported_for_at

Логическое время наблюдения.

Для регулярных слотов использовать nominal time:

```text
DAY      → target_date 13:00
EVENING  → target_date 19:00
```

Для EXTRA использовать фактическое выбранное пользователем время.

Если EXTRA создаётся сейчас:

```text
reported_for_at = current time
```

Если EXTRA добавляется задним числом, пользователь может выбрать время.

## created_at

Настоящее время фактического создания записи.

Никогда не подменять его временем, к которому относится запись.

## updated_at

`NULL`, пока запись ни разу не редактировалась после первоначального сохранения.

После изменения:

```text
updated_at = actual edit time
```

Историю предыдущих значений хранить не нужно.

## is_corrected

Отдельное persisted-поле не обязательно.

При необходимости вычислять:

```text
is_corrected = updated_at != null
```

В CSV экспортировать `is_corrected`.

---

# 10. Backfilled / запись задним числом

Должна существовать возможность внести состояние за прошлую дату.

Хранить:

```text
is_backfilled: Boolean
```

Значение `true`, если запись была впервые создана в календарную дату позже `target_date`.

Пример:

```text
target_date = 2026-08-12
created_at  = 2026-08-14T18:43
is_backfilled = true
```

При этом `created_at` обязательно остаётся 14 августа.

Редактирование старой уже существующей записи не превращает её автоматически в backfilled.

В UI для таких записей показывать ненавязчивую метку:

> Добавлено задним числом

---

# 11. Метрики состояния

В MVP набор метрик фиксированный.

Архитектура БД при этом должна позволять в будущем добавлять новые типы метрик без изменения таблицы `CheckIn`.

Не делать поля вида:

```text
checkin.energy
checkin.anxiety
checkin.musclePain
...
```

Основная модель:

```text
MetricDefinition
Observation
```

---

# 12. Шкальные метрики

Все значения — целые числа от `0` до `10`, nullable.

## energy

UI name:

> Энергия

Range:

```text
0..10
```

Якоря:

```text
0 — вообще нет ощущения, что могу что-то делать:
    ни есть, ни встать с кровати

5 — получается что-то потыкать,
    быт примерно ок

10 — постоянно что-то делаю весь день
```

## anxiety

UI name:

> Тревога

Range:

```text
0..10
```

Якоря:

```text
0 — чувствую и веду себя спокойно или счастливо,
    не нервничаю, не ною

5 — ною как обычно,
    но физически всё ок

10 — всё тело напрягается,
     психую,
     есть яркий комок тревоги
```

## want_to_cry

UI name:

> Хочется плакать

Range:

```text
0..10
```

Это субъективное количество/выраженность за день.

Дополнительные якоря не определять.

Пользователь самостоятельно интерпретирует шкалу.

## crying

UI name:

> Пореветь

Range:

```text
0..10
```

Это субъективное количество фактического плача за день.

Дополнительные якоря не определять.

Пользователь самостоятельно интерпретирует шкалу.

---

# 13. Булевы метрики

Булевы значения обязательно должны быть nullable.

То есть существует три состояния:

```text
NULL  = не отвечено
false = нет
true  = да
```

Нельзя превращать отсутствие ответа в `false`.

Метрики:

```text
fogginess
→ Ватность

top_pressure_sensation
→ Ощущение давления сверху

physical_fatigue
→ Физическая усталость

muscle_pain
→ Боль в мышцах

```

Важно:

`Ощущение давления сверху` является субъективным симптомом и не связано автоматически с артериальным давлением.

---

# 14. Видимость метрик по типу check-in

У каждой MetricDefinition хранить:

```text
enabled
show_in_extra
sort_order
```

Пользователь должен иметь возможность изменить эти настройки через экран Settings.

В MVP пользователь **не создаёт новые типы метрик**.

Пользователь может:

- включить/выключить существующую метрику;
- выбрать, показывается ли она в DAY;
- выбрать, показывается ли она в EVENING;
- выбрать, показывается ли она в EXTRA.

## Default configuration

Для:

```text
energy
anxiety
want_to_cry
crying
fogginess
top_pressure_sensation
physical_fatigue
muscle_pain
```

по умолчанию:

```text
enabled = true
show_in_extra = true
```


---

# 15. Частично заполненный check-in

Check-in не обязан содержать ответы на все вопросы.

Любое значение может оставаться `NULL`.

Нельзя:

- автоматически записывать 0;
- автоматически записывать `false`;
- заставлять пользователя отвечать на все вопросы.

## Scale UI

До взаимодействия:

> Не выбрано

После выбора хранить целое число `0..10`.

Пользователь должен иметь возможность снова очистить значение до `NULL`.

## Boolean UI

Использовать явное трёхсостояние, например:

```text
Не выбрано | Нет | Да
```

или эквивалентный понятный UI.

Не использовать обычный Switch с `false` по умолчанию: это уничтожает различие между `NULL` и `false`.

---

# 16. Пустой check-in

Не сохранять совершенно пустой check-in.

Для сохранения требуется хотя бы:

- одна заполненная metric;

или

- непустой комментарий.

Само добавление давления без состояния не должно автоматически создавать пустой check-in.

Давление может существовать отдельно.

---

# 17. Комментарий

У каждого check-in есть:

```text
comment: String?
```

Один комментарий на весь check-in.

UI:

> Комментарий

Multiline text field.

Комментарий optional.

Не создавать отдельные комментарии для отдельных метрик.

---

# 18. Создание check-in

## DAY

Может быть открыт:

- с главного экрана;
- из календаря;
- из дневного уведомления.

Если DAY уже существует:

- открыть существующий check-in на просмотр/редактирование;
- не создавать второй.

## EVENING

Аналогично DAY.

## EXTRA

Создаётся только вручную из приложения.

У EXTRA никогда нет уведомления.

На один день допустимо несколько EXTRA.

---

# 19. Уведомления

Есть два регулярных уведомления.

```text
13:00 → DAY
19:00 → EVENING
```

Точность до минуты не является обязательной.

Небольшая задержка доставки Android допустима.

Не использовать exact alarm permission в MVP.

Использовать подходящий inexact AlarmManager scheduling.

---

# 20. Логика дневного уведомления

Около 13:00 приложение проверяет:

```text
существует ли DAY check-in на текущую target_date?
```

Если существует:

```text
notification = не показывать
```

Если не существует:

показать notification.

Пример текста:

Title:

> Как ты сейчас?

Body:

> Заполни дневное состояние

---

# 21. Логика вечернего уведомления

Около 19:00 проверить:

```text
существует ли EVENING check-in на текущую дату?
```

Если да:

не показывать notification.

Если нет:

показать.

Title:

> Как ты сейчас?

Body:

> Заполни вечернее состояние

---

# 22. Notification deep link

Это критическое требование.

Каждое уведомление должно явно содержать:

```text
target_date
slot
```

Нельзя при открытии уведомления определять слот по текущему времени.

Пример:

```text
DAY notification создан для:
2026-08-16 / DAY
```

Пользователь нажимает его в 17:40.

Обязательно открыть:

```text
2026-08-16 / DAY
```

а НЕ:

```text
EVENING
EXTRA
```

Если пользователь нажал старое уведомление на следующий день:

открыть тот `target_date + slot`, для которого оно было создано.

Если запись уже успела появиться:

открыть существующую запись.

---

# 23. Cancellation notification

После успешного сохранения DAY:

удалить соответствующее активное DAY notification, если оно ещё отображается.

После успешного сохранения EVENING:

аналогично.

При удалении check-in не нужно автоматически показывать уже пропущенное уведомление повторно.

---

# 24. Notification permissions

Корректно обработать runtime notification permission.

Если разрешения нет:

- приложение продолжает полностью работать;
- check-in можно создавать вручную;
- Settings показывает состояние разрешения;
- должна быть возможность перейти к выдаче разрешения.

Не падать при denied permission.

---

# 25. Rescheduling notifications

Уведомления должны продолжать работать после:

- перезагрузки телефона;
- перезапуска приложения;
- изменения timezone;
- изменения времени устройства.

При необходимости использовать system broadcast receiver и повторное scheduling.

Не создавать дубликаты alarm.

---

# 26. Главный экран — Today

Главный экран приложения:

> Сегодня

Bottom navigation:

```text
Сегодня
Календарь
Графики
Настройки
```

На Today показать минимум:

```text
Текущая дата

[ День ]
status: заполнено / не заполнено

[ Вечер ]
status: заполнено / не заполнено

[ + Дополнительное состояние ]

[ + Давление ]
```

Если DAY/EVENING заполнен:

показывать время создания и короткий summary, например значения энергии/тревоги.

Tap открывает запись.

---

# 27. Full-screen Check-in screen

Notification должен открывать именно этот экран.

Top app bar:

```text
День — 16 августа
```

или:

```text
Вечер — 16 августа
```

или:

```text
Дополнительное состояние
```

При backfilled entry:

> Добавлено задним числом

Далее последовательно отображать активные для этого slot метрики.

Пример:

```text
Энергия
[0 -------- 10]
5

Тревога
[0 -------- 10]
7

Хочется плакать
...

Ватность
[Не выбрано] [Нет] [Да]

...

Комментарий
[                         ]

Артериальное давление
[ + Добавить ]

[ Сохранить ]
```

---

# 28. Поведение незаписанных изменений

Сохранение должно быть явным.

Если пользователь изменил форму и нажал Back:

показать подтверждение, например:

> Изменения не сохранены. Выйти?

Не терять введённые данные молча.

Autosave для MVP не требуется.

---

# 29. Редактирование check-in

Любой существующий check-in можно открыть и изменить.

После изменения:

```text
updated_at = actual current timestamp
```

В UI можно показать:

> Изменено

Не нужно показывать историю версий.

---

# 30. Удаление check-in

Любой check-in можно удалить.

Перед удалением обязательное подтверждение.

Удалить связанные Observation.

Связанное измерение артериального давления по возможности **не удалять автоматически**.

Если удаляется check-in:

```text
blood_pressure.checkin_id = NULL
```

Само измерение остаётся в истории давления.

---

# 31. Backfill UX

Основной сценарий:

```text
Календарь
→ выбрать прошлый день
→ Добавить состояние
```

Если DAY отсутствует:

предлагать `День`.

Если EVENING отсутствует:

предлагать `Вечер`.

Всегда можно выбрать:

`Дополнительное`.

Будущие даты выбирать нельзя.

---

# 32. Артериальное давление

Blood pressure — самостоятельная сущность.

Она не является Observation обычной metric.

Schema:

```text
BloodPressureMeasurement

id
measured_at
created_at
updated_at?
systolic
diastolic
pulse?
source
checkin_id?
```

`source`:

```text
MANUAL
OCR
```

---

# 33. Поля давления

Обязательные:

```text
systolic: Int
diastolic: Int
measured_at
```

Optional:

```text
pulse: Int?
checkin_id?
```

UI names:

```text
Верхнее / SYS
Нижнее / DIA
Пульс
```

Не интерпретировать значения медицински.

Не показывать:

- «высокое»;
- «низкое»;
- «опасное»;
- «нормальное».

Только хранить данные.

---

# 34. Добавление давления отдельно

Из Today:

```text
+ Давление
```

открывает Blood Pressure Editor.

Пользователь может:

1. ввести значения вручную;
2. сделать/выбрать фото.

После сохранения:

```text
checkin_id = NULL
```

если давление было создано отдельно.

---

# 35. Давление внутри check-in

В Check-in screen:

```text
Артериальное давление
+ Добавить
```

Давление optional.

Check-in можно сохранить без него.

При сохранении измерения, созданного как часть check-in:

```text
checkin_id = current checkin id
```

Давление при этом остаётся полноценной самостоятельной записью и должно появляться в общем списке измерений.

---

# 36. OCR давления

Поддержать:

```text
Сделать фото
```

и:

```text
Выбрать фото
```

Использовать on-device text recognition.

Фото является временным input.

Не сохранять фотографию после завершения OCR.

Временный файл/cache удалить после:

- успешного подтверждения;
- cancel;
- ошибки, когда он больше не нужен.

---

# 37. OCR pipeline

Pipeline:

```text
photo
→ ML Kit Text Recognition
→ recognized raw text
→ BloodPressureParser
→ candidate SYS/DIA/pulse
→ confirmation screen
→ manual correction
→ save
```

OCR никогда не должен автоматически сохранять измерение.

Даже при уверенном распознавании пользователь всегда видит confirmation screen.

---

# 38. OCR confirmation screen

Пример:

```text
Проверь значения

SYS
[ 118 ]

DIA
[ 76 ]

Пульс
[ 68 ]

[ Сохранить ]
```

Все поля editable.

Если распознать конкретное поле не удалось:

оставить его пустым.

Можно показать распознанный raw text как debug/helper text, но raw OCR text не требуется сохранять в БД.

---

# 39. BloodPressureParser

Parser должен быть отдельным unit-testable классом.

Он получает OCR text/layout и пытается найти подходящие числовые кандидаты.

Не обучать собственную ML-модель.

Не строить сложную нейросеть.

Не использовать medical interpretation.

Если parsing неоднозначный — лучше вернуть меньше заполненных полей и заставить пользователя ввести их руками, чем молча сохранить неправильные данные.

---

# 40. Менструальный цикл

Для MVP нужен только:

> первый день менструации

Не нужно:

- прогнозировать цикл;
- хранить фазы;
- прогнозировать следующую менструацию;
- считать овуляцию.

---

# 41. CycleEvent

Entity:

```text
CycleEvent

id
date
source
external_record_id?
created_at
```

Source:

```text
MANUAL
HEALTH_CONNECT
```

---

# 42. Ручное начало цикла

На выбранной дате пользователь может:

```text
Отметить начало цикла
```

Ручную запись можно:

- создать;
- удалить;
- исправить дату.

Будущие даты не нужны.

На одну дату не показывать два одинаковых события.

---

# 43. День цикла

Не хранить `cycle_day` как отдельное пользовательское значение.

Вычислять:

```text
cycle_day =
current_date - latest_period_start_date + 1
```

Если предыдущего начала цикла нет:

cycle day неизвестен.

---

# 44. Health Connect

Интеграция выполняется через Health Connect.

Не делать прямую интеграцию с Flo API.

Нужен только read access.

Никогда не писать данные цикла из этого приложения обратно в Health Connect в MVP.

Импортировать menstruation period records и использовать start date как начало цикла.

---

# 45. Health Connect UX

Settings section:

```text
Цикл

Health Connect
Статус: подключено / не подключено / недоступно

[ Подключить ]
[ Синхронизировать ]
```

При подключении запросить только минимально необходимые permissions для чтения данных менструации.

Если Health Connect:

- отсутствует;
- недоступен;
- permission denied;
- возвращает ошибку;

приложение продолжает работать.

Ручные CycleEvent всегда доступны.

---

# 46. Health Connect sync

При sync:

- получить доступные MenstruationPeriodRecord;
- извлечь даты начала;
- преобразовать их в локальные CycleEvent;
- source = HEALTH_CONNECT;
- не создавать duplicate на одну и ту же дату.

Ручная запись имеет приоритет в смысле того, что sync не должен её удалять или перезаписывать.

При повторной синхронизации корректно обрабатывать уже импортированные записи.

Не создавать одинаковые события повторно.

Автоматический sync допустимо запускать при открытии приложения, если permission уже выдан, но он не должен блокировать UI.

Также должна существовать явная кнопка Sync.

---

# 47. Календарь

Экран Calendar показывает месячный календарь.

В каждой date cell отображать компактные indicators:

```text
DAY exists
EVENING exists
cycle start exists
blood pressure exists
```

Не пытаться помещать все значения метрик непосредственно внутрь календарной клетки.

---

# 48. Day Details

Tap по дню → экран дня.

Пример:

```text
16 августа

День
13:12
Энергия 4
Тревога 7
...

Вечер
19:31
Энергия 6
...

Дополнительные состояния
17:42
...

Давление
118 / 76 — 14:03

Начало цикла
```

Отсюда можно:

- открыть DAY;
- открыть EVENING;
- открыть EXTRA;
- добавить отсутствующий DAY;
- добавить отсутствующий EVENING;
- добавить EXTRA;
- добавить давление;
- отметить начало цикла.

---

# 49. Analytics — общие требования

Все исходные наблюдения сохранять без разрушительной агрегации.

Никогда не заменять raw observations дневными/недельными средними в БД.

Все aggregate вычислять только для отображения/аналитики.

Это критическое требование.

---

# 50. Analytics — диапазон дат

Поддержать:

```text
7 дней
30 дней
3 месяца
Произвольный период
```

Произвольный период:

```text
start date
end date
```

---

# 51. Analytics — временная группировка

Поддержать:

```text
Raw
По дням
По неделям
По месяцам
```

Raw:

рисовать отдельные observations.

По дням:

группировать по календарной дате.

По неделям:

группировать по календарной неделе.

По месяцам:

группировать по календарному месяцу.

---

# 52. Analytics — фильтр check-in slot

Пользователь должен иметь возможность включать/выключать:

```text
DAY
EVENING
EXTRA
```

Минимальные сценарии:

- только DAY;
- только EVENING;
- DAY + EVENING;
- все check-in.

При DAY + EVENING пользователь должен иметь возможность увидеть их как разные series.

Например:

```text
Энергия · День
Энергия · Вечер
```

То есть данные нельзя заранее объединять.

---

# 53. Aggregation числовых шкал

Для `0..10` metrics:

Raw:

```text
исходные значения
```

Day/week/month aggregation:

```text
arithmetic mean
```

по non-null observations в выбранном bucket.

`NULL` игнорировать.

Никогда не считать `NULL = 0`.

---

# 54. Aggregation boolean metrics

Raw:

```text
false → 0
true  → 1
NULL  → отсутствующая точка
```

При day/week/month aggregation:

```text
share_true =
count(true) / count(non-null answers)
```

Если non-null answers = 0:

значение отсутствует.

Не считать missing response как false.

---

# 55. Blood pressure charts

В Analytics должна существовать возможность смотреть давление.

Давление не смешивать без необходимости с 0..10 metrics на одной Y-axis.

Dedicated chart:

```text
SYS
DIA
Pulse optional
```

Raw:

отдельные измерения.

При day/week/month grouping допустимо использовать arithmetic mean для каждой series.

---

# 56. Analytics metric selection

Пользователь может выбирать несколько metrics.

Например:

```text
[x] Энергия
[x] Тревога
[ ] Хочется плакать
[ ] Пореветь
...
```

---

# 57. Graph layout

Поддержать два режима:

```text
Вместе
Отдельно
```

## Вместе

Совместимые metrics отображаются как несколько series на одном chart.

Например:

```text
Energy 0..10
Anxiety 0..10
Want to cry 0..10
```

могут находиться на одном графике.

## Отдельно

Каждая metric получает отдельный chart.

Если выбранные metrics имеют несовместимые единицы/scale, приложение может автоматически разделить их на отдельные compatible chart groups.

Не нормализовать значения незаметно для пользователя.

---

# 58. Legend

Analytics:

```text
Показывать легенду: ON/OFF
```

Настройку можно сохранять в DataStore.

---

# 59. Missing data на графиках

Отсутствующие данные должны оставаться отсутствующими.

Не:

- интерполировать;
- forward-fill;
- записывать 0;
- записывать false;
- дорисовывать synthetic points.

---

# 60. Room schema

Использовать примерно следующие сущности.

## MetricDefinitionEntity

```text
id: String primary key

display_name: String
type: SCALE | BOOLEAN

min_value: Int?
max_value: Int?

enabled: Boolean

show_in_extra: Boolean

sort_order: Int

anchor_low: String?
anchor_mid: String?
anchor_high: String?
```

Stable IDs:

```text
energy
anxiety
want_to_cry
crying
fogginess
top_pressure_sensation
physical_fatigue
muscle_pain
```

---

# 61. CheckInEntity

```text
id: String / UUID

target_date: LocalDate-compatible persisted value

slot:
  DAY
  EVENING
  EXTRA

reported_for_at: timestamp

created_at: timestamp

updated_at: timestamp?

is_backfilled: Boolean

comment: String?
```

Enforce business rule:

```text
one DAY per target_date
one EVENING per target_date
```

Repository/database logic must protect against duplicates even under repeated notification taps.

---

# 62. ObservationEntity

```text
id: String / UUID

checkin_id
metric_id

numeric_value: Double?
boolean_value: Boolean?
```

Exactly one value field is used depending on MetricDefinition type.

Unique logical pair:

```text
checkin_id + metric_id
```

Foreign key:

```text
checkin → CASCADE DELETE
metric definition → do not silently delete historical data
```

---

# 63. BloodPressureMeasurementEntity

```text
id

measured_at
created_at
updated_at?

systolic
diastolic
pulse?

source:
  MANUAL
  OCR

checkin_id?
```

On deletion of check-in:

prefer `SET NULL` semantics for checkin_id.

---

# 64. CycleEventEntity

```text
id
date

source:
  MANUAL
  HEALTH_CONNECT

external_record_id?

created_at
```

Avoid exact duplicate cycle start events for the same date.

---

# 65. Room migrations

Database starts at schema version 1.

Never enable destructive migration for normal production use.

Specifically запрещено использовать как простой выход:

```text
fallbackToDestructiveMigration()
```

Пользовательские longitudinal data важнее удобства миграции.

При изменении schema создавать migration.

Добавить migration tests, когда появится schema v2+.

---

# 66. DataStore

Использовать DataStore для настроек, которые не являются historical observations.

Например:

```text
analytics date range preference
analytics legend preference
analytics selected metrics
notifications enabled
health connect sync state metadata
```

Metric visibility допустимо хранить в Room MetricDefinition, поскольку это часть конфигурации самой metric.

---

# 67. CSV export

Export является обязательной фичей MVP.

Пользователь — Python/DS user, поэтому export должен быть пригоден для pandas без ручного исправления формата.

Использовать:

```text
UTF-8
comma delimiter
proper CSV quoting
ISO-8601 timestamps
dot decimal separator
```

---

# 68. Формат export

Создать export bundle:

```text
state-tracker-export-YYYYMMDD-HHmm.zip
```

Внутри CSV:

```text
checkins.csv
observations.csv
blood_pressure.csv
cycle_events.csv
metrics.csv
```

ZIP является только контейнером.

Сами данные должны быть обычными CSV.

---

# 69. checkins.csv

Columns:

```text
checkin_id
target_date
slot
reported_for_at
created_at
updated_at
is_backfilled
is_corrected
comment
```

`is_corrected`:

```text
updated_at != NULL
```

---

# 70. observations.csv

Long format.

Columns:

```text
observation_id
checkin_id

target_date
slot
reported_for_at

checkin_created_at
checkin_updated_at

is_backfilled
is_corrected

metric_id
metric_name
metric_type

numeric_value
boolean_value
```

Не экспортировать шкальные metrics wide columns вида:

```text
energy_day
energy_evening
...
```

Long format является основным.

---

# 71. blood_pressure.csv

Columns:

```text
measurement_id
measured_at
created_at
updated_at
systolic
diastolic
pulse
source
checkin_id
```

---

# 72. cycle_events.csv

Columns:

```text
cycle_event_id
date
source
created_at
external_record_id
```

---

# 73. metrics.csv

Columns:

```text
metric_id
metric_name
metric_type
min_value
max_value
enabled
show_in_extra
sort_order
anchor_low
anchor_mid
anchor_high
```

---

# 74. Export destination

Использовать системный Android save/share flow.

Не требовать broad filesystem permissions.

После успешного export показать:

> Экспорт готов

При ошибке показать понятное сообщение.

Import/restore из CSV в MVP не нужен.

---

# 75. Privacy

Приложение предназначено для очень чувствительных персональных данных.

Требования:

- никакой пользовательской аналитики;
- никакого Firebase Analytics;
- никакой рекламы;
- никакой telemetry;
- никакого backend;
- никакого cloud sync;
- не отправлять данные состояния в интернет;
- не отправлять OCR image на сервер;
- использовать on-device OCR;
- не хранить input image после завершения OCR.

Если технически возможно без нарушения обязательных интеграций, не запрашивать INTERNET permission.

Отключить Android cloud backup приложения:

```text
android:allowBackup="false"
```

В Settings показать предупреждение:

> Данные хранятся только на этом устройстве. Удаление приложения удалит локальные данные. Для сохранения истории используй экспорт CSV.

---

# 76. Permissions

Запрашивать только необходимые permissions и только в контексте соответствующей функции.

Возможные:

```text
POST_NOTIFICATIONS
Health Connect read permission
```

Для выбора фото предпочитать system Photo Picker, чтобы не запрашивать широкий доступ к медиатеке.

Для камеры использовать современный permission flow только если выбранный implementation действительно требует CAMERA.

Отказ в любой optional permission не должен ломать остальные функции.

---

# 77. Settings screen

Sections:

## Метрики

Для каждой metric:

```text
Enabled

Показывать:
[x] День
[x] Вечер
[x] Дополнительное
```

Утренняя разбитость default:

```text
День = ON
Вечер = OFF
Дополнительное = OFF
```

## Уведомления

Показать:

```text
День: около 13:00
Вечер: около 19:00

Notifications enabled / disabled
System permission status
```

Менять время уведомлений в MVP не обязательно.

## Health Connect

Connection state.

Connect/sync buttons.

## Данные

```text
Экспорт CSV
```

И privacy warning про локальное хранение.

---

# 78. Ошибки

Никакая ожидаемая ошибка не должна crash приложение.

Обрабатывать:

- database error;
- permission denied;
- Health Connect unavailable;
- Health Connect read failure;
- OCR failure;
- no text found;
- invalid image;
- CSV save failure;
- notification permission denied;
- duplicate DAY/EVENING creation race.

UI должен выдавать понятное сообщение и позволять продолжить работу.

---

# 79. Loading states

Health Connect и OCR могут быть asynchronous.

Для них нужен явный loading indicator.

Не блокировать main thread.

Все Room/IO/OCR операции выполнять корректно вне main thread согласно API.

---

# 80. State management

Compose screens должны получать immutable-ish UI state из ViewModel.

Предпочтительно:

```text
StateFlow<UiState>
```

UI events направлять обратно в ViewModel.

Не делать composable источником истины для сохранённых данных.

---

# 81. Даты и timezone

Calendar semantics основана на local date пользователя.

Timestamps хранить в однозначном машиночитаемом формате.

Не строить business logic на formatted display strings.

После смены timezone:

- существующие `target_date` не должны неожиданно переезжать на соседнюю дату;
- уведомления должны быть rescheduled по новому локальному времени.

---

# 82. Валидация шкал

Scale metrics:

```text
integer only
0 <= value <= 10
```

Room/repository не должен принимать значение вне диапазона.

---

# 83. Валидация blood pressure

Поля должны быть числовыми integer.

Не использовать medical normal ranges как product validation.

Запрещено блокировать сохранение потому, что значение считается медицински «ненормальным».

Можно защищаться только от явно нечислового/технически некорректного input.

---

# 84. Accessibility / basic UX

Использовать стандартные Material components.

Tap targets должны быть нормального размера.

Все interactive elements должны иметь readable labels/content descriptions, где это необходимо.

UI должен работать как минимум в:

```text
light theme
dark theme
```

Не делать сложный custom design до завершения функциональности.

---

# 85. Tests — обязательно

Core logic должна иметь unit tests.

Минимум протестировать следующие случаи.

## CheckInRepository

- create DAY;
- нельзя создать второй DAY той же даты;
- create EVENING;
- несколько EXTRA допустимы;
- partial check-in сохраняется;
- `0` не превращается в null;
- `false` не превращается в null;
- null остаётся null;
- backfilled вычисляется корректно;
- edit выставляет updated_at;
- delete удаляет observations;
- delete check-in не уничтожает отдельное давление.

## Notifications

- DAY already exists → notification не показывается;
- EVENING already exists → notification не показывается;
- DAY notification deep link всегда открывает DAY;
- DAY notification, открытый после 15:00, всё ещё открывает DAY;
- old notification открывает original target_date;
- повторный tap не создаёт duplicate check-in.

## Analytics

- null не считается нулём;
- boolean null не считается false;
- daily mean считается по non-null;
- weekly mean считается корректно;
- boolean share_true считается по answered values;
- slot filtering работает;
- DAY и EVENING могут быть получены отдельными series.

## CSV

- корректные headers;
- UTF-8;
- comment с запятой/переносом строки правильно quoted;
- nullable values export корректно;
- is_corrected вычисляется корректно;
- timestamps parseable.

## BloodPressureParser

Покрыть набором synthetic OCR strings:

- SYS/DIA/pulse найдены;
- pulse отсутствует;
- лишние числа;
- ничего не распознано;
- неоднозначные данные.

Parser не должен crash на произвольной строке.

## Health Connect

Вынести работу за interface/data source, чтобы можно было тестировать с fake implementation.

---

# 86. Compose/UI tests

Не требуется покрывать UI на 100%.

Но желательно иметь smoke tests минимум для:

- Today screen;
- partial check-in;
- saving DAY;
- opening existing check-in;
- calendar day navigation.

---

# 87. Definition of Done для проекта

Проект считается готовым только если:

- [ ] проект создаётся из текущей директории;
- [ ] Gradle wrapper присутствует;
- [ ] debug APK собирается;
- [ ] приложение запускается;
- [ ] база создаётся;
- [ ] seed metrics создаются;
- [ ] DAY работает;
- [ ] EVENING работает;
- [ ] EXTRA работает;
- [ ] partial answers работают;
- [ ] null/false/0 различаются;
- [ ] comments работают;
- [ ] backfill работает;
- [ ] edit работает;
- [ ] delete работает;
- [ ] уведомление DAY работает;
- [ ] уведомление EVENING работает;
- [ ] suppression при заполненном slot работает;
- [ ] notification deep link открывает правильный slot;
- [ ] давление вручную работает;
- [ ] давление внутри check-in работает;
- [ ] фото OCR работает;
- [ ] OCR confirmation работает;
- [ ] цикл вручную работает;
- [ ] Health Connect integration работает либо корректно показывает unavailable/denied state;
- [ ] Calendar работает;
- [ ] Day Details работает;
- [ ] Analytics показывает raw data;
- [ ] Analytics умеет day/week/month grouping;
- [ ] DAY/EVENING filters работают;
- [ ] несколько 0..10 metrics можно показать вместе;
- [ ] separate charts работают;
- [ ] blood pressure chart работает;
- [ ] CSV export создаёт корректные файлы;
- [ ] core unit tests проходят;
- [ ] приложение не требует backend;
- [ ] приложение не теряет БД при обычном upgrade;
- [ ] README описывает сборку и запуск.

---

# 88. Порядок реализации

Реализовывать не всё одновременно.

## Phase 1 — project skeleton

Создать Android project:

- Kotlin;
- Compose;
- Material 3;
- Navigation;
- Room;
- basic dependency container.

Добавить:

```text
README.md
DECISIONS.md
```

Добиться успешного build.

---

## Phase 2 — database

Реализовать:

```text
MetricDefinition
CheckIn
Observation
BloodPressureMeasurement
CycleEvent
```

Seed default metrics.

Добавить DAO/repositories/tests.

До UI убедиться unit tests, что:

```text
NULL != 0
NULL != false
```

---

## Phase 3 — Today + Check-in

Реализовать:

- Today;
- DAY;
- EVENING;
- EXTRA;
- scale fields;
- boolean fields;
- comment;
- partial save;
- edit;
- delete.

После этой фазы приложение уже должно быть пригодно для ручного ежедневного tracking.

---

## Phase 4 — Calendar

Реализовать:

- month view;
- indicators;
- Day Details;
- backfill.

---

## Phase 5 — Notifications

Реализовать:

- 13:00 DAY;
- 19:00 EVENING;
- permission;
- slot suppression;
- correct deep links;
- cancel after save;
- reschedule after boot/timezone changes.

---

## Phase 6 — Blood pressure

Сначала manual input.

Затем:

- blood pressure history;
- optional check-in link;
- photo picker/camera;
- ML Kit;
- parser;
- confirmation.

Не начинать OCR до работающего manual pressure input.

---

## Phase 7 — Cycle

Сначала manual CycleEvent.

Затем Health Connect.

Health Connect failure не должен влиять на manual data.

---

## Phase 8 — Analytics

Реализовать query/aggregation layer отдельно от chart rendering.

Сначала добиться корректных datasets для:

```text
raw
day
week
month
slot filters
```

Покрыть tests.

Только после этого рисовать charts.

---

## Phase 9 — Export

Реализовать CSV bundle.

Проверить экспорт вручную и unit tests.

---

## Phase 10 — polish

После завершения core:

- loading/error states;
- dark mode;
- accessibility;
- visual cleanup;
- README;
- release APK sanity test.

---

# 89. README.md

После реализации README должен содержать:

```text
# State Tracker

Что это

## Requirements

Android Studio / JDK / SDK requirements

## Build

./gradlew assembleDebug

## Tests

./gradlew test

## APK

где искать debug APK

## Architecture

короткое описание

## Data storage

Room, local only

## Notifications

13:00 / 19:00

## Health Connect

что импортируется

## OCR

как работает

## Export

какие CSV создаются

## Privacy

данные остаются локально
```

---

# 90. DECISIONS.md

Если в процессе потребовалось принять не описанное здесь техническое решение, записывать:

```text
## YYYY-MM-DD — название решения

Context:
...

Decision:
...

Reason:
...

Alternatives considered:
...
```

Не использовать DECISIONS.md как способ отменять требования AGENTS.md.

---

# 91. Требования к качеству кода

Код должен быть читаемым человеком, который знает Kotlin на базовом уровне, но не является Android specialist.

Поэтому:

- понятные class/function names;
- минимум magic behavior;
- минимум reflection;
- не создавать abstraction ради abstraction;
- комментарии там, где logic неочевидна;
- business rules держать отдельно от UI;
- aggregation держать отдельно от chart renderer;
- OCR parsing держать отдельно от ML Kit adapter;
- Health Connect держать за interface;
- CSV export держать отдельно от ViewModel.

---

# 92. Что нельзя делать агенту

Не:

- заменять Kotlin на Flutter;
- добавлять backend;
- добавлять Firebase;
- отправлять health data во внешние API;
- добавлять AI interpretation;
- принимать `NULL` за `false`;
- принимать `NULL` за `0`;
- хранить только агрегированные значения;
- определять DAY/EVENING из текущего времени при notification click;
- создавать notification для EXTRA;
- создавать второй DAY/EVENING за дату;
- сохранять OCR result без confirmation;
- делать прямую интеграцию с Flo;
- удалять пользовательскую Room DB ради migration convenience;
- придумывать медицинские thresholds;
- требовать обязательного заполнения всех полей;
- сохранять изображения тонометра без отдельного будущего требования;
- блокировать приложение, если Health Connect недоступен.

---

# 93. Критические invariants

Эти правила должны соблюдаться всегда.

```text
Missing != 0
Missing != false

DAY per date <= 1
EVENING per date <= 1
EXTRA per date unlimited

EXTRA has no scheduled notification

Notification carries target_date + slot

Notification click never infers slot from current clock

Raw observations are preserved

created_at is never replaced by historical target time

updated_at identifies corrected records

Blood pressure may exist without check-in

Check-in may exist without blood pressure

Health Connect is optional

Manual cycle entry is always available

OCR never saves without user confirmation

No destructive DB migration

No backend
```

---

# 94. Первый запуск coding agent

Начни работу с выполнения следующих шагов:

1. Прочитай весь `AGENTS.md`.
2. Создай `README.md`.
3. Создай `DECISIONS.md`.
4. Создай native Android Kotlin project в текущей директории.
5. Используй package/application id:

```text
com.local.statetracker
```

6. Display app name:

```text
Состояние
```

7. Настрой Gradle Kotlin DSL и version catalog.
8. Подключи Compose + Material 3 + Room + Navigation.
9. Не подключай Health Connect, ML Kit и chart library до соответствующей фазы, если они мешают минимальному initial build.
10. Создай skeleton navigation.
11. Убедись:

```bash
./gradlew assembleDebug
```

завершается успешно.

12. Затем реализуй Phase 2.
13. После каждой фазы:
    - собрать проект;
    - запустить релевантные tests;
    - исправить compile/test failures;
    - только после этого идти дальше.

Не оставляй приложение в заведомо некомпилируемом состоянии между фазами.

---

# 95. Финальный результат

Результатом должна быть директория, которую пользователь может открыть в Android Studio и выполнить:

```bash
./gradlew test
./gradlew assembleDebug
```

После чего получить устанавливаемый APK.

Приложение должно позволить полностью пройти основной сценарий:

```text
получить дневное уведомление
→ открыть DAY
→ частично заполнить состояние
→ оставить комментарий
→ при желании добавить давление
→ сохранить

вечером получить EVENING
→ заполнить

позже открыть Calendar
→ увидеть обе записи

добавить старую запись задним числом

отметить начало цикла

увидеть историю на графиках

экспортировать данные в CSV
```

Если этот end-to-end flow работает, данные не теряются, а перечисленные invariants соблюдаются — MVP считается функционально завершённым.
