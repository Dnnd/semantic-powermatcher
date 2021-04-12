package ru.agentlab.semantic.wot.api;

import org.eclipse.rdf4j.model.Statement;

public interface MetadataParser<M> {
    MetadataParser<M> process(Statement st);

    M build();
}
