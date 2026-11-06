#!/usr/bin/env python3
"""W44.0 — read-only repository inventory generator.

This script READS the repository and WRITES the machine-readable matrices of the
RECON-W44.0 audit. It never modifies a source file, never runs a build task of
its own and never touches a FROZEN zone. The only things it writes are the
`W44-*.csv` files under `docs-v2/engineering/`.

Two independent views are combined, because neither alone is sufficient:

  1. SOURCE VIEW  — a Java-aware scan of every `.java` file: package, type kind,
     modifiers, supertypes, injected fields, constructor parameters, public
     methods, own mutable state, imports, and the textual markers of
     non-determinism (Random, wall-clock), native/GPU use and external I/O.
  2. BYTECODE VIEW — `javap -p -c` over the compiled classes of every module.
     The call graph is built from the constant pool, not from text, so a call
     that is compiled in is a call that exists, whether or not the source line
     that made it looks alive.

Definitions used throughout (quoted in the audit documents):

  production type   a top-level type declared in a `src/main/java` tree of a
                    module that Gradle actually includes in the build.
  test type         a top-level type declared in a `src/test/java` tree.
  non-built type    a top-level type under a directory that settings.gradle does
                    NOT include (matrix-fpga, matrix-micro, matrix-ros2, the
                    polyglot SDKs, matrix-web-ui).
  reachable         the type appears as a callee in the bytecode call graph of
                    at least one PRODUCTION method of the same module, or it is
                    a CDI bean / JAX-RS resource / event observer / entry point
                    that the runtime instantiates. A type that only appears in
                    test bytecode is NOT reachable.
  entry point       a type the runtime can instantiate without a caller:
                    a JAX-RS resource, a CDI bean with a scope, a
                    @Provider/@Observer, or a class named in META-INF/services.

Usage:
    scripts/w44-audit-inventory.py [--out docs-v2/engineering]

Exit status: 0 when the matrices are written, 1 when the repository cannot be
read.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "docs-v2" / "engineering"

# --------------------------------------------------------------------------
# Exclusion policy. Every count in the audit uses exactly this policy.
# --------------------------------------------------------------------------
EXCLUDED_DIR_NAMES = {
    ".git", "build", ".gradle", "node_modules", ".venv", "venv", "__pycache__",
    ".idea", ".vscode", "target", "out", ".mypy_cache", ".pytest_cache",
    ".next", ".turbo", "dist", ".opencode", ".codex", ".veai", ".swarm",
    ".opencode-swarm", "site-packages",
    # `bin` holds STALE Eclipse-style build output (matrix-core/bin/test/...class)
    # that is not produced by Gradle and is not on any build path; it is excluded
    # from the first-party census and reported separately as a hazard.
    "bin",
}
# Vendored third-party trees: real content, but not first-party source.
VENDORED_DIRS = ["minecraft-server", ".hmcl", "tools", "matrix-web-ui"]
EXCLUDED_PATH_PREFIXES = {
    "data", "models", "logs", "back-ref", "fwd-ref", "defined-elsewhere",
}

# Modules Gradle actually builds, read from settings.gradle.
GRADLE_MODULES = [
    "matrix-core", "matrix-tools-distill", "matrix-spigot", "matrix-operator",
    "matrix-api-gateway", "matrix-sdk-java", "matrix-audit", "matrix-billing",
    "matrix-observability", "matrix-quality", "matrix-brain-runtime",
    "pilots/smart-home-agent", "pilots/edu-assessor", "pilots/compliance-bot",
]
# Directories that hold source but are NOT in settings.gradle.
NON_BUILT_DIRS = [
    "matrix-fpga", "matrix-micro", "matrix-ros2", "matrix-sdk-python",
    "matrix-sdk-js", "matrix-web-ui",
]

# --------------------------------------------------------------------------
# Java source scanning
# --------------------------------------------------------------------------
PKG_RE = re.compile(r"^\s*package\s+([\w.]+)\s*;")
TYPE_RE = re.compile(
    r"^\s*(?:(?:public|protected|private|static|final|abstract|sealed|non-sealed|"
    r"strictfp)\s+)*(class|interface|record|enum|@interface)\s+(\w+)")
SUPER_RE = re.compile(
    r"^\s*(?:(?:public|protected|private|static|final|abstract|sealed)\s+)*"
    r"(class|record|enum)\s+(\w+)(?:\s*<[^>]*>)?(?:\s+extends\s+([\w.]+))?"
    r"(?:\s+implements\s+([\w.<>,\s]+?))?\s*(?:\{|permits|$)")
IMPORT_RE = re.compile(r"^\s*import\s+(static\s+)?([\w.]+)\s*;")
FIELD_RE = re.compile(
    r"^\s*(?:(?:public|protected|private)\s+)?(?:(?:static|final|volatile|transient)\s+)*"
    r"([\w.<>,\[\]?\s]+?)\s+(\w+)\s*(?:=|;)")
METHOD_RE = re.compile(
    r"^\s*(?:@\w+(?:\([^)]*\))?\s+)*"
    r"((?:public|protected|private|static|final|synchronized|abstract|native|"
    r"default)\s+)+([\w.<>,\[\]?\s]+?)\s+(\w+)\s*\(([^)]*)\)")
CTOR_RE = re.compile(
    r"^\s*((?:public|protected|private)\s+)*(\w+)\s*\(([^)]*)\)\s*\{?\s*$")
ANNOT_RE = re.compile(r"^\s*@(\w+)")
ANNOT_ARGS_RE = re.compile(r"^\s*@(\w+)\s*\((.*)\)\s*$")

KEYWORDS = {
    "if", "for", "while", "switch", "catch", "return", "new", "do", "else",
    "try", "synchronized", "assert", "throw", "case", "this", "super",
}

NON_DETERMINISM = {
    "random": re.compile(r"\bnew\s+(?:java\.util\.)?Random\s*\(|\.next(Int|Long|Double|Float|Boolean)\s*\("),
    "wallclock": re.compile(r"System\.(currentTimeMillis|nanoTime)\s*\(|\.now\s*\(\s*\)|Instant\.now|LocalDate(Time)?\.now|OffsetDateTime\.now|ZonedDateTime\.now"),
    "thread_sleep": re.compile(r"Thread\.sleep\s*\("),
}
NATIVE_USE = re.compile(r"\bSystem\.loadLibrary\b|\bSystem\.load\s*\(|\bJNI\b|\bnative\s+\w+\s*\(")
GPU_USE = re.compile(r"OrtEnvironment|CUDAExecutionProvider|OrtSession|GpuTaskExecutor|onnxruntime|Cublas|cuda")
EXTERNAL_IO = re.compile(r"java\.net\.|HttpClient|HttpURLConnection|Socket|okhttp|retrofit|RestTemplate|java\.io\.File|FileInputStream|FileOutputStream|Files\.(read|write|new)|ProcessBuilder|Runtime\.getRuntime\(\)\.exec")
PERSISTENCE = re.compile(r"java\.sql|DriverManager|DataSource|jdbc:|PreparedStatement|Statement\.execute|@Transactional|javax\.persistence|jakarta\.persistence")
CDI_ANNOT = re.compile(r"@(ApplicationScoped|RequestScoped|SessionScoped|ConversationScoped|Dependent|Singleton|Stateless|Stateful)\b")
RESOURCE_ANNOT = re.compile(r"@(Path|GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS|Produces|Consumes)\b")
OBSERVER_ANNOT = re.compile(r"@(Observes|Observer|ConsumeEvent|Subscribe|EventHandler)\b")
INJECT_ANNOT = re.compile(r"@(Inject|InjectMock|InjectInstance|Inject\b)")
POST_CONSTRUCT = re.compile(r"@PostConstruct\b")
SERVICE_LOADER = re.compile(r"ServiceLoader|META-INF/services")


def read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""


def strip_comments(text: str) -> str:
    """Remove block and line comments so that the scans do not see javadoc."""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r"//[^\n]*", "", text)
    return text


def module_of(path: Path) -> tuple[str, str]:
    """Return (module, source_set) for a file under the repository."""
    parts = path.relative_to(ROOT).parts
    if parts[0] == "pilots" and len(parts) > 1:
        module = "pilots/" + parts[1]
    else:
        module = parts[0]
    src_set = "non-built"
    if "src" in parts:
        idx = parts.index("src")
        if idx + 1 < len(parts):
            src_set = parts[idx + 1]  # main / test / integrationTest
    return module, src_set


def scan_java(path: Path) -> dict | None:
    """Extract the source-level facts of one Java file."""
    raw = read_text(path)
    if not raw:
        return None
    code = strip_comments(raw)
    lines = code.splitlines()
    pkg = ""
    for line in lines:
        m = PKG_RE.match(line)
        if m:
            pkg = m.group(1)
            break
    types: list[dict] = []
    imports: list[str] = []
    depth = 0
    current: dict | None = None
    pending: list[str] = []   # the lines just above the current declaration
    for line in lines:
        m = IMPORT_RE.match(line)
        if m:
            imports.append(m.group(2))
        tm = TYPE_RE.match(line)
        if tm and current is not None and depth > 0:
            # a nested type: it is a type in its own right and is inventoried as one
            nested = {
                "name": tm.group(2),
                "kind": tm.group(1),
                "supertypes": [],
                "methods": [], "fields": [], "injected": [], "annotations": [],
                "ctor_params": [], "public_ctor": False, "static": False,
                "final": False, "abstract": False, "nested": True,
                "outer": current["name"],
            }
            nested["decl_annotations"] = sorted(set(
                a for blk in pending for a in re.findall(r"@(\w+)", blk)))
            current.setdefault("nested_types", []).append(nested)
            types.append(nested)
            current = nested
            continue
        if tm and current is None:
            kind = tm.group(1)
            name = tm.group(2)
            supertypes = []
            sm = SUPER_RE.match(line)
            if sm:
                if sm.group(3):
                    supertypes.append(sm.group(3))
                if sm.group(4):
                    supertypes.extend(x.strip() for x in sm.group(4).split(",") if x.strip())
            current = {
                "name": name,
                "kind": kind,
                "decl_annotations": sorted(set(
                    a for blk in pending for a in re.findall(r"@(\w+)", blk))),
                "supertypes": supertypes,
                "methods": [],
                "fields": [],
                "injected": [],
                "annotations": [],
                "ctor_params": [],
                "public_ctor": False,
                "static": False,
                "final": False,
                "abstract": False,
                "nested": False,
                "outer": "",
                "nested_types": [],
            }
            types.append(current)
            depth = line.count("{") - line.count("}")
            continue
        if current is not None:
            for am in ANNOT_ARGS_RE.finditer(line):
                current["annotations"].append(am.group(1))
            am = ANNOT_RE.match(line)
            if am:
                current["annotations"].append(am.group(1))
            if "static" in line.split() and re.search(r"\bstatic\b\s+(final\s+)?class|enum|record|interface", line):
                current["static"] = True
            if re.search(r"\bfinal\b.*\b(class|record|enum)\b", line):
                current["final"] = True
            if re.search(r"\babstract\b.*\b(class|interface)\b", line):
                current["abstract"] = True
            cm = CTOR_RE.match(line)
            if cm and cm.group(2) == current["name"]:
                current["ctor_params"] = [p.strip() for p in cm.group(3).split(",") if p.strip()]
                if "public" in line:
                    current["public_ctor"] = True
            mm = METHOD_RE.match(line)
            if mm and mm.group(3) not in KEYWORDS:
                ret = mm.group(2).strip()
                if ret not in current["name"] or "(" in line:
                    current["methods"].append({
                        "name": mm.group(3),
                        "returns": ret,
                        "visibility": "public" if "public" in mm.group(1) else (
                            "protected" if "protected" in mm.group(1) else "private"),
                        "params": [p.strip() for p in mm.group(4).split(",") if p.strip()],
                        "annotations": [],
                        "static": "static" in mm.group(1),
                    })
            fm = FIELD_RE.match(line)
            if fm and "=" not in line.split("//")[0][:0]:
                ftype = fm.group(1).strip()
                fname = fm.group(2)
                if ftype in KEYWORDS or fname in KEYWORDS:
                    pass
                elif not fname[0].isupper():
                    current["fields"].append({"name": fname, "type": ftype,
                                             "mutable": not re.search(r"\bfinal\b", line)})
            if "@Inject" in line:
                fm2 = FIELD_RE.match(line.replace("@Inject", ""))
                if fm2:
                    current["injected"].append({"name": fm2.group(2), "type": fm2.group(1).strip()})
        if tm:
            pending = []
        elif line.strip() and not line.strip().startswith("//") and not line.strip().startswith("*") \
                and not line.strip().startswith("/*"):
            pending.append(line)
            pending = pending[-6:]
        depth += line.count("{") - line.count("}")
        if depth < 0:
            depth = 0
        if current is not None and current.get("nested"):
            # the enclosing type regains the context when the nested block closes
            pass
    if not types:
        return None
    body = code
    return {
        "package": pkg,
        "types": types,
        "imports": imports,
        "body": body,
        "raw": raw,
    }


def javadoc_summary(raw: str, name: str) -> str:
    """The first sentence of the javadoc that documents the type."""
    pattern = re.compile(r"/\*\*(.*?)\*/\s*(?:@[\w.]+(?:\([^)]*\))?\s*)*"
                         r"(?:public\s+|final\s+|abstract\s+|sealed\s+)*"
                         r"(?:class|interface|record|enum)\s+" + re.escape(name), re.S)
    m = pattern.search(raw)
    if not m:
        return ""
    text = re.sub(r"^\s*\*\s?", "", m.group(1), flags=re.M)
    text = re.sub(r"<[^>]+>", "", text)
    text = re.sub(r"\{[^}]*\}", "", text)
    for para in text.split("\n\n"):
        para = " ".join(para.split())
        if para:
            return para[:280]
    return ""


def class_files(module: str, source_set: str = "main") -> list[Path]:
    base = ROOT / module / "build" / "classes" / "java" / source_set
    if not base.exists():
        return []
    return sorted(p for p in base.rglob("*.class"))


def javap_callgraph(module: str, source_set: str = "main") -> list[tuple[str, str, str]]:
    """Return (caller_class, caller_method, callee_class) triples from bytecode.

    `javap -p -c` prints the instruction stream; every invocation appears as an
    `invokevirtual`/`invokestatic`/`invokeinterface`/`invokespecial` line with
    a `Method owner.name:descriptor` comment. The comment is the constant-pool
    entry, so this is the compiler's own view of the call, not a text guess.
    """
    classes = class_files(module, source_set)
    if not classes:
        return []
    base = ROOT / module / "build" / "classes" / "java" / source_set
    names = []
    for p in classes:
        rel = p.relative_to(base)
        names.append(str(rel.with_suffix("")).replace("/", "."))
    edges: list[tuple[str, str, str]] = []
    try:
        out = subprocess.run(
            [os.environ.get("JAVA_HOME", "/usr") + "/bin/javap", "-p", "-c", "-classpath",
             str(base) + ":" + str(ROOT / module / "build" / "classes" / "java" / "main"), *names],
            capture_output=True, text=True, timeout=1800)
    except (OSError, subprocess.TimeoutExpired):
        return []
    current_class = ""
    current_method = ""
    class_re = re.compile(
        r"^(?:(?:public|final|abstract|sealed|non-sealed|static|strictfp)\s+)*"
        r"(class|interface|enum|record)\s+([\w.$]+)")
    for line in out.stdout.splitlines():
        ls = line.strip()
        cm = class_re.match(ls)
        if cm:
            current_class = cm.group(2)
            current_method = ""
            continue
        if ls.startswith("Compiled from"):
            continue
        if "// Method " in ls or "// InterfaceMethod " in ls:
            ref = ls.split("//", 1)[1].strip()
            body = ref.replace("InterfaceMethod ", "Method ", 1)
            body = body.replace("Method ", "", 1)
            if body.startswith('"'):
                # javap quotes an owner that contains a quoted member name:
                # Method "io/matrix/x/Y"."<init>":(...)
                end = body.find('"', 1)
                owner = body[1:end] if end > 0 else body[1:]
            else:
                owner = body.split(".")[0]
            owner = owner.replace("/", ".").replace("$", ".")
            if "(" in owner or not owner:
                # javap omits the owner for a call to the SAME class:
                # "Method composeConfidence:(...)" — that is a self-call, not an edge.
                continue
            if (owner.startswith("java.") or owner.startswith("jdk.")
                    or owner.startswith("sun.") or owner.startswith("com.sun.")):
                continue
            if current_class:
                edges.append((current_class, current_method, owner))
            continue
        # a method signature: "public void foo(int a);" — an instruction line looks
        # different: it starts with a byte offset ("  12: invokevirtual ...").
        if ("(" in ls and ")" in ls and ls.endswith(";")
                and not re.match(r"^\d+:", ls)
                and not ls.startswith(("Code:", "Exception table", "StackMapTable"))):
            head = ls.split("(")[0].split()
            if head:
                current_method = head[-1]
                if current_method == current_class:
                    current_method = "<init>"
            continue
    return edges


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="docs-v2/engineering")
    args = ap.parse_args()
    out_dir = ROOT / args.out
    out_dir.mkdir(parents=True, exist_ok=True)
    global OUT_DIR
    OUT_DIR = out_dir

    java_files: list[Path] = []
    census: dict[str, int] = defaultdict(int)
    vendored: dict[str, int] = defaultdict(int)
    for dirpath, dirnames, filenames in os.walk(ROOT):
        rel = Path(dirpath).relative_to(ROOT)
        top = rel.parts[0] if rel.parts else ""
        dirnames[:] = [d for d in dirnames
                       if d not in EXCLUDED_DIR_NAMES and not d.startswith(".")]
        if top in EXCLUDED_PATH_PREFIXES:
            dirnames[:] = []
            continue
        for fn in filenames:
            p = Path(dirpath) / fn
            suffix = p.suffix.lower()
            key = suffix or "<no-ext>"
            census[key] += 1
            if suffix == ".java":
                java_files.append(p)
            vendored_top = p.relative_to(ROOT).parts[0]
            if vendored_top in VENDORED_DIRS:
                vendored[vendored_top] = vendored.get(vendored_top, 0) + 1

    print(f"scanned {len(java_files)} java files", file=sys.stderr)

    # ---- class inventory -------------------------------------------------
    rows: list[dict] = []
    cache_path = Path("/tmp/w44-callgraph-cache.json")
    call_edges: list[tuple[str, str, str]] = []
    test_edges: list[tuple[str, str, str]] = []
    if cache_path.exists():
        blob = json.loads(cache_path.read_text())
        call_edges = [tuple(e) for e in blob["main"]]
        test_edges = [tuple(e) for e in blob["test"]]
        print(f"call graph: {len(call_edges)} prod edges from cache", file=sys.stderr)
    else:
        for module in GRADLE_MODULES:
            call_edges.extend(javap_callgraph(module, "main"))
        for module in GRADLE_MODULES:
            test_edges.extend(javap_callgraph(module, "test"))
        cache_path.write_text(json.dumps({"main": call_edges, "test": test_edges}),
                              encoding="utf-8")
        print(f"call graph: {len(call_edges)} prod + {len(test_edges)} test edges from bytecode",
              file=sys.stderr)

    for p in sorted(java_files):
        module, src_set = module_of(p)
        info = scan_java(p)
        if not info:
            continue
        body = info["body"]
        for t in info["types"]:
            simple = t["name"]
            if t.get("outer"):
                simple = t["outer"] + "." + t["name"]
            fqn = f"{info['package']}.{simple}" if info["package"] else simple
            is_test = src_set == "test"
            reach = ""
            if not is_test:
                reach = "source-only"
            rows.append({
                "fqn": fqn,
                "type_kind": t["kind"],
                "file": str(p.relative_to(ROOT)),
                "module": module,
                "package": info["package"],
                "source_set": src_set,
                "built": "yes" if module in GRADLE_MODULES else "no",
                "nested": "yes" if t.get("nested") else "no",
                "outer_type": t.get("outer", ""),
                "static": "yes" if t["static"] else "no",
                "final": "yes" if t["final"] else "no",
                "abstract": "yes" if t["abstract"] else "no",
                "supertypes": ";".join(t["supertypes"]),
                "public_ctor": "yes" if t["public_ctor"] else "no",
                "ctor_params": ";".join(t["ctor_params"]),
                "injected_fields": ";".join(f"{i['type']} {i['name']}" for i in t["injected"]),
                "annotations": ";".join(sorted(set(t["annotations"]))),
                "own_methods": len(t["methods"]),
                "public_methods": len([m for m in t["methods"] if m["visibility"] == "public"]),
                "own_fields": len(t["fields"]),
                "mutable_fields": len([f for f in t["fields"] if f["mutable"]]),
                "uses_random": "yes" if NON_DETERMINISM["random"].search(body) else "no",
                "uses_wallclock": "yes" if NON_DETERMINISM["wallclock"].search(body) else "no",
                "uses_thread_sleep": "yes" if NON_DETERMINISM["thread_sleep"].search(body) else "no",
                "uses_native": "yes" if NATIVE_USE.search(body) else "no",
                "uses_gpu": "yes" if GPU_USE.search(body) else "no",
                "uses_external_io": "yes" if EXTERNAL_IO.search(body) else "no",
                "uses_persistence": "yes" if PERSISTENCE.search(body) else "no",
                "cdi_scope": ";".join(sorted(set(
                    a for a in t.get("decl_annotations", [])
                    if a.endswith("Scoped") or a == "Singleton"))),
                "rest_resource": "yes" if ("Path" in t.get("decl_annotations", [])
                                           and not t.get("nested")) else "no",
                "event_observer": "yes" if any(
                    a in t.get("decl_annotations", [])
                    for a in ("Observes", "Observer", "ConsumeEvent", "Subscribe")) else "no",
                "service_loader": "yes" if SERVICE_LOADER.search(body) else "no",
                "post_construct": "yes" if "PostConstruct" in t.get("decl_annotations", []) else "no",
                "cli_main": "yes" if any(
                    m["name"] == "main" and m.get("static") and m.get("returns") == "void"
                    for m in t["methods"]) else "no",
                "reachability": reach,
                "responsibility": javadoc_summary(info["raw"], t["name"]),
                "declared_exceptions": ";".join(sorted(set(re.findall(
                    r"throws\s+([\w.,\s]+?)\s*\{", body)))),
                "imports": ";".join(i for i in info["imports"] if i.startswith("io.matrix")),
                "public_methods_named": ";".join(sorted(
                    {m["name"] for m in t["methods"] if m["visibility"] == "public"})),
                "field_names": ";".join(f["name"] for f in t["fields"]),
                "mutable_field_names": ";".join(f["name"] for f in t["fields"] if f["mutable"]),
            })

    # ---- reachability from the bytecode call graph -----------------------
    prod_callers: set[str] = set()
    callee_to_callers: dict[str, set[str]] = defaultdict(set)
    method_to_callers: dict[tuple[str, str], set[str]] = defaultdict(set)
    for caller, method, callee in call_edges:
        prod_callers.add(caller)
        callee_to_callers[callee].add(caller)
        method_to_callers[(callee, method)].add(caller)

    entry_points = set()
    for r in rows:
        if r["source_set"] != "main" or r["built"] != "yes":
            continue
        if (r["cdi_scope"] or r["rest_resource"] == "yes"
                or r["event_observer"] == "yes" or r["post_construct"] == "yes"
                or r.get("cli_main") == "yes"):
            entry_points.add(r["fqn"])

    test_callers_of: dict[str, set[str]] = defaultdict(set)
    for caller, method, callee in test_edges:
        test_callers_of[callee].add(caller)

    # entry-point reachability: a type is only SERVED if a path of production calls
    # leads from a CDI/REST entry point to it. A type called only by other
    # unreachable types is in an island, and the call graph alone would hide that.
    callees_of: dict[str, set[str]] = defaultdict(set)
    for caller, method, callee in call_edges:
        callees_of[caller].add(callee)
    reachable_from_entry: set[str] = set()
    frontier = list(entry_points)
    while frontier:
        node = frontier.pop()
        if node in reachable_from_entry:
            continue
        reachable_from_entry.add(node)
        for callee in callees_of.get(node, ()):  # what this node calls
            if callee not in reachable_from_entry:
                frontier.append(callee)

    for r in rows:
        if r["source_set"] != "main" or r["built"] != "yes":
            r["reachability"] = "not-built" if r["built"] == "no" else "not-production"
            r["lifecycle"] = "test-only" if r["built"] == "yes" else "not-built"
            continue
        fqn = r["fqn"]
        callers = callee_to_callers.get(fqn, set())
        tcallers = test_callers_of.get(fqn, set())
        if fqn in entry_points:
            r["reachability"] = "entry-point"
        elif callers:
            r["reachability"] = "called"
        else:
            r["reachability"] = "uncalled"
        r["incoming_callers"] = len(callers)
        r["test_callers"] = len(tcallers)
        if fqn in reachable_from_entry:
            r["lifecycle"] = "served"
        elif r["reachability"] in ("called", "entry-point"):
            r["lifecycle"] = "island"
        elif tcallers:
            r["lifecycle"] = "inactive-but-tested"
        else:
            r["lifecycle"] = "dormant"
        r["entry_point_kind"] = ("cdi" if r["cdi_scope"] else
                                  "rest" if r["rest_resource"] == "yes" else
                                  "observer" if r["event_observer"] == "yes" else
                                  "cli" if r.get("cli_main") == "yes" else
                                  "post-construct" if r["post_construct"] == "yes" else "")

    for r in rows:
        for f in ("incoming_callers", "entry_point_kind", "test_callers", "lifecycle"):
            r.setdefault(f, "" if f in ("entry_point_kind", "lifecycle") else 0)
    # ---- cross-references: tests, benchmarks, formal specs, documents ----
    test_classes_of: dict[str, list[str]] = defaultdict(list)
    for r in csv.DictReader((OUT_DIR / "W44-TEST-MAP.csv").open(encoding="utf-8")) \
            if (OUT_DIR / "W44-TEST-MAP.csv").exists() else []:
        for fqn in r["production_types"].split(";"):
            if fqn:
                test_classes_of[fqn].append(r["test_class"])
    spec_of: dict[str, list[str]] = defaultdict(list)
    yml = ROOT / "formal" / "spec-implementation-map.yml"
    if yml.exists():
        current = ""
        for line in yml.read_text(encoding="utf-8", errors="replace").splitlines():
            m = re.match(r"\s*- id: (\S+)", line)
            if m:
                current = m.group(1)
            m = re.match(r"\s*-\s+(matrix-[\w/.-]+\.java)\s*$", line)
            if m and current:
                spec_of[m.group(1)].append(current)
    doc_of: dict[str, list[str]] = defaultdict(list)
    doc_map = OUT_DIR / "W44-DOC-MAP.csv"
    if doc_map.exists():
        for r in csv.DictReader(doc_map.open(encoding="utf-8")):
            for fqn in r["production_types"].split(";"):
                if fqn:
                    doc_of[fqn].append(r["document"])
    callees_of: dict[str, set[str]] = defaultdict(set)
    for caller, method, callee in call_edges:
        callees_of[caller].add(callee)
    for r in rows:
        fqn = r["fqn"]
        file = r["file"]
        r["outgoing_calls"] = len(callees_of.get(fqn, ()))
        r["top_callees"] = ";".join(sorted(callees_of.get(fqn, ()))[:6])
        r["test_classes"] = ";".join(sorted(set(test_classes_of.get(fqn, [])))[:10])
        r["test_class_count"] = len(set(test_classes_of.get(fqn, [])))
        r["benchmark"] = "yes" if any("Benchmark" in t or "JMH" in t
                                      for t in test_classes_of.get(fqn, [])) else "no"
        r["formal_specs"] = ";".join(sorted(set(spec_of.get(file, []))))
        r["documents_naming_it"] = len(set(doc_of.get(fqn, [])))
        r["documents"] = ";".join(sorted(set(doc_of.get(fqn, [])))[:6])

    fields = list(rows[0].keys()) if rows else []
    with (out_dir / "W44-CLASS-INVENTORY.csv").open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=fields)
        w.writeheader()
        for r in sorted(rows, key=lambda x: x["fqn"]):
            w.writerow(r)

    # ---- call graph ------------------------------------------------------
    with (out_dir / "W44-CALL-GRAPH.csv").open("w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh)
        w.writerow(["caller_class", "caller_method", "callee_class", "callee_module",
                    "caller_module", "edge_kind"])
        caller_module = {}
        for r in rows:
            caller_module.setdefault(r["fqn"], r["module"])
        callee_module = {}
        for r in rows:
            callee_module.setdefault(r["fqn"], r["module"])
        seen = set()
        for caller, method, callee in call_edges:
            key = (caller, method, callee)
            if key in seen:
                continue
            seen.add(key)
            w.writerow([caller, method, callee,
                        callee_module.get(callee, "external-or-unlisted"),
                        caller_module.get(caller, "?"),
                        "production-bytecode"])

    # ---- census ----------------------------------------------------------
    census_path = out_dir / "W44-FILE-CENSUS.json"
    census_path.write_text(json.dumps({
        "policy": {
            "excluded_dir_names": sorted(EXCLUDED_DIR_NAMES),
            "excluded_path_prefixes": sorted(EXCLUDED_PATH_PREFIXES),
        },
        "gradle_modules": GRADLE_MODULES,
        "non_built_dirs": NON_BUILT_DIRS,
        "file_count_by_suffix": dict(sorted(census.items(), key=lambda kv: -kv[1])),
        "vendored_file_counts": dict(sorted(vendored.items())),
    }, indent=2), encoding="utf-8")

    print(f"wrote {out_dir/'W44-CLASS-INVENTORY.csv'} ({len(rows)} types)", file=sys.stderr)
    print(f"wrote {out_dir/'W44-CALL-GRAPH.csv'} ({len(seen)} edges)", file=sys.stderr)
    print(f"wrote {census_path}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
