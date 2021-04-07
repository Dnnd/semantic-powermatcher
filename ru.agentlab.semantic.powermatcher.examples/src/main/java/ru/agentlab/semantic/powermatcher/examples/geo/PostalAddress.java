package ru.agentlab.semantic.powermatcher.examples.geo;

import org.eclipse.rdf4j.model.Statement;

import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.ADDRESS_LOCALITY;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.STREET_ADDRESS;

public class PostalAddress {
    private final String addressLocality;
    private final String streetAddress;

    public PostalAddress(String addressLocality, String streetAddress) {
        this.addressLocality = addressLocality;
        this.streetAddress = streetAddress;
    }

    public String getAddressLocality() {
        return addressLocality;
    }

    public String getStreetAddress() {
        return streetAddress;
    }

    public static class Builder {
        private String addressLocality;
        private String streetAddress;

        void process(Statement st) {
            if (st.getPredicate().equals(ADDRESS_LOCALITY)) {
                addressLocality = st.getObject().stringValue();
            } else if (st.getPredicate().equals(STREET_ADDRESS)) {
                streetAddress = st.getObject().stringValue();
            }
        }

        public PostalAddress build() {
            return new PostalAddress(addressLocality, streetAddress);
        }

    }
}
