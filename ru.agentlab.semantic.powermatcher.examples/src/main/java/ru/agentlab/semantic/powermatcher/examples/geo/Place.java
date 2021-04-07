package ru.agentlab.semantic.powermatcher.examples.geo;

import org.eclipse.rdf4j.model.Statement;

import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.NAME;

public class Place {
    private final String name;
    private final PostalAddress address;
    private final GeoCoordinates location;

    public Place(String name, PostalAddress address, GeoCoordinates location) {
        this.name = name;
        this.address = address;
        this.location = location;
    }

    public String getName() {
        return name;
    }

    public PostalAddress getAddress() {
        return address;
    }

    public GeoCoordinates getLocation() {
        return location;
    }

    public static class Builder {
        private String name;
        private final PostalAddress.Builder addressBuilder;
        private final GeoCoordinates.Builder locationBuilder;

        public Builder(PostalAddress.Builder addressBuilder, GeoCoordinates.Builder locationBuilder) {
            this.addressBuilder = addressBuilder;
            this.locationBuilder = locationBuilder;
        }

        public void process(Statement st) {
            if (st.getPredicate().equals(NAME)) {
                name = st.getObject().stringValue();
            } else {
                addressBuilder.process(st);
                locationBuilder.process(st);
            }
        }

        public Place build() {
            return new Place(name, addressBuilder.build(), locationBuilder.build());
        }
    }
}
