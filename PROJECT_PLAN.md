# Проект: Мобильный монитор для очереди обработки аудио

**Версия:** 1.0  
**Дата:** 2026-07-13  
**Репозиторий:** `super-resolution-android` (отдельно от backend)  
**Backend:** [super-resolution](../super-resolution) — Web :**8080**, `GET /api/mobile-status`

---

## 1. Общая архитектура

```
┌─────────────────────────────────────────────────────────────┐
│                    Локальная сеть                         │
│                                                           │
│  ┌──────────────┐      ┌──────────────────────────────┐ │
│  │   Android    │      │   FastAPI :8080              │ │
│  │  устройство  │ ───▶ │      Веб-сервер + API        │ │
│  │   (клиент)   │ ◀── │                              │ │
│  └──────────────┘      │  ┌────────────────────────┐ │ │
│                        │  │  Существующий код       │ │ │
│                        │  │  (сбор метрик + UI)     │ │ │
│                        │  └────────────────────────┘ │ │
│                        │          │                   │ │
│                        │          ▼                   │ │
│                        │  ┌────────────────────────┐ │ │
│                        │  │     RabbitMQ           │ │ │
│                        │  │    (очередь задач)     │ │ │
│                        │  └────────────────────────┘ │ │
│                        └──────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 1.1. Компоненты
- **Backend** — репозиторий [super-resolution](../super-resolution): FastAPI :8080, `GET /api/mobile-status` (**готово**)
- **Android** — этот репозиторий: Kotlin + Jetpack Compose
- **Один GPU-worker** — `workers_total` обычно 0 или 1, `workers_busy` 0 или 1 (не «5 из 3»)

### 1.2. Семантика полей API (как на сервере сейчас)

| Поле | Значение |
|------|----------|
| `queue_size` | max(задач `queued` в SQLite, ready в RabbitMQ) |
| `workers_total` | consumer_count в RabbitMQ (0 = worker не подключён) |
| `workers_busy` | задач в статусе `processing` (0 или 1) |
| `tasks_completed_today` | `done` за сегодня по **UTC** (как в журнале jobs) |
| `timestamp` | UNIX, секунды |

При `status == "error"` дополнительно: `error_message` (строка).

**Авторизация:** если на сервере задан `APP_PASSWORD` — HTTP Basic (`admin` + пароль), как у Web UI. Сейчас можно без пароля; в приложении **заложить** поля логин/пароль (скрытые, необязательные), сохранять в SharedPreferences.

---

## 2. Этап 1: Backend API ✅ (репозиторий super-resolution)

Реализовано в другом репозитории — здесь только проверка с телефона.

- `scripts/mobile_status.py` → `GET /api/mobile-status`
- Порт **8080**, не 8000

### 2.4. Проверка

```bash
curl http://192.168.1.201:8080/api/mobile-status
# пример: {"status":"ok","timestamp":...,"queue_size":3,"workers_total":1,"workers_busy":1,"tasks_completed_today":7}
```

### 2.5. Критерии успеха
- [x] Эндпоинт в backend
- [ ] `curl` / приложение с телефона по Wi‑Fi
- [ ] firewall (если нужен)

---

## 3. Этап 2: Android-приложение (базовое)

### 3.1. Цель этапа
Создать минимальное приложение, которое делает GET-запрос к эндпоинту и отображает данные на экране.

### 3.2. Требования
- Kotlin, Jetpack Compose, Retrofit + Gson
- minSdk **24**
- HTTP cleartext (`usesCleartextTraffic`) — LAN без HTTPS
- Один экран
- Поля: **IP**, **порт** (default 8080), опционально **логин/пароль** (на будущее, Basic auth)
- Сохранять IP/порт в **SharedPreferences**
- Кнопка «Обновить»

### 3.3. Структура проекта

```
app/src/main/java/ru/max/superresolution/monitor/
├── MainActivity.kt
├── NetworkClient.kt      # Retrofit + OkHttp Basic auth если пароль задан
├── ApiService.kt
└── StatusResponse.kt     # data class под JSON API
```

### 3.4. Промпт для Cursor

Скопируйте этот текст в Cursor (в режиме агента):

> Напиши Android приложение на Kotlin + Jetpack Compose.
> 
> **Функционал**:
> - Поля: IP (placeholder `192.168.1.201`), порт (`8080`), опционально логин `admin` и пароль
> - URL: `http://{IP}:{PORT}/api/mobile-status`
> - Отображение:
>   - ONLINE / OFFLINE (зелёный / красный) по `status=="ok"`
>   - В очереди: `{queue_size}`
>   - Worker: «занят» если `workers_busy > 0`, иначе «свободен»; подпись `подключён` если `workers_total > 0`, иначе «нет consumer»
>   - Готово сегодня: `{tasks_completed_today}`
>   - Обновлено: `{timestamp}` → локальное время
> - При ошибке (нет сети, таймаут, 404) показать Toast с текстом ошибки
> - Статус ONLINE отображать зеленым цветом, OFFLINE — красным
> 
> **Требования**:
> - Разрешить HTTP в манифесте: `android:usesCleartextTraffic="true"`
> - Интернет-разрешение: `android.permission.INTERNET`
> - Использовать Retrofit с Gson-конвертером
> - Минимальный API = 24
> - Весь код в одном пакете, без сложной архитектуры
> - Для преобразования timestamp использовать SimpleDateFormat

### 3.5. Зависимости (build.gradle.kts)

```kotlin
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    
    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
}
```

### 3.6. Проверка работы
- [ ] Сборка APK проходит без ошибок
- [ ] Приложение устанавливается на телефон
- [ ] Поля ввода принимают IP и порт
- [ ] По кнопке выполняется запрос
- [ ] Данные отображаются корректно
- [ ] При недоступности сервера показывается ошибка

---

## 4. Этап 3: Автообновление

### 4.1. Цель этапа
Добавить автоматическое обновление данных каждые N секунд без нажатия кнопки.

### 4.2. Реализация
В том же Activity добавить:
- `LaunchedEffect` с бесконечным циклом и задержкой
- Обновление UI при получении новых данных
- Отмену обновлений при уничтожении Activity

### 4.3. Промпт для Cursor (дополнение)

> Добавь в существующий код автообновление:
> - После первого успешного запроса данные обновляются каждые 10 секунд
> - Использовать `LaunchedEffect(Unit)` с `delay(10000)` в бесконечном цикле
> - Пока идет запрос — показывать CircularProgressIndicator
> - При ошибке — прекратить автообновление (или показать ошибку)
> - Добавить кнопку "Стоп/Старт" для управления автообновлением

---

## 5. Этап 4: Уведомления

### 5.1. Цель этапа
Отправлять Android-уведомление, когда очередь полностью обработана.

### 5.2. Условие

Уведомление **только при переходе** «была работа» → «всё тихо»:

- раньше: `queue_size > 0` **или** `workers_busy > 0`
- сейчас: `queue_size == 0` **и** `workers_busy == 0`

Не слать при первом запуске на пустой системе и не повторять, пока очередь снова не наполнится.

### 5.3. Реализация
- NotificationChannel для Android 8+
- Уведомление с текстом "Все задачи выполнены! 🎉"
- Отправлять только когда состояние изменилось с "есть задачи" на "нет задач"
- Использовать флаг, чтобы не спамить повторными уведомлениями

### 5.4. Промпт для Cursor (дополнение)

> Добавь отправку уведомления:
> - Если `queue_size == 0` и `workers_busy == 0` — отправить уведомление
> - Уведомление: заголовок "Очередь пуста", текст "Все задачи обработаны!"
> - Использовать NotificationManagerCompat
> - Создать NotificationChannel (для Android 8+)
> - Отправлять уведомление только при переходе из "не пусто" в "пусто" (использовать флаг)
> - Не отправлять повторно, если очередь остается пустой

---

## 6. Этап 5: Сборка и установка (гайд)

### 6.1. Подготовка окружения

```bash
# Установка Java
sudo apt install openjdk-17-jdk

# Скачать Android Studio (или только CLI)
wget https://redirector.gvt1.com/edgedl/android/studio/ide-zips/2023.3.1.18/android-studio-2023.3.1.18-linux.tar.gz
tar -xzf android-studio-*.tar.gz
cd android-studio/bin
./studio.sh
```

### 6.2. Настройка телефона

1. Включить режим разработчика:
   - Настройки → О телефоне → Нажать 7 раз на "Номер сборки"
2. Включить отладку по USB:
   - Настройки → Для разработчиков → Отладка по USB → Включить
3. Подключить телефон по USB

### 6.3. Сборка APK

**Через Android Studio (проще):**
```
Build → Build Bundle(s) / APK(s) → Build APK(s)
```

**Через командную строку:**
```bash
cd /path/to/android/project
./gradlew assembleDebug
# APK будет в: app/build/outputs/apk/debug/
```

### 6.4. Установка на телефон

```bash
# Убедиться, что устройство видно
adb devices

# Установить APK
adb install app/build/outputs/apk/debug/app-debug.apk
```

**Альтернатива:** скопировать APK на телефон и открыть через файловый менеджер.

### 6.5. Чек-лист перед запуском
- [ ] Телефон и сервер в одной WiFi-сети
- [ ] На сервере запущен Python-скрипт с эндпоинтом
- [ ] Порт доступен (проверить через `curl http://192.168.x.x:8080/api/mobile-status` с компьютера)
- [ ] На телефоне разрешена установка из неизвестных источников
- [ ] В приложении правильно введен IP и порт

---

## 7. Дорожная карта по времени

| Этап | Задача | Время |
|------|--------|-------|
| 1 | Backend API (другой репо) | ✅ |
| 2 | Android базовое | 2–3 ч |
| 3 | Автообновление | 30 минут |
| 4 | Уведомления | 30 минут |
| 5 | Сборка и установка | 1 час |
| **Итого** | | **≈ 5 часов** |

---

## 8. Полезные команды для отладки

### Проверка сети
```bash
# С компьютера
curl -v http://localhost:8080/api/mobile-status

# С телефона (через adb)
adb shell curl http://192.168.1.100:8080/api/mobile-status
```

### Просмотр логов Android
```bash
adb logcat | grep -i "audiomonitor"
```

### Переустановка приложения
```bash
adb uninstall com.yourcompany.audiomonitor
adb install app-debug.apk
```

---

## 9. Возможные проблемы и решения

| Проблема | Решение |
|----------|---------|
| **Connection refused** | Проверьте, что сервер запущен и порт открыт. Проверьте IP в настройках телефона |
| **Cleartext HTTP not permitted** | Добавьте `android:usesCleartextTraffic="true"` в AndroidManifest.xml |
| **Приложение убивает фон** | Используйте `ForegroundService` для уведомлений в фоне |
| **Не парсится JSON** | Убедитесь, что data class поля совпадают с ключами JSON |
| **Gradle не скачивает зависимости** | Проверьте интернет, добавьте зеркало Maven в `build.gradle.kts` |

---

## 10. Следующие шаги (после прототипа)

Когда базовое приложение работает:

1. **Добавить больше метрик** из RabbitMQ (время последней задачи, ID активных воркеров)
2. **История состояния** — сохранять предыдущие значения и отображать график (или список последних проверок)
3. **Виджеты** — добавить виджет на рабочий стол Android для быстрого взгляда
4. **Темная тема** — поддержка Material Design 3

---

## 11. Итоговый чек-лист

- [x] Эндпоинт `/api/mobile-status` (backend)
- [ ] Android-приложение получает и отображает данные
- [ ] Автообновление работает (каждые 10 сек)
- [ ] Уведомления приходят при опустении очереди
- [ ] Приложение собрано в APK и установлено на телефон
- [ ] Работает в реальной сети (WiFi)

---

**Лицензия:** MIT (или ваша).  
**Поддержка:** Вопросы по этапам — в Issues проекта.

---