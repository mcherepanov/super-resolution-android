# SR Monitor (super-resolution-android)

Android-приложение для мониторинга очереди обработки аудио на сервере [super-resolution](https://gitverse.ru/Max_Cherep/super-resolution).

Показывает статус worker, текущую задачу, прогресс и очередь. При завершении всех задач — уведомление со звуком (если включено).

## Быстрый старт

### Требования

- Android 7.0+ (API 24)
- Сервер super-resolution в той же локальной сети (Wi‑Fi)
- Android Studio (для сборки) или готовый APK

### Сборка

```bash
git clone git@gitverse.ru:Max_Cherep/super-resolution-android.git
cd super-resolution-android
# Укажите путь к SDK в local.properties: sdk.dir=/path/to/Android/Sdk
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

### Установка на телефон

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Или скопируйте APK на устройство и установите вручную.

### Первый запуск

1. Укажите **адрес сервера** (IP или hostname) и порт `8080`.
2. Если на сервере задан `APP_PASSWORD` — логин `admin` и пароль.
3. Нажмите **Обновить**.
4. Для фонового мониторинга включите **Уведомления при завершении** и разрешите их в Android.

Фоновый мониторинг работает только при включённых уведомлениях. Остановка: выключить переключатель или смахнуть приложение из списка недавних.

## API

```
GET http://<host>:8080/api/mobile-status
```

Авторизация: HTTP Basic (если на сервере задан пароль).

| Поле | Смысл |
|------|--------|
| `queue_size` | задач в очереди |
| `workers_busy` | worker занят (0/1) |
| `workers_total` | consumer в RabbitMQ |
| `tasks_completed_today` | готово за сегодня (UTC) |
| `current_job` | текущая задача (имя, прогресс, фильтры) |

## Документация

- [PROJECT_PLAN.md](PROJECT_PLAN.md) — план разработки, этапы, чек-листы
- [CONTRIBUTING](.gitverse/CONTRIBUTING.md) — как вносить изменения
- [SECURITY](.gitverse/SECURITY.md) — сообщения об уязвимостях

## Лицензия

[MIT](LICENSE)

## Автор

© [@MaxCherepanov](https://gitverse.ru/Max_Cherep)
