package ru.agentlab.semantic.wot.repositories;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries;
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPattern;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.agentlab.changetracking.filter.ChangetrackingFilter;
import ru.agentlab.changetracking.sail.ChangeTrackerConnection;
import ru.agentlab.semantic.wot.observation.api.Observation;
import ru.agentlab.semantic.wot.observation.api.ObservationBuilder;
import ru.agentlab.semantic.wot.observation.api.ObservationFactory;
import ru.agentlab.semantic.wot.thing.ConnectionContext;
import ru.agentlab.semantic.wot.thing.Thing;
import ru.agentlab.semantic.wot.thing.ThingPropertyAffordance;
import ru.agentlab.semantic.wot.utils.Utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.var;
import static org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.select;
import static org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.tp;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.*;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.HAS_VALUE;

public class ThingPropertyAffordanceRepository implements WotRepository {
    private final ConnectionContext context;

    public ThingPropertyAffordanceRepository(ConnectionContext context) {
        this.context = context;
    }

    public Flux<ThingPropertyAffordance> getPropertyAffordancesWithType(Thing thing, IRI... desiredAffordanceType) {
        var thingIRI = thing.getIRI();
        return Utils.supplyAsyncWithCancel(() -> {
            Variable affordanceIRI = var("affordanceIRI");
            GraphPattern pattern;
            if (desiredAffordanceType == null || desiredAffordanceType.length == 0) {
                pattern = tp(thingIRI, HAS_PROPERTY_AFFORDANCE, affordanceIRI);
            } else {
                pattern = tp(thingIRI, HAS_PROPERTY_AFFORDANCE, affordanceIRI)
                        .and(tp(affordanceIRI, RDF.TYPE, desiredAffordanceType[0]));
                for (int i = 1; i < desiredAffordanceType.length; ++i) {
                    pattern = pattern.union(
                            tp(thingIRI, HAS_PROPERTY_AFFORDANCE, affordanceIRI)
                                    .and(tp(affordanceIRI, RDF.TYPE, desiredAffordanceType[1]))
                    );
                }
            }
            SelectQuery query = Queries.SELECT(affordanceIRI).where(pattern).distinct();

            var prepared = context.getConnection().prepareTupleQuery(query.getQueryString());
            return prepared.evaluate().stream().map(binding -> {
                var foundIRI = (IRI) binding.getBinding("affordanceIRI").getValue();
                return getThingPropertyAffordanceSync(thing, foundIRI);
            });
        }, context.getExecutor()).flatMapMany(Flux::fromStream);
    }

    public ThingPropertyAffordance getThingPropertyAffordanceSync(Thing thing, IRI affordanceIRI) {
        Model model = new LinkedHashModel();
        List<IRI> types = new ArrayList<>();

        for (var st : getConnectionContext().getConnection().getStatements(affordanceIRI, null, null)) {
            model.add(st);
            if (st.getPredicate().equals(RDF.TYPE)) {
                types.add((IRI) st.getObject());
            }
        }
        return new ThingPropertyAffordance(thing.getIRI(), affordanceIRI, types, model);
    }

    public Mono<ThingPropertyAffordance> getThingPropertyAffordance(Thing thing, IRI affordanceIRI) {
        return Utils.supplyAsyncWithCancel(() -> getThingPropertyAffordanceSync(thing, affordanceIRI),
                                           getConnectionContext().getExecutor()
        );
    }

    public <T, M> Mono<Observation<T, M>> latestObservation(ThingPropertyAffordance propertyAffordance, ObservationFactory<T, M> observationFactory) {
        return latestObservation(propertyAffordance.getIRI(), observationFactory);
    }

    public <T, M> Mono<Observation<T, M>> latestObservation(IRI propertyAffordanceIRI, ObservationFactory<T, M> observationFactory) {
        return latestObservation(propertyAffordanceIRI, observationFactory.createObservationBuilder(null));
    }

    public <T, M> Mono<Observation<T, M>> latestObservation(ThingPropertyAffordance propertyAffordance, ObservationBuilder<T, M> builder) {
        return latestObservation(propertyAffordance.getIRI(), builder);
    }

    public <T, M> Mono<Observation<T, M>> latestObservation(IRI propertyAffordanceIRI, ObservationBuilder<T, M> builder) {
        return Utils.supplyAsyncWithCancel(
                () -> latestObservationSync(propertyAffordanceIRI, builder),
                context.getExecutor()
        );
    }

    public <T, M> Observation<T, M> latestObservationSync(IRI propertyAffordanceIRI, ObservationBuilder<T, M> builder) {
        var conn = this.context.getConnection();
        Variable mostRecent = var("mostRecent");
        Variable lastModified = var("lastModified");
        Variable obs = var("obs");
        Variable value = var("value");
        Variable pred = var("pred");
        Variable subj = var("subj");
        var query = Queries.CONSTRUCT(
                tp(obs, DESCRIBED_BY_AFFORDANCE, propertyAffordanceIRI),
                tp(obs, MODIFIED, mostRecent),
                tp(obs, HAS_VALUE, value),
                tp(obs, pred, subj)
        ).where(select(obs, Expressions.max(lastModified).as(mostRecent))
                        .where(tp(obs, DESCRIBED_BY_AFFORDANCE, propertyAffordanceIRI),
                               tp(obs, MODIFIED, lastModified)
                        )
                        .groupBy(obs),
                tp(obs, HAS_VALUE, value)
        );
        conn.prepareGraphQuery(query.getQueryString()).evaluate().forEach(builder::process);
        return builder.build();
    }

    public <T, M> Flux<Observation<T, M>> subscribeOnLatestObservations(ThingPropertyAffordance propertyAffordance,
                                                                        ObservationFactory<T, M> observationFactory,
                                                                        Comparator<Observation<T, M>> comparator) {
        return subscribeOnLatestObservations(propertyAffordance.getIRI(), observationFactory, comparator);
    }

    public <T, M> Flux<Observation<T, M>> subscribeOnLatestObservations(IRI propertyAffordanceIRI,
                                                                        ObservationFactory<T, M> observationFactory,
                                                                        Comparator<Observation<T, M>> comparator) {
        var scheduler = Schedulers.fromExecutor(context.getExecutor());
        ChangetrackingFilter filter = Utils.makeAffordanceObservationsFilter(propertyAffordanceIRI);
        var sailConn = (ChangeTrackerConnection) context.getSailConnection();

        return sailConn.events(scheduler)
                .flatMap(changes -> Utils.extractLatestObservation(
                        changes,
                        observationFactory,
                        filter,
                        comparator
                ));
    }

    @Override
    public ConnectionContext getConnectionContext() {
        return context;
    }

}
