package ru.agentlab.semantic.wot.utils;


import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import ru.agentlab.changetracking.filter.ChangetrackingFilter;
import ru.agentlab.changetracking.filter.Transformations;
import ru.agentlab.changetracking.sail.TransactionChanges;
import ru.agentlab.semantic.wot.observation.api.Observation;
import ru.agentlab.semantic.wot.observation.api.ObservationFactory;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.*;

public class Utils {
    public static <T, M> Mono<Observation<T, M>> extractLatestObservation(TransactionChanges changes,
                                                                          ObservationFactory<T, M> obsFactory,
                                                                          ChangetrackingFilter changesFilter,
                                                                          Comparator<Observation<T, M>> comparator) {
        Map<IRI, Model> modelsBySubject = Transformations.groupBySubject(changes.getAddedStatements());
        return modelsBySubject.entrySet()
                .stream()
                .flatMap(entity -> {
                    IRI observationIRI = entity.getKey();
                    Model observationModel = entity.getValue();
                    return changesFilter.matchModel(observationModel)
                            .map(model -> obsFactory.createObservation(observationIRI, observationModel))
                            .stream();
                })
                .max(comparator)
                .map(Mono::just)
                .orElseGet(Mono::empty);
    }

    public static ChangetrackingFilter makeAffordanceObservationsFilter(IRI affordanceIRI) {
        return ChangetrackingFilter.builder()
                .addPattern(null,
                            DESCRIBED_BY_AFFORDANCE,
                            affordanceIRI,
                            ChangetrackingFilter.Filtering.ADDED
                )
                .addPattern(null, HAS_VALUE, null, ChangetrackingFilter.Filtering.ADDED)
                .addPattern(null, MODIFIED, null, ChangetrackingFilter.Filtering.ADDED)
                .build();
    }

    public static <T> Mono<T> withCancel(CompletableFuture<T> future) {
        return withCancel(future, true);
    }

    public static <T> Mono<T> supplyAsync(Supplier<T> supplier, ExecutorService executor) {
        return Mono.fromFuture(CompletableFuture.supplyAsync(supplier, executor));
    }

    public static <T> Mono<T> supplyAsyncWithCancel(Supplier<T> supplier, ExecutorService executor) {
        return withCancel(CompletableFuture.supplyAsync(supplier, executor));
    }

    public static Mono<Void> supplyAsyncWithCancel(Runnable runnable, ExecutorService executor) {
        return withCancel(CompletableFuture.runAsync(runnable, executor));
    }

    public static <T> Mono<T> supplyAsyncWithCancel(Supplier<T> supplier, ExecutorService executor, boolean mayInterruptIfRunning) {
        return withCancel(CompletableFuture.supplyAsync(supplier, executor), mayInterruptIfRunning);
    }

    public static <T> Mono<T> withCancel(CompletableFuture<T> future, boolean mayInterruptOnCancel) {
        return Mono.fromFuture(future)
                .doFinally(signalType -> {
                    if (signalType.equals(SignalType.CANCEL)) {
                        future.cancel(mayInterruptOnCancel);
                    }
                });
    }
}
