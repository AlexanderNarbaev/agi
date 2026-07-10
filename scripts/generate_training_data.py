#!/usr/bin/env python3
"""
M.A.T.R.I.X. Training Data Generator

Analyzes ideas from the SINV forum and generates training data for MATRIX.
Extracts key themes, patterns, and creates Q&A pairs for world understanding.

Usage:
    python3 generate_training_data.py [--input-dir DIR] [--output-dir DIR] [--max-ideas N]
"""

import argparse
import json
import os
import sys
from pathlib import Path
from collections import Counter
import re

# Add project root to path
PROJECT_ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(PROJECT_ROOT))


def load_ideas(input_dir: Path, max_ideas: int = 0) -> list[dict]:
    """Load ideas from JSON files."""
    ideas = []
    files = sorted(input_dir.glob("sinv_idea_*.json"))
    
    if max_ideas > 0:
        files = files[:max_ideas]
    
    for f in files:
        try:
            with open(f, 'r', encoding='utf-8') as fp:
                data = json.load(fp)
                if 'title' in data and 'text' in data:
                    ideas.append(data)
        except Exception as e:
            print(f"Error loading {f}: {e}")
    
    return ideas


def extract_themes(ideas: list[dict]) -> dict[str, list[str]]:
    """Extract key themes from ideas."""
    themes = {}
    
    for idea in ideas:
        title = idea.get('title', '')
        text = idea.get('text', '')
        track = idea.get('track_name', 'Other')
        
        if track not in themes:
            themes[track] = []
        
        themes[track].append(title)
    
    return themes


def extract_keywords(text: str) -> list[str]:
    """Extract keywords from text."""
    # Simple keyword extraction
    words = re.findall(r'\b[а-яА-Яa-zA-Z]{4,}\b', text.lower())
    
    # Filter common words
    stop_words = {'который', 'которые', 'которая', 'которое', 'этот', 'эта', 'это', 'эти',
                  'для', 'или', 'также', 'как', 'что', 'где', 'когда', 'почему', 'зачем',
                  'очень', 'более', 'менее', 'самый', 'такой', 'такая', 'такое', 'такие'}
    
    return [w for w in words if w not in stop_words]


def generate_qa_pairs(ideas: list[dict]) -> list[dict]:
    """Generate Q&A pairs from ideas."""
    qa_pairs = []
    
    for idea in ideas:
        title = idea.get('title', '')
        text = idea.get('text', '')
        track = idea.get('track_name', 'Other')
        
        # Extract first paragraph as context
        paragraphs = text.split('\n\n')
        context = paragraphs[0] if paragraphs else text[:500]
        
        # Generate Q&A based on title
        if 'автономн' in title.lower() or 'робот' in title.lower():
            qa_pairs.append({
                'question': 'Что такое автономные системы?',
                'answer': f'Автономные системы - это роботы и транспортные средства, способные действовать без постоянного контроля человека. Пример: {title}',
                'category': 'robotics',
                'source': idea.get('id', '')
            })
        
        if 'искусственн' in title.lower() or 'нейрон' in title.lower() or 'ии' in title.lower():
            qa_pairs.append({
                'question': 'Как искусственный интеллект помогает в инновациях?',
                'answer': f'ИИ используется для автоматизации, анализа данных и принятия решений. Пример из проекта: {title}',
                'category': 'ai',
                'source': idea.get('id', '')
            })
        
        if 'эколог' in title.lower() or 'природ' in title.lower():
            qa_pairs.append({
                'question': 'Какие экологические инновации существуют?',
                'answer': f'Экологические инновации включают зеленые технологии, переработку отходов и возобновляемую энергию. Пример: {title}',
                'category': 'ecology',
                'source': idea.get('id', '')
            })
        
        if 'образован' in title.lower() or 'обучен' in title.lower():
            qa_pairs.append({
                'question': 'Как современные технологии меняют образование?',
                'answer': f'Технологии делают образование более доступным и интерактивным. Пример: {title}',
                'category': 'education',
                'source': idea.get('id', '')
            })
        
        if 'медицин' in title.lower() or 'здоров' in title.lower():
            qa_pairs.append({
                'question': 'Какие инновации в медицине?',
                'answer': f'Медицинские инновации включают телемедицину, AI-диагностику и персонализированную медицину. Пример: {title}',
                'category': 'healthcare',
                'source': idea.get('id', '')
            })
        
        # Generic Q&A based on track
        qa_pairs.append({
            'question': f'Расскажите о проекте в области {track.lower()}',
            'answer': f'{title}. {context[:200]}',
            'category': track.lower(),
            'source': idea.get('id', '')
        })
    
    return qa_pairs


def generate_world_understanding(ideas: list[dict]) -> list[dict]:
    """Generate world understanding training data."""
    training_data = []
    
    # Extract common patterns
    patterns = Counter()
    
    for idea in ideas:
        text = idea.get('text', '')
        keywords = extract_keywords(text)
        patterns.update(keywords)
    
    # Generate training pairs based on common themes
    common_themes = patterns.most_common(20)
    
    for theme, count in common_themes:
        training_data.append({
            'input': f'Что такое {theme}?',
            'output': f'{theme} - это важная тема в инновациях. Упоминается в {count} проектах.',
            'type': 'world_understanding'
        })
    
    return training_data


def save_training_data(qa_pairs: list[dict], world_data: list[dict], output_dir: Path):
    """Save training data to files."""
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # Save Q&A pairs
    qa_file = output_dir / 'qa_pairs.json'
    with open(qa_file, 'w', encoding='utf-8') as f:
        json.dump(qa_pairs, f, ensure_ascii=False, indent=2)
    print(f"Saved {len(qa_pairs)} Q&A pairs to {qa_file}")
    
    # Save world understanding data
    world_file = output_dir / 'world_understanding.json'
    with open(world_file, 'w', encoding='utf-8') as f:
        json.dump(world_data, f, ensure_ascii=False, indent=2)
    print(f"Saved {len(world_data)} world understanding pairs to {world_file}")
    
    # Save combined training data
    combined = qa_pairs + world_data
    combined_file = output_dir / 'combined_training.json'
    with open(combined_file, 'w', encoding='utf-8') as f:
        json.dump(combined, f, ensure_ascii=False, indent=2)
    print(f"Saved {len(combined)} total training pairs to {combined_file}")


def main():
    parser = argparse.ArgumentParser(description='Generate training data from SINV ideas')
    parser.add_argument('--input-dir', type=Path, 
                       default=Path('/home/alexandr-narbaev/Projects/opora/data/sinv/buffer/raw'),
                       help='Directory containing SINV JSON files')
    parser.add_argument('--output-dir', type=Path,
                       default=PROJECT_ROOT / 'models' / 'training_data',
                       help='Output directory for training data')
    parser.add_argument('--max-ideas', type=int, default=0,
                       help='Maximum number of ideas to process (0 = all)')
    args = parser.parse_args()
    
    print("=" * 60)
    print("M.A.T.R.I.X. Training Data Generator")
    print("=" * 60)
    print(f"Input: {args.input_dir}")
    print(f"Output: {args.output_dir}")
    print()
    
    # Load ideas
    print("Loading ideas...")
    ideas = load_ideas(args.input_dir, args.max_ideas)
    print(f"Loaded {len(ideas)} ideas")
    
    # Extract themes
    print("\nExtracting themes...")
    themes = extract_themes(ideas)
    print(f"Found {len(themes)} tracks:")
    for track, titles in sorted(themes.items(), key=lambda x: -len(x[1]))[:10]:
        print(f"  {track}: {len(titles)} ideas")
    
    # Generate Q&A pairs
    print("\nGenerating Q&A pairs...")
    qa_pairs = generate_qa_pairs(ideas)
    print(f"Generated {len(qa_pairs)} Q&A pairs")
    
    # Generate world understanding data
    print("\nGenerating world understanding data...")
    world_data = generate_world_understanding(ideas)
    print(f"Generated {len(world_data)} world understanding pairs")
    
    # Save training data
    print("\nSaving training data...")
    save_training_data(qa_pairs, world_data, args.output_dir)
    
    print("\n" + "=" * 60)
    print("Training data generation complete!")
    print("=" * 60)


if __name__ == '__main__':
    main()
