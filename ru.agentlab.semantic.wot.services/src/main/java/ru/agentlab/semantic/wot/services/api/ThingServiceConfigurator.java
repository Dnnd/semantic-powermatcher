package ru.agentlab.semantic.wot.services.api;

import java.util.Dictionary;

public interface ThingServiceConfigurator {
    Dictionary<String, ?> getConfiguration();

    enum Properties {
        THING_IRI
    }
}
