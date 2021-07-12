package ru.agentlab.semantic.wot.services.api;

import org.osgi.service.cm.Configuration;
import reactor.core.publisher.Mono;
import ru.agentlab.semantic.wot.thing.Thing;

public interface ThingServiceFactory {
    Configuration registerThingServiceConfigurationSync(ThingServiceConfigurator configurator, Thing thing);

    Mono<Configuration> registerThingServiceConfiguration(ThingServiceConfigurator configurator, Thing thing);
}
