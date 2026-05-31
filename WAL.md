📍 Статус: Фаза 0 «Искра», итерация 0.1 завершена. TruthTable (K_MAX=16, evaluate, random, Avro-сериализация), DecisionTree (sealed interface с Leaf/Split, toTruthTable), Avro-схема mpdt_neuron.avsc реализованы. 48 юнит-тестов проходят (все комбинации k=1..16, консистентность дерево↔таблица). Сборка Gradle успешна.
🚀 Активный этап: Фаза 0, итерация 0.2 — Chromosome и генетические операторы (FlipLeaf, SplitLeaf, PruneTree, Crossover) по L5.
🛑 Защищённые зоны: MPDT-нейрон — только дискретные операции (BitSet, long[], побитовые), K_MAX=16, FROZEN-аксиомы, Три запрета.
