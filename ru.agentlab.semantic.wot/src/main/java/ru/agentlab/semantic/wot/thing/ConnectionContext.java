package ru.agentlab.semantic.wot.thing;

import org.eclipse.rdf4j.repository.RepositoryConnection;

import java.util.concurrent.ExecutorService;

public class ConnectionContext {
    private final ExecutorService executor;
    private final RepositoryConnection connection;

    public ConnectionContext(ExecutorService executor, RepositoryConnection connection) {
        this.executor = executor;
        this.connection = connection;
    }

    public RepositoryConnection getConnection() {
        return connection;
    }

    public ExecutorService getExecutor() {
        return executor;
    }
}
