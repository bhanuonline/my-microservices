package interview.project.teahut;

import java.util.HashMap;
import java.util.Map;

public class IngredientStore {

    private final Map<String, Integer> stock    = new HashMap<>();
    private final Map<String, Integer> capacity = new HashMap<>();

    public static final Map<TeaType, Map<String, Integer>> RECIPES = new HashMap<>();

    static {
        Map<String, Integer> simple = new HashMap<>();
        simple.put("water", 150);
        simple.put("milk", 100);
        simple.put("teaLeaves", 5);
        simple.put("sugar", 10);
        RECIPES.put(TeaType.SIMPLE, simple);

        Map<String, Integer> kadak = new HashMap<>();
        kadak.put("water", 150);
        kadak.put("milk", 80);
        kadak.put("teaLeaves", 8);
        kadak.put("sugar", 10);
        kadak.put("ginger", 5);
        RECIPES.put(TeaType.KADAK, kadak);
    }

    public IngredientStore() {
        capacity.put("water", 5000);
        capacity.put("milk", 3000);
        capacity.put("teaLeaves", 500);
        capacity.put("sugar", 800);
        capacity.put("ginger", 300);
        stock.putAll(capacity); // start full
    }

    public synchronized void consume(TeaType type) {
        Map<String, Integer> recipe = RECIPES.get(type);

        // Phase 1 — validate ALL before touching anything
        for (Map.Entry<String, Integer> entry : recipe.entrySet()) {
            int available = stock.getOrDefault(entry.getKey(), 0);
            if (available < entry.getValue()) {
                throw new InsufficientIngredientsException(entry.getKey());
            }
        }

        // Phase 2 — all good, deduct
        for (Map.Entry<String, Integer> entry : recipe.entrySet()) {
            stock.merge(entry.getKey(), -entry.getValue(), Integer::sum);
        }
    }

    public synchronized void refill(String ingredient, int amount) {
        int cap  = capacity.getOrDefault(ingredient, 0);
        int curr = stock.getOrDefault(ingredient, 0);
        stock.put(ingredient, Math.min(curr + amount, cap));
    }

    public synchronized void refillAll() {
        stock.putAll(capacity);
    }

    public synchronized void printStatus() {
        System.out.println("\n  ── Ingredient Stock ──────────────────");
        stock.forEach((ingredient, current) -> {
            int cap = capacity.get(ingredient);
            int pct = (current * 100) / cap;

            // build a 10-block bar  █ = filled  ░ = empty
            String bar = "█".repeat(pct / 10) + "░".repeat(10 - pct / 10);

            System.out.printf("  %-12s %s  %4d / %4d%n",
                    ingredient, bar, current, cap);
        });
        System.out.println("  ──────────────────────────────────────");
    }
}