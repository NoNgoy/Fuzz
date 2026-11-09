# protobuf-javalite 4.x не несёт своих правил R8. Lite-рантайм находит поля сообщений
# по именам (onboardingCompleted_ и т.д.), и без этого правила релизная сборка падает
# при первом чтении настроек: «Field … for … not found».
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
