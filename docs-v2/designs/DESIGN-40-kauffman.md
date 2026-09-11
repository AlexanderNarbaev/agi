# DESIGN-40 — Kauffman Boolean Networks (Random Boolean Networks)

> RUN 378. Из systems biology (Kauffman 1969) — random Boolean
> networks: N nodes, each with K inputs, random Boolean function.
> Self-organized criticality на edge of order/chaos. Применяем к
> layer dynamics.

## 1. Источник

- Kauffman S.A. (1969) "Metabolic stability and epigenesis in
  randomly constructed genetic nets"
- Kauffman S.A. (1993) "The Origins of Order"

## 2. KauffmanNetwork

```java
public final class KauffmanNetwork {
    /** Pure function. Update all nodes according to their
     *  random Boolean function. */
    public static boolean[] step(boolean[] state, int[][] functions,
                                 int[][] inputs);
}
```

## 3. Применение

- Layer-level dynamics: each layer as a Kauffman network
- Criticality analysis: frozen vs chaotic
- Phase transition: K=2 frozen, K=4 critical, K=5+ chaotic

## 4. CONSTITUTION

- I: pure
- V: тесты
