# Анализ аппаратных мощностей — MATRIX

**Дата:** 2026-07-06
**Статус:** Актуально

---

## 1. Железо хоста

| Компонент | Характеристики |
|-----------|---------------|
| **CPU** | AMD Ryzen 9 9955HX |
| Архитектура | Zen 5 (AVX-512, AVX_VNNI, BF16) |
| Ядра (physical) | 16 |
| Потоки (logical) | 32 (2 threads/core) |
| **RAM** | 59 GiB |
| Используется | ~29 GiB |
| Свободно | ~29 GiB |
| Swap | 37 GiB (3.8 GiB used) |
| **GPU** | NVIDIA GeForce RTX 5070 |
| VRAM | 12 GB (12227 MiB) |
| CUDA | 13.2 |
| Driver | 595.71.05 |
| Загрузка | 6% GPU, 14 MiB VRAM (свободна) |
| **Диск** | 469 GB NVMe |
| Занято | 187 GB |
| Свободно | 259 GB (58%) |

---

## 2. Minikube K8s кластер

| Параметр | Значение |
|----------|----------|
| Версия | v1.35.1 |
| Driver | docker |
| CPU allocatable | 32 ядра (все логические) |
| Memory allocatable | ~59.5 GB (62390608 KiB) |
| Storage allocatable | ~468 GB (491106976 KiB) |

### Running pods (namespace: matrix)

| Pod | CPU Request | Memory Request | Статус |
|-----|-------------|----------------|--------|
| matrix-core | — | — | Running |
| paper-server | — | — | Running |
| postgres | — | — | Running |
| redis | — | — | Running |
| kafka | — | — | Running |
| minio | — | — | Running |
| prometheus | — | — | Running |
| grafana | — | — | Running |
| jaeger | — | — | Running |

> **Примечание:** Metrics server не установлен — точной статистики потребления по подам нет.

---

## 3. Docker ресурсы

| Тип | Total | Active | Reclaimable |
|-----|-------|--------|-------------|
| Images | 36.3 GB | 11 шт | 12.64 GB (34%) |
| Containers | 2.8 GB | 9 шт | 2.74 GB (97%) |
| Volumes | 7.5 GB | 11 шт | 80 MB (1%) |
| Build Cache | 877 MB | — | 877 MB |

**Можно освободить:** ~16 GB через `docker system prune`

---

## 4. Оценка для ML/инференса

### Текущие возможности

| Задача | Оценка | Комментарий |
|--------|--------|-------------|
| Инференс <1B моделей | ✅ Отлично | CPU + RAM достаточно |
| Инференс 1-3B моделей | ✅ Хорошо | 59 GB RAM позволяют загрузить веса |
| Инференс 3-7B моделей | ⚠️ Возможно | С квантизацией (4-bit/8-bit) |
| Инференс >7B моделей | ❌ Сложно | Требуется GPU offloading |
| GPU инференс (CUDA) | ✅ Отлично | RTX 5070 12GB свободна |
| Конвертация весов | ✅ Отлично | CPU-bound, 32 потока |
| Обучение (fine-tune) | ⚠️ Ограничено | 12GB VRAM для LoRA/QLoRA |

### Рекомендации по ресурсам

| Модель | RAM для инференса | VRAM (GPU) | Рекомендация |
|--------|-------------------|------------|--------------|
| <1B (Qwen3-0.6B, SmolLM2-360M) | ~2 GB | ~1.5 GB | CPU или GPU |
| 1-2B (Qwen3-1.7B, DeepSeek-R1-1.5B) | ~4 GB | ~3 GB | GPU предпочтительнее |
| 3-4B (Phi-4-mini, SmolLM3-3B) | ~8 GB | ~6 GB | GPU с квантизацией |
| 7B+ (Qwen3-8B, Llama-3.1-8B) | ~16 GB | ~10 GB | Только GPU + 4-bit |

---

## 5. Узкие места

1. **Metrics server** — не установлен в minikube, нет мониторинга потребления подов
2. **GPU utilization** — RTX 5070 практически не используется (6% загрузка)
3. **Docker images** — 12.64 GB reclaimable, стоит почистить
4. **Jaeger v1** — EOL 2025-12-31, нужна миграция на Jaeger v2

---

## 6. Рекомендации

1. **Установить metrics server** для мониторинга потребления подов
2. **Использовать GPU** для инференса моделей 1-4B (CUDA доступен)
3. **Очистить Docker** — `docker system prune` освободит ~16 GB
4. **Настроить GPU passthrough в minikube** для GPU-инференса в K8s
5. **Мигрировать на Jaeger v2** до конца 2025 года
