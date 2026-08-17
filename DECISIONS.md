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
