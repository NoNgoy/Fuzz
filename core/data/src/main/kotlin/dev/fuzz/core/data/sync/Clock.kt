package dev.fuzz.core.data.sync

import javax.inject.Inject

/** Время для полей updated_at. Отдельно, чтобы тесты не зависели от часов. */
internal open class Clock
    @Inject
    constructor() {
        open fun nowMs(): Long = System.currentTimeMillis()
    }
