package ru.agentlab.semantic.wot.observation.api;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;

public interface ActionBuilder<I, O, M> {

    ActionBuilder<I, O, M> process(Statement st);

    default ActionBuilder<I, O, M> processAll(Model model) {
        model.forEach(this::process);
        return this;
    }

    Action<I, O, M> build();
}
