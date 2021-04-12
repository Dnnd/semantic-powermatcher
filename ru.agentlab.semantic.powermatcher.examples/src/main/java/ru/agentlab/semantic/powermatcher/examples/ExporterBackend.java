package ru.agentlab.semantic.powermatcher.examples;

import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.repository.RepositoryConnection;


public interface ExporterBackend {
    void exportData(RepositoryConnection source, Resource... contexts);
}
