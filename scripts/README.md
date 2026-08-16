# scripts/ — Python-скрипты для research (NOT in production)

**CONSTITUTION VII.1:** Python допустим только в `docs/research/` и `scripts/`.
Все скрипты в этой папке — для ручного research-запуска, НЕ вызываются из Gradle/Java рантайма.

## Использование

| Скрипт | Назначение | Как запустить |
|--------|------------|---------------|
| pretrain_neurons.py | MPDT-нейроны из transformer weights (demo/safetensors) | `python3 pretrain_neurons.py --demo` |
| pretrain_large.py | Larger pretrain pipeline | `python3 pretrain_large.py --help` |
| merge_pretrained_neurons.py | Merge multiple pretrain outputs | `python3 merge_pretrained_neurons.py --help` |
| generate_training_data.py | Generate training data for agents | `python3 generate_training_data.py --help` |
| analyze_forum_ideas.py | Analyze forum ideas corpus | `python3 analyze_forum_ideas.py --help` |
| gridworld_visualize.py / gridworld_viz.py | Visualize Gridworld agent | `python3 gridworld_visualize.py` |
| robot_arm_sim.py | Robot arm simulation | `python3 robot_arm_sim.py` |
| sinv_tool.py | SINV analysis tool | `python3 sinv_tool.py --help` |
| yggdrasil-mock.py | Mock Yggdrasil service | `python3 yggdrasil-mock.py` |
| deploy.py | Manual deploy helper | `python3 deploy.py --help` |

## Из Java рантайма

Java-вызовы этих скриптов должны быть обёрнуты в:
```java
if (!Boolean.getBoolean("matrix.research.enabled")) {
    throw new IllegalStateException("Research-only script (CONSTITUTION VII.1)");
}
```

## Что НЕ делать

- Не добавлять в Gradle-зависимости (`build.gradle`)
- Не использовать в production pipeline
- Не вызывать без флага `-Dmatrix.research.enabled=true`
