package ru.agentlab.semantic.powermatcher.examples;

import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class FileExportBackend implements ExporterBackend {
    private final File exportTarget;

    public FileExportBackend(File exportTarget) {
        this.exportTarget = exportTarget;
    }

    @Override
    public void exportData(RepositoryConnection source, Resource... context) {
        try (var writer = new FileWriter(exportTarget, false)) {
            var rdfHandler = Rio.createWriter(RDFFormat.TURTLE, writer);
            source.export(rdfHandler, context);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
