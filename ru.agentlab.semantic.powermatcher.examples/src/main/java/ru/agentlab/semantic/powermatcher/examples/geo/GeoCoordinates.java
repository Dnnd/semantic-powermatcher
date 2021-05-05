package ru.agentlab.semantic.powermatcher.examples.geo;

import org.eclipse.rdf4j.model.Statement;

import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.LATITUDE;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.LONGITUDE;

public class GeoCoordinates {

    private final double longitude;
    private final double latitude;

    public GeoCoordinates(double longitude, double latitude) {
        this.longitude = longitude;
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public static class Builder {
        private double longitude;
        private double latitude;

        public void process(Statement st) {
            if (st.getPredicate().equals(LONGITUDE)) {
                longitude = Double.parseDouble(st.getObject().stringValue());
            } else if (st.getPredicate().equals(LATITUDE)) {
                latitude = Double.parseDouble(st.getObject().stringValue());
            }
        }

        public GeoCoordinates build() {
            return new GeoCoordinates(longitude, latitude);
        }
    }
}
