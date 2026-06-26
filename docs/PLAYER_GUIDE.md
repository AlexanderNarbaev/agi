# MATRIX Player Guide — Minecraft Client Setup

## 1. Установка клиента Minecraft Java Edition 1.20.4

### Вариант А — Официальный лаунчер (лицензия)

1. Купить лицензию на [minecraft.net](https://www.minecraft.net) (~$30)
2. Скачать [Minecraft Launcher](https://www.minecraft.net/download)
3. Установить, войти в аккаунт Microsoft/Mojang
4. В лаунчере: **Installations → New → Version 1.20.4 → Create**
5. Запустить с выбранной версией 1.20.4

### Вариант Б — Prism Launcher (бесплатно, офлайн)

1. Скачать [Prism Launcher](https://prismlauncher.org/download/)
2. Установить: `sudo apt install prismlauncher` (Linux) или `winget install PrismLauncher` (Windows)
3. Запустить Prism Launcher
4. **Add Instance →** Name: `MATRIX 1.20.4`, Version: `1.20.4`, Loader: `None`
5. Settings → Accounts → Add Offline → Name: любое имя
6. Settings → Java → Java 21 или 25

### Вариант В — Консольный клиент (без GUI)

```bash
# Установка Java 21+ и Minecraft через пакетный менеджер
sudo apt install openjdk-21-jdk

# Загрузка консольного клиента
mkdir -p ~/.minecraft/versions/1.20.4
cd ~/.minecraft/versions/1.20.4

# Скачать клиентский JAR
curl -L -o client.jar \
  "https://piston-meta.mojang.com/v1/packages/<hash>/client.jar"
# Или использовать Prism Launcher — он автоматически загрузит нужные файлы
```

## 2. Подключение к серверу MATRIX

### Запуск сервера (на локальной машине)

```bash
cd /path/to/agi/minecraft-server
java -Xmx2G -Xms1G -jar paper.jar --nogui
```

Сервер запустится на `localhost:25565`.

### Подключение из клиента

1. Запустить Minecraft 1.20.4
2. **Multiplayer** → **Direct Connect** (или **Add Server**)
3. Server Address: `localhost` (или `127.0.0.1`)
4. **Join Server**

Если сервер на другой машине — указать её IP-адрес.

### Настройка офлайн-режима

Для тестов без лицензии в `server.properties` установлено `online-mode=false`.

В Prism Launcher для офлайн-игры:
- Settings → Accounts → Add Offline → любое имя

## 3. Команды MATRIX в игре

Открыть чат (клавиша `T` или `/`) и ввести:

| Команда | Описание |
|---------|----------|
| `/matrix start` | Запустить MPDT-нейробота |
| `/matrix stop` | Остановить бота |
| `/matrix status` | Показать состояние бота (тики, блоки добыто) |
| `/matrix train` | Запустить обучение нейросети (асинхронно) |

### Что делает бот
- **Сенсоры (20 бит):** блоки вокруг (15 бит), здоровье (1), голод (2), инструмент (1), еда (1)
- **Действия:** Move (N/S/W/E/STAY), Mine, Eat
- **Логика:** MPDT-нейроны (DecisionTree) оценивают сенсоры → выбирают действие
- **Тик:** 20 тиков/сек — каждый тик sensor→action→execute

## 4. Управление в Minecraft

| Клавиша | Действие |
|---------|----------|
| `W/A/S/D` | Ходьба |
| `Space` | Прыжок |
| `ЛКМ` | Ломать блоки / атака |
| `E` | Инвентарь |
| `T` / `/` | Чат (команды) |
| `F3` | Отладка (координаты, FPS) |
| `Esc` | Меню |

## 5. Разработка и отладка

### Логи плагина

```bash
tail -f minecraft-server/logs/latest.log | grep MatrixSpigot
```

### Пересборка плагина после изменений

```bash
./gradlew :matrix-spigot:fatJar
cp matrix-spigot/build/libs/matrix-spigot-1.0.0-all.jar minecraft-server/plugins/matrix-spigot.jar
# Рестарт сервера: команда reload в консоли сервера
```

### Тестирование без клиента (Java-песочница)

```bash
# Встроенный симулятор — не требует Minecraft
./gradlew runMinecraftExperiment
```

## 6. Сетевая игра

Для игры по сети:

1. В `server.properties`: `server-ip=0.0.0.0`
2. Открыть порт на роутере: `25565 TCP`
3. Клиенты подключаются по внешнему IP сервера
4. Для безопасности: установить `white-list=true` и `/whitelist add <игрок>`

### Docker-версия (альтернатива)

```bash
# itzg/minecraft-server — готовый образ с Paper
docker run -d -p 25565:25565 \
  -e EULA=TRUE -e VERSION=1.20.4 -e TYPE=PAPER \
  -v ./plugins:/plugins \
  itzg/minecraft-server
```
