package dev.fuzz.core.model

/** Оценка трека: лайк попадает в «Мне нравится», дизлайк прячет трек из волны (раздел 3.3 ТЗ). */
enum class Reaction { None, Like, Dislike }
