package ru.agentlab.semantic.wot.repositories;

import ru.agentlab.semantic.wot.thing.ConnectionContext;

public interface WotRepository {
    ConnectionContext getConnectionContext();

    default void cancel() {
        getConnectionContext().getConnection().close();
        getConnectionContext().getExecutor().shutdown();
    }
}
