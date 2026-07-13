# super-resolution-android

Мобильный монитор очереди обработки аудио (Kotlin + Jetpack Compose).

Подключается к серверу [super-resolution](../super-resolution) по локальной сети:

```
GET http://<IP>:8080/api/mobile-status
```

## Документация

- [PROJECT_PLAN.md](PROJECT_PLAN.md) — этапы, промпты, чек-листы

## Связь с backend

| Поле JSON | Смысл |
|-----------|--------|
| `queue_size` | задач в очереди |
| `workers_busy` | worker занят (0/1) |
| `workers_total` | consumer в RabbitMQ |
| `tasks_completed_today` | готово за сегодня (UTC) |

Если на сервере задан `APP_PASSWORD` — HTTP Basic (`admin` + пароль), как у Web UI.
