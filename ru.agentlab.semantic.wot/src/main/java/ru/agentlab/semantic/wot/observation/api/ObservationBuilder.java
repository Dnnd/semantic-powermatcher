package ru.agentlab.semantic.wot.observation.api;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;

public interface ObservationBuilder<T, M> {
    ObservationBuilder<T, M> process(Statement st);

    default ObservationBuilder<T, M> processAll(Model model) {
        model.forEach(this::process);
        return this;
    }

    Observation<T, M> build();
}
