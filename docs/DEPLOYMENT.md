# MATRIX Deployment Guide

**Версия:** v3.1
**Дата:** 2026-07-13

---

## Обзор

MATRIX поддерживает два способа развертывания:

| Способ | Окружение | Сложность | Время запуска |
|--------|-----------|-----------|---------------|
| Docker Compose | Development / Production | Легко | ~5 минут |
| Minikube K8s | Development / Staging | Средне | ~15 минут |

---

## 1. Docker Compose (Рекомендуется)

### 1.1 Полный стек (Production-like)

Запускает всё: matrix-core + PostgreSQL + Redis + Kafka + Minecraft.

```bash
# Клонировать репозиторий
git clone https://gitverse.ru/AlexandrNarbaev/agi.git
cd agi

# Запустить полный стек
docker compose up --build

# Или в фоновом режиме
docker compose up --build -d
```

**Сервисы:**

| Сервис | Порт | URL | Описание |
|--------|------|-----|----------|
| matrix-core | 8080 | http://localhost:8080 | REST API |
| matrix-core (metrics) | 9091 | http://localhost:9091 | Prometheus метрики |
| PostgreSQL | 5432 | localhost:5432 | Event journal persistence |
| Redis | 6379 | localhost:6379 | Neuron cache |
| Kafka | 9092 | localhost:9092 | Event streaming |
| Minecraft | 25565 | localhost:25565 | Paper 1.20.4 |

**Проверка:**

```bash
# Проверить health
curl http://localhost:8080/q/health

# Проверить API
curl http://localhost:8080/v1/models

# Проверить метрики
curl http://localhost:9091/q/metrics
```

**Остановка:**

```bash
docker compose down

# С удалением volumes (данные)
docker compose down -v
```

---

### 1.2 Только инфраструктура (Development)

Запускает только PostgreSQL + Redis + Kafka. matrix-core запускается локально.

```bash
# Запустить инфраструктуру
docker compose -f docker-compose.dev.yml up -d

# Запустить matrix-core в dev mode
./gradlew :matrix-core:quarkusDev
```

**Преимущества:**
- Горячая перезагрузка (Quarkus dev mode)
- Быстрая компиляция
- Отладка через IDE

---

### 1.3 Конфигурация

#### Переменные окружения

| Переменная | Описание | Значение по умолчанию |
|------------|----------|----------------------|
| `QUARKUS_PROFILE` | Quarkus профиль | `prod` |
| `QUARKUS_HTTP_PORT` | HTTP порт | `8080` |
| `QUARKUS_DATASOURCE_JDBC_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://postgres:5432/matrix` |
| `QUARKUS_DATASOURCE_USERNAME` | PostgreSQL пользователь | `matrix` |
| `QUARKUS_DATASOURCE_PASSWORD` | PostgreSQL пароль | `matrix` |
| `QUARKUS_REDIS_HOSTS` | Redis hosts | `redis:6379` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap servers | `kafka:9092` |
| `QUARKUS_MICROMETER_EXPORT_PROMETHEUS_HOST` | Prometheus host | `0.0.0.0` |
| `QUARKUS_MICROMETER_EXPORT_PROMETHEUS_PORT` | Prometheus порт | `9091` |
| `QUARKUS_OPENTELEMETRY_TRACER_EXPORTER_OTLP_ENDPOINT` | OTLP endpoint | `http://jaeger:4317` |
| `MATRIX_MODELS_PRETRAINED_DIR` | Путь к pretrained моделям | `/app/models/pretrained` |
| `TELEGRAM_BOT_TOKEN` | Telegram бот токен | (пусто) |

#### Volumes

| Volume | Описание |
|--------|----------|
| `pgdata` | PostgreSQL данные |
| `redisdata` | Redis данные |
| `kafkadata` | Kafka логи |
| `mcdata` | Minecraft мир |

#### Resource Limits

В `docker-compose.yml`:

```yaml
services:
  matrix-core:
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '0.5'
          memory: 512M
```

---

### 1.4 Pretrained Models

Pretrained модели монтируются read-only:

```yaml
volumes:
  - ./models/pretrained:/app/models/pretrained:ro
```

**Структура:**

```
models/pretrained/
├── smollm2-135m/
│   ├── neurons.avro
│   └── metadata.json
└── qwen2.5-0.5b/
    ├── neurons.avro
    └── metadata.json
```

---

## 2. Minikube K8s

### 2.1 Предварительные требования

- Minikube (v1.30+)
- kubectl
- Docker
- 32 GB RAM рекомендуется

### 2.2 Быстрый запуск

```bash
# Запуск всего стека
./scripts/matrix-minikube.sh start

# Проверить статус
./scripts/matrix-minikube.sh status

# Остановить
./scripts/matrix-minikube.sh stop
```

### 2.3 Ручной запуск

```bash
# Запустить minikube
minikube start --cpus=32 --memory=59g --driver=docker

# Применить манифесты
kubectl apply -k infra/k8s/minikube/

# Проверить поды
kubectl get pods -n matrix

# Получить URL
minikube service matrix-core -n matrix --url
```

### 2.4 Сервисы (NodePort)

| Сервис | NodePort | URL |
|--------|----------|-----|
| matrix-core | 30091 | http://matrix.local:30091 |
| Grafana | 30300 | http://grafana.local:30300 |
| Prometheus | 30090 | http://prometheus.local:30090 |
| Jaeger | 31686 | http://jaeger.local:31686 |
| MinIO | 30900 | http://minio.local:30900 |
| Minecraft | 32565 | minecraft.local:32565 |

### 2.5 DNS Setup

Добавить в `/etc/hosts`:

```
192.168.49.2 matrix.local grafana.local prometheus.local jaeger.local minio.local
```

Где `192.168.49.2` — IP minikube (получить через `minikube ip`).

---

## 3. Kubernetes Manifests

### 3.1 Структура

```
infra/k8s/
├── base/
│   ├── namespace.yaml
│   ├── configmap.yaml
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── hpa.yaml
│   ├── vpa.yaml
│   ├── pvc.yaml
│   ├── rbac.yaml
│   ├── servicemonitor.yaml
│   ├── prometheusrule.yaml
│   ├── strimzi-kafka.yaml
│   ├── minio-tenant.yaml
│   ├── loki.yaml
│   └── kustomization.yaml
├── minikube/
│   ├── matrix-core.yaml
│   └── kustomization.yaml
└── runbooks.md
```

### 3.2 Key Manifests

#### Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: matrix-core
  namespace: matrix
spec:
  replicas: 1
  selector:
    matchLabels:
      app: matrix-core
  template:
    metadata:
      labels:
        app: matrix-core
    spec:
      containers:
      - name: matrix-core
        image: matrix-core:latest
        ports:
        - containerPort: 8080
        - containerPort: 9091
        env:
        - name: QUARKUS_PROFILE
          value: "prod"
        - name: BRC_MAX_STEPS
          value: "5"
        - name: RAG_TOP_K
          value: "5"
        resources:
          limits:
            cpu: "2"
            memory: "2Gi"
          requests:
            cpu: "500m"
            memory: "512Mi"
        livenessProbe:
          httpGet:
            path: /q/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 15
        readinessProbe:
          httpGet:
            path: /q/health
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5
```

#### Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: matrix-core
  namespace: matrix
spec:
  type: NodePort
  selector:
    app: matrix-core
  ports:
  - name: http
    port: 8080
    nodePort: 30091
  - name: metrics
    port: 9091
    nodePort: 30091
```

---

## 4. Мониторинг

### 4.1 Prometheus

**Endpoint:** `:9091/q/metrics`

**Ключевые метрики для мониторинга:**

```promql
# CPU usage
process_cpu_seconds_total{job="matrix-core"}

# Memory usage
jvm_memory_used_bytes{job="matrix-core"}

# Request rate
rate(matrix_api_requests_total[5m])

# Error rate
rate(matrix_api_requests_total{status=~"5.."}[5m])

# Latency p99
histogram_quantile(0.99, rate(matrix_api_latency_seconds_bucket[5m]))

# Evolution fitness
matrix_evolution_fitness_best

# Agent ticks
rate(matrix_agent_ticks_total[5m])
```

### 4.2 Grafana

**URL:** http://localhost:3000 (Docker) | http://grafana.local:30300 (K8s)

**Default credentials:** admin/admin

**Dashboards:**
- MATRIX Overview
- Neuron Activity
- Evolution Progress
- Agent Performance
- System Health

### 4.3 Jaeger

**URL:** http://localhost:16686 (Docker) | http://jaeger.local:31686 (K8s)

Traces automatically collected via OpenTelemetry.

### 4.4 Loki

Logs available in Grafana (Loki datasource).

**Структура логов:**

```json
{
  "timestamp": "2026-07-13T10:00:00Z",
  "level": "INFO",
  "logger": "io.matrix.api.OpenAIChatResource",
  "message": "Chat completion: model=M.A.T.R.I.X. inputLen=10 verdict=ALLOWED responseLen=50",
  "traceId": "abc123",
  "spanId": "def456"
}
```

---

## 5. Troubleshooting

### 5.1 Docker Compose

#### Проблема: matrix-core не запускается

```bash
# Проверить логи
docker compose logs matrix-core

# Проверить health
docker compose ps

# Пересобрать
docker compose up --build --force-recreate matrix-core
```

#### Проблема: PostgreSQL не доступен

```bash
# Проверить логи
docker compose logs postgres

# Проверить подключение
docker compose exec postgres pg_isready -U matrix -d matrix

# Перезапустить
docker compose restart postgres
```

#### Проблема: Kafka не запускается

```bash
# Проверить логи
docker compose logs kafka

# Kafka требует больше времени для старта
# Подождать 30-60 секунд

# Проверить топики
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --list
```

#### Проблема: Redis не доступен

```bash
# Проверить логи
docker compose logs redis

# Проверить ping
docker compose exec redis redis-cli ping
```

---

### 5.2 Minikube

#### Проблема: Поды в CrashLoopBackOff

```bash
# Проверить логи
kubectl logs -n matrix <pod-name>

# Описать под
kubectl describe pod -n matrix <pod-name>

# Проверить события
kubectl get events -n matrix --sort-by='.lastTimestamp'
```

#### Проблема: Недостаточно ресурсов

```bash
# Проверить ресурсы
kubectl top nodes
kubectl top pods -n matrix

# Увеличить ресурсы minikube
minikube stop
minikube start --cpus=32 --memory=59g
```

#### Проблема: DNS не работает

```bash
# Проверить /etc/hosts
cat /etc/hosts | grep matrix

# Получить IP minikube
minikube ip

# Добавить DNS записи
echo "$(minikube ip) matrix.local grafana.local prometheus.local jaeger.local" | sudo tee -a /etc/hosts
```

---

### 5.3 Приложение

#### Проблема: Этический фильтр блокирует всё

```bash
# Проверить логи на REJECTED
docker compose logs matrix-core | grep REJECTED

# Проверить конфигурацию фильтра
# Фильтр блокирует: kill, torture, enslave, autonomous weapons
```

#### Проблема: Нейроны не обучаются

```bash
# Проверить метрики эволюции
curl http://localhost:9091/q/metrics | grep evolution

# Проверить логи
docker compose logs matrix-core | grep -i evolution

# Увеличить population/generations
curl -X POST http://localhost:8080/api/v1/evolve \
  -H 'Content-Type: application/json' \
  -d '{"generations": 100, "population": 50}'
```

#### Проблема: WebSocket disconnects

```bash
# Проверить rate limit (100 сообщений на сессию)
# Проверить логи
docker compose logs matrix-core | grep WebSocket

# Переподключиться
# Rate limit сбрасывается при новом подключении
```

#### Проблема: Telegram бот не отвечает

```bash
# Проверить токен
echo $TELEGRAM_BOT_TOKEN

# Проверить логи
docker compose logs matrix-core | grep Telegram

# Проверить webhook
curl https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/getMe
```

---

### 5.4 Производительность

#### Медленные ответы API

```bash
# Проверить latency метрики
curl http://localhost:9091/q/metrics | grep latency

# Проверить CPU/Memory
docker stats

# Оптимизировать:
# 1. Увеличить Xmx
# 2. Увеличить количество replicas (K8s)
# 3. Включить Redis кэш
```

#### Высокое потребление памяти

```bash
# Проверить JVM heap
docker compose exec matrix-core jcmd 1 GC.heap_info

# Оптимизировать:
# 1. Уменьшить VQVAE_CODEBOOK_SIZE
# 2. Уменьшить RAG_TOP_K
# 3. Включить eviction в Redis
```

---

## 6. Backup & Recovery

### 6.1 Docker Compose

```bash
# Backup PostgreSQL
docker compose exec postgres pg_dump -U matrix matrix > backup.sql

# Backup Redis
docker compose exec redis redis-cli BGSAVE
docker compose cp redis:/data/dump.rdb ./backup/dump.rdb

# Backup Kafka
docker compose cp kafka:/tmp/kraft-combined-logs ./backup/kafka-logs

# Restore PostgreSQL
cat backup.sql | docker compose exec -T postgres psql -U matrix matrix

# Restore Redis
docker compose cp ./backup/dump.rdb redis:/data/dump.rdb
docker compose restart redis
```

### 6.2 Minikube

```bash
# Backup PVCs
kubectl get pvc -n matrix
# Use Velero or manual kubectl cp

# Backup ConfigMaps
kubectl get configmap -n matrix -o yaml > configmaps-backup.yaml

# Backup Secrets
kubectl get secrets -n matrix -o yaml > secrets-backup.yaml
```

---

## 7. Security

### 7.1 Network Security

```yaml
# docker-compose.yml
services:
  matrix-core:
    networks:
      - internal
    ports:
      - "127.0.0.1:8080:8080"  # Только localhost

networks:
  internal:
    driver: bridge
```

### 7.2 Secrets Management

```bash
# Docker secrets
echo "my-secret-password" | docker secret create pg_password -

# Kubernetes secrets
kubectl create secret generic matrix-secrets \
  --from-literal=postgres-password=my-secret \
  --from-literal=telegram-token=my-token \
  -n matrix
```

### 7.3 TLS/SSL

Для production используйте reverse proxy (nginx, Traefik):

```nginx
server {
    listen 443 ssl;
    server_name matrix.example.com;

    ssl_certificate /etc/ssl/certs/matrix.crt;
    ssl_certificate_key /etc/ssl/private/matrix.key;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /api/v1/agent/ws {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

---

*Конец DEPLOYMENT.md — v3.1, 2026-07-13*
