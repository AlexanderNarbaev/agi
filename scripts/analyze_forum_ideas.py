#!/usr/bin/env python3
"""
SINV Forum Ideas Analyzer — extracts themes, categories, and generates
training pairs for the MATRIX project from forum ideas JSON files.

Source: /home/alexandr-narbaev/Projects/opora/data/sinv/buffer/raw/
Output: models/training_data/forum_analysis.json
        models/training_data/forum_training_pairs.json
"""

import json
import os
import re
import sys
import random
from collections import Counter, defaultdict
from pathlib import Path

# ── Configuration ────────────────────────────────────────────────────────────
SOURCE_DIR = Path("/home/alexandr-narbaev/Projects/opora/data/sinv/buffer/raw")
OUTPUT_DIR = Path("/home/alexandr-narbaev/Projects/agi/models/training_data")
MAX_FILES = 5000
SEED = 42

# ── Target categories for MATRIX project ─────────────────────────────────────
MATRIX_INTEREST_CATEGORIES = {
    "ai_neural": {
        "label": "AI & Neural Networks",
        "keywords": [
            "искусственный интеллект", "нейросеть", "нейронная сеть",
            "машинное обучение", "глубокое обучение", "AI", "ML",
            "искусственного интеллекта", "нейросет", "алгоритм",
            "большие данные", "big data", "data science",
            "распознавание образов", "компьютерное зрение",
            "computer vision", "NLP", "обработка языка",
            "генеративн", "LLM", "GPT", "трансформер",
            "reinforcement learning", "обучение с подкреплением",
        ],
        "weight": 1.0,
    },
    "robotics_autonomous": {
        "label": "Robotics & Autonomous Systems",
        "keywords": [
            "робот", "робототехник", "автономн", "беспилот",
            "дрон", "БПЛА", "судовождение", "автопилот",
            "манипулятор", "сенсор", "датчик", "мехатрон",
            "автоматизаци", "промышленный робот",
            "экзоскелет", "андроид", "колесный робот",
        ],
        "weight": 1.0,
    },
    "education_knowledge": {
        "label": "Education & Knowledge Systems",
        "keywords": [
            "образование", "обучение", "школа", "университет",
            "вуз", "студент", "преподава", "курс",
            "знание", "компетенци", "навык", "квалификац",
            "педагог", "дидакт", "методик", "учебн",
            "просвещение", "лекци", "семинар", "тренинг",
            "онлайн-курс", "MOOC", "edtech", "EdTech",
            "цифровое образование", "цифровой университет",
        ],
        "weight": 0.9,
    },
    "innovation_management": {
        "label": "Innovation Management",
        "keywords": [
            "инноваци", "стартап", "венчур", "акселератор",
            "технопарк", "инкубатор", "коммерциализаци",
            "патент", "изобретен", "рационализатор",
            "технологическое предпринимательство",
            "трансфер технологий", "R&D", "НИОКР",
            "инновационная экосистема", "инновационного развития",
        ],
        "weight": 0.85,
    },
    "problem_solving": {
        "label": "Problem-Solving Methodologies",
        "keywords": [
            "решение проблем", "методологи", "системный анализ",
            "ТРИЗ", "изобретательск", "мозговой штурм",
            "brainstorm", "дизайн-мышление", "design thinking",
            "agile", "scrum", "lean", "бережлив",
            "проектное управление", "управление проект",
            "цикл", "итераци", "оптимизаци",
            "моделирование", "прогнозирование",
        ],
        "weight": 0.85,
    },
    "scientific_thinking": {
        "label": "Scientific Thinking",
        "keywords": [
            "наук", "исследование", "эксперимент", "гипотез",
            "лаборатори", "фундаментальн", "прикладн",
            "научный подход", "верификаци", "фальсифицируем",
            "эмпирическ", "теоретическ", "методолог",
            "междисциплинарн", "конвергенци", "синтез",
            "доказатель", "статистик", "анализ данных",
        ],
        "weight": 0.85,
    },
    "digital_transformation": {
        "label": "Digital Transformation",
        "keywords": [
            "цифровиз", "цифровая экономика", "цифровой",
            "цифровые технологии", "IT", "информационны",
            "программн", "платформ", "API", "облачн",
            "блокчейн", "квантов", "интернет вещей",
            "IoT", "Industry 4.0", "индустрия 4.0",
            "умный", "смарт", "smart",
        ],
        "weight": 0.80,
    },
    "ecology_sustainability": {
        "label": "Ecology & Sustainability",
        "keywords": [
            "эколог", "зелен", "устойчив", "возобновля",
            "климат", "углерод", "энергоэффективн",
            "переработк", "утилизац", "рециклинг",
            "биоразлагаем", "эко", "природ",
            "sustainability", "ESG",
        ],
        "weight": 0.70,
    },
    "social_development": {
        "label": "Social Development",
        "keywords": [
            "социальн", "волонтер", "благотворительн",
            "инклюз", "доступн", "качество жизни",
            "здравоохранение", "медицинск", "телемедицин",
            "демограф", "пенсион", "пожил",
        ],
        "weight": 0.65,
    },
    "transport_infrastructure": {
        "label": "Transport & Infrastructure",
        "keywords": [
            "транспорт", "дорог", "мост", "логистик",
            "инфраструктур", "строительств", "ЖКХ",
            "авиа", "железнодорож", "морск", "речн",
            "автомобиль", "электромобиль", "заправк",
        ],
        "weight": 0.60,
    },
}

# ── Extended keyword mapping for finer-grained tagging ───────────────────────
SUBCATEGORY_KEYWORDS = {
    "computer_vision": ["компьютерное зрение", "computer vision", "распознавание изображен", "видеоаналитик"],
    "nlp": ["NLP", "обработка естественного языка", "текстовый анализ", "языковая модель"],
    "robotics_industrial": ["промышленный робот", "производственный робот", "манипулятор"],
    "drones": ["дрон", "БПЛА", "беспилотный летательный", "квадрокоптер"],
    "autonomous_vehicles": ["автономный транспорт", "беспилотный автомобиль", "автопилот"],
    "online_education": ["онлайн-обучение", "дистанционное образование", "цифровая платформа обучен", "MOOC"],
    "school_education": ["школа", "школьник", "учител", "урок", "ЕГЭ", "ОГЭ"],
    "higher_education": ["университет", "вуз", "бакалавр", "магистр", "аспирант"],
    "startup_ecosystem": ["стартап", "акселератор", "венчурный", "инкубатор"],
    "triz": ["ТРИЗ", "теория решения изобретательских задач", "изобретательская задача"],
    "project_management": ["управление проектами", "проектный менеджмент", "agile", "scrum"],
    "data_science": ["data science", "аналитик данных", "big data", "машинное обучение"],
    "quantum": ["квантов", "quantum"],
    "iot": ["интернет вещей", "IoT", "умный дом", "умный город"],
    "healthcare": ["медицин", "здравоохранение", "пациент", "диагност", "лечени"],
}

# ── Helper functions ─────────────────────────────────────────────────────────

def load_json_files(source_dir, max_files=MAX_FILES):
    """Load up to max_files JSON files, skipping broken ones."""
    files = sorted(source_dir.glob("*.json"))[:max_files]
    ideas = []
    errors = 0
    for fp in files:
        try:
            if os.path.getsize(fp) == 0:
                errors += 1
                continue
            data = json.loads(fp.read_text(encoding="utf-8"))
            if "title" not in data or "text" not in data:
                errors += 1
                continue
            ideas.append(data)
        except (json.JSONDecodeError, UnicodeDecodeError, KeyError):
            errors += 1
    return ideas, errors


def extract_themes(text, keyword_map):
    """Extract themes from text using keyword matching."""
    text_lower = text.lower() if text else ""
    matches = []
    for cat, info in keyword_map.items():
        score = 0
        matched_kw = []
        for kw in info["keywords"]:
            if kw.lower() in text_lower:
                score += 1
                matched_kw.append(kw)
        if score > 0:
            matches.append({
                "category": cat,
                "label": info["label"],
                "weight": info["weight"],
                "score": score,
                "matched_keywords": matched_kw[:5],
            })
    matches.sort(key=lambda x: x["score"], reverse=True)
    return matches


def classify_idea(idea):
    """Classify an idea into MATRIX interest categories."""
    title = idea.get("title", "") or ""
    text = idea.get("text", "") or ""
    track = idea.get("track_name", "") or ""
    combined = f"{title} {text[:2000]} {track}"

    themes = extract_themes(combined, MATRIX_INTEREST_CATEGORIES)
    subcat = extract_themes(combined, {k: {"label": k, "keywords": v, "weight": 0.5} for k, v in SUBCATEGORY_KEYWORDS.items()})

    primary = themes[0] if themes else None

    return {
        "id": idea.get("id", ""),
        "title": title,
        "track": track,
        "project_id": idea.get("project_id", ""),
        "text_length": idea.get("text_len", len(text)),
        "primary_category": primary["label"] if primary else "Other",
        "categories": themes,
        "subcategories": [s["label"] for s in subcat],
        "relevance_score": sum(t["score"] * t["weight"] for t in themes),
    }


def is_matrix_relevant(classified, min_categories=1):
    """Check if idea is relevant to MATRIX project."""
    target_labels = {info["label"] for info in MATRIX_INTEREST_CATEGORIES.values()}
    relevant_cats = [c for c in classified["categories"] if c["label"] in target_labels]
    return len(relevant_cats) >= min_categories


def extract_key_points(text, max_points=4):
    """Extract key points from idea text using heuristics."""
    points = []
    text_clean = re.sub(r'ЗАГОЛОВОК:.*?\n', '', text)
    sentences = re.split(r'(?<=[.!?])\s+', text_clean)

    for sent in sentences[:30]:
        sent = sent.strip()
        if len(sent) < 20 or len(sent) > 500:
            continue
        action_words = [
            "создать", "разработать", "внедрить", "обеспечить", "повысить",
            "улучшить", "оптимизировать", "автоматизировать", "реализовать",
            "построить", "запустить", "организовать", "подготовить",
        ]
        if any(aw in sent.lower() for aw in action_words):
            points.append(sent)
        if len(points) >= max_points:
            break

    if len(points) < 2:
        points = [s.strip() for s in sentences[:10] if len(s.strip()) > 30][:max_points]

    return points[:max_points]


def generate_training_pair_qna(idea, classified):
    """Generate a Q&A training pair from a classified idea."""
    primary = classified["primary_category"]
    title = classified["title"]
    text = idea.get("text", "")[:1000]
    points = extract_key_points(text)
    points_text = "\n".join(f"- {p}" for p in points)

    questions = [
        f"Проанализируй идею «{title}». Какие технологии нужны для её реализации?",
        f"Как можно улучшить проект «{title}» с помощью ИИ и робототехники?",
        f"Какие инновационные подходы применимы для реализации идеи «{title}»?",
        f"Предложи план внедрения идеи «{title}» в образовательную программу.",
        f"Какие научные методы помогут оценить эффективность идеи «{title}»?",
    ]

    answers = [
        f"Для реализации идеи «{title}» (категория: {primary}) требуются: "
        f"{', '.join(c['label'] for c in classified['categories'][:3])}. "
        f"Ключевые аспекты:\n{points_text}",

        f"Идея «{title}» может быть усилена через интеграцию ИИ-компонентов "
        f"для анализа данных, роботизированных решений для автоматизации "
        f"процессов и нейросетевых моделей для прогнозирования.",

        f"Инновационные подходы для «{title}» включают: "
        f"системное мышление, ТРИЗ-методологию, итеративное прототипирование "
        f"и междисциплинарный синтез. Рекомендуется создать цифрового двойника.",

        f"Для внедрения «{title}» в образование: разработать учебный модуль "
        f"с практическими кейсами, создать симуляционную среду, организовать "
        f"проектную работу студентов с менторами из индустрии.",

        f"Оценка эффективности «{title}»: квантифицируемые метрики (ROI, "
        f"time-to-market), качественные показатели (экспертные оценки), "
        f"A/B тестирование гипотез, статистический анализ результатов.",
    ]

    q_idx = hash(title) % len(questions)
    return {
        "source_id": classified["id"],
        "category": primary,
        "question": questions[q_idx],
        "answer": answers[q_idx],
    }


# ── Main analysis pipeline ────────────────────────────────────────────────────

def main():
    print("=" * 60)
    print("SINV Forum Ideas Analyzer for MATRIX Project")
    print("=" * 60)

    # 1. Load data
    print(f"\n[1/5] Loading JSON files (max {MAX_FILES})...")
    ideas, read_errors = load_json_files(SOURCE_DIR, MAX_FILES)
    print(f"  Loaded: {len(ideas)} ideas, Errors: {read_errors}")

    # 2. Classify
    print("\n[2/5] Classifying ideas into categories...")
    classified = []
    for i, idea in enumerate(ideas):
        if (i + 1) % 1000 == 0:
            print(f"  Processed: {i + 1}/{len(ideas)}")
        classified.append(classify_idea(idea))
    print(f"  Classified: {len(classified)} ideas")

    # 3. Category statistics
    print("\n[3/5] Computing category statistics...")
    category_counter = Counter()
    track_counter = Counter()
    subcat_counter = Counter()
    relevance_scores = []

    for c in classified:
        for cat in c["categories"]:
            category_counter[cat["label"]] += cat["score"]
        track_counter[c["track"]] += 1
        for s in c["subcategories"]:
            subcat_counter[s] += 1
        relevance_scores.append(c["relevance_score"])

    # 4. Identify best ideas for MATRIX
    print("\n[4/5] Identifying best MATRIX-relevant ideas...")
    relevant = [c for c in classified if is_matrix_relevant(c)]

    top_ai = sorted(
        [c for c in relevant if any(cat["label"] == "AI & Neural Networks" for cat in c["categories"])],
        key=lambda x: x["relevance_score"], reverse=True
    )[:20]

    top_robotics = sorted(
        [c for c in relevant if any(cat["label"] == "Robotics & Autonomous Systems" for cat in c["categories"])],
        key=lambda x: x["relevance_score"], reverse=True
    )[:20]

    top_education = sorted(
        [c for c in relevant if any(cat["label"] == "Education & Knowledge Systems" for cat in c["categories"])],
        key=lambda x: x["relevance_score"], reverse=True
    )[:20]

    top_innovation = sorted(
        [c for c in relevant if any(cat["label"] == "Innovation Management" for cat in c["categories"])],
        key=lambda x: x["relevance_score"], reverse=True
    )[:20]

    top_all = sorted(relevant, key=lambda x: x["relevance_score"], reverse=True)[:50]

    # 5. Generate training pairs
    print("\n[5/5] Generating training Q&A pairs...")
    training_pairs = []
    ideas_lookup = {idea["id"]: idea for idea in ideas}

    # Deterministic but diverse selection
    random.seed(SEED)
    training_candidates = relevant.copy()
    random.shuffle(training_candidates)

    for c in training_candidates:
        idea = ideas_lookup.get(c["id"])
        if idea and c["relevance_score"] > 1.0:
            pair = generate_training_pair_qna(idea, c)
            training_pairs.append(pair)
        if len(training_pairs) >= 2000:
            break

    print(f"  Generated: {len(training_pairs)} training pairs")

    # ── Build output ──────────────────────────────────────────────────────────

    results = {
        "meta": {
            "source": str(SOURCE_DIR),
            "total_files_in_dir": len(list(SOURCE_DIR.glob("*.json"))),
            "files_processed": len(ideas),
            "read_errors": read_errors,
            "date_generated": "2026-07-10",
            "script_version": "1.0",
        },
        "overview": {
            "total_analyzed": len(classified),
            "matrix_relevant": len(relevant),
            "matrix_relevant_pct": round(len(relevant) / max(len(classified), 1) * 100, 1),
            "mean_relevance_score": round(sum(relevance_scores) / max(len(relevance_scores), 1), 2),
        },
        "top_categories": [
            {"category": cat, "score": int(score)}
            for cat, score in category_counter.most_common(20)
        ],
        "top_subcategories": [
            {"subcategory": sub, "count": count}
            for sub, count in subcat_counter.most_common(15)
        ],
        "top_tracks": [
            {"track": track, "count": count}
            for track, count in track_counter.most_common(10)
        ],
        "best_ideas": {
            "top_overall": [
                {
                    "id": c["id"],
                    "title": c["title"],
                    "primary_category": c["primary_category"],
                    "relevance_score": round(c["relevance_score"], 2),
                    "track": c["track"],
                }
                for c in top_all
            ],
            "ai_neural_networks": [
                {"id": c["id"], "title": c["title"], "relevance_score": round(c["relevance_score"], 2)}
                for c in top_ai
            ],
            "robotics_autonomous": [
                {"id": c["id"], "title": c["title"], "relevance_score": round(c["relevance_score"], 2)}
                for c in top_robotics
            ],
            "education": [
                {"id": c["id"], "title": c["title"], "relevance_score": round(c["relevance_score"], 2)}
                for c in top_education
            ],
            "innovation": [
                {"id": c["id"], "title": c["title"], "relevance_score": round(c["relevance_score"], 2)}
                for c in top_innovation
            ],
        },
        "insights": [],
    }

    # ── Generate insights for MATRIX ──────────────────────────────────────────

    ai_ideas_count = sum(
        1 for c in classified
        if any(cat["label"] == "AI & Neural Networks" for cat in c["categories"])
    )
    robotics_count = sum(
        1 for c in classified
        if any(cat["label"] == "Robotics & Autonomous Systems" for cat in c["categories"])
    )
    edu_count = sum(
        1 for c in classified
        if any(cat["label"] == "Education & Knowledge Systems" for cat in c["categories"])
    )
    innov_count = sum(
        1 for c in classified
        if any(cat["label"] == "Innovation Management" for cat in c["categories"])
    )
    sci_count = sum(
        1 for c in classified
        if any(cat["label"] == "Scientific Thinking" for cat in c["categories"])
    )

    insights = [
        f"Из {len(classified)} проанализированных идей {ai_ideas_count} ({round(ai_ideas_count/max(len(classified),1)*100,1)}%) связаны с ИИ и нейросетями — "
        f"это создаёт основу для MATRIX-модулей анализа и генерации идей.",

        f"Робототехника и автономные системы представлены в {robotics_count} идеях ({round(robotics_count/max(len(classified),1)*100,1)}%) — "
        f"потенциал для симуляционного обучения MATRIX в средах PyBullet/ROS2/Gazebo.",

        f"Образовательные инициативы ({edu_count} идей, {round(edu_count/max(len(classified),1)*100,1)}%) — "
        f"прямое применение для MATRIX как AI-учителя и генератора учебных программ.",

        f"Инновационный менеджмент ({innov_count} идей) — "
        f"MATRIX может выступать AI-акселератором: оценка идей, поиск аналогов, оптимизация.",

        f"Научное мышление ({sci_count} идей) — "
        f"подтверждает запрос на AI-ассистента для формулирования гипотез и планирования экспериментов.",

        f"Средний релевантный скоринг: {round(sum(relevance_scores)/max(len(relevance_scores),1),2)} — "
        f"указывает на значительную долю идей, применимых для обучения MATRIX.",

        "Рекомендация: создать MATRIX-модуль «Оценка форумных идей» — классификация, кластеризация, "
        "генерация плана реализации на основе паттернов успешных проектов.",

        "Паттерн: наиболее успешные идеи сочетают технологическую новизну, образовательный компонент "
        "и чёткую методологию внедрения — эту структуру MATRIX должен воспроизводить.",

        "Выявлен gap: идеи по научной методологии часто не содержат конкретных метрик — "
        "MATRIX может генерировать метрики и KPI автоматически.",
    ]

    results["insights"] = insights

    # ── Save outputs ──────────────────────────────────────────────────────────

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    analysis_path = OUTPUT_DIR / "forum_analysis.json"
    with open(analysis_path, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f"\n  Saved analysis: {analysis_path} ({analysis_path.stat().st_size:,} bytes)")

    pairs_path = OUTPUT_DIR / "forum_training_pairs.json"
    with open(pairs_path, "w", encoding="utf-8") as f:
        json.dump({
            "meta": {
                "source": "SINV forum ideas analysis",
                "total_pairs": len(training_pairs),
                "format": "Q&A pairs for LLM fine-tuning",
            },
            "pairs": training_pairs,
        }, f, ensure_ascii=False, indent=2)
    print(f"  Saved training pairs: {pairs_path} ({pairs_path.stat().st_size:,} bytes)")

    # ── Print summary ─────────────────────────────────────────────────────────

    print("\n" + "=" * 60)
    print("RESULTS SUMMARY")
    print("=" * 60)
    print(f"\n  1. Total ideas analyzed: {len(classified)}")
    print(f"\n  2. Top 10 Categories:")
    for i, (cat, score) in enumerate(category_counter.most_common(10), 1):
        bar = "█" * min(int(score / max(category_counter.values()) * 30), 30) if category_counter.values() else ""
        print(f"     {i:2d}. {cat:<40s} {int(score):>6d}  {bar}")

    print(f"\n  3. Training pairs generated: {len(training_pairs)}")

    print(f"\n  4. Sample training pairs:")
    for i, pair in enumerate(training_pairs[:5], 1):
        print(f"     --- Pair {i} ---")
        print(f"     Category: {pair['category']}")
        print(f"     Q: {pair['question'][:120]}...")
        print(f"     A: {pair['answer'][:120]}...")

    print(f"\n  5. Key insights for MATRIX:")
    for i, insight in enumerate(insights[:6], 1):
        print(f"     {i}. {insight[:120]}...")

    print("\n" + "=" * 60)
    print("Done. Files saved to models/training_data/")
    print("=" * 60)

    return results, training_pairs


if __name__ == "__main__":
    results, training_pairs = main()
