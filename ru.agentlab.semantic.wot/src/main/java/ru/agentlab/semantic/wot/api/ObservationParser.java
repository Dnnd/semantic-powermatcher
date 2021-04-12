package ru.agentlab.semantic.wot.api;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;

public interface ObservationParser<T, M extends Metadata<M>> {
    ObservationParser<T, M> process(Statement st);

    default ObservationParser<T, M> processAll(Model model) {
        model.forEach(this::process);
        return this;
    }

    Observation<T, M> build();
}
