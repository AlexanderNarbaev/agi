# M.A.T.R.I.X. — Migration & Portability Guide

**Version:** v3.58
**Last updated:** 2026-07-26

---

## Overview

This guide covers migrating M.A.T.R.I.X. between environments (dev → staging → production), backing up state, and porting to new infrastructure.

---

## 1. Environment Tiers

| Tier | Purpose | Resources | Notes |
|------|---------|-----------|-------|
| **Local Dev** | Development, testing | Docker Compose, single host | Recommended for getting started |
| **Minikube** | Integration testing | Minikube 4 CPU, 8GB RAM | All services in K8s |
| **Staging** | Pre-production | 3+ node K8s cluster | Production-like |
| **Production** | Live deployment | HA K8s cluster, 3+ replicas | Full monitoring |

---

## 2. Data Migration

### 2.1. What Needs to be Migrated

| Data Type | Location | Critical? | Backup Method |
|-----------|----------|-----------|---------------|
| PostgreSQL data | `/var/lib/postgresql/data` (container) | YES | pg_dump |
| Redis cache | `/data` (container) | NO | Optional |
| Kafka topics | `/tmp/kraft-combined-logs` (container) | YES | Kafka tools |
| MinIO data | `/data` (container) | YES | mc client |
| Pretrained models | `models/` (git) | NO | Git |
| Training data | `models/training_data/` (git) | NO | Git |
| Conversation logs | `data/conversations/` (runtime) | NO | Volume |
| Neuron snapshots | `models/merged/` (runtime) | YES | Backup script |

### 2.2. PostgreSQL Migration

```bash
# Export
docker exec agi-postgres-1 pg_dump -U matrix -d matrix > backup_$(date +%Y%m%d).sql

# Import
cat backup_20260726.sql | docker exec -i agi-postgres-1 psql -U matrix -d matrix
```

### 2.3. Redis Migration

```bash
# Export
docker exec agi-redis-1 redis-cli SAVE
docker cp agi-redis-1:/data/dump.rdb ./redis_backup.rdb

# Import
docker cp ./redis_backup.rdb agi-redis-1:/data/dump.rdb
docker compose restart redis
```

### 2.4. Kafka Migration

```bash
# List topics
docker exec agi-kafka-1 /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

# Export topic
docker exec agi-kafka-1 /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic matrix-events --from-beginning > events.json

# Import topic
docker exec -i agi-kafka-1 /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic matrix-events < events.json
```

### 2.5. MinIO Migration

```bash
# Install mc client
docker exec agi-minio-1 mc alias set local http://localhost:9000 minioadmin minioadmin

# Export bucket
docker exec agi-minio-1 mc mirror local/matrix-models ./backup/

# Import bucket
docker exec agi-minio-1 mc mirror ./backup/ local/matrix-models
```

### 2.6. Neuron Snapshots (Critical Knowledge)

```bash
# Backup
tar czf neurons_$(date +%Y%m%d).tar.gz matrix-core/models/merged/

# Restore
tar xzf neurons_20260726.tar.gz
```

---

## 3. Application Migration

### 3.1. Code Migration

The code is in Git. To migrate:

```bash
# Clone on new host
git clone https://github.com/AlexanderNarbaev/agi.git
cd agi

# Or pull latest
git pull origin main
```

### 3.2. Build Once, Run Anywhere

Build the Quarkus app and copy the artifact:

```bash
# Build
./gradlew :matrix-core:build

# Copy
scp matrix-core/build/quarkus-app/quarkus-run.jar user@new-host:/app/

# Run on new host
ssh user@new-host "cd /app && java -jar quarkus-run.jar"
```

### 3.3. Docker Image Build

```bash
# Build Docker image
docker build -f Dockerfile -t matrix-core:v3.58 .

# Tag for registry
docker tag matrix-core:v3.58 your-registry/matrix-core:v3.58

# Push
docker push your-registry/matrix-core:v3.58

# Pull on new host
docker pull your-registry/matrix-core:v3.58
```

---

## 4. Cloud Migration

### 4.1. AWS

```bash
# Build and push to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <account>.dkr.ecr.us-east-1.amazonaws.com
docker build -t matrix-core .
docker tag matrix-core:latest <account>.dkr.ecr.us-east-1.amazonaws.com/matrix-core:v3.58
docker push <account>.dkr.ecr.us-east-1.amazonaws.com/matrix-core:v3.58

# Use RDS for PostgreSQL, ElastiCache for Redis, MSK for Kafka
```

### 4.2. Google Cloud

```bash
# Push to GCR
gcloud auth configure-docker
docker tag matrix-core:v3.58 gcr.io/your-project/matrix-core:v3.58
docker push gcr.io/your-project/matrix-core:v3.58

# Use Cloud SQL, Memorystore, Pub/Sub
```

### 4.3. Azure

```bash
# Push to ACR
az acr login --name yourregistry
docker tag matrix-core:v3.58 yourregistry.azurecr.io/matrix-core:v3.58
docker push yourregistry.azurecr.io/matrix-core:v3.58

# Use Azure Database, Cache, Event Hubs
```

### 4.4. Self-Hosted Bare Metal

```bash
# Build native image
./gradlew :matrix-core:build -Dquarkus.native.enabled=true

# Copy binary
scp matrix-core/build/*-runner user@server:/app/matrix-core

# Run as systemd service
sudo systemctl enable /etc/systemd/system/matrix-core.service
sudo systemctl start matrix-core
```

---

## 5. Kubernetes Migration

### 5.1. From Minikube to Production

```bash
# Export configurations
kubectl get all,configmap,secret,pvc -n matrix -o yaml > k8s_state.yaml

# Apply to production cluster
kubectl --context=production-context apply -f k8s_state.yaml
```

### 5.2. Using Helm

```bash
# Create Helm chart
helm create matrix-core
# Edit values.yaml with your config
helm install matrix-core ./matrix-core --namespace matrix
```

### 5.3. Persistent Volume Migration

```bash
# Backup PVC
kubectl get pvc -n matrix
kubectl cp matrix/matrix-core-pod:/data ./pvc_backup.tar.gz

# Restore on new cluster
kubectl cp ./pvc_backup.tar.gz matrix/matrix-core-pod:/data
```

---

## 6. Version Upgrades

### 6.1. Minor Version Upgrade (v3.58 → v3.59)

```bash
# Stop application
docker compose down matrix-core  # or kubectl scale --replicas=0

# Backup
./scripts/backup.sh

# Update code
git pull origin main

# Rebuild
./gradlew :matrix-core:build

# Migrate data (if schema changed)
./gradlew :matrix-core:quarkusRun -- --matrix migrate

# Restart
docker compose up -d matrix-core
```

### 6.2. Major Version Upgrade (v3.x → v4.0)

Read [MIGRATION_GUIDE_v4.md](docs/MIGRATION_GUIDE_v4.md) (when published).

---

## 7. Disaster Recovery

### 7.1. Full System Backup

```bash
#!/bin/bash
# scripts/backup.sh

BACKUP_DIR="backups/$(date +%Y%m%d_%H%M%S)"
mkdir -p $BACKUP_DIR

# PostgreSQL
docker exec agi-postgres-1 pg_dump -U matrix -d matrix > $BACKUP_DIR/postgres.sql

# Redis snapshot
docker exec agi-redis-1 redis-cli SAVE
docker cp agi-redis-1:/data/dump.rdb $BACKUP_DIR/redis.rdb

# Neuron snapshots
tar czf $BACKUP_DIR/neurons.tar.gz matrix-core/models/merged/

# Conversation logs
tar czf $BACKUP_DIR/conversations.tar.gz matrix-core/data/

# Training data
tar czf $BACKUP_DIR/training_data.tar.gz matrix-core/models/training_data/

# Code snapshot
git archive --format=tar.gz HEAD > $BACKUP_DIR/code.tar.gz

echo "Backup complete: $BACKUP_DIR"
```

### 7.2. Full System Restore

```bash
#!/bin/bash
# scripts/restore.sh

BACKUP_DIR=$1

# PostgreSQL
cat $BACKUP_DIR/postgres.sql | docker exec -i agi-postgres-1 psql -U matrix -d matrix

# Redis
docker cp $BACKUP_DIR/redis.rdb agi-redis-1:/data/dump.rdb
docker compose restart redis

# Neurons
tar xzf $BACKUP_DIR/neurons.tar.gz

# Conversations
tar xzf $BACKUP_DIR/conversations.tar.gz

# Training data
tar xzf $BACKUP_DIR/training_data.tar.gz

# Code (if needed)
tar xzf $BACKUP_DIR/code.tar.gz

echo "Restore complete"
```

---

## 8. Monitoring During Migration

- Watch `http://localhost:9091/q/health` (or K8s URL)
- Monitor `http://localhost:9091/q/metrics` for Prometheus
- Check Grafana dashboards: `http://localhost:30300`
- Check Jaeger traces: `http://localhost:31686`

---

## 9. Rollback Plan

If something goes wrong after migration:

```bash
# 1. Stop new version
docker compose down
# or
kubectl scale deployment matrix-core -n matrix --replicas=0

# 2. Restore from backup
./scripts/restore.sh backups/20260725/

# 3. Restart old version
git checkout v3.57  # or previous version
./gradlew :matrix-core:quarkusDev
```

---

## 10. Pre-Migration Checklist

- [ ] Full backup completed
- [ ] All tests pass
- [ ] Documentation updated
- [ ] WAL files current
- [ ] Monitoring alerts configured
- [ ] Rollback plan documented
- [ ] Team notified
- [ ] Maintenance window scheduled

---

*Last updated: 2026-07-26 | Version: v3.58*
