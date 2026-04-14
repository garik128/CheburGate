# CheburGate

<p align="center"><img src="icon.png" width="96"/></p>

Android браузер заблокированных сервисов через встроенный sing-box прокси.

> **⚠️ Disclaimer**
> 
> Создан с помощью Claude Code пользователем без какого-либо опыта в программировании. Внутри — лишние файлы, костыли, дублирующийся код и кривая логика. Всё как положено.
>
> Разрабатывался для личных целей. Протестирован через hysteria2 на Samsung Galaxy Fold 7 (только Telegram и YouTube). Иногда крашится, но, в целом, работает :)
>
> Используйте **AS IS** — как есть. Можете форкнуть и адаптировать под себя.
> Никаких претензий, пожеланий и просьб «а можно добавить...» — не принимается.
> Всё работает ровно настолько, насколько работает.

[Скачать последнюю версию APK](https://github.com/garik128/CheburGate/releases/latest/download/cheburgate.apk)

**Ключевые особенности:**
- Работает без VPN-флага
- Не требует root
- Встроенный HTTP прокси (127.0.0.1 + рандомный порт + сессионный токен)
- Поддержка конфигов `vless://` (tcp и xhttp) и `hysteria2://`
- Загрузка иконок сервисов через Coil
- Редактирование серверов прямо в приложении

## Требования

- **Android SDK**: Min 26 (Android 8.0), Target 36
- **Java**: 11+
- **Gradle**: 8.13.2+
- **Kotlin**: 2.0.21

## Сборка

1. Скачайте [sing-box-extended](https://github.com/shtorm-7/sing-box-extended) (автор [shtorm-7](https://github.com/shtorm-7)) бинарники под нужные ABI и поместите в `app/src/main/jniLibs/{arm64-v8a,armeabi-v7a}`.

2. Соберите проект (JDK берётся из Android Studio):
   ```bash
   JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleRelease \
     -x lintVitalRelease -x lintVitalAnalyzeRelease -x lintVitalReportRelease
   cp app/build/outputs/apk/release/app-release.apk app/release/cheburgate.apk
   ```
   Lint-таски пропускаются — при открытой Android Studio она блокирует lint-кэш.

3. Подписанный APK: `app/release/cheburgate.apk`

## Архитектура

```
com.android.cheburgate/
├── core/               — Управление sing-box процессом
├── data/               — База данных (Room), модели
├── ui/                 — UI слой (View system + ViewBinding)
│   ├── main/           — Главный экран
│   ├── browser/        — WebView с прокси
│   ├── servers/        — Управление конфигами
│   ├── settings/       — Настройки
│   └── history/        — История посещений
├── widget/             — App Widget
└── util/               — Утилиты
```

## Технический стек

- **Язык**: Kotlin 2.0.21
- **Build**: Gradle 8.13.2 + KSP
- **Прокси-ядро**: sing-box (бинарник → ProcessBuilder)
- **БД**: Room + KSP (НЕ kapt)
- **UI**: View system + ViewBinding (НЕ Compose)
- **Темы**: Material3 DayNight
- **Иконки**: Coil + DuckDuckGo Favicons API

## Безопасность

Локальный прокси защищён тремя слоями:
- **Рандомный порт** при каждом старте (`ServerSocket(0)`)
- **Bind 127.0.0.1** — недоступен из LAN
- **Сессионный токен** — sing-box требует Basic-авторизацию, токен генерируется через `SecureRandom` при каждом запуске. Сторонние приложения могут обнаружить открытый порт, но не могут использовать прокси для определения IP upstream-сервера — запросы без токена отклоняются с 407. Обработка 407 реализована через `WebViewClient.onReceivedHttpAuthRequest`.

## Ограничения

- Прокси работает только пока открыто приложение, при закрытии останавливается
- Факт наличия приложения виден через `PackageManager` — неизбежно для launcher-активити
- WebRTC заблокирован через JS-инъекцию (защита от IP-утечки)
- WebRTC звонки не работают через HTTP прокси (ограничение WebView)
- Push-уведомления в WebView требуют Service Worker

## Структура конфигов

Поддерживаемые форматы:
- **VLESS**: `vless://uuid@host:port?...` (xhttp, tcp, reality, xtls-rprx-vision)
- **Hysteria2**: `hy2://password@host:port?...` (salamander obfuscation)

## Интеграция

- `MainActivity` — главный экран с сеткой иконок сервисов
- `BrowserActivity` — WebView с ProxyController
- `ServersActivity` — импорт/экспорт конфигов
- `HistoryActivity` — история посещений
- `CheburWidget` — 4×1 App Widget (быстрый доступ к сервисам)
