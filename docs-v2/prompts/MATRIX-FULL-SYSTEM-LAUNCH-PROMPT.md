# MATRIX Production Ecosystem — Full System Launch Prompt

## Context & Current State

You are operating in the `/home/alexandr-narbaev/Projects/agi` repository where **10 Transformation Waves (T-01 → T-10)** have been completed, transforming the MATRIX research civilization (W1500, 1,059+ tests) into a production-ready global ecosystem.

**Verified Components Ready for Launch:**
- ✅ **matrix-api-gateway**: Quarkus REST API with JWT auth, RBAC (3 tiers), rate limiting, OWASP validation, OpenAPI spec
- ✅ **matrix-core**: BIR/HDC/MCTS hybrid inference engine, FROZEN modulators, omni-modal transcoders
- ✅ **matrix-sdk-java**: Typed client with async/streaming support
- ✅ **matrix-audit**: Immutable hash-chained logging, GDPR pruner, compliance reporter
- ✅ **matrix-billing**: Credit ledger, license validation, Stripe webhook handler
- ✅ **matrix-observability**: Prometheus metrics, health checks, alert dispatcher
- ✅ **matrix-quality**: 14 Goal Guard review gates + CLI orchestrator
- ✅ **matrix-web-ui**: Next.js landing page + XAI dashboard (separate Node.js toolchain)
- ✅ **Pilots**: smart-home-agent, edu-assessor, compliance-bot
- ✅ **Deploy artifacts**: Dockerfiles, Helm chart, docker-compose.yml, Grafana/Prometheus configs
- ✅ **Documentation**: 32 bilingual pages (RU/EN) in docs-v2/ecosystem/
- ✅ **CI/CD**: GitHub Actions pipeline (build → test → security → docker → deploy)

**System Requirements:**
- Java 25 GraalVM CE (`~/.sdkman/candidates/java/25.0.2-graalce`)
- Node.js 18+ (for matrix-web-ui)
- Docker + Docker Compose
- PostgreSQL 15+ (or use embedded Dev Services)
- Redis (for rate limiting, or use embedded)
- 8GB+ RAM available

---

## Objective

Launch the complete MATRIX Production Ecosystem locally with:
1. **All backend services** running (API Gateway + Core + supporting modules)
2. **Database + Redis** initialized
3. **Frontend dashboard** accessible at `http://localhost:3000`
4. **API endpoints** available at `http://localhost:8080`
5. **Real-time metrics** visible in Grafana at `http://localhost:3001`
6. **Interactive chat interface** connected to live MATRIX instance
7. **Audit logs** streaming in real-time
8. **Billing/subscription system** active with test mode

Then provide the user with:
- Direct chat interface URL
- API playground URL (Swagger UI)
- Grafana dashboard URL
- Sample curl commands for testing
- Test credentials for each tier (FREE/PRO/ENTERPRISE)
- Live metric snapshots every 30 seconds

---

## Execution Plan

### Phase 1: Pre-Launch Validation (5 minutes)

**Step 1.1: Verify all modules compile**
```bash
./gradlew clean build -x test --no-daemon
```
Expected: All 12 Gradle modules compile successfully (matrix-web-ui excluded)

**Step 1.2: Run full test suite**
```bash
./gradlew test --no-daemon --rerun-tasks
```
Expected: 1,200+ tests passing (research core + transformation waves)

**Step 1.3: Validate documentation parity**
```bash
./scripts/validate-docs.sh
```
Expected: RU/EN parity confirmed, no broken links

**Step 1.4: Check Goal Guard status**
```bash
./gradlew :matrix-quality:run --args="orchestrate --format=text"
```
Expected: All 14 gates pass, quality score > 90%

**Step 1.5: Verify Docker environment**
```bash
docker --version && docker compose version
```
Expected: Docker 24+, Compose v2.20+

---

### Phase 2: Infrastructure Startup (10 minutes)

**Step 2.1: Start PostgreSQL + Redis via Docker Compose**
```bash
cd /home/alexandr-narbaev/Projects/agi/deploy
docker compose up -d postgres redis
```
Wait for healthy status:
```bash
docker compose ps
# postgres: healthy
# redis: healthy
```

**Step 2.2: Initialize database schema**
```bash
docker compose exec postgres psql -U matrix -d matrix_db -c "CREATE TABLE IF NOT EXISTS schema_migrations (...);"
# Or run Flyway/Liquibase migration if configured
```

**Step 2.3: Seed test data**
Create `seed-data.sql`:
```sql
-- Test users (3 tiers)
INSERT INTO users (id, email, password_hash, tier, api_key) VALUES
  ('usr_free_001', 'free@test.com', '$2a$10$...', 'FREE', 'sk_test_free_abc123'),
  ('usr_pro_001', 'pro@test.com', '$2a$10$...', 'PRO', 'sk_test_pro_xyz789'),
  ('usr_ent_001', 'enterprise@test.com', '$2a$10$...', 'ENTERPRISE', 'sk_test_ent_qwe456');

-- Test credit balances
INSERT INTO credit_ledger (user_id, balance_cents, currency) VALUES
  ('usr_free_001', 0, 'USD'),
  ('usr_pro_001', 4900, 'USD'),
  ('usr_ent_001', 100000, 'USD');

-- Test licenses
INSERT INTO licenses (key, type, status, expires_at) VALUES
  ('LIC-FREE-TEST-001', 'FREE', 'ACTIVE', NOW() + INTERVAL '30 days'),
  ('LIC-PRO-TEST-001', 'PRO', 'ACTIVE', NOW() + INTERVAL '365 days'),
  ('LIC-ENT-TEST-001', 'ENTERPRISE', 'ACTIVE', NOW() + INTERVAL '365 days');
```

Run:
```bash
docker compose exec postgres psql -U matrix -d matrix_db -f /docker-entrypoint-initdb.d/seed-data.sql
```

---

### Phase 3: Backend Services Launch (15 minutes)

**Step 3.1: Build native API Gateway image (GraalVM)**
```bash
cd /home/alexandr-narbaev/Projects/agi
docker build -f deploy/docker/Dockerfile.api-gateway -t matrix-api-gateway:latest .
```
Expected: 126MB native image (check logs for "Image build successful")

**Step 3.2: Start API Gateway with dev profile**
```bash
docker compose up -d api-gateway
```
Wait for startup:
```bash
docker compose logs -f api-gateway | grep "Quarkus"
# Expected: "Quarkus 3.x started in 0.105s"
```

**Step 3.3: Verify API health**
```bash
curl -s http://localhost:8080/health/live | jq .
```
Expected: `{"status": "UP"}`

**Step 3.4: Check all endpoints**
```bash
curl -s http://localhost:8080/q/openapi | head -20
# Expected: OpenAPI 3.0 spec YAML
```

**Step 3.5: Test authentication**
```bash
curl -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"free@test.com","password":"test123"}' | jq .
```
Expected: JWT token response

**Step 3.6: Test analyze endpoint**
```bash
curl -X POST http://localhost:8080/v1/analyze \
  -H "Authorization: Bearer sk_test_free_abc123" \
  -H "Content-Type: application/json" \
  -d '{"query":"What is 2+2?","modality":"text"}' | jq .
```
Expected: Analysis result with confidence score

**Step 3.7: Test explain endpoint**
```bash
curl -X GET http://localhost:8080/v1/explain/{result_id} \
  -H "Authorization: Bearer sk_test_free_abc123" | jq .
```
Expected: XAI explanation with decision timeline

**Step 3.8: Test audit logs**
```bash
curl -X GET http://localhost:8080/v1/audit/logs?limit=10 \
  -H "Authorization: Bearer sk_test_ent_abc123" | jq .
```
Expected: List of immutable audit events

---

### Phase 4: Frontend Dashboard Launch (10 minutes)

**Step 4.1: Install dependencies**
```bash
cd /home/alexandr-narbaev/Projects/agi/matrix-web-ui
npm ci
```

**Step 4.2: Build production bundle**
```bash
npm run build
```
Expected: `.next/` directory with optimized assets

**Step 4.3: Start Next.js server**
```bash
npm start
```
Wait for:
```
✓ Ready in 2.3s
○ Compiled /
✓ Compiled /dashboard
```

**Step 4.4: Verify landing page**
```bash
curl -s http://localhost:3000 | grep -o "<title>.*</title>"
```
Expected: `<title>MATRIX — Hybrid Neuro-Symbolic AI Civilization</title>`

**Step 4.5: Verify dashboard**
```bash
curl -s http://localhost:3000/dashboard | grep -o "XAI Dashboard"
```
Expected: "XAI Dashboard" found

---

### Phase 5: Observability Stack Launch (5 minutes)

**Step 5.1: Start Prometheus + Grafana**
```bash
cd /home/alexandr-narbaev/Projects/agi/deploy
docker compose up -d prometheus grafana
```

**Step 5.2: Verify Prometheus targets**
```bash
curl -s http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | select(.health=="up")'
```
Expected: api-gateway showing as UP

**Step 5.3: Access Grafana**
```bash
echo "Grafana available at: http://localhost:3001"
echo "Login: admin / admin (change on first login)"
```

**Step 5.4: Import dashboard**
```bash
curl -X POST http://localhost:3001/api/dashboards/import \
  -H "Content-Type: application/json" \
  -H "Authorization: Basic YWRtaW46YWRtaW4=" \
  -d @grafana/matrix-dashboard.json
```

---

### Phase 6: Interactive Demo Mode (Ongoing)

**Step 6.1: Launch demo chatbot script**
Create `demo-chat.sh`:
```bash
#!/bin/bash
API_KEY="sk_test_free_abc123"
BASE_URL="http://localhost:8080"

echo "🤖 MATRIX Chat Interface (type 'quit' to exit)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

while true; do
  echo -n "👤 You: "
  read user_input
  
  if [[ "$user_input" == "quit" ]]; then
    echo "👋 Goodbye!"
    break
  fi
  
  response=$(curl -s -X POST "$BASE_URL/v1/analyze" \
    -H "Authorization: Bearer $API_KEY" \
    -H "Content-Type: application/json" \
    -d "{\"query\":\"$user_input\",\"modality\":\"text\"}")
  
  answer=$(echo "$response" | jq -r '.result.answer')
  confidence=$(echo "$response" | jq -r '.confidence.score')
  
  echo "🧠 MATRIX: $answer (confidence: ${confidence}%)"
  echo ""
done
```

Make executable and run:
```bash
chmod +x demo-chat.sh
./demo-chat.sh
```

**Step 6.2: Real-time metrics viewer**
Create `metrics-watch.sh`:
```bash
#!/bin/bash
echo "📊 MATRIX Live Metrics (Ctrl+C to stop)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

while true; do
  clear
  echo "⏰ $(date '+%Y-%m-%d %H:%M:%S')"
  echo ""
  
  # API requests per minute
  rpm=$(curl -s http://localhost:9090/api/v1/query?query=rate(http_requests_total[1m]) | jq -r '.data.result[0].value[1]')
  echo "📡 API Requests/min: ${rpm:-0}"
  
  # Average response time (p95)
  p95=$(curl -s http://localhost:9090/api/v1/query?query=histogram_quantile(0.95,rate(http_request_duration_seconds_bucket[5m])) | jq -r '.data.result[0].value[1]')
  echo "⚡ Response Time (p95): ${p95:-0}s"
  
  # Active users
  active=$(curl -s http://localhost:9090/api/v1/query?query=active_users | jq -r '.data.result[0].value[1]')
  echo "👥 Active Users: ${active:-0}"
  
  # Credit consumption
  credits=$(curl -s http://localhost:9090/api/v1/query?query=credit_consumption_total | jq -r '.data.result[0].value[1]')
  echo "💳 Credits Consumed: ${credits:-0}"
  
  # Health status
  health=$(curl -s http://localhost:8080/health/ready | jq -r '.status')
  echo "❤️  System Health: $health"
  
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  sleep 5
done
```

Run:
```bash
chmod +x metrics-watch.sh
./metrics-watch.sh
```

**Step 6.3: Audit log stream**
Create `audit-stream.sh`:
```bash
#!/bin/bash
API_KEY="sk_test_ent_abc123"
LAST_ID=0

echo "🔍 MATRIX Audit Log Stream (Ctrl+C to stop)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

while true; do
  response=$(curl -s -X GET "http://localhost:8080/v1/audit/logs?since=$LAST_ID&limit=10" \
    -H "Authorization: Bearer $API_KEY")
  
  new_events=$(echo "$response" | jq -r '.events[] | select(.id > '$LAST_ID')')
  
  if [[ -n "$new_events" ]]; then
    echo "$new_events" | jq -r '"\(.timestamp) | \(.event_type) | User: \(.user_id) | Action: \(.action)"'
    LAST_ID=$(echo "$response" | jq -r '.events[-1].id')
  fi
  
  sleep 3
done
```

Run:
```bash
chmod +x audit-stream.sh
./audit-stream.sh
```

---

### Phase 7: Final Verification & User Handoff

**Step 7.1: Generate access summary**
Create `ACCESS_SUMMARY.md`:
```markdown
# 🚀 MATRIX Production Ecosystem — Access Summary

## 🌐 Web Interfaces

| Service | URL | Credentials |
|---------|-----|-------------|
| Landing Page | http://localhost:3000 | Public |
| XAI Dashboard | http://localhost:3000/dashboard | API Key required |
| Swagger UI | http://localhost:8080/q/swagger-ui | API Key required |
| Grafana | http://localhost:3001 | admin / admin |

## 🔑 Test API Keys

| Tier | API Key | Rate Limit | Features |
|------|---------|------------|----------|
| FREE | `sk_test_free_abc123` | 100/hr | Basic analyze, limited XAI |
| PRO | `sk_test_pro_xyz789` | 1000/hr | Full analyze, priority XAI, federate |
| ENTERPRISE | `sk_test_ent_qwe456` | Unlimited | All features + audit logs + custom models |

## 📡 Quick Test Commands

### Analyze Query
```bash
curl -X POST http://localhost:8080/v1/analyze \
  -H "Authorization: Bearer sk_test_free_abc123" \
  -H "Content-Type: application/json" \
  -d '{"query":"Explain quantum entanglement","modality":"text"}'
```

### Get Explanation
```bash
curl http://localhost:8080/v1/explain/{result_id} \
  -H "Authorization: Bearer sk_test_pro_xyz789"
```

### Check Audit Logs
```bash
curl http://localhost:8080/v1/audit/logs \
  -H "Authorization: Bearer sk_test_ent_qwe456"
```

### View Metrics
```bash
curl http://localhost:9090/api/v1/query?query=up
```

## 💬 Interactive Chat

Run the chat interface:
```bash
./demo-chat.sh
```

## 📊 Live Monitoring

Watch real-time metrics:
```bash
./metrics-watch.sh
```

Stream audit logs:
```bash
./audit-stream.sh
```

## 🎯 Sample Use Cases

### 1. Smart Home Anomaly Detection
```bash
curl -X POST http://localhost:8080/v1/analyze \
  -H "Authorization: Bearer sk_test_pro_xyz789" \
  -H "Content-Type: application/json" \
  -d '{
    "query":"Temperature spike in living room",
    "modality":"sensor",
    "context":{"device":"thermostat_01","value":32.5,"threshold":26.0}
  }'
```

### 2. Educational Assessment
```bash
curl -X POST http://localhost:8080/v1/analyze \
  -H "Authorization: Bearer sk_test_ent_qwe456" \
  -H "Content-Type: application/json" \
  -d '{
    "query":"Student answered 7/10 on algebra test",
    "modality":"assessment",
    "context":{"student_id":"stu_123","topic":"algebra","score":0.7}
  }'
```

### 3. Compliance Check
```bash
curl -X POST http://localhost:8080/v1/analyze \
  -H "Authorization: Bearer sk_test_ent_qwe456" \
  -H "Content-Type: application/json" \
  -d '{
    "query":"Check GDPR compliance for data retention policy",
    "modality":"document",
    "context":{"policy":"retain_user_data_5_years","jurisdiction":"EU"}
  }'
```

## 🛠️ Troubleshooting

### API not responding
```bash
docker compose logs api-gateway | tail -50
```

### Database connection failed
```bash
docker compose logs postgres | tail -20
```

### Frontend build errors
```bash
cd matrix-web-ui && npm run build 2>&1 | tail -30
```

### Metrics not appearing
```bash
curl http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | select(.health!="up")'
```

## 📈 Success Criteria

✅ All services running (`docker compose ps` shows all healthy)  
✅ Landing page loads at http://localhost:3000  
✅ Dashboard accessible at http://localhost:3000/dashboard  
✅ API responds to authenticated requests  
✅ Metrics streaming in Grafana  
✅ Audit logs capturing all actions  
✅ Chat interface functional  
✅ All 3 pricing tiers working  

---

**Next Steps:**
1. Explore the XAI Dashboard to visualize decision-making
2. Run sample use cases from different tiers
3. Monitor real-time metrics while interacting
4. Review audit logs for compliance verification
5. Test rate limiting by exceeding FREE tier limits
6. Experiment with pilot packages (smart-home, edu, compliance)

**Support:** Check `docs-v2/ecosystem/` for detailed guides (RU/EN)
```

**Step 7.2: Display summary to user**
```bash
cat ACCESS_SUMMARY.md
```

**Step 7.3: Confirm all systems operational**
```bash
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🎉 MATRIX Production Ecosystem Fully Operational!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📍 Services Status:"
docker compose ps --format "table {{.Name}}\t{{.Status}}"
echo ""
echo "🌐 Access Points:"
echo "   • Landing Page:     http://localhost:3000"
echo "   • XAI Dashboard:    http://localhost:3000/dashboard"
echo "   • API Swagger UI:   http://localhost:8080/q/swagger-ui"
echo "   • Grafana Metrics:  http://localhost:3001"
echo ""
echo "💬 Start chatting:"
echo "   ./demo-chat.sh"
echo ""
echo "📊 Watch live metrics:"
echo "   ./metrics-watch.sh"
echo ""
echo "🔍 Stream audit logs:"
echo "   ./audit-stream.sh"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
```

---

## Constraints & Safety

- **No LLM imports in runtime**: Enforced by `.github/workflows/branch-validation.yml`
- **FROZEN modulators**: ETHICAL_FILTER, SAFETY_MONITOR, LIE_DETECTOR, CONSISTENCY_CHECKER cannot be modified
- **Seeded Random only**: No non-deterministic behavior in production
- **No consciousness claims**: Use "emergent coordination", "modulator levels" per CONSTITUTION Article VI
- **Apache-2.0 core**: All code remains open source per Article VIII
- **GDPR compliance**: Data pruned after retention period, tombstones preserved
- **Rate limiting enforced**: Prevent abuse of FREE tier
- **Immutable audit trail**: Hash-chained logs cannot be altered

---

## Expected Output

After executing this prompt, the user will have:

1. ✅ **Fully running ecosystem** with all 10 transformation waves active
2. ✅ **Interactive chat interface** to converse with MATRIX in real-time
3. ✅ **Live metrics dashboard** showing API performance, user activity, credit consumption
4. ✅ **Audit log stream** demonstrating compliance and traceability
5. ✅ **Multi-tier access** to test FREE/PRO/ENTERPRISE features
6. ✅ **Sample use cases** for smart home, education, and compliance scenarios
7. ✅ **Complete documentation** with troubleshooting guides
8. ✅ **Production-ready deployment** that can be scaled to Kubernetes

The system will be ready for immediate evaluation, partner demonstrations, and pilot deployments.

---

## Rollback Procedure (if needed)

If any step fails:

```bash
# Stop all services
docker compose down -v

# Revert to backup branch
git checkout backup-main-pre-merge-<timestamp>

# Rebuild from scratch
./gradlew clean build
docker compose up -d
```

---

**Execute this prompt now to launch the complete MATRIX Production Ecosystem.**
