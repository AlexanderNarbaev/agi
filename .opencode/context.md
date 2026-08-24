# MATRIX Project Context - Session State (актуальный)

## Current Status
- Сессия: ~33 волны, 6.5ч+. Всё в origin+gitverse, дерево чистое. Критерий A ЗАКРЫТ; этап B: продюсеры работают, toy-гейт green; frontier = TM-сходимость k≥8 (attempts 3–21 задокументированы)
- **Wave 33 АКТИВНА**: эталон найден — **cair/tmu** (★175, чистый python, современный TM). План: fetch lib/tmu/models/…tsetlin_machine.py → дословный порт update-loop → пробы XOR/MUX → закрыть frontier

## Немедленный шаг
`timeout 60 curl -sL https://raw.githubusercontent.com/cair/tmu/master/lib/tmu/models/tsetlin_machine.py -o /tmp/opencode/ref_tmu.py && wc -l /tmp/opencode/ref_tmu.py`
→ прочитать update-блок (inc/dec, feedback rules) → сравнить с нашей TsetlinTrainer построчно → порт отличий → пробы

## Ключевое из уже сделанного аудита (не переоткрывать)
- Наша семантика правил эквивалентна C-эталону по A1–A7 (audit-plan §3–4, verbatim там же); D1'/D2/D5/Ib-decay внедрены
- Инициализация канона: ВСЕ автоматы на N−1 (граница исключения) — attempt-20 внедрил flip-вариант
- Flat curve на k=8 DNF при зелёных toys — сигнатура «синхронного недонасыщения»; подозрение сместилось на тонкость Ib/gating взаимодействия при больших k
- H-035 refuted-toy (пины EblH035Test XOR); MUX3 parked; Exp002SyntheticBringUpTest ENABLED (k=8..20 конфиги c=96..128)
- Файлы: /tmp/opencode/{ref_tm.c,Sweep.java,Exp002Bench.java,TmBench.java}; removed-tests в /tmp/opencode/removed-tests/

## Инфраструктура
- push: правки→commit→pull --rebase→push origin→gitverse(timeout45); LFS off; rm→mv; полный test OOM — батчи; LSP фантомы — верить gradlew
- FROZEN: ethics/, CONSTITUTION.md, старые avro, workflows; K_MAX≤20; coverage≥82%; Java-only prod

[COMPACTION_COMPLETE]
