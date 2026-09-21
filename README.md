# 🎵 Materialy Music

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-brightgreen.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203%20Expressive-purple.svg)](https://developer.android.com/jetpack/compose)
[![Release](https://img.shields.io/badge/Release-v1.0.0-orange.svg)](https://github.com/lonxzsy/materialy/releases)

**Materialy Music** — это современный Android аудиоплеер и клиент для потокового воспроизведения музыки, спроектированный в соответствии с дизайн-системой **Material Design 3 Expressive**. Приложение объединяет локальное воспроизведение, онлайн-стриминг, умный персонализированный поток музыки «Моя Волна», динамическую колористику обложек и встроенную систему автоматических онлайн-обновлений через GitHub Releases.

---

## ✨ Ключевые возможности

- 🎨 **Material 3 Expressive Design**:
  - Адаптивные динамические формы и скругления (Pills, Rounded Corners 16–28dp).
  - Живые пружинные микро-анимации (`bouncy`, Spring Motion).
  - Динамическая экстракция палитры из обложек треков (`DynamicThemeManager`) с гармонизацией фонов, карточек и статус-бара/навбара.

- 🌊 **«Моя Волна»**:
  - Персональный бесконечный поток рекомендаций на основе истории прослушиваний и поисковых запросов пользователя.
  - Настраиваемые вайбы: *«Мой вайб»*, *«Бодрое»*, *«Спокойное»*, *«Открытие»*.
  - Уникальный интерактивный экран с вибрирующими визуальными волнами, реагирующими на энергию басов.

- 🎛️ **Профессиональный звук**:
  - 5-полосный аппаратный эквалайзер с кастомными и встроенными пресетами.
  - Регулируемые Bass Boost и Virtualizer (3D-звук).
  - **Плавное звучание (Fade)**: Fade-in при старте, Fade-out при паузе и бесшовный кроссфейд при смене треков.

- 📦 **Система онлайн-обновлений**:
  - Автоматическая проверка новых версий при старте и ручная проверка в «Настройках».
  - Загрузка APK через `OkHttpClient` с отображением прогресса в реальном времени.
  - Безопасная установка через `FileProvider` и `ACTION_VIEW`.
  - Статический манифест `version.json` на GitHub CDN (без ограничений GitHub REST API).
  - Автоматическая сборка релизов в GitHub Actions при создании git-тегов (`v*`).

---

## 🛠️ Стек технологий и архитектура

- **Язык**: Kotlin 1.9.24
- **UI Toolkit**: Jetpack Compose, Material 3 Expressive, Compose Navigation
- **DI**: Dagger Hilt
- **Аудиодвижок**: AndroidX Media3 (ExoPlayer), Android AudioEffect (Equalizer, BassBoost, Virtualizer)
- **Изображения**: Coil 2.x
- **Сетевой стек**: OkHttp 4.x, Kotlinx Serialization
- **Асинхронность**: Kotlin Coroutines & StateFlow / SharedFlow
- **Сборка**: Gradle Kotlin DSL (`build.gradle.kts`), KSP, R8/ProGuard

---

## 🚀 Сборка и установка

### Требования
- JDK 17
- Android SDK (API 35, Build-tools 35.0.0, Platform API 26+)

### Сборка Release APK
```bash
./gradlew assembleRelease
```
Собранный APK будет находиться в `app/build/outputs/apk/release/app-release.apk`.

### Установка через ADB
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## 🔄 Публикация обновлений через GitHub Actions

1. Измените `versionCode` и `versionName` в `app/build.gradle.kts`.
2. Обновите информацию о релизе в корневом файле `version.json`:
   ```json
   {
     "versionCode": 2,
     "versionName": "1.0.1",
     "downloadUrl": "https://github.com/lonxzsy/materialy/releases/download/v1.0.1/app-release.apk",
     "releaseNotes": "Список изменений в новой версии..."
   }
   ```
3. Создайте тег и отправьте его в репозиторий:
   ```bash
   git tag v1.0.1
   git push origin main --tags
   ```
4. GitHub Actions автоматически соберет Release APK, подпишет его и создаст релиз на GitHub с прикрепленным APK.

Подробная документация по системе обновлений находится в файле [AUTO_UPDATE_GUIDE.md](AUTO_UPDATE_GUIDE.md).

---

## 📄 Лицензия

Проект распространяется для личного и образовательного использования.
