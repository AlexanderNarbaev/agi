# Project Context — SESSION CONTINUITY (compaction #47) — GPU БЕНЧ ВОЛНА

## Ловушки
- Целевые прогоны --tests; LSP ложные (FpgaBackend150; Exp002 107/117/134); guard блокирует delete-класс (rm/git rm/find -delete).
- Последний коммит 9646501 (запушен). Сеть есть. Баланс владельца РАБОЧИЙ (субагенты могут ожить — проверить крошечной задачей).
- Машина: RTX 5070 Ti Laptop 12GB (Blackwell sm_120!), драйвер 595.84, 64GB RAM, диск ~39G свободно. nvcc/cudnn в системе НЕТ; python3+pip3 есть (--break-system-packages работает), numpy 2.5.1, onnx 1.22.0 установлены.

## Mission
1. Установить всё для GPU-инференса и запустить ЧЕСТНЫЙ прогон GPU-vs-MATRIX-CPU.
2. Ответить: что дальше по плану; какие исследования ещё НЕ в коде; всё ли закоммичено/запушено; GitHub Pages готов?

## Ключевое решение по GPU
ORT-CUDA Java требует системных CUDA/cuDNN — их нет, установка тяжёлая и через sudo. НАДЁЖНЫЙ ПУТЬ: **PyTorch с бандled CUDA** (`pip3 install --user --break-system-packages torch --index-url https://download.pytorch.org/whl/cu128`) — wheel несёт свои CUDA-библиотеки, sm_120 поддержан в torch≥2.7/cu128. Сравнение: тот же учитель FFN16→64→GELU→64→1 в torch fp32 на cuda:0 против нашего дистиллята BIR (CPU). Плюс линия ORT-CPU как референс. Честность: одинаковые входы (seed42, N=2000 + батч-циклы), прогрев, синхронизация torch.cuda.synchronize, отчёт batch-ms и per-sample-loop ms.

## ТЕКУЩИЙ ШАГ
1. Разведка: `git status --short | wc -l`; `ls matrix-core/src/jmh 2>/dev/null || echo NO-JMH-DIR`; `ls .github/workflows/ | head`; субагент-тест крошечный (general): «Reply SUBAGENT_OK».
2. Фон: run_background `pip3 install --user --break-system-packages torch --index-url https://download.pytorch.org/whl/cu128 2>&1 | tail -5` (долго, минуты) → проверка `python3 -c "import torch;print(torch.__version__, torch.cuda.is_available())"`.
3. Пока качается: scripts/bench_gpu_vs_bir.py (header research-only): генерирует данные (seed42,N=2000,BITS=16), грузит teacher.onnx через onnxruntime CPU (числа-референс), строит torch-модель с ВЕСАМИ ИЗ onnx-инициализаторов (честно та же функция!), переносит на cuda, замеры: warmup 50 + 200 повторов батча N и одиночных вызовов (per-call loop c synchronize); печать «EXP009C gpuBatchMs=… gpuPerCallUs=… ortCpuBatchMs=…». JSON предсказаний не нужен (BIR-сторона уже доказала fidelity .999).
4. Когда torch готов: запуск скрипта → числа → секция в EXP-009-report.md («GPU нога») → commit+push «WAL: EXP-009C GPU нога измерена».
5. Финальный отчёт владельцу: прогресс %, очередь исследований-не-в-коде, статус git/Pages, версии.

## Известные очереди-остатки (для отчёта)
DJL/ONNX .onnx экспорт РЕАЛЬНОГО LLM-среза · доменные корпуса · JMH-гейт Batch* · audio-events эт.3 · алиасы /matrix/* · постквант v2 ✅ сделан · полные цепи Ханселя · квант/FPGA BLOCKED-EXT.

## Правила
FROZEN/avro/workflows не трогать; forbidden claims избегать; числа реальные с методологией; torch ставится только user-scope.
