package ru.agentlab.semantic.wot.observation.api;

public interface Observation<T, M> {
    void setMetadata(M metadata);

    M getMetadata();

    T getValue();
}

