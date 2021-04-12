package ru.agentlab.semantic.wot.repositories;

import ru.agentlab.semantic.wot.api.Metadata;
import ru.agentlab.semantic.wot.api.Observation;
import ru.agentlab.semantic.wot.thing.ConnectionContext;

public class ObservationsRepository {
    private ConnectionContext context;

    <T, M extends Metadata<M>> void write(Observation<T, M> serializer) {

    }
}
