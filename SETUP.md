# Подготовка окружения

## Обязательно перед сборкой

### 1. Скачайте sing-box бинарники

sing-box бинарники слишком большие для git, поэтому скачиваются отдельно:

1. Перейдите на https://github.com/SagerNet/sing-box/releases
2. Найдите последний релиз
3. Скачайте бинарники для нужных ABI:
   - `sing-box-*-android-arm64.zip` → распакуйте `sing-box` в `app/src/main/assets/singbox/arm64-v8a/`
   - `sing-box-*-android-armv7.zip` → распакуйте `sing-box` в `app/src/main/assets/singbox/armeabi-v7a/`
   - `sing-box-*-android-x86_64.zip` → распакуйте `sing-box` в `app/src/main/assets/singbox/x86_64/`

Итоговая структура:
```
app/src/main/assets/singbox/
├── arm64-v8a/
│   └── sing-box
├── armeabi-v7a/
│   └── sing-box
└── x86_64/
    └── sing-box
```

### 2. Установите зависимости

```bash
# Загрузите Gradle зависимости
./gradlew --refresh-dependencies
```

## Сборка

```bash
# Debug сборка
./gradlew assembleDebug

# Release сборка (требует подписи)
./gradlew assembleRelease

# Сборка и запуск на подключенном устройстве
./gradlew installDebug
./gradlew runDebug
```

## IDE

### Android Studio / IntelliJ IDEA

1. Откройте проект через "Open Project"
2. Выберите папку проекта
3. IDE автоматически загрузит зависимости (может занять время)
4. Убедитесь, что выбран правильный Android SDK в Project Structure

### Первый запуск

1. Создайте эмулятор или подключите устройство
2. Выполните `./gradlew installDebug`
3. Приложение появится в меню приложений

## Проблемы

### "sing-box не найден"

Проверьте, что бинарники находятся в правильной папке:
```bash
ls -la app/src/main/assets/singbox/arm64-v8a/sing-box
```

### Ошибка Gradle

```bash
# Очистите Gradle кэш
./gradlew clean
./gradlew --stop

# Переколпилируйте
./gradlew assembleDebug
```

### OutOfMemory при сборке

Увеличьте память для Gradle в `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
```

## Переменные окружения

Для локальной разработки используйте `local.properties`:

```properties
sdk.dir=/path/to/android/sdk
```

Этот файл уже в `.gitignore`, поэтому не будет залит на GitHub.
