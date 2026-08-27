
# DRAFT — Native Build

## Что

Команды и prereqs для сборки matrix-core как native-исполняемого файла через GraalVM/Mandrel.

## Шаги

1. Установить GraalVM JDK + `gu install native-image`.
2. `./gradlew :matrix-core:build -Dquarkus.native.enabled=true -Dnative.native-image=true`.
3. Артефакт: `matrix-core/build/matrix-core-runner`.

## Метрики / Гейты

- Бинарь ≤ 200 MiB.
- Время старта ≤ 1 секунды.
- RSS idle ≤ 256 MiB.

## Отложено

- Cross-build (linux-x64 ↔ aarch64); reproducibility через container.