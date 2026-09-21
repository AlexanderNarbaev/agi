==========================================
sandbox-ui README
==========================================

Sandbox UI — visual proof (RUN 332)

This directory captures the responses the sandbox UI
endpoints return. To regenerate with real Quarkus boot:

  ./gradlew :matrix-core:quarkusDev
  curl http://localhost:9091/v1/sandbox/inspect
  curl 'http://localhost:9091/v1/chain-debug/neuron?id=0'
  curl -X POST -H 'Content-Type: application/json' \
       -d '{"input":"..."}' http://localhost:9091/v1/sandbox/explain
  curl http://localhost:9091/v1/sandbox/topology

Files:
  01-GET-sandbox-inspect.txt        chain state snapshot
  02-GET-chain-debug-neuron.txt     single-neuron introspection
  03-POST-sandbox-explain.txt       decision explanation trace
  04-GET-sandbox-topology.txt       full layer/neuron map

