package ru.agentlab.semantic.wot.api;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;

public interface ObservationFactory<T, M extends Metadata<M>> {

    ObservationParser<T, M> createObservationBuilder(IRI observationIRI);

    default Observation<T, M> createObservation(IRI observationIRI, Model observationModel) {
        return createObservationBuilder(observationIRI).processAll(observationModel).build();
    }

}
