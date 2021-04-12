package ru.agentlab.semantic.wot.api;

import ru.agentlab.semantic.wot.thing.ThingActionAffordance;

public interface ObservationsSource<T, M extends Metadata<M>> {
    Observation<T, M> getObservation(ThingActionAffordance affordance, T value);
}
