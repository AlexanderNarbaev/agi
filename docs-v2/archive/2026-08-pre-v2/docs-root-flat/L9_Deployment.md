# L9 — Руководство по развёртыванию и эксплуатации

**Дата:** 2026-06-01
**Статус:** Утверждён
**Назначение:** Определить процедуры упаковки, развёртывания, оркестрации и масштабирования системы МАТРИЦА в Kubernetes-окружении, включая локальную разработку и управление жизненным циклом через Kubernetes Operator.

---

## 1. Введение

### 1.1. Назначение документа

Настоящий документ описывает, как компоненты МАТРИЦЫ — от инфраструктурных сервисов (Kafka, MinIO) до инстансов приложения — упаковываются, разворачиваются и управляются в production- и dev-средах. Он дополняет архитектурные спецификации L0–L8 практическими инструкциями по эксплуатации.

### 1.2. Принципы развёртывания

- **Инфраструктура как код (IaC):** все манифесты, конфигурации и операторы хранятся в репозитории и разворачиваются автоматически.
- **Горизонтальная масштабируемость:** инстансы МАТРИЦЫ масштабируются по нагрузке с помощью HPA и VPA.
- **Самовосстановление:** Kubernetes автоматически перезапускает упавшие Pods; состояние восстанавливается из журналов событий и снапшотов (L6).
- **Безопасность по умолчанию:** взаимодействие между компонентами шифруется, доступ ограничен сетевыми политиками.
- **Локальная разработка:** полный цикл можно запустить на minikube или kind с ограниченными ресурсами.

---

## 2. Инфраструктурные компоненты

### 2.1. Обзор

Внешние сервисы, необходимые для работы МАТРИЦЫ, разворачиваются как Kubernetes-native операторы или StatefulSet'ы:

| Компонент | Способ развёртывания | Назначение |
|-----------|----------------------|------------|
| Apache Kafka | Strimzi Operator | Обмен сигналами, журнал событий, консенсус |
| MinIO | MinIO Operator | Хранение снапшотов `.ldn`, FNL, глобальное хранилище |
| Apache Ignite | Ignite Operator (или StatefulSet) | Распределённый кеш для реестра Ноосферы |
| PostgreSQL (опционально) | Cloud Native PostgreSQL Operator | Event Sourcing через Pekko Persistence JDBC |

### 2.2. Strimzi (Kafka)

```yaml
# kafka-cluster.yaml
apiVersion: kafka.strimzi.io/v1beta2
kind: Kafka
metadata:
  name: matrix-kafka
spec:
  kafka:
    replicas: 3
    listeners:
      - name: plain
        port: 9092
        type: internal
        tls: false
      - name: tls
        port: 9093
        type: internal
        tls: true
    config:
      auto.create.topics.enable: "false"
      log.retention.hours: 72
  entityOperator:
    topicOperator: {}
    userOperator: {}
```

### 2.3. MinIO Tenant

```yaml
# minio-tenant.yaml
apiVersion: minio.min.io/v2
kind: Tenant
metadata:
  name: matrix-minio
spec:
  pools:
    - servers: 4
      volumesPerServer: 2
      volumeClaimTemplate:
        spec:
          accessModes: [ "ReadWriteOnce" ]
          resources:
            requests:
              storage: 1Ti
  requestAutoCert: true
```

### 2.4. Запуск инфраструктуры

```bash
kubectl apply -f kafka-cluster.yaml
kubectl apply -f minio-tenant.yaml
```

---

## 3. Упаковка инстанса МАТРИЦЫ

### 3.1. Docker-образ

Используется многоступенчатая сборка с GraalVM Native Image.

```dockerfile
FROM ghcr.io/graalvm/graalvm-ce:ol9-java25-23.1.0 AS build
WORKDIR /app
COPY . .
RUN ./gradlew build -Dquarkus.package.type=native

FROM quay.io/quarkus/quarkus-micro-image:2.0
WORKDIR /app
COPY --from=build /app/build/*-runner /app/runner
COPY config/application.properties /app/config/
RUN mkdir /data
EXPOSE 8080 25520
ENTRYPOINT ["/app/runner", "-Dquarkus.http.host=0.0.0.0"]
```

### 3.2. Конфигурация

`application.properties` содержит параметры подключения к Kafka, MinIO, настройки Pekko Cluster. Чувствительные данные вынесены в Kubernetes Secrets.

```properties
quarkus.http.port=8080
kafka.bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
minio.url=${MINIO_URL:http://minio:9000}
pekko.cluster.seed-nodes=${PEKKO_SEED_NODES:akka://matrix@localhost:25520}
```

---

## 4. Оркестрация в Kubernetes

### 4.1. Deployment (NeuronClusterActor)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: matrix-neuron
spec:
  replicas: 3
  selector:
    matchLabels:
      app: matrix-neuron
  template:
    metadata:
      labels:
        app: matrix-neuron
    spec:
      containers:
      - name: instance
        image: matrix-instance:0.1.0
        ports:
        - containerPort: 8080
        - containerPort: 25520
        env:
        - name: KAFKA_BOOTSTRAP_SERVERS
          valueFrom:
            secretKeyRef:
              name: matrix-secrets
              key: kafka-bootstrap
        - name: PEKKO_SEED_NODES
          value: "akka://matrix@matrix-neuron-0.matrix-neuron:25520"
        readinessProbe:
          httpGet:
            path: /q/health/ready
            port: 8080
        livenessProbe:
          httpGet:
            path: /q/health/live
            port: 8080
        volumeMounts:
        - name: data
          mountPath: /data
      volumes:
      - name: data
        persistentVolumeClaim:
          claimName: matrix-data-pvc
```

### 4.2. Service и Headless Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: matrix-neuron
spec:
  ports:
  - port: 8080
    name: http
  - port: 25520
    name: pekko
  clusterIP: None  # headless для Pekko Cluster
  selector:
    app: matrix-neuron
```

---

## 5. Kubernetes Operator для МАТРИЦЫ

### 5.1. Цель

Kubernetes Operator инкапсулирует знания о жизненном цикле системы: создание/удаление кластеров, масштабирование, обновление конфигурации, бэкап и восстановление. Он управляет Custom Resources (CR), а не отдельными подами вручную.

### 5.2. Custom Resource Definition (CRD)

#### MatrixCluster

Описывает целый экземпляр системы (один или несколько инстансов).

```yaml
apiVersion: matrix.io/v1
kind: MatrixCluster
metadata:
  name: my-matrix
spec:
  neuronReplicas: 3
  mediatorReplicas: 1
  kafkaBootstrap: matrix-kafka-kafka-bootstrap:9092
  minioEndpoint: http://minio:9000
  storage:
    dataSize: 10Gi
    snapshotBucket: matrix-snapshots
  monitoring:
    enablePrometheus: true
```

#### MatrixLobe (FNL)

Загрузка специализированной доли в кластер.

```yaml
apiVersion: matrix.io/v1
kind: MatrixLobe
metadata:
  name: vision-fnl
spec:
  clusterRef: my-matrix
  source:
    bucket: matrix-fnl
    key: vision/v1.0.ldn
  replicas: 2
```

### 5.3. Логика оператора (упрощённый контроллер)

Оператор следит за CR `MatrixCluster` и выполняет:

1. Создаёт Deployment для нейронов (`NeuronClusterActor`) и отдельный для InstanceMediator.
2. Настраивает ConfigMap и Secret.
3. При изменении `neuronReplicas` обновляет Deployment.
4. При удалении CR удаляет связанные ресурсы.
5. Управляет бэкапом: периодически создаёт снапшоты в MinIO через Job.

### 5.4. Реализация оператора

Оператор можно реализовать на Java (Quarkus + fabric8 Kubernetes Client) или Go (kubebuilder). Для MVP рекомендуется использовать fabric8, так как стек уже Java.

```java
@Controller
public class MatrixClusterController implements Reconciler<MatrixCluster> {
    @Override
    public UpdateControl<MatrixCluster> reconcile(MatrixCluster cluster, Context context) {
        // Создать или обновить Deployment для нейронов
        // Создать Service
        // Создать PVC
        return UpdateControl.updateStatus(cluster);
    }
}
```

---

## 6. Автомасштабирование

### 6.1. HorizontalPodAutoscaler (HPA)

Масштабирование по CPU и пользовательской метрике `matrix_signal_rate` (количество сигналов в секунду).

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: matrix-neuron-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: matrix-neuron
  minReplicas: 2
  maxReplicas: 100
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Pods
    pods:
      metric:
        name: matrix_signal_rate
      target:
        type: AverageValue
        averageValue: "10000"
```

### 6.2. VerticalPodAutoscaler (VPA)

VPA анализирует историческое потребление ресурсов нейронами и рекомендует/автоматически корректирует requests/limits.

```yaml
apiVersion: autoscaling.k8s.io/v1
kind: VerticalPodAutoscaler
metadata:
  name: matrix-neuron-vpa
spec:
  targetRef:
    apiVersion: "apps/v1"
    kind: Deployment
    name: matrix-neuron
  updatePolicy:
    updateMode: "Auto"
```

**Примечание:** VPA перезапускает под при изменении ресурсов, поэтому для stateless-компонентов (которые быстро восстанавливают состояние) это допустимо. Для акторов с долгой репликацией событий лучше использовать VPA в режиме `Off` (только рекомендации) и применять их при следующем деплое.

### 6.3. Cluster Autoscaler

При росте количества подов выше доступных ресурсов узлов автоматически добавляет новые узлы в кластер (поддерживается в EKS, GKE, AKS).

---

## 7. Метрики и мониторинг

### 7.1. Экспорт метрик

Quarkus приложение использует расширение `quarkus-micrometer-registry-prometheus`. Все стандартные метрики (HTTP, JVM) дополняются кастомными:

| Метрика | Тип | Описание |
|---------|-----|----------|
| `matrix_neuron_evaluate_duration_seconds` | Histogram | Время выполнения evaluate |
| `matrix_signal_rate` | Gauge | Сигналов/сек через кластер |
| `matrix_neuron_count` | Gauge | Количество активных нейронов |
| `matrix_accuracy` | Gauge | Средняя точность предсказаний |
| `matrix_hades_count` | Counter | Число срабатываний HADES |
| `matrix_ethical_violations` | Counter | Нарушений Этического фильтра |

### 7.2. Grafana дашборд (образец)

Панели:
- Пропускная способность сигналов по кластерам.
- Задержка инференса (p50, p99).
- Количество мутаций в минуту.
- Статус HADES и Derangement.
- Потребление ресурсов подами.

---

## 8. Локальная разработка

### 8.1. Minikube

```bash
minikube start --cpus 4 --memory 8192
# Развернуть инфраструктуру
kubectl apply -f kafka-cluster.yaml
kubectl apply -f minio-tenant.yaml
# Собрать образ локально
eval $(minikube docker-env)
./gradlew build -Dquarkus.container-image.build=true
# Применить оператор
kubectl apply -f operator/
# Создать экземпляр
kubectl apply -f examples/my-matrix.yaml
```

### 8.2. Kind (Kubernetes in Docker)

```bash
kind create cluster --config kind-config.yaml
# аналогично применить манифесты
```

**kind-config.yaml** для проброса портов:

```yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
- role: control-plane
  extraPortMappings:
  - containerPort: 30080
    hostPort: 8080
```

---

## 9. Сетевая безопасность и надёжная связь

### 9.1. Network Policies

Ограничиваем входящий трафик: NeuronClusterActor принимает только от InstanceMediator и других Neuron-подов.

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: matrix-neuron-netpol
spec:
  podSelector:
    matchLabels:
      app: matrix-neuron
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: matrix-mediator
    - podSelector:
        matchLabels:
          app: matrix-neuron
  policyTypes:
  - Ingress
```

### 9.2. mTLS

Внутри кластера можно включить mTLS через Istio или Linkerd. Для простоты начинаем с Istio sidecar injection и PeerAuthentication.

```yaml
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: matrix-mtls
spec:
  mtls:
    mode: STRICT
```

---

## 10. Резервное копирование и восстановление

- **Снапшоты состояния:** создаются периодически (CronJob) с использованием CLI-утилиты `matrix-ctl snapshot create`.
- **Журнал событий Kafka:** retention настроен на 72 часа; для долгосрочного хранения используется коннектор Kafka → MinIO.
- **Восстановление:** при старте пода загружается последний снапшот из MinIO, затем проигрываются события из Kafka, начиная с offset'а, сохранённого в снапшоте.

---

## 11. Заключение

Документ L9 предоставляет полный план развёртывания системы МАТРИЦА в production- и dev-окружениях с использованием Kubernetes, оператора для автоматизации управления жизненным циклом, HPA/VPA для автоматического масштабирования и встроенных метрик для мониторинга. Это делает систему готовой к реальной эксплуатации и дальнейшему развитию.

---

## Quick Start with minikube

### Prerequisites
- minikube v1.34+
- kubectl v1.32+
- Docker (for building images)

### Step-by-step

```bash
# 1. Start minikube
minikube start --cpus 4 --memory 8192

# 2. Build Docker image locally
eval $(minikube docker-env)
./gradlew :matrix-core:quarkusBuild -Dquarkus.package.jar.type=uber-jar
docker build -t ghcr.io/alexandernarbaev/matrix-core:latest .

# 3. Deploy flat manifests (namespace, deployment, service, HPA)
kubectl apply -k infra/k8s/

# 4. Wait for pods to be ready
kubectl wait --for=condition=ready pod -l app=matrix-core -n matrix --timeout=120s

# 5. Port-forward for local access
kubectl port-forward svc/matrix-core 9091:9091 -n matrix &

# 6. Verify health
curl http://localhost:9091/q/health/ready
curl http://localhost:9091/q/health/live

# 7. Check metrics
curl http://localhost:9091/metrics
```

### Deploy with Operator (advanced)

```bash
# 1. Apply CRD and RBAC
kubectl apply -f infra/k8s/base/crd.yaml
kubectl apply -f infra/k8s/base/rbac.yaml

# 2. Apply operator Deployment (if using in-cluster operator)
# or run locally for development
./gradlew :matrix-operator:run

# 3. Create a MatrixCluster instance
kubectl apply -f infra/k8s/base/cr-example.yaml

# 4. Watch cluster status
kubectl get matrixclusters -n matrix -w
kubectl describe matrixcluster my-matrix -n matrix
```

### Full infrastructure stack (base kustomization)

```bash
# Deploy all infrastructure: Kafka, MinIO, monitoring, operator CRD
kubectl apply -k infra/k8s/base/

# Check all pods
kubectl get pods -n matrix -w
```

### Useful commands

```bash
# View logs
kubectl logs -l app=matrix-core -n matrix -f

# Scale manually
kubectl scale deployment matrix-core -n matrix --replicas=5

# Check HPA status
kubectl get hpa -n matrix

# Delete everything
kubectl delete namespace matrix
```
