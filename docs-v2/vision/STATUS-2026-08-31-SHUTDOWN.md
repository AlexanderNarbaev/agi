# Shutdown Investigation (Wave G follow-up, 2026-08-31)

## Outcome
**The "server shuts down after 30s" was a false alarm.**

The Quarkus server is stable. The actual cause was the
**`timeout 50` bash command** I was using to start it — it sends
SIGTERM to the JVM at 50 seconds.

## Verification
Started without `timeout`:
- 60s after start: server up
- 90s after start: server up
- Chat endpoint responds to queries

## What was actually working
- ✅ Server stable (no internal shutdown)
- ✅ Chat endpoint (`/v1/chat/completions`) responds
- ✅ 13,416 training pairs loaded (`MatrixTrainingService`)
- ✅ 3 distilled models: sentiment-classifier, topic-router, sentiment-classifier/distilbert-sst2-coverage
- ✅ ConversationRecorder records chats
- ✅ ChatDrivenTrainer runs online training cycles

## Modular split status
**Deferred to a dedicated session** — many transitive dependencies:

| File | Pulls in |
|---|---|
| MemoryHierarchy | cluster.FNLMetadata, cluster.NeuronInstance, etc. |
| CauldronProtocol | simulation.AgentBrain, simulation.SimulationRunner, etc. |
| TelegramBotService | mediator.DriverState, mediator.DriverType, etc. |
| HadesDemoCommand | hades.*, cluster.* |
| MatrixMcpServer | (transitive) |

Clean split would require also moving MemoryHierarchy, CauldronProtocol,
TelegramBotService, and CLI commands. That's a refactor of 30+ files
that interact with the simulation packages, plus the
multi-subproject Gradle build (matrix-sim/ as `application` plugin
so `nohup ./bin/matrix-sim` runs the simulation standalone).

## Recommendation
**For now**: use `nohup java -jar matrix-core/build/matrix-core-1.0.0-runner.jar &` (no timeout)
to keep the chat server running indefinitely.

**For later**: full Pekko-extraction as a dedicated 1-2 day refactor.
