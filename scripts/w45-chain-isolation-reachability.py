#!/usr/bin/env python3
"""W45.2 — the reachability evidence of the chain isolation.

The owner's decision (W45.2, decision 2) is that the two chains stay isolated:
the epistemic application (chain A) and the brain runtime (chain B) must not
meet. This script produces the EVIDENCE for that decision; the gate
(`scripts/w45-chain-isolation-check.sh`) judges it.

**Why the bytecode and not the text.** A textual search mistakes a mention for
a call: a comment, a log message or an import says nothing about whether the
code can reach a class. The call edges here are resolved from the constant
pool, through `javap -p -c`: every `invoke*` instruction names the owner of
the callee in the compiler's own constant pool, and every `new`, `checkcast`
and `instanceof` names the class it operates on. The edges are therefore what
the COMPILER saw, not what a reader guessed.

**What it computes.**
  1. the call graph of the compiled classes of every module on chain B's
     classpath (the modules chain B could possibly walk into);
  2. the transitive closure of that graph from each chain B entry point;
  3. the transitive closure from the chain A store entry points, so that the
     "epistemic store subsystem" is DERIVED from the bytecode and not written
     down in a document;
  4. the route census of both chains, read out of the compiled annotations
     (`@Path`, `@GET`, `@POST`, ...) and out of the compiled route literals of
     the JDK HTTP server of chain B.

Usage:
    w45-chain-isolation-reachability.py --root <repo> --json <file>
                                         [--javap <path to javap>]
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from collections import deque
from pathlib import Path

# The modules of the two chains, and the role each one plays.
#
#   chain-a  the epistemic and workbench application (matrix-core)
#   chain-b  the brain runtime and its gateway
#   chain-b-dep  a module chain B depends on, and therefore can walk into
#
# The universe of the reachability proof is the UNION of them: a class of
# chain B may call a class of matrix-core (the modules share a classpath),
# and the proof has to follow that call into matrix-core to be worth
# anything.
MODULES = (
    ("matrix-core", "chain-a"),
    ("matrix-brain-runtime", "chain-b"),
    ("matrix-api-gateway", "chain-b"),
    ("matrix-audit", "chain-b-dep"),
)

# The entry points of chain B, as the owner decision names them.
CHAIN_B_ENTRY_POINTS = (
    "io.matrix.api.MinimalHttpServer",
    "io.matrix.api.llm_quarantine.BrainProduction",
    "io.matrix.api.brain.ProductionBrainClient",
    "io.matrix.brain.runtime.TrueMindCycle",
)

# The entry points of chain A: the surfaces that serve the epistemic model
# and the store that holds it.
CHAIN_A_ENTRY_POINTS = (
    "io.matrix.cognitive.CognitiveResource",
    "io.matrix.cognitive.CognitiveService",
    "io.matrix.cognitive.CognitiveKernel",
    "io.matrix.cognitive.CognitiveStore",
    "io.matrix.knowledge.KnowledgeWorkbench",
    "io.matrix.knowledge.KnowledgeStore",
)

# The packages whose classes belong to the two stores of chain A.
COGNITIVE_PACKAGE = "io.matrix.cognitive."
KNOWLEDGE_PACKAGE = "io.matrix.knowledge."

# The names by which the EPISTEMIC DATABASE is identified in the bytecode.
#
# The store is identified by the database it writes, not by the package it
# lives in: the classes whose constant pool NAMES the database are the
# classes that can open it, and a class that cannot name it cannot open it.
EPISTEMIC_DATABASE_MARKERS = (b"matrix.knowledge.path", b"workbench.sqlite")

# The packages of the PERSISTENCE stack. A class that reaches one of them can
# open a SQLite database at all, and the epistemic database is a SQLite
# database: a class that cannot reach this stack cannot open it.
PERSISTENCE_PACKAGES = ("java.sql.", "javax.sql.", "org.sqlite.")

# The HTTP methods that can change a state.
WRITE_METHODS = ("POST", "PUT", "DELETE", "PATCH")

# The prefixes of the chain A surface. A route under one of them belongs to
# chain A and must not be reachable from chain B.
CHAIN_A_ROUTE_PREFIXES = ("/v1/cognitive", "/v1/workbench")

# W51 — the package of the canonical contract layer. Both chains may READ it:
# it is the shared vocabulary of the bridge. It must not be able to open a
# database, serve a route or reach a store, because it is shared by two
# processes and a shared class is a class both of them can call.
CONTRACT_PACKAGE = "io.matrix.contract."

# W51 — the package of the chain A bridge server. It owns the write path of
# the bridge, so chain B must never reach it: the crossing happens over the
# network, not inside one class graph.
BRIDGE_SERVER_PACKAGE = "io.matrix.bridge."

# W51 — the chain B side of the bridge. It is the only class of chain B that
# talks to chain A, and the gate names it so that "the bridge is the only
# crossing" is a checked statement.
BRIDGE_CLIENT = "io.matrix.brain.runtime.bridge.BridgePublisher"

# W52 — the SECOND direction of the bridge. The client of the second direction
# lives in chain A (the epistemic core offers what it established) and the
# receiver of it lives in chain B (the brain takes it into its memory). Both
# are measured the way the first direction is measured: the client must reach
# no class of the store, and the receiver must reach no class of chain A's
# bridge server.
KNOWLEDGE_CLIENT = "io.matrix.bridge.KnowledgePublisher"
KNOWLEDGE_RECEIVER = "io.matrix.api.bridge.KnowledgeBridge"

# W53 — the third direction of the bridge: chain A delegates a bounded cognitive
# task, chain B executes it. The client is chain A's publisher, the receiver is
# chain B's task bridge. The same two properties hold as for the second
# direction: the client must reach no class of the store, and the receiver must
# reach no class of chain A's bridge server and no class of chain A's database.
TASK_CLIENT = "io.matrix.bridge.TaskPublisher"
TASK_RECEIVER = "io.matrix.api.bridge.TaskBridge"

# The annotation names of the JAX-RS surface.
HTTP_METHOD_ANNOTATIONS = {
    "jakarta.ws.rs.GET": "GET",
    "jakarta.ws.rs.POST": "POST",
    "jakarta.ws.rs.PUT": "PUT",
    "jakarta.ws.rs.DELETE": "DELETE",
    "jakarta.ws.rs.PATCH": "PATCH",
    "jakarta.ws.rs.HEAD": "HEAD",
    "jakarta.ws.rs.OPTIONS": "OPTIONS",
}


def class_dir(root: Path, module: str) -> Path:
    return root / module / "build" / "classes" / "java" / "main"


def class_names(base: Path) -> list[str]:
    """The FQNs of the compiled classes of a module."""
    if not base.exists():
        return []
    names = []
    for path in base.rglob("*.class"):
        rel = path.relative_to(base).with_suffix("")
        names.append(str(rel).replace("/", "."))
    return sorted(names)


def run_javap(javap: str, classpath: str, names: list[str], extra: list[str]) -> str:
    """The disassembly of the given classes."""
    if not names:
        return ""
    try:
        done = subprocess.run(
            [javap, *extra, "-classpath", classpath, *names],
            capture_output=True, text=True, timeout=1800)
    except (OSError, subprocess.TimeoutExpired) as failure:
        print(f"javap failed: {failure}", file=sys.stderr)
        return ""
    return done.stdout


CLASS_HEADER = re.compile(
    r"^(?:(?:public|final|abstract|sealed|non-sealed|static|strictfp)\s+)*"
    r"(class|interface|enum|record|@interface)\s+([\w.$]+)")
METHOD_SIGNATURE = re.compile(
    r"^  (?:public|protected|private|static|final|abstract|default|synchronized|native|"
    r"transient|volatile|strictfp|<init>|\s)*[\w.$\[\]<>, ?]+\s+[\w$]+\(.*\);$")


def parse_edges(disassembly: str) -> tuple[dict[str, set[str]], list[tuple[str, str, str]],
                                           dict[str, set[str]]]:
    """The outgoing edges of every class in a disassembly.

    Returns the adjacency (class -> referenced classes), the list of the edges
    with their kind, so that the report can say WHICH edge reaches a forbidden
    class and not only that some edge does, and the persistence references of
    every class: the classes that reach the JDBC stack, and therefore CAN
    open a SQLite database at all.
    """
    adjacency: dict[str, set[str]] = {}
    edges: list[tuple[str, str, str]] = []
    persistence: dict[str, set[str]] = {}
    current_class = ""
    for raw in disassembly.splitlines():
        line = raw.strip()
        header = CLASS_HEADER.match(line)
        if header:
            current_class = header.group(2)
            adjacency.setdefault(current_class, set())
            persistence.setdefault(current_class, set())
            continue
        if not current_class:
            continue
        kind = ""
        owner = ""
        # The comment that names the constant-pool entry follows the
        # instruction, so it is a SUBSTRING of the line and not its start.
        if "// Method " in line or "// InterfaceMethod " in line:
            kind = "call"
            body = line.split("//", 1)[1].strip()
            body = body.replace("InterfaceMethod ", "Method ", 1)
            body = body.replace("Method ", "", 1)
            owner = owner_of(body)
        elif "// Field " in line:
            kind = "field"
            body = line.split("//", 1)[1].strip()
            body = body.replace("Field ", "", 1)
            owner = owner_of(body)
        elif "// class " in line:
            kind = "type"
            owner = line.split("// class ", 1)[1].strip()
        if not kind:
            continue
        if not owner or "(" in owner:
            # javap omits the owner of a call to the SAME class; that is a
            # self-call and not an edge to another class.
            continue
        owner = owner.replace("/", ".").replace("$$", "$")
        if owner.startswith(PERSISTENCE_PACKAGES):
            persistence[current_class].add(owner)
            continue
        if owner.startswith(("java.", "jdk.", "sun.", "com.sun.", "javax.")):
            continue
        adjacency[current_class].add(owner)
        edges.append((current_class, owner, kind))
    return adjacency, edges, persistence


def owner_of(body: str) -> str:
    """The owner of a Method or Field constant-pool entry."""
    if body.startswith('"'):
        end = body.find('"', 1)
        return body[1:end] if end > 0 else body[1:]
    return body.split(".")[0]


def reachable_from(adjacency: dict[str, set[str]], universe: set[str],
                   entry_points: list[str]) -> set[str]:
    """The transitive closure of the call graph from the entry points.

    Only the classes that EXIST in the compiled universe are followed: a call
    to a class of the JDK or of a third-party library leaves the universe of
    MATRIX, and the proof is about MATRIX.
    """
    seen: set[str] = set()
    queue = deque(entry_points)
    while queue:
        current = queue.popleft()
        if current in seen:
            continue
        seen.add(current)
        for neighbour in adjacency.get(current, ()):
            if neighbour in universe and neighbour not in seen:
                queue.append(neighbour)
    return seen


def parse_annotations(disassembly: str) -> tuple[list[tuple[str, str]], dict[str, list[tuple[str, str]]]]:
    """The annotations of a class, read out of its disassembly.

    `javap -v` prints the class-level annotations in the class attributes
    (at the left margin) and the method-level ones inside the method (indented
    by four spaces). The parser uses that indentation, and not a guess about
    the order of the output.

    Returns the class-level annotations and the method-level ones, each as a
    list of (annotation type, value) pairs.
    """
    class_annotations: list[tuple[str, str]] = []
    method_annotations: dict[str, list[tuple[str, str]]] = {}
    current_method = ""
    block_indent = -1
    pending_type = ""
    for raw in disassembly.splitlines():
        stripped = raw.strip()
        indent = len(raw) - len(raw.lstrip())
        if stripped == "RuntimeVisibleAnnotations:":
            block_indent = indent
            pending_type = ""
            continue
        if block_indent >= 0:
            if stripped.startswith("RuntimeVisible") or stripped.startswith("SourceFile") \
                    or stripped.startswith("Signature") or stripped.startswith("InnerClasses"):
                block_indent = -1
                pending_type = ""
                continue
            # The class attributes are at the left margin; the attributes of a
            # method are indented. The class-level block comes AFTER the
            # methods, so the name of the last method says nothing about it.
            class_block = block_indent == 0
            if indent == 2 and stripped.endswith(");") and "(" in stripped:
                # A method signature: the block that follows belongs to it.
                current_method = stripped.split("(")[0].split()[-1]
                continue
            if indent == block_indent + 4 and stripped and not stripped.startswith("#") \
                    and not stripped.startswith(")"):
                name = stripped.split("(")[0].strip()
                if name in HTTP_METHOD_ANNOTATIONS:
                    if not class_block and current_method:
                        method_annotations.setdefault(current_method, []).append(
                            (name, HTTP_METHOD_ANNOTATIONS[name]))
                elif name == "jakarta.ws.rs.Path":
                    pending_type = name
                continue
            if indent == block_indent + 6 and stripped.startswith("value="):
                value = stripped.split("=", 1)[1].strip().strip('"')
                if pending_type == "jakarta.ws.rs.Path":
                    if class_block:
                        class_annotations.append((pending_type, value))
                    elif current_method:
                        method_annotations.setdefault(current_method, []).append(
                            (pending_type, value))
                pending_type = ""
                continue
            continue
        if indent == 2 and stripped.endswith(");") and "(" in stripped:
            current_method = stripped.split("(")[0].split()[-1]
    return class_annotations, method_annotations


def annotation_census(javap: str, classpath: str, names: list[str]) -> list[dict]:
    """The routes declared by the compiled annotations.

    The class-level `@Path` and the method-level `@Path` are combined, and the
    HTTP method is read from the method's own annotation. Everything is read
    out of the compiled class file: a route that is not in the bytecode is
    not a route.
    """
    census: list[dict] = []
    for name in names:
        disassembly = run_javap(javap, classpath, [name], ["-v", "-p"])
        if not disassembly:
            continue
        class_annotations, method_annotations = parse_annotations(disassembly)
        class_path = ""
        for annotation, value in class_annotations:
            if annotation == "jakarta.ws.rs.Path":
                class_path = value
        for method, annotations in method_annotations.items():
            http = ""
            method_path = ""
            for annotation, value in annotations:
                if annotation in HTTP_METHOD_ANNOTATIONS:
                    http = value
                elif annotation == "jakarta.ws.rs.Path":
                    method_path = value
            if not http:
                continue
            census.append({
                "class": name,
                "method": method,
                "http": http,
                "path": (class_path + method_path) or class_path,
                "class_path": class_path,
            })
    return census


def served_routes(javap: str, classpath: str, name: str) -> list[str]:
    """The route literals of a JDK HTTP server, read out of the bytecode.

    The `com.sun.net.httpserver` server of chain B does not use annotations:
    its routes are the string literals it passes to `createContext`. They are
    read out of the constant pool of the class, so the census of the SERVED
    surface is derived from the compiled code as well.
    """
    disassembly = run_javap(javap, classpath, [name], ["-p", "-c"])
    routes: list[str] = []
    for raw in disassembly.splitlines():
        if "// String " not in raw:
            continue
        literal = raw.split("// String ", 1)[1].strip()
        if literal.startswith("/") and literal not in routes:
            routes.append(literal)
    return sorted(routes)


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", required=True)
    parser.add_argument("--json", required=True)
    parser.add_argument("--javap", default=os.environ.get("JAVA_HOME", "/usr") + "/bin/javap")
    args = parser.parse_args()
    root = Path(args.root).resolve()

    # ── the universe ──────────────────────────────────────────────────────
    module_classes: dict[str, list[str]] = {}
    class_module: dict[str, str] = {}
    module_of: dict[str, str] = {}
    for module, role in MODULES:
        names = class_names(class_dir(root, module))
        module_classes[module] = names
        module_of[module] = role
        for name in names:
            class_module.setdefault(name, module)
    universe = set(class_module)
    classpath = ":".join(str(class_dir(root, module)) for module, _ in MODULES)

    # ── the call graph ────────────────────────────────────────────────────
    adjacency: dict[str, set[str]] = {}
    edges: list[tuple[str, str, str]] = []
    persistence: dict[str, set[str]] = {}
    for module, _role in MODULES:
        names = module_classes[module]
        if not names:
            continue
        disassembly = run_javap(args.javap, classpath, names, ["-p", "-c"])
        found_adjacency, found_edges, found_persistence = parse_edges(disassembly)
        for name, targets in found_adjacency.items():
            adjacency.setdefault(name, set()).update(targets)
        for name, targets in found_persistence.items():
            persistence.setdefault(name, set()).update(targets)
        edges.extend(found_edges)

    # ── the closures ──────────────────────────────────────────────────────
    chain_b_closure = reachable_from(adjacency, universe, list(CHAIN_B_ENTRY_POINTS))
    chain_a_closure = reachable_from(adjacency, universe, list(CHAIN_A_ENTRY_POINTS))

    def closure_of(entry: str) -> set[str]:
        return reachable_from(adjacency, universe, [entry])

    per_entry = []
    for entry in CHAIN_B_ENTRY_POINTS:
        reached = closure_of(entry)
        per_entry.append({
            "entry_point": entry,
            "module": class_module.get(entry, "not-compiled"),
            "reachable": len(reached),
            "cognitive": sorted(c for c in reached if c.startswith(COGNITIVE_PACKAGE)),
            "knowledge": sorted(c for c in reached if c.startswith(KNOWLEDGE_PACKAGE)),
        })

    chain_b_cognitive = sorted(c for c in chain_b_closure if c.startswith(COGNITIVE_PACKAGE))
    chain_b_knowledge = sorted(c for c in chain_b_closure if c.startswith(KNOWLEDGE_PACKAGE))

    # The epistemic store subsystem: DERIVED from the bytecode, as the
    # closure of the two store classes of chain A.
    store_closure: set[str] = set()
    for entry in ("io.matrix.knowledge.KnowledgeStore", "io.matrix.knowledge.KnowledgeWorkbench",
                  "io.matrix.cognitive.CognitiveStore"):
        store_closure |= closure_of(entry)
    chain_b_store = sorted(chain_b_closure & store_closure)
    chain_b_persistence = sorted(c for c in chain_b_closure if persistence.get(c))
    # The store's OWN classes: the classes of the store subsystem that live in
    # the two store packages. The shared substrate (the truth tables, the
    # Tsetlin automaton, the HDC encoding) is NOT part of the store: both
    # chains use it, and its presence in the intersection of the two closures
    # says nothing about the isolation.
    store_own = sorted(c for c in store_closure
                       if c.startswith(COGNITIVE_PACKAGE) or c.startswith(KNOWLEDGE_PACKAGE))
    chain_b_store_own = sorted(chain_b_closure & set(store_own))
    # The classes that name the epistemic database in their constant pool.
    epistemic_database_classes = sorted(
        name for name in universe
        if names_the_epistemic_database(class_dir(root, "matrix-core"), name))
    chain_b_epistemic_database = sorted(chain_b_closure & set(epistemic_database_classes))
    chain_b_knowledge_persistence = sorted(
        c for c in chain_b_closure
        if c.startswith(KNOWLEDGE_PACKAGE) and persistence.get(c))

    # ── the route census ──────────────────────────────────────────────────
    chain_a_route_classes = [
        name for name in module_classes["matrix-core"]
        if name.startswith("io.matrix") and has_path_annotation(
            class_dir(root, "matrix-core") / (name.replace(".", "/") + ".class"))]
    chain_b_route_classes = [
        name for name in module_classes["matrix-api-gateway"]
        if has_path_annotation(
            class_dir(root, "matrix-api-gateway") / (name.replace(".", "/") + ".class"))]

    chain_a_routes = annotation_census(args.javap, classpath, chain_a_route_classes)
    chain_b_routes = annotation_census(args.javap, classpath, chain_b_route_classes)
    chain_b_served = served_routes(args.javap, classpath, "io.matrix.api.MinimalHttpServer")

    # The chain A route classes: the classes that SERVE the chain A surface.
    chain_a_surface_classes = sorted({
        route["class"] for route in chain_a_routes
        if any(route["path"].startswith(prefix) for prefix in CHAIN_A_ROUTE_PREFIXES)})
    chain_b_reaches_chain_a_surface = sorted(
        c for c in chain_b_closure if c in set(chain_a_surface_classes))

    # ── W51: the bridge, the contract layer and the crossings ───────────────
    #
    # The contract layer is shared substrate: chain B reads it and chain A
    # reads it. It is allowed to be shared ONLY while it is pure. A contract
    # class that can open a database is a class that both processes can call
    # to open one, and the isolation stops being an isolation.
    chain_b_contract = sorted(c for c in chain_b_closure if c.startswith(CONTRACT_PACKAGE))
    chain_b_contract_impure = sorted(
        c for c in chain_b_contract
        if persistence.get(c) or c in set(chain_a_surface_classes)
        or c in set(store_own))

    # The crossing: a class that chain B can reach AND that is one of the
    # STORE'S OWN classes. There must be none: the bridge crosses over the
    # network, not inside one class graph.
    #
    # The store's OWN classes and not the store's whole closure: the shared
    # substrate — the truth tables, the Tsetlin automaton, the HDC encoding —
    # is reachable from both chains and is not a path to the store. That is
    # the distinction W45.2 §4.4 draws, and G2 uses the same one.
    crossing = sorted(chain_b_closure & set(store_own))

    # The chain A bridge server must not be reachable from chain B.
    chain_b_reaches_bridge_server = sorted(
        c for c in chain_b_closure if c.startswith(BRIDGE_SERVER_PACKAGE))

    # The chain B bridge client: it must be reachable, and it must reach no
    # class of the store.
    bridge_client_reachable = BRIDGE_CLIENT in chain_b_closure
    # The client's own closure is computed even when chain B does not call it
    # yet: the property that matters is that the class CANNOT reach the store,
    # and that is true whether or not anybody calls it.
    bridge_client_reaches_store = sorted(closure_of(BRIDGE_CLIENT) & set(store_own))

    # W52 — the second direction. The client of the second direction is a class
    # of chain A: it must reach no class of the store, for the same reason the
    # client of the first direction does not. The receiver of the second
    # direction is a class of chain B: it must reach no class of chain A's
    # bridge server, or the crossing would happen inside one class graph
    # instead of over the network.
    knowledge_client_reaches_store = sorted(closure_of(KNOWLEDGE_CLIENT) & set(store_own))
    knowledge_receiver_reaches_bridge_server = sorted(
        c for c in closure_of(KNOWLEDGE_RECEIVER) if c.startswith(BRIDGE_SERVER_PACKAGE))
    knowledge_receiver_reaches_store = sorted(
        closure_of(KNOWLEDGE_RECEIVER) & set(store_own))
    knowledge_client_reachable = KNOWLEDGE_CLIENT in chain_a_closure
    knowledge_receiver_reachable = KNOWLEDGE_RECEIVER in chain_b_closure

    # W53 — the third direction. The task client of chain A must reach no class
    # of the store: it sends a request, it does not write into the epistemic
    # model. The task receiver of chain B must reach no class of chain A's
    # bridge server and no class of chain A's database: the cognitive work
    # crosses over the network, and chain B's own inbox is chain B's own file.
    task_client_reaches_store = sorted(closure_of(TASK_CLIENT) & set(store_own))
    task_receiver_reaches_bridge_server = sorted(
        c for c in closure_of(TASK_RECEIVER) if c.startswith(BRIDGE_SERVER_PACKAGE))
    task_receiver_reaches_store = sorted(closure_of(TASK_RECEIVER) & set(store_own))
    task_client_reachable = TASK_CLIENT in chain_a_closure
    task_receiver_reachable = TASK_RECEIVER in chain_b_closure

    report = {
        "modules": [
            {"name": module, "role": module_of[module], "classes": len(module_classes[module])}
            for module, _ in MODULES],
        "universe_classes": len(universe),
        "edges": len(edges),
        "entry_points": per_entry,
        "chain_b": {
            "reachable_total": len(chain_b_closure),
            "reachable_by_module": count_by(chain_b_closure, class_module),
            "reachable_cognitive": chain_b_cognitive,
            "reachable_knowledge": chain_b_knowledge,
            "reachable_store_subsystem": chain_b_store,
            "reachable_store_own": chain_b_store_own,
            "reachable_epistemic_database": chain_b_epistemic_database,
            "reaches_chain_a_surface": chain_b_reaches_chain_a_surface,
            "reachable_persistence": chain_b_persistence,
            "reachable_knowledge_persistence": chain_b_knowledge_persistence,
            "reachable_contract": chain_b_contract,
            "reachable_contract_impure": chain_b_contract_impure,
            "reaches_bridge_server": chain_b_reaches_bridge_server,
            "crossings": crossing,
            "bridge_client_reachable": bridge_client_reachable,
            "bridge_client_reaches_store": bridge_client_reaches_store,
            "knowledge_receiver_reachable": knowledge_receiver_reachable,
            "knowledge_receiver_reaches_store": knowledge_receiver_reaches_store,
            "knowledge_receiver_reaches_bridge_server":
                knowledge_receiver_reaches_bridge_server,
            "task_receiver_reachable": task_receiver_reachable,
            "task_receiver_reaches_store": task_receiver_reaches_store,
            "task_receiver_reaches_bridge_server": task_receiver_reaches_bridge_server,
        },
        "chain_a": {
            "knowledge_client_reachable": knowledge_client_reachable,
            "knowledge_client_reaches_store": knowledge_client_reaches_store,
            "task_client_reachable": task_client_reachable,
            "task_client_reaches_store": task_client_reaches_store,
            "store_subsystem_classes": len(store_closure),
            "store_own_classes": store_own,
            "epistemic_database_classes": epistemic_database_classes,
            "store_subsystem_cognitive": sorted(
                c for c in store_closure if c.startswith(COGNITIVE_PACKAGE)),
            "route_classes": chain_a_surface_classes,
            "reachable_total": len(chain_a_closure),
        },
        "routes": {
            "chain_a": chain_a_routes,
            "chain_b_jaxrs": chain_b_routes,
            "chain_b_served": chain_b_served,
        },
    }
    Path(args.json).write_text(json.dumps(report, indent=2), encoding="utf-8")
    print_report(report)
    return 0


def names_the_epistemic_database(base: Path, name: str) -> bool:
    """Whether the compiled class names the epistemic database.

    The constant pool of a class file holds the literal strings the class
    uses. A class that names the path of the epistemic database can open it;
    a class that does not, cannot.
    """
    try:
        data = (base / (name.replace(".", "/") + ".class")).read_bytes()
    except OSError:
        return False
    return any(marker in data for marker in EPISTEMIC_DATABASE_MARKERS)


def has_path_annotation(class_file: Path) -> bool:
    """Whether a compiled class carries the JAX-RS `@Path` annotation.

    The check reads the constant pool of the class FILE: the descriptor of
    the annotation is a UTF-8 entry of it. It is a pre-filter for the
    expensive `javap -v` pass, not the census itself.
    """
    try:
        return b"Ljakarta/ws/rs/Path;" in class_file.read_bytes()
    except OSError:
        return False


def count_by(classes: set[str], class_module: dict[str, str]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for name in classes:
        module = class_module.get(name, "outside-the-universe")
        counts[module] = counts.get(module, 0) + 1
    return dict(sorted(counts.items()))


def print_report(report: dict) -> None:
    print("===== the census of the reachability =====")
    print("the modules of the two chains and their compiled classes:")
    for module in report["modules"]:
        print(f"  {module['name']:<24} {module['role']:<14} {module['classes']:>6} classes")
    print(f"  the universe of the proof: {report['universe_classes']} classes, "
          f"{report['edges']} resolved edges")
    print()
    print("the chain B entry points and what each of them reaches:")
    for entry in report["entry_points"]:
        print(f"  {entry['entry_point']:<58} {entry['reachable']:>5} classes "
              f"(module {entry['module']})")
    chain_b = report["chain_b"]
    print(f"  the union of the chain B closure: {chain_b['reachable_total']} classes "
          f"{chain_b['reachable_by_module']}")
    print()
    print(f"  classes of the cognitive store (io.matrix.cognitive.**) reached: "
          f"{len(chain_b['reachable_cognitive'])}")
    for name in chain_b["reachable_cognitive"]:
        print(f"    {name}")
    print(f"  classes of the knowledge package (io.matrix.knowledge.**) reached: "
          f"{len(chain_b['reachable_knowledge'])}")
    for name in chain_b["reachable_knowledge"]:
        print(f"    {name}")
    print(f"  classes of the EPISTEMIC STORE subsystem reached: "
          f"{len(chain_b['reachable_store_subsystem'])}")
    for name in chain_b["reachable_store_subsystem"]:
        print(f"    {name}")
    print(f"  classes of the store's OWN packages reached: "
          f"{len(chain_b['reachable_store_own'])}")
    for name in chain_b["reachable_store_own"]:
        print(f"    {name}")
    print(f"  classes that NAME the epistemic database in their constant pool: "
          f"{len(chain_b['reachable_epistemic_database'])}")
    for name in chain_b["reachable_epistemic_database"]:
        print(f"    {name}")
    print(f"  chain A surface classes reached from chain B: "
          f"{len(chain_b['reaches_chain_a_surface'])}")
    for name in chain_b["reaches_chain_a_surface"]:
        print(f"    {name}")
    print(f"  classes of chain B that reach the PERSISTENCE stack (java.sql, org.sqlite): "
          f"{len(chain_b['reachable_persistence'])}")
    for name in chain_b["reachable_persistence"]:
        print(f"    {name}")
    print(f"  classes of io.matrix.knowledge.** that reach the persistence stack: "
          f"{len(chain_b['reachable_knowledge_persistence'])}")
    for name in chain_b["reachable_knowledge_persistence"]:
        print(f"    {name}")
    print(f"  the receiver of the SECOND direction (W52) is reachable from chain B: "
          f"{chain_b['knowledge_receiver_reachable']}")
    print(f"  classes of the store its own closure reaches: "
          f"{len(chain_b['knowledge_receiver_reaches_store'])}")
    for name in chain_b["knowledge_receiver_reaches_store"]:
        print(f"    {name}")
    print(f"  classes of chain A's bridge server its own closure reaches: "
          f"{len(chain_b['knowledge_receiver_reaches_bridge_server'])}")
    for name in chain_b["knowledge_receiver_reaches_bridge_server"]:
        print(f"    {name}")
    print(f"  the client of the SECOND direction (W52) is reachable from chain A: "
          f"{report['chain_a']['knowledge_client_reachable']}")
    print(f"  classes of the store its own closure reaches: "
          f"{len(report['chain_a']['knowledge_client_reaches_store'])}")
    for name in report["chain_a"]["knowledge_client_reaches_store"]:
        print(f"    {name}")
    print()
    print("the route census, read out of the compiled annotations:")
    chain_a_routes = report["routes"]["chain_a"]
    chain_b_routes = report["routes"]["chain_b_jaxrs"]
    print(f"  chain A: {len(chain_a_routes)} annotated routes in "
          f"{len(report['chain_a']['route_classes'])} classes")
    for route in chain_a_routes:
        if any(route["path"].startswith(prefix) for prefix in CHAIN_A_ROUTE_PREFIXES):
            print(f"    {route['http']:<6} {route['path']:<40} {route['class']}")
    print(f"  chain B: {len(chain_b_routes)} annotated routes")
    for route in chain_b_routes:
        print(f"    {route['http']:<6} {route['path']:<40} {route['class']}")
    print(f"  chain B, the routes the compiled server SERVES: "
          f"{len(report['routes']['chain_b_served'])}")
    for route in report["routes"]["chain_b_served"]:
        print(f"    {route}")


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
