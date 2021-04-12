package ru.agentlab.semantic.powermatcher.examples;

import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;

public class RepositoryExportBackend implements ExporterBackend {
    private final Repository target;

    public RepositoryExportBackend(Repository target) {
        this.target = target;
    }


    @Override
    public void exportData(RepositoryConnection source, Resource... contexts) {
        try (var exporter = target.getConnection()) {
            exporter.begin();
            for (var statement : exporter.getStatements(null, null, null, contexts)) {
                exporter.add(statement);
            }
            exporter.commit();
        }
    }
}
