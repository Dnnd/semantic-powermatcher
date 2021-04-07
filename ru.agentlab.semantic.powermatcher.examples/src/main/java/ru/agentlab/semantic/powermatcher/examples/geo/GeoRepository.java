package ru.agentlab.semantic.powermatcher.examples.geo;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries;
import reactor.core.publisher.Mono;
import ru.agentlab.semantic.wot.thing.ConnectionContext;
import ru.agentlab.semantic.wot.utils.Utils;

import static org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.var;
import static org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.tp;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.*;

public class GeoRepository {
    private final ConnectionContext context;

    public GeoRepository(ConnectionContext context) {
        this.context = context;
    }

    Mono<Place> get(IRI placeIRI) {
        return Utils.supplyAsyncWithCancel(() -> {
            var conn = context.getConnection();
            Variable placeName = var("placeName");
            Variable placeLocation = var("location");
            Variable locLong = var("longitude");
            Variable locLat = var("latitude");
            Variable placeAddress = var("address");
            Variable addrStreet = var("street");
            Variable addrLocality = var("addressLocality");
            var query = Queries.CONSTRUCT().where(
                    tp(placeIRI, NAME, placeName),
                    tp(placeIRI, LOCATION, placeLocation),
                    tp(placeLocation, LONGITUDE, locLong),
                    tp(placeLocation, LATITUDE, locLat),
                    tp(placeIRI, ADDRESS, placeAddress),
                    tp(placeAddress, ADDRESS_LOCALITY, addrLocality),
                    tp(placeAddress, STREET_ADDRESS, addrStreet)
            );
            var builder = new Place.Builder(
                    new PostalAddress.Builder(),
                    new GeoCoordinates.Builder()
            );
            try (var resp = conn.prepareGraphQuery(query.getQueryString()).evaluate()) {
                resp.forEach(builder::process);
            }
            return builder.build();
        }, context.getExecutor());
    }
}
