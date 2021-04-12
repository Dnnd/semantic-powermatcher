package ru.agentlab.semantic.wot.api;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;

public interface ActionParser<I, O, M extends Metadata<M>> {

    ActionParser<I, O, M> process(Statement st);

    default ActionParser<I, O, M> processAll(Model model) {
        model.forEach(this::process);
        return this;
    }

    Action<I, O, M> build();
}
