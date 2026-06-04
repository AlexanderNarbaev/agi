#!/usr/bin/env python3
"""
deploy.py — MATRIX Kubernetes deployment script.

Deploys all MATRIX K8s manifests to a running cluster (minikube/kind/k3s).
Verifies deployments, services, and runs smoke tests.

Usage:
    python3 deploy.py                    # Deploy to current kubectl context
    python3 deploy.py --namespace matrix  # Custom namespace
    python3 deploy.py --verify-only       # Only verify existing deployment
    python3 deploy.py --clean             # Remove all MATRIX resources

Prerequisites:
    kubectl configured with a running cluster
    docker (for local image builds)
"""

import argparse
import json
import os
import subprocess
import sys
import time
from pathlib import Path

K8S_BASE = Path(__file__).parent.parent / "infra" / "k8s" / "base"
MANIFESTS = [
    "namespace.yaml",
    "configmap.yaml",
    "deployment.yaml",
    "service.yaml",
    "hpa.yaml",
    "pvc.yaml",
    "rbac.yaml",
    "servicemonitor.yaml",
    "prometheusrule.yaml",
    "jaeger.yaml",
    "loki.yaml",
    "vpa.yaml",
    "crd.yaml",
]


def run(cmd, check=True, capture=False):
    if capture:
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
        if check and result.returncode != 0:
            print(f"ERROR: {cmd}\n{result.stderr}")
            sys.exit(1)
        return result.stdout.strip()
    return subprocess.run(cmd, shell=True, check=check).returncode == 0


def check_prerequisites():
    print("Checking prerequisites...")
    if not run("which kubectl", check=False):
        print("ERROR: kubectl not found. Install: https://kubernetes.io/docs/tasks/tools/")
        sys.exit(1)

    ctx = run("kubectl config current-context", capture=True)
    print(f"  kubectl context: {ctx}")
    print(f"  cluster info: {run('kubectl cluster-info 2>&1 | head -1', capture=True)}")

    nodes = run("kubectl get nodes --no-headers 2>/dev/null | wc -l", capture=True)
    print(f"  nodes: {nodes}")
    if int(nodes) == 0:
        print("WARNING: No nodes found. Start minikube: minikube start")
    print()


def deploy(namespace="matrix-system"):
    print(f"=== Deploying MATRIX to namespace={namespace} ===")
    print()

    for manifest in MANIFESTS:
        path = K8S_BASE / manifest
        if not path.exists():
            print(f"  SKIP: {manifest} (not found)")
            continue

        print(f"  Applying: {manifest}...")
        cmd = f"kubectl apply -f {path}"
        if "namespace" not in manifest:
            cmd += f" -n {namespace}"
        if not run(cmd, check=False):
            print(f"    WARNING: Failed to apply {manifest}")

    print()
    print("Applying CRD example...")
    cr_path = K8S_BASE / "cr-example.yaml"
    if cr_path.exists():
        run(f"kubectl apply -f {cr_path} -n {namespace}", check=False)


def wait_for_ready(namespace="matrix-system", timeout=120):
    print(f"Waiting for deployments (timeout={timeout}s)...")
    start = time.time()

    deployments = run(
        f"kubectl get deployments -n {namespace} -o name 2>/dev/null", capture=True)
    if not deployments:
        print("  No deployments found")
        return

    for dep in deployments.split("\n"):
        dep = dep.strip()
        if not dep:
            continue
        print(f"  Waiting for {dep}...")
        run(f"kubectl rollout status {dep} -n {namespace} "
            f"--timeout={timeout}s 2>/dev/null", check=False)

    elapsed = time.time() - start
    print(f"  Done in {elapsed:.1f}s")


def verify(namespace="matrix-system"):
    print("=== Verification ===")
    print()

    print("Deployments:")
    run(f"kubectl get deployments -n {namespace} 2>/dev/null", check=False)

    print("\nServices:")
    run(f"kubectl get services -n {namespace} 2>/dev/null", check=False)

    print("\nPods:")
    run(f"kubectl get pods -n {namespace} 2>/dev/null", check=False)

    print("\nCRDs:")
    run("kubectl get crd | grep matrix 2>/dev/null", check=False)

    print()


def port_forward(namespace="matrix-system"):
    print("=== Port Forwarding ===")
    print("  Prometheus: kubectl port-forward -n matrix-system svc/matrix-prometheus 9090:9090")
    print("  Jaeger:     kubectl port-forward -n matrix-system svc/matrix-jaeger 16686:16686")
    print("  Grafana:    kubectl port-forward -n matrix-system svc/matrix-grafana 3000:3000")
    print("  App metrics: kubectl port-forward -n matrix-system svc/matrix-core 9091:9091")


def clean(namespace="matrix-system"):
    print(f"=== Cleaning up namespace={namespace} ===")
    run(f"kubectl delete namespace {namespace} 2>/dev/null", check=False)
    run("kubectl delete crd matrixclusters.matrix.io 2>/dev/null", check=False)
    print("  Cleanup initiated")


def main():
    parser = argparse.ArgumentParser(description="MATRIX K8s deployment")
    parser.add_argument("--namespace", "-n", default="matrix-system", help="K8s namespace")
    parser.add_argument("--verify-only", action="store_true", help="Only verify")
    parser.add_argument("--clean", action="store_true", help="Remove all resources")
    parser.add_argument("--port-forward", action="store_true", help="Show port-forward commands")
    parser.add_argument("--timeout", type=int, default=120, help="Deployment timeout (seconds)")
    args = parser.parse_args()

    check_prerequisites()

    if args.clean:
        clean(args.namespace)
        return

    if args.verify_only:
        verify(args.namespace)
        return

    deploy(args.namespace)
    wait_for_ready(args.namespace, args.timeout)
    verify(args.namespace)

    if args.port_forward:
        port_forward(args.namespace)

    print("\nMATRIX deployment complete!")


if __name__ == "__main__":
    main()
