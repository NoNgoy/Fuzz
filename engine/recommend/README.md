# engine:recommend — движок волны

Чистый Kotlin без Android. Реализует раздел 4 ТЗ и раздел «Движок волны» архитектуры.

## Как устроено

1. **Профиль вкуса** (`profile/`): события `WaveSignal` (дослушал, пропустил и на какой секунде, лайк, дизлайк, в плейлист) копятся в векторы тегов и артистов с затуханием 30 дней. Без истории вкус берётся из самой коллекции.
2. **Корзины** (`candidate/`): любимое, непрослушанное, похожее по графу Last.fm/ListenBrainz, Jamendo по тегам, превью вне коллекции. Граф обходится в ширину на всю сессию (`SimilarityExplorer`): каждая доливка раскрывает следующих артистов, поэтому поток не сужается до пары похожих треков.
3. **Оценка** (`rank/Scorer`): вкус по тегам, артист, аудиопризнаки, занятие и настроение, новизна (знак зависит от ползунка), популярность, штрафы за недавнее и пропуски.
4. **Пачка** (`rank/BatchAssembler`): доли корзин по ползунку, MMR против однообразия, плавные переходы темпа и энергии, не больше 2 треков артиста подряд.
5. **Сессия** (`WaveSession`): пул в памяти; пропуск, лайк и дизлайк пересобирают остаток пачки без запросов (≈10 мс).

Все веса, пропорции корзин и таблицы занятий и настроений лежат в `src/main/resources/.../config/wave-config.json`.

## Как подключить (core:data)

Нужно реализовать порты из `port/WavePorts.kt`:

| Порт | Чем реализуется |
|---|---|
| `WaveLibrary` | Room: треки коллекции с тегами и аудиопризнаками |
| `SimilarityGraph` | `MetadataProvider` из meta:lastfm и meta:listenbrainz, с кэшем |
| `ExternalCatalog` (`Preview`) | source:deezer-preview |
| `ExternalCatalog` (`OpenCatalog`) | source:jamendo |

```kotlin
val engine = WaveEngine(library, graph, listOf(previews, jamendo))
val profile = engine.buildProfile(collection, history)
val session = engine.session(WaveSeed.MyWave, settings, profile)
val first = session.start()           // до 1 с, из локальных корзин
scope.launch { session.enrich() }     // сетевые корзины фоном
session.nextBatch()                   // когда в очереди осталось 3 трека
session.onSignal(signal, upcomingIds) // после каждого события плеера
```

Свои API из раздела 8.4 ТЗ добавляются как `CandidateGenerator` через `extraGenerators`.

## Проверка

`sim:wave` гоняет волну на синтетическом мире (600 треков в коллекции, 400 артистов вокруг) и проверяет требования раздела 4.3: нет повторов за 2 часа, при ползунке выше 70 больше половины треков новые, старт до 1 с, пересчёт до 300 мс, 1000 треков подряд без обрыва.
