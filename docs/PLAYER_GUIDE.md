# MATRIX Player Guide — Minecraft Client Setup

## Быстрый старт (без лицензии)

```bash
matrix-minecraft
```

Используется **HMCL** — не требует Microsoft-аккаунта, лицензии, подписки.

В HMCL:
1. **Accounts** → **Add Offline** → любой ник
2. **Versions** → Install **1.20.4** (скачает автоматически, ~200 MB)
3. **Launch** → **Multiplayer** → Direct Connect → **localhost**
4. Чат: `/matrix start`

## Альтернативные лаунчеры (все бесплатные)

| Лаунчер | Офлайн | Комментарий |
|---------|--------|-------------|
| **HMCL** | ✅ Гарантированно | Уже установлен (`java -jar ~/.local/bin/hmcl.jar`) |
| **MultiMC** | ✅ | `sudo apt install multimc` |
| **Prism Launcher** | ⚠️ Нестабильно | В новых версиях кнопка Offline может быть скрыта |
| **TLauncher** | ✅ | tlauncher.org |

## Если нужна лицензия

Minecraft Java & Bedrock Edition стоит **$29.99 USD** (~2600 RUB) на [minecraft.net](https://minecraft.net).

**Способы сэкономить:**

| Способ | Цена | Детали |
|--------|------|--------|
| Xbox Game Pass PC | ~$9.99/мес | Включает Minecraft Java. Первый месяц часто $1 |
| Steam / G2A / Eneba | $15–25 | Ключи от реселлеров (риск блокировки) |
| Турецкий регион Xbox | ~$5 | Региональная цена через VPN + турецкую карту |
| Аргентина Xbox | ~$3 | Самая дешёвая, нужна карта Аргентины |
| Подарочные карты | $20–30 | Карты Minecraft в DNS, М.Видео, Ozon |
| Скидки | −30–50% | Распродажи: Чёрная пятница, день рождения Minecraft (17 мая) |

**Инструкция для Game Pass ($1 первый месяц):**
1. Зайти на [xbox.com/ru-RU/xbox-game-pass/pc-game-pass](https://xbox.com)
2. Оформить подписку → первый месяц $1
3. Скачать Xbox App → установить Minecraft Launcher
4. Войти в Microsoft-аккаунт → игра доступна по подписке

## Подключение к серверу

Сервер запускается автоматически при `matrix-minecraft`, либо вручную:

```bash
cd minecraft-server
java -Xmx2G -Xms1G -jar paper.jar --nogui
```

Сервер на `localhost:25565`, офлайн-режим включён (`online-mode=false`).

## Команды MATRIX

| Команда | Описание |
|---------|----------|
| `/matrix start` | Запустить MPDT-нейробота |
| `/matrix stop` | Остановить бота |
| `/matrix status` | Состояние (тики, блоки добыто) |
| `/matrix train` | Обучение нейросети |

## Что делает бот

- **Сенсоры (20 бит):** блоки вокруг, здоровье, голод, инструмент
- **Действия:** Move (N/S/W/E/STAY), Mine, Eat
- **Логика:** DecisionTree нейроны → action

## Для разработчиков

```bash
# Пересборка плагина
./gradlew :matrix-spigot:fatJar
cp matrix-spigot/build/libs/matrix-spigot-1.0.0-all.jar minecraft-server/plugins/matrix-spigot.jar

# Тестирование без клиента
./gradlew runMinecraftExperiment

# Логи
tail -f minecraft-server/logs/latest.log | grep MatrixSpigot
```
