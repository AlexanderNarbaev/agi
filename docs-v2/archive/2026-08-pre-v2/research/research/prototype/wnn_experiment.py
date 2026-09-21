#!/usr/bin/env python3
"""
EXP-пилот H-010: однопроходовый WNN-producer (WiSARD-стиль) против эталонов прототипа.
RAM-узел WNN структурно тождественен TT BirUnit (SUBSTRATE-MODELS §2.2):
обучение = запись 1 по адресу, один проход по корпусу, без градиентов и эпох.
Запуск: python3 wnn_experiment.py (нужны numpy, scikit-learn; MNIST через openml)
"""
import time, numpy as np
from sklearn.datasets import fetch_openml

class WiSARD:
    """WiSARD: ансамбль RAM-узлов (LUT 2^tuple), однопроходовая запись, bleaching при предсказании."""
    def __init__(self, n_bits, tuple_size=8, n_classes=10, seed=42, n_tuples=None):
        rng = np.random.default_rng(seed)
        nt = n_tuples or max(1, n_bits // tuple_size)
        self.maps = [rng.choice(n_bits, size=tuple_size, replace=False) for _ in range(nt)]
        self.tables = [[{} for _ in range(nt)] for _ in range(n_classes)]
        self.C = n_classes
    def _addr(self, x):
        return [int(sum(int(b) << i for i, b in enumerate(x[m]))) for m in self.maps]
    def fit(self, X, y):
        for xi, yi in zip(X, y):
            for t, a in zip(self.tables[int(yi)], self._addr(xi)):
                t[a] = t.get(a, 0) + 1
    def predict(self, X):
        out = []
        for x in X:
            addr = self._addr(x)
            b = 1
            while b <= 256:
                scores = [sum(1 for t, a in zip(self.tables[c], addr) if t.get(a, 0) >= b)
                          for c in range(self.C)]
                mx = max(scores)
                if scores.count(mx) == 1 and mx > 0:
                    out.append(int(np.argmax(scores))); break
                b += 1
            else:
                out.append(int(np.argmax([sum(t.get(a, 0) for t, a in zip(self.tables[c], addr))
                                          for c in range(self.C)])))
        return np.array(out)

# --- MUX-11 ---
rng = np.random.default_rng(7)
def mux11(X):
    a = X[:, :3] @ np.array([4, 2, 1])
    return X[np.arange(len(X)), 3 + a]
Xmux = rng.integers(0, 2, size=(4096, 11)).astype(np.uint8)
ymux = mux11(Xmux)
tr, te = slice(0, 3072), slice(3072, None)
for ts in (4, 8):
    t0 = time.perf_counter()
    w = WiSARD(11, tuple_size=ts, n_classes=2, seed=42, n_tuples=8)
    w.fit(Xmux[tr], ymux[tr]); t1 = time.perf_counter()
    print(f"MUX-11 tuple={ts}: acc={(w.predict(Xmux[te]) == ymux[te]).mean():.4f}, "
          f"обучение={1000*(t1-t0):.1f} мс")

# --- MNIST: термометрический код (quantile+термометр, DESIGN-03 §2.1) ---
mnist = fetch_openml('mnist_784', version=1, as_frame=False, parser='auto')
Xf = mnist.data / 255.0
ym = mnist.target.astype(int)
ytr, yte = ym[:60000], ym[60000:]
Xmt = np.concatenate([(Xf > t) for t in (0.25, 0.5, 0.75)], axis=1).astype(np.uint8)
Xtr_t, Xte_t = Xmt[:60000], Xmt[60000:]
for ts, nt in ((8, 294), (28, 84)):
    t0 = time.perf_counter()
    w = WiSARD(784*3, tuple_size=ts, n_classes=10, seed=42, n_tuples=nt)
    w.fit(Xtr_t, ytr); t1 = time.perf_counter()
    pred = w.predict(Xte_t[:2000]); t2 = time.perf_counter()
    print(f"MNIST термометр×3 tuple={ts} узлов={nt}: acc={(pred == yte[:2000]).mean():.4f}, "
          f"обучение={t1-t0:.1f} с, инференс={(t2-t1)/2000*1e6:.0f} мкс/образец (python-скобка)")
