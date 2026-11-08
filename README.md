# Fuzz

Личный Android-плеер с волной рекомендаций, работающий офлайн. ТЗ, архитектура и дизайн согласованы в проекте «Музло».

## Сборка

Нужны JDK 17+ и Android SDK (compileSdk 37). Путь к SDK задаётся в `local.properties` (`sdk.dir=…`) или через `ANDROID_HOME`.

```bash
./gradlew :app:assembleDebug          # отладочный APK: app/build/outputs/apk/debug
./gradlew detekt spotlessCheck test   # статический анализ, форматирование, unit-тесты
./gradlew :app:lintDebug              # Android Lint по приложению и всем модулям
./gradlew assertModuleGraph           # правила зависимостей между модулями
```

Ключи API в репозиторий не коммитятся: они подставляются из `local.properties` или вводятся в настройках приложения.

## Модули

| Группа | Модули | Что внутри |
|---|---|---|
| Точка сборки | `app` | Activity, навигация, граф Hilt, подключение плагинов |
| Ядро | `core:model`, `core:common`, `core:domain` (JVM) | Сущности, утилиты, use case и контракты |
| Ядро | `core:data`, `core:database`, `core:datastore`, `core:network`, `core:player`, `core:native`, `core:design`, `core:ui`, `core:testing` | Данные, Room, настройки, сеть, Media3, JNI, тема, общие экраны, тестовые утилиты |
| Движки (JVM) | `engine:recommend`, `engine:search`, `engine:match` | Волна, нечёткий поиск, сопоставление треков |
| Плагины | `source:*`, `meta:*`, `import:formats`, `connect:*` | Источники музыки, метаданные, импорт, свои API |
| Экраны | `feature:*` | Главная, волна, коллекция, поиск, плеер, импорт, настройки, подключения, онбординг |
| Симуляция | `sim:wave` | Проверка требований к волне из раздела 9.1 ТЗ |

Правила зависимостей (проверяет `assertModuleGraph`): фича знает только `core:domain`, `core:model`, `core:ui`, `core:design`; плагины знают контракты и сеть, но не друг друга; движки знают только модель и общие утилиты; всё связывает `app`.

Настройки сборки общие для всех модулей и лежат в `build-logic` (convention-плагины `fuzz.*`), версии зависимостей в `gradle/libs.versions.toml`.
