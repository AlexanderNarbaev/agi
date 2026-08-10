#!/usr/bin/env python3
"""
Прототип BIR-вычислений (research-контур, допустим Python — CONSTITUTION VII.1).
Назначение: измерение характеристик форм TT/CLAUSESET и конвейера
«дерево → клаузы» (TREPAN-style, DESIGN-04 §3.1) на ноутбучном железе.
Числа из этого прототипа — прототипные (numpy), продакшн-цели — JVM/JMH (METRICS.md).

Запуск: python3 bir_prototype.py  (нужны numpy, scikit-learn; MNIST грузится через openml)
Выход: печать результатов + prototype/results.json
"""
import json, time, numpy as np

rng = np.random.default_rng(42)
results = {}

# ---------- 1. TT-форма: evaluate, память ----------
k = 16
tt = rng.integers(0, 2, size=2**k, dtype=np.uint8)
tt_packed = np.packbits(tt, bitorder='little').view(np.uint64)

def eval_tt_batch(packed, idx):
    idx = idx.astype(np.uint64)
    w = packed[idx >> np.uint64(6)]
    return ((w >> (idx & np.uint64(63))) & np.uint64(1)).astype(np.uint8)

idx = rng.integers(0, 2**k, size=5_000_000).astype(np.uint64)
assert (eval_tt_batch(tt_packed, idx[:10000]) == tt[idx[:10000].astype(int)]).all()
t0 = time.perf_counter(); eval_tt_batch(tt_packed, idx); t1 = time.perf_counter()
results['tt_eval_ns'] = (t1 - t0) / len(idx) * 1e9
results['tt_memory_k16_bytes'] = int(tt_packed.nbytes)

# ---------- 2. CLAUSESET: evaluate, память ----------
def cs_predict(masks, X):
    votes = np.zeros((len(X), len(masks)), int)
    for c, (P, Ng) in masks.items():
        fire = ~((P[None, :, :] & ~X[:, None, :]).any(axis=2) |
                 (Ng[None, :, :] & X[:, None, :]).any(axis=2))
        votes[:, c] = fire.sum(axis=1)
    return votes.argmax(axis=1)

# ---------- 3. Конвейер «дерево → клаузы» (TREPAN-style) ----------
from sklearn.tree import DecisionTreeClassifier

def tree_to_clauses(tree, n_classes):
    clauses = {c: [] for c in range(n_classes)}
    L, R, F = tree.children_left, tree.children_right, tree.feature
    def walk(n, path):
        if L[n] == -1:
            clauses[int(tree.value[n][0].argmax())].append(path); return
        walk(L[n], path + [(F[n], 1)])   # ¬x_f
        walk(R[n], path + [(F[n], 0)])   # x_f
    walk(0, []); return clauses

def clauses_to_masks(clauses_by_class, kf):
    out = {}
    for c, paths in clauses_by_class.items():
        P = np.zeros((len(paths), kf), bool); Ng = np.zeros((len(paths), kf), bool)
        for i, p in enumerate(paths):
            for f, kind in p:
                (P if kind == 0 else Ng)[i, f] = True
        out[c] = (P, Ng)
    return out

# --- 3a. MNIST (бинаризация по пер-пиксельной медиане, офлайн-границы) ---
from sklearn.datasets import fetch_openml
mnist = fetch_openml('mnist_784', version=1, as_frame=False, parser='auto')
Xr, yr = mnist.data[:20000], mnist.target[:20000].astype(int)
Xt, yt = mnist.data[60000:70000], mnist.target[60000:70000].astype(int)
med = np.median(Xr, axis=0)
Xb, Xtb = (Xr > med), (Xt > med)
kf = Xb.shape[1]

t0 = time.perf_counter()
dt = DecisionTreeClassifier(max_depth=14, min_samples_leaf=5, random_state=0).fit(Xb[:10000], yr[:10000])
masks = clauses_to_masks(tree_to_clauses(dt.tree_, 10), kf)
results['mnist_tree_acc'] = float(dt.score(Xtb, yt))
results['mnist_cs_acc'] = float((cs_predict(masks, Xtb) == yt).mean())
results['mnist_clauses'] = int(sum(len(p[0]) for p in masks.values()))
results['mnist_model_bytes_bool'] = int(sum(p[0].nbytes + p[1].nbytes for p in masks.values()))
results['mnist_model_bytes_packed'] = results['mnist_model_bytes_bool'] // 8
t0 = time.perf_counter(); cs_predict(masks, Xtb[:2000]); t1 = time.perf_counter()
results['mnist_cs_eval_us'] = (t1 - t0) / 2000 * 1e6

# --- 3b. Multiplexer-11: точное восстановление + эквивалентность перечислением ---
def make_mux(n):
    X = rng.integers(0, 2, size=(n, 11)).astype(bool)
    addr = X[:, 0].astype(int) * 4 + X[:, 1].astype(int) * 2 + X[:, 2].astype(int)
    return X, X[np.arange(n), 3 + addr]

Xmu, ymu = make_mux(4096)
dt_mu = DecisionTreeClassifier(random_state=0).fit(Xmu, ymu)
masks_mu = clauses_to_masks(tree_to_clauses(dt_mu.tree_, 2), 11)
results['mux11_fidelity'] = float((cs_predict(masks_mu, Xmu) == dt_mu.predict(Xmu)).mean())
results['mux11_clauses'] = int(sum(len(p[0]) for p in masks_mu.values()))

tt_mu = np.zeros(2**11, np.uint8)
for i in range(2**11):
    b = [(i >> j) & 1 for j in range(11)]
    tt_mu[i] = b[3 + b[0] * 4 + b[1] * 2 + b[2]]
equiv = all(cs_predict(masks_mu, np.array([[(i >> j) & 1 for j in range(11)]], dtype=bool))[0] == tt_mu[i]
            for i in range(2**11))
results['mux11_equiv_enumeration'] = bool(equiv)

# --- 3c. Parity-8: потолок DNF ---
Xpa = rng.integers(0, 2, size=(2048, 8)).astype(bool); ypa = Xpa.sum(axis=1) % 2
dt_pa = DecisionTreeClassifier(random_state=0).fit(Xpa, ypa)
masks_pa = clauses_to_masks(tree_to_clauses(dt_pa.tree_, 2), 8)
results['parity8_acc'] = float((cs_predict(masks_pa, Xpa) == ypa).mean())
results['parity8_clauses_for_1'] = int(len(masks_pa[1][0]))   # теор. минимум 2^7 = 128

print(json.dumps(results, indent=2, ensure_ascii=False))
with open('results.json', 'w') as f:
    json.dump(results, f, indent=2, ensure_ascii=False)