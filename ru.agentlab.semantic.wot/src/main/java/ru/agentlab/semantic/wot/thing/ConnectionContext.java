package ru.agentlab.semantic.wot.thing;

import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.sail.SailConnection;

import java.util.concurrent.ExecutorService;

public class ConnectionContext {
    private final ExecutorService executor;
    private final SailRepositoryConnection connection;

    public ConnectionContext(ExecutorService executor, SailRepositoryConnection connection) {
        this.executor = executor;
        this.connection = connection;
    }

    public void close() {
        connection.close();
        executor.shutdown();
    }

    public SailRepositoryConnection getConnection() {
        return connection;
    }

    public SailRepositoryConnection createConnection() {
        return (SailRepositoryConnection) connection.getRepository().getConnection();
    }

    public SailConnection getSailConnection() {
        return connection.getSailConnection();
    }

    public ExecutorService getExecutor() {
        return executor;
    }
}
