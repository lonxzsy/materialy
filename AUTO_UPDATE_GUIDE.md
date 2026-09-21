# Руководство по внедрению онлайн-обновлений через GitHub (Kotlin / Android)

Данное решение является на 100% бесплатным, не требует собственного сервера и обходит лимиты обращений к GitHub API за счет использования статического манифеста версий.

---

## 1. Архитектура решения

1. **Репозиторий:** В ветке `main` лежит статический файл `version.json`.
2. **CDN GitHub:** Приложение проверяет этот файл по ссылке `raw.githubusercontent.com`.
3. **Хранилище сборок:** Скомпилированный файл `app-release.apk` прикрепляется к GitHub Releases.
4. **Android-клиент:**
   * Сверяет установленный `versionCode` с актуальным в сети.
   * Скачивает APK во внутренний кэш приложения (`context.cacheDir/updates`).
   * Запрашивает разрешение на установку из неизвестных источников (Android 8.0+).
   * Запускает системный установщик через `FileProvider`.

---

## 2. Настройка Android-проекта

### Шаг 2.1: Разрешения в `AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />

<application ...>
    <!-- FileProvider для передачи APK системному установщику -->
    <provider
        android:name="androidx.core.content.FileProvider"
        android:authorities="${applicationId}.provider"
        android:exported="false"
        android:grantUriPermissions="true">
        <meta-data
            android:name="android.support.FILE_PROVIDER_PATHS"
            android:resource="@xml/file_paths" />
    </provider>
</application>
```

### Шаг 2.2: Пути FileProvider (`res/xml/file_paths.xml`)

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path name="cached_apks" path="updates/" />
</paths>
```

---

## 3. Манифест версий (`version.json`)

Размещен в корне репозитория:
```json
{
  "versionCode": 1,
  "versionName": "1.0.0",
  "downloadUrl": "https://github.com/lonxzsy/materialy/releases/download/v1.0.0/app-release.apk",
  "releaseNotes": "Список изменений:\n- Персонализированная Моя Волна\n- Material 3 Expressive UI\n- Улучшенный звук и эквалайзер"
}
```

---

## 4. Как опубликовать новую версию с обновлением

1. Увеличьте `versionCode` и `versionName` в `app/build.gradle.kts` (например, `versionCode = 2`, `versionName = "1.0.1"`).
2. Обновите `version.json` в корне репозитория с новыми данными и описанием изменений.
3. Закоммитьте изменения и создайте git-тег:
   ```bash
   git add .
   git commit -m "Release v1.0.1"
   git tag v1.0.1
   git push origin main --tags
   ```
4. GitHub Actions автоматически соберет релизный APK и опубликует его в раздел GitHub Releases.
5. При следующем запуске или при нажатии «Обновления» в настройках приложение автоматически обнаружит новую версию, покажет список изменений, скачает APK и предложит установку в 1 клик!
