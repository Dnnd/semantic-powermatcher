package ru.agentlab.semantic.wot.observation.api;

public interface Action<I, O, M> {
    void setMetadata(M metadata);

    M getMetadata();

    I getInput();

    O getOutput();
}
