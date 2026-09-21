# M.A.T.R.I.X. — Operations Runbook

**Version:** v3.58
**Last updated:** 2026-07-26

---

## Daily Operations

### Health Check
```bash
# Local
curl http://localhost:9091/q/health

# Kubernetes
curl http://192.168.49.2:30091/q/health
```

Expected: `{"status":"UP",...}`

### View Logs
```bash
# Local (Quarkus dev mode logs to console)
# Use `tail -f` or IDE console

# Docker
docker compose logs -f matrix-core

# Kubernetes
kubectl logs -n matrix -f deployment/matrix-core
```

### Check Training Status
```bash
curl http://localhost:9091/v1/chat/status
```

### Check Evolution Status
```bash
curl http://localhost:9091/v1/chat/status/train
```

---

## Weekly Operations

### Check Disk Usage
```bash
# Docker
docker system df

# Kubernetes
kubectl exec -n matrix -it <pod> -- df -h
```

### Backup Knowledge
```bash
# Backup neurons
tar czf /backup/neurons_$(date +%Y%m%d).tar.gz matrix-core/models/merged/

# Backup training data
tar czf /backup/training_$(date +%Y%m%d).tar.gz matrix-core/models/training_data/
```

### Update Pretrained Models
```bash
# Via API
curl -X POST http://localhost:9091/api/v1/import/all
```

---

## Monitoring

### Metrics (Prometheus)
```
http://localhost:9091/q/metrics
```

### Dashboards (Grafana)
```
http://localhost:30300
```

Default credentials: admin/admin (change in production)

### Traces (Jaeger)
```
http://localhost:31686
```

### Key Metrics to Monitor
- `matrix_neurons_count` — total active neurons
- `matrix_evolution_generation` — current generation
- `matrix_training_cycles_total` — training cycles
- `matrix_api_requests_total` — API usage
- `matrix_frozen_violations_total` — FROZEN violations (should be 0)
- `matrix_consensus_failures_total` — consensus failures
- `matrix_rag_searches_total` — RAG usage
- `matrix_chat_completions_total` — chat API usage

---

## Incident Response

### Application Crash
1. Check logs: `docker compose logs matrix-core` or `kubectl logs`
2. Check health: `curl /q/health`
3. Check resources: `docker stats` or `kubectl top pods`
4. Restart: `docker compose restart matrix-core` or `kubectl rollout restart`

### Out of Memory
1. Check heap: `jcmd <PID> GC.heap_info`
2. Increase heap: Set `JAVA_OPTS=-Xmx8g`
3. Check for memory leaks in recent code

### High CPU
1. Check evolution/training load
2. Scale up: `kubectl scale deployment matrix-core --replicas=5`
3. Consider reducing evolution population size

### Database Connection Issues
1. Check PostgreSQL: `docker compose ps postgres`
2. Check connection pool: see metrics
3. Restart database: `docker compose restart postgres`

### Kafka Issues
1. Check Kafka: `docker compose ps kafka`
2. Check topics: `kafka-topics.sh --bootstrap-server localhost:9092 --list`
3. Check lag: `kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list`

---

## Scaling

### Vertical Scaling
```bash
# Increase CPU/memory in docker-compose.yml
# Or for K8s, edit resources in deployment
kubectl edit deployment matrix-core -n matrix
```

### Horizontal Scaling
```bash
# Docker Compose
docker compose up -d --scale matrix-core=3

# Kubernetes
kubectl scale deployment matrix-core -n matrix --replicas=5
```

### Auto-scaling
```bash
# HPA already configured in infra/k8s/base/hpa.yaml
kubectl autoscale deployment matrix-core -n matrix --min=2 --max=10 --cpu-percent=80
```

---

## Maintenance Windows

### Scheduled Tasks
- **Daily:** Health checks, log review
- **Weekly:** Backups, metrics review
- **Monthly:** Full system audit, performance review
- **Quarterly:** Major version upgrade planning

### Update Procedure
1. Announce maintenance window (24h notice minimum)
2. Stop accepting new requests (drain load balancer)
3. Backup all data
4. Pull latest code: `git pull origin main`
5. Rebuild: `./gradlew :matrix-core:build`
6. Restart: `docker compose restart matrix-core`
7. Verify: `curl /q/health`
8. Monitor: `docker compose logs -f`

---

## Security

### FROZEN Constraints
**NEVER modify:**
- FROZEN neurons in `io.matrix.ethics.FrozenEthicalFNL`
- `K_MAX = 20` in `TruthTable.java`
- Three prohibitions (NO_KILLING, NO_TORTURE, NO_ENSLAVEMENT)
- AGPL license + ethical restrictions

### Access Control
- API endpoints require authentication (configure in production)
- Database access: limited to matrix user
- Redis: password-protected in production
- Kafka: SASL/SSL in production

### Audit Trail
All decisions logged to event journal:
- Training events
- Evolution events
- API calls
- FROZEN violations (should be 0)
- Consensus decisions

---

## Troubleshooting Quick Reference

| Issue | Check | Fix |
|-------|-------|-----|
| Health DOWN | `/q/health` | See specific failed check |
| 503 errors | API logs | Restart service |
| Slow response | Metrics | Scale up |
| Memory leak | JVM heap dump | Add to crash report |
| Disk full | `df -h` | Cleanup logs, archive data |
| High latency | Jaeger traces | Optimize slow paths |

---

## Contacts

- **Repository:** https://github.com/AlexanderNarbaev/agi
- **Issues:** GitHub Issues
- **Author:** Alexander Narbaev
- **License:** AGPLv3 + Ethical Restrictions

---

*Last updated: 2026-07-26 | Version: v3.58*
