package ru.agentlab.semantic.powermatcher.examples.uncontrolled;

import java.util.List;

public class WindGeneratorModel {
    private final List<Float> values;
    private int currentValueIdx = 0;

    public WindGeneratorModel(List<Float> values) {
        this.values = values;
    }

    public Float next() {
        var currentValue = values.get(currentValueIdx);
        currentValueIdx = currentValueIdx + 1;
        if (currentValueIdx >= values.size()) {
            currentValueIdx = 0;
        }
        return currentValue;
    }
}
