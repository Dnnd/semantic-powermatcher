package ru.agentlab.semantic.wot.observation.api;

import org.eclipse.rdf4j.model.Statement;

public interface MetadataBuilder<M> {
    MetadataBuilder<M> process(Statement st);

    M build();
}
