package ru.agentlab.semantic.wot.services.api;

import org.osgi.service.cm.Configuration;
import ru.agentlab.semantic.wot.thing.Thing;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public class ThingServiceScopedContext {
    private AtomicReference<Configuration> configuration;
    private final Thing thing;

    public ThingServiceScopedContext(Thing thing) {
        this.thing = thing;
    }

    public Configuration getConfiguration() {
        return configuration.get();
    }

    public void setConfiguration(Configuration configuration) {
        this.configuration.set(configuration);
    }

    public Thing getThing() {
        return thing;
    }

    public void close() throws IOException {
        var config = this.configuration.getAndSet(null);
        if (config != null) {
            config.delete();
        }
    }
}
