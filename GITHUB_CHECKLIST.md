# Чек-лист перед заливкой на GitHub

## Файлы и папки

- [x] `.gitignore` обновлён:
  - Исключает CLAUDE.md (конфиденциальные инструкции)
  - Исключает links.txt (тестовые конфиги)
  - Исключает log.txt и логи
  - Исключает .kotlin/ (IDE служебная папка)
  - Исключает app/src/main/assets/singbox/ (бинарники слишком большие)

- [x] `.gitattributes` добавлен (правильная обработка бинарников и языков)

- [x] `README.md` создан (описание проекта, требования, архитектура)

- [x] `SETUP.md` создан (инструкции по подготовке, скачиванию sing-box)

- [x] `gradle.properties` содержит только стандартные настройки (без конфиденциальных данных)

## Перед git init

```bash
# 1. Убедитесь, что git инициализирован
git init

# 2. Проверьте, какие файлы будут залиты
git status

# 3. Проверьте gitignore работает корректно
git check-ignore -v links.txt      # должен выдать путь, значит игнорируется
git check-ignore -v CLAUDE.md      # должен выдать путь, значит игнорируется
git check-ignore -v log.txt        # должен выдать путь, значит игнорируется

# 4. Добавьте все файлы
git add .

# 5. Создайте начальный коммит
git commit -m "Initial commit: CheburGate Android app"

# 6. Добавьте remote
git remote add origin https://github.com/YOUR_USERNAME/CheburGate.git

# 7. Залейте на GitHub
git push -u origin main
```

## После создания на GitHub

1. Добавьте описание репо (на странице About)
2. Установите лицензию (рекомендуется MIT или Apache 2.0)
3. Настройте branch protection rules если нужно
4. Добавьте topics: `android`, `proxy`, `sing-box`, `vless`, `hysteria2`
5. Создайте Issues для известных TODO:
   - QR-сканер в AddViaLinkFragment
   - Загрузка файлов через WebView
   - Preferences для geoip:ru блока

## Секретность

Убедитесь, что в коде нет:
- API ключей
- UUID прокси-конфигов
- IP адресов серверов
- Пароля/токенов

Текущее состояние: **ОК** (links.txt исключен из git через gitignore)

## Документация

- [ ] README.md — прочитан и актуален
- [ ] SETUP.md — инструкции понятны
- [ ] AndroidManifest.xml содержит правильные permissions
- [ ] build.gradle.kts содержит правильные зависимости

## Готово к заливке ✓
