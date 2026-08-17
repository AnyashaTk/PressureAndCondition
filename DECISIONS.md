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
