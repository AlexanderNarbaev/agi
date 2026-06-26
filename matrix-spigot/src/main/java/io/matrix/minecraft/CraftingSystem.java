package io.matrix.minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minecraft-like crafting system.
 *
 * <p>Recipes: resources → crafted items. Higher-tier tools
 * enable mining of harder blocks.
 */
public class CraftingSystem {

    public record Recipe(String name, Map<String, Integer> inputs,
                          Map<String, Integer> outputs) {}

    private final List<Recipe> recipes = new ArrayList<>();

    public CraftingSystem() {
        add("Wooden Planks", map("WOOD", 1), map("PLANKS", 4));
        add("Sticks", map("PLANKS", 2), map("STICK", 4));
        add("Wooden Pickaxe", map("PLANKS", 3, "STICK", 2), map("WOODEN_PICKAXE", 1));
        add("Stone Pickaxe", map("COBBLESTONE", 3, "STICK", 2), map("STONE_PICKAXE", 1));
        add("Iron Pickaxe", map("IRON_INGOT", 3, "STICK", 2), map("IRON_PICKAXE", 1));
        add("Diamond Pickaxe", map("DIAMOND", 3, "STICK", 2), map("DIAMOND_PICKAXE", 1));
        add("Furnace", map("COBBLESTONE", 8), map("FURNACE", 1));
        add("Iron Ingot", map("IRON_ORE", 1, "COAL", 1), map("IRON_INGOT", 1));
        add("Gold Ingot", map("GOLD_ORE", 1, "COAL", 1), map("GOLD_INGOT", 1));
        add("Torch", map("STICK", 1, "COAL", 1), map("TORCH", 4));
        add("Bread", map("WHEAT", 3), map("BREAD", 1));
        add("Stone Sword", map("COBBLESTONE", 2, "STICK", 1), map("STONE_SWORD", 1));
    }

    private void add(String name, Map<String, Integer> inputs,
                     Map<String, Integer> outputs) {
        recipes.add(new Recipe(name, inputs, outputs));
    }

    @SafeVarargs
    private static Map<String, Integer> map(Object... pairs) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return m;
    }

    /**
     * Attempts to craft using available inventory.
     *
     * @return recipe name if successful, null if no recipe matches
     */
    public String tryCraft(Map<String, Integer> inventory, String desiredOutput) {
        for (Recipe recipe : recipes) {
            if (recipe.outputs().containsKey(desiredOutput)) {
                if (canCraft(inventory, recipe)) {
                    consume(inventory, recipe);
                    produce(inventory, recipe);
                    return recipe.name();
                }
            }
        }
        return null;
    }

    /**
     * Tries to craft anything possible from current inventory.
     *
     * @return list of crafted recipe names
     */
    public List<String> craftAllPossible(Map<String, Integer> inventory) {
        List<String> crafted = new ArrayList<>();
        boolean changed;
        do {
            changed = false;
            for (Recipe recipe : recipes) {
                if (canCraft(inventory, recipe)) {
                    consume(inventory, recipe);
                    produce(inventory, recipe);
                    crafted.add(recipe.name());
                    changed = true;
                }
            }
        } while (changed);
        return crafted;
    }

    public List<Recipe> recipes() { return List.copyOf(recipes); }

    public List<Recipe> availableRecipes(Map<String, Integer> inventory) {
        return recipes.stream()
                .filter(r -> canCraft(inventory, r))
                .toList();
    }

    private boolean canCraft(Map<String, Integer> inv, Recipe recipe) {
        for (var entry : recipe.inputs().entrySet()) {
            if (inv.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private void consume(Map<String, Integer> inv, Recipe recipe) {
        for (var entry : recipe.inputs().entrySet()) {
            inv.put(entry.getKey(), inv.get(entry.getKey()) - entry.getValue());
        }
    }

    private void produce(Map<String, Integer> inv, Recipe recipe) {
        for (var entry : recipe.outputs().entrySet()) {
            inv.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
    }
}
