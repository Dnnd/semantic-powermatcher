package ru.agentlab.semantic.wot.thing;

import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.sail.Sail;
import org.eclipse.rdf4j.sail.SailConnection;
import org.eclipse.rdf4j.sail.helpers.SailUtil;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.ExecutorService;

public class ConnectionContext {
    private final ExecutorService executor;


    private final Scheduler scheduler;
    private final SailRepositoryConnection connection;
    private final SailConnection sailConnection;

    public ConnectionContext(ExecutorService executor,
                             SailRepositoryConnection connection,
                             SailConnection sailConnection) {
        this.executor = executor;
        this.scheduler = Schedulers.fromExecutor(executor);
        this.connection = connection;
        this.sailConnection = sailConnection;
    }

    public <T extends Sail> ConnectionContext(ExecutorService executor, SailRepository repository, Class<T> sailClass) {
        this.executor = executor;
        this.scheduler = Schedulers.fromExecutor(executor);
        var sail = SailUtil.findSailInStack(repository.getSail(), sailClass);
        this.sailConnection = sail.getConnection();
        this.connection = repository.getConnection();
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
        return sailConnection;
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }
}
