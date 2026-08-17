# State Tracker

Локальное Android-приложение «Состояние» для дневных, вечерних и дополнительных check-in, давления, отметок начала цикла, календаря, графиков и CSV-экспорта. Приложение не интерпретирует данные и не даёт медицинских рекомендаций.

## Requirements

- Android Studio с Android SDK 36
- JDK 17 или новее (Gradle использует Java target 17)
- Android 9+ (`minSdk 28`); основной ориентир — Samsung Galaxy S24

## Build

```bash
./gradlew assembleDebug
```

## Tests

```bash
./gradlew test
```

## APK

Debug APK создаётся в `app/build/outputs/apk/debug/app-debug.apk`.

## Architecture

Один Android-модуль: Compose UI → ViewModel → repositories → Room / Health Connect / ML Kit / AlarmManager / CSV exporter. Зависимости создаются вручную в `AppContainer`.

## Data storage

История хранится только локально в Room. DAY и EVENING уникальны в пределах даты; EXTRA не ограничены. Отсутствующее значение сохраняется отдельно от `0` и `false`. Destructive migrations не используются.

## Notifications

Inexact-напоминания планируются около 13:00 и 19:00, восстанавливаются после перезагрузки и смены времени. Deep link содержит исходные дату и слот.

## Health Connect

Опционально импортируются только даты начала `MenstruationPeriodRecord`. При недоступности Health Connect ручные отметки продолжают работать.

## OCR

Фото обрабатывается on-device с ML Kit. Распознанные SYS/DIA/пульс всегда показываются для ручной проверки и не сохраняются автоматически.

## Export

Системный диалог создаёт ZIP с `checkins.csv`, `observations.csv`, `blood_pressure.csv`, `cycle_events.csv`, `metrics.csv` в UTF-8.

## Privacy

Нет backend, рекламы, telemetry или INTERNET permission. Android backup отключён. Удаление приложения удалит локальную БД; для сохранения истории используйте экспорт.
