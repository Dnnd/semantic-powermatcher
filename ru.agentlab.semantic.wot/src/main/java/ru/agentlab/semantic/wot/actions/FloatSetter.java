package ru.agentlab.semantic.wot.actions;

import ru.agentlab.semantic.wot.observation.api.Action;

public class FloatSetter<M> implements Action<Float, Void, M> {
    private final Float inputData;
    private M metadata;

    public FloatSetter(Float inputData) {
        this.inputData = inputData;
    }

    @Override
    public void setMetadata(M metadata) {
        this.metadata = metadata;
    }

    @Override
    public M getMetadata() {
        return metadata;
    }

    @Override
    public Float getInput() {
        return inputData;
    }

    @Override
    public Void getOutput() {
        return null;
    }
}
