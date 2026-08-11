package com.mycraft.worldchestloot;

final class LootProbability {
    private final String configured;
    private final double fallback;

    private LootProbability(String configured, double fallback) {
        this.configured = configured;
        this.fallback = fallback;
    }

    static LootProbability parse(Object value, double fallback) {
        return new LootProbability(value == null ? null : String.valueOf(value), fallback);
    }

    double resolve(LootEvaluationContext context) {
        return context.probability(this, configured, fallback);
    }

    double editorValue() {
        if (configured == null) return fallback;
        try { return Math.max(0, Double.parseDouble(configured.trim())); }
        catch (NumberFormatException ex) { return 0; }
    }
}
