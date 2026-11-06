# The two chains

Two serving applications. **They do not meet.** A question about one is never answered with
evidence from the other.

| | chain A | chain B |
|---|---|---|
| the application | the Quarkus workbench and epistemic application | the JDK HTTP gateway and the brain runtime |
| the modules | `matrix-core` | `matrix-api-gateway`, `matrix-brain-runtime` |
| the port | **9091** | **8765** |
| the routes | **210** | **28** |
| the database | `data/knowledge/workbench.sqlite` | `data/mind/mind.sqlite` |
| the entry point | the JAX-RS resources under `io.matrix.api` | `io.matrix.api.MinimalHttpServer` |

The source of record for both is `docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md`; the
running topology is `docs-v2/architecture/RUNTIME-TOPOLOGY.md`; the persistence and the
transaction boundaries are `docs-v2/architecture/DATA-AND-MEMORY-ARCHITECTURE.md`.

## The isolation rule

The two chains share a library — many classes are common — and share **no** data path. A
class that is common to both is a **library**, not a path: its presence in both chains does
not put it on a serving path. The audit that measured this is recorded in
`docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md`.

## The stub default

Chain B answers `/v1/analyze` from an **87-line stub** unless `MATRIX_MODE=production`. The
variable falls back to the system property `matrix.mode`; the live mode is reported by the
`mode` field of `GET /health/live`. **A deployment with no `MATRIX_MODE` set is not running
the real brain at all** — and a capability that is only served in production mode must be
reported as such.

## The registry is the authority on "served"

`docs-v2/architecture/CAPABILITY-REGISTRY.yml` holds the capability records with four axes.
The rule it applies:

- **SERVED** requires a named `serving_path`. Without one, the capability is IMPLEMENTED
  and the answer says **UNREACHABLE**.
- **MEASURED** requires a reproducible benchmark, not a number in prose.
