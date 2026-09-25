# MATRIX Deployment (Wave T-09)

Production deployment artifacts for MATRIX Hybrid Neuro-Symbolic AI.

## Structure

```
deploy/
├── docker/                    # Multi-stage Dockerfiles
│   ├── Dockerfile.api-gateway  # Quarkus REST + native compile
│   └── Dockerfile.web-ui       # Next.js 14
├── helm/matrix/                # Helm chart for K8s
│   ├── Chart.yaml
│   ├── values.yaml
│   └── templates/
│       ├── deployment.yaml
│       ├── service.yaml
│       ├── secret.yaml
│       └── hpa.yaml
├── grafana/                    # Grafana dashboard
│   └── matrix-dashboard.json
├── prometheus/                 # Prometheus config
│   ├── prometheus.yml          # scrape config
│   └── matrix-alerts.yml       # alert rules
├── docker-compose.yml          # Local dev stack
└── README.md                   # this file
```

## Local Development

```bash
docker compose -f deploy/docker-compose.yml up
```

Services:
- matrix-api-gateway → http://localhost:8080
- matrix-web-ui → http://localhost:3000
- prometheus → http://localhost:9090
- grafana → http://localhost:3001 (admin/admin)
- alertmanager → http://localhost:9093

## Production Deployment

```bash
# Build & push images
docker build -f deploy/docker/Dockerfile.api-gateway -t alexandernarbaev/matrix-api-gateway:0.1.0 .
docker build -f deploy/docker/Dockerfile.web-ui -t alexandernarbaev/matrix-web-ui:0.1.0 .
docker push alexandernarbaev/matrix-api-gateway:0.1.0
docker push alexandernarbaev/matrix-web-ui:0.1.0

# Deploy to K8s
helm upgrade --install matrix-prod deploy/helm/matrix \
  --namespace matrix-prod --create-namespace \
  --set image.tag=0.1.0 \
  --set secrets.stripeWebhookSecret=$STRIPE_WEBHOOK_SECRET \
  --set secrets.licenseSigningSecret=$LICENSE_SIGNING_SECRET \
  --wait --timeout 10m
```

## Alerts

Defined in `prometheus/matrix-alerts.yml`:
- `MatrixAuditChainTampered` (critical) — hash chain integrity lost
- `MatrixHighLatency` (critical) — p95 > 1s for 5min
- `MatrixFederationDegraded` (warning) — < 3 nodes for 10min
- `MatrixRateLimitSpike` (warning) — > 10 req/s rejected
- `MatrixNoTraffic` (critical) — no requests for 5min (outage)
- `MatrixGdprErasureSpike` (warning) — > 50 erasures/hour

## CI/CD

`.github/workflows/ci.yml` orchestrates:
1. Build + Test (Java modules)
2. Security scan (SpotBugs, TruffleHog, OWASP)
3. Build + push Docker images
4. Deploy to staging (on develop)
5. Deploy to production (on main, manual approval)
