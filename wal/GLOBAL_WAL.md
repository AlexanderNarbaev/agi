📍 v3.58 — Improvements wave 2: 7 REST API endpoints, 3 extractors, FPGA synthesis TCL, ROS2 setup/launch, GraalVM CI. BUILD SUCCESSFUL.
🚀 Active: P2P REST (/peers, /publish, /query, /trust), Federated REST (/round, /status, /model, /config), Verification REST (/properties, /stats, /violations). Image+Audio extractors. FPGA Vivado TCL+XDC. ROS2 setup.py+launch+package.xml. GraalVM native CI.
🛑 Protected: Pekko 1.6.0, K_MAX=20, FROZEN-нейроны, Quarkus 3.37.3, Java 25, AGPLv3+ethics, 82% coverage floor

## Wave 37 — Improvements Wave 2

### REST APIs
| Endpoint | Method | Package |
|----------|--------|---------|
| /api/v1/noosphere/p2p/peers | GET | P2P Noosphere |
| /api/v1/noosphere/p2p/publish | POST | P2P Noosphere |
| /api/v1/noosphere/p2p/query | GET | P2P Noosphere |
| /api/v1/noosphere/p2p/trust | GET | P2P Noosphere |
| /api/v1/federated/round | POST | Federated Learning |
| /api/v1/federated/status | GET | Federated Learning |
| /api/v1/federated/model | GET | Federated Learning |
| /api/v1/verification/properties | GET | Formal Verification |
| /api/v1/verification/stats | GET | Formal Verification |
| /api/v1/verification/violations | GET | Formal Verification |

### New Classes
- ImageFeatureExtractor — histogram-based image features
- AudioFeatureExtractor — energy+ZCR audio features
- MultimodalFeatureExtractor — unified multi-modal extraction
- ContinuousVerifier — scheduled runtime verification
- FederatedResource — REST API for federated learning
- P2PResource — REST API for P2P network
- VerificationResource — REST API for verification

### Infrastructure
- .github/workflows/native.yml — GraalVM native CI
- matrix-fpga/synth_xilinx.tcl — Vivado synthesis script
- matrix-fpga/constraints/basys3.xdc — FPGA constraints
- matrix-fpga/Makefile.improvements — build automation
- matrix-ros2/setup.py + package.xml + launch/

### Verification
- compileJava: BUILD SUCCESSFUL
- compileTestJava: BUILD SUCCESSFUL
- 37 tests: BUILD SUCCESSFUL
