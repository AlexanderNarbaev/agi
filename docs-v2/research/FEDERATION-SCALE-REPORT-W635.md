# Federation Scale Simulation Report (W606)

**Date:** 2026-09-20

| Scenario | Nodes | Alive | Consensus | Duration (ms) |
|----------|-------|-------|-----------|---------------|
| Resilience 10% kill | 100 | 90 | 100.0% | 6 |
| Resilience 30% kill | 100 | 75 | 100.0% | 0 |
| Resilience 50% kill | 100 | 61 | 100.0% | 0 |
| Consensus Convergence | 100 | 100 | 100.0% | 0 |
| Sybil Attack 20% malicious | 100 | 100 | 0.0% | 0 |
| Resilience 10% kill | 500 | 452 | 100.0% | 1 |
| Resilience 30% kill | 500 | 377 | 100.0% | 0 |
| Resilience 50% kill | 500 | 302 | 100.0% | 0 |
| Consensus Convergence | 500 | 500 | 100.0% | 0 |
| Sybil Attack 20% malicious | 500 | 500 | 0.0% | 1 |
| Resilience 10% kill | 1000 | 907 | 100.0% | 0 |
| Resilience 30% kill | 1000 | 742 | 100.0% | 0 |
| Resilience 50% kill | 1000 | 614 | 100.0% | 0 |
| Consensus Convergence | 1000 | 1000 | 100.0% | 1 |
| Sybil Attack 20% malicious | 1000 | 1000 | 0.0% | 0 |

## Detailed Metrics

### Resilience 10% kill

- **recovered:** 90
- **recoveryRate:** 0.9
- **killed:** 10

### Resilience 30% kill

- **recovered:** 75
- **recoveryRate:** 0.75
- **killed:** 30

### Resilience 50% kill

- **recovered:** 61
- **recoveryRate:** 0.61
- **killed:** 50

### Consensus Convergence

- **converged:** true
- **rounds:** 1

### Sybil Attack 20% malicious

- **malicious:** 20
- **detected:** 0
- **falsePositives:** 0
- **detectionRate:** 0.0

### Resilience 10% kill

- **recovered:** 452
- **recoveryRate:** 0.904
- **killed:** 50

### Resilience 30% kill

- **recovered:** 377
- **recoveryRate:** 0.754
- **killed:** 150

### Resilience 50% kill

- **recovered:** 302
- **recoveryRate:** 0.604
- **killed:** 250

### Consensus Convergence

- **converged:** true
- **rounds:** 1

### Sybil Attack 20% malicious

- **malicious:** 100
- **detected:** 0
- **falsePositives:** 2
- **detectionRate:** 0.0

### Resilience 10% kill

- **recovered:** 907
- **recoveryRate:** 0.907
- **killed:** 100

### Resilience 30% kill

- **recovered:** 742
- **recoveryRate:** 0.742
- **killed:** 300

### Resilience 50% kill

- **recovered:** 614
- **recoveryRate:** 0.614
- **killed:** 500

### Consensus Convergence

- **converged:** true
- **rounds:** 1

### Sybil Attack 20% malicious

- **malicious:** 200
- **detected:** 0
- **falsePositives:** 8
- **detectionRate:** 0.0


