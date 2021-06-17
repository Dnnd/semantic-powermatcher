package ru.agentlab.semantic.wot.services.providers;

import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.agentlab.changetracking.sail.ChangeTracker;
import ru.agentlab.changetracking.sail.ChangeTrackerConnection;
import ru.agentlab.semantic.wot.repositories.ThingRepository;
import ru.agentlab.semantic.wot.services.api.DeclarativeServiceLaunchConfiguration;
import ru.agentlab.semantic.wot.services.api.SailRepositoryProvider;
import ru.agentlab.semantic.wot.services.api.ThingServiceConfigurator;
import ru.agentlab.semantic.wot.services.api.ThingServiceScopedContext;
import ru.agentlab.semantic.wot.thing.ConnectionContext;
import ru.agentlab.semantic.wot.thing.Thing;
import ru.agentlab.semantic.wot.utils.Utils;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

@Component(scope = ServiceScope.SINGLETON, immediate = true)
@Designate(ocd = ThingServiceFactory.Config.class)
public class ThingServiceFactory {
    private final List<ThingServiceConfigurator> preActivated = new CopyOnWriteArrayList<>();

    private final Map<String, Disposable> subscriptions = new ConcurrentHashMap<>();

    private final Logger logger = LoggerFactory.getLogger(ThingServiceFactory.class);
    private volatile ConnectionContext context;
    private volatile ConfigurationAdmin configurationAdmin;

    @ObjectClassDefinition
    public @interface Config {
        boolean shutdownServicesOnDeactivate() default true;
    }

    @Reference
    public void bindSailRepositoryProvider(SailRepositoryProvider repositoryProvider) {
        var repo = repositoryProvider.getRepository();
        var executor = Executors.newSingleThreadExecutor();
        context = new ConnectionContext(executor, repo, ChangeTracker.class);
    }

    @Reference
    public void bindConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
    }

    @Reference(
            policy = ReferencePolicy.DYNAMIC,
            cardinality = ReferenceCardinality.MULTIPLE,
            unbind = "removeConfigurator")
    public void addConfigurator(ThingServiceConfigurator configurator) {
        logger.info("Adding ThingServiceConfigurator for {} ...", configurator.getModelIRI());
        if (context == null) {
            preActivated.add(configurator);
        } else {
            enableServiceDiscovery(configurator);
        }
        logger.info("Adding ThingServiceConfigurator for {} ... Done", configurator.getModelIRI());
    }

    public void removeConfigurator(ThingServiceConfigurator configurator) {
        logger.info("Removing ThingServiceConfigurator for {} ...", configurator.getModelIRI());
        subscriptions.remove(configurator.getConfiguratorPID()).dispose();
        logger.info("Removing ThingServiceConfigurator for {} ... Done", configurator.getModelIRI());
    }

    @Activate
    public void activate(Config ignore) {
        logger.info("Activating ThingService Factory...");
        preActivated.forEach(this::enableServiceDiscovery);
        preActivated.clear();
        logger.info("Activating ThingService Factory...Done");
    }

    @Deactivate
    public void deactivate() {
        subscriptions.values().forEach(Disposable::dispose);
    }

    private void deactivateConfigurations(Collection<Configuration> configurationsToDeactivate) {
        configurationsToDeactivate.forEach(configuration -> {
            try {
                configuration.delete();
            } catch (IOException e) {
                logger.error("unable to delete configuration {}", configuration.getPid());
            }
        });
    }

    private void enableServiceDiscovery(ThingServiceConfigurator configurator) {
        var repository = new ThingRepository(context);
        var servicesContextSource = repository.discoverDeploymentsOf(configurator.getModelIRI())
                                              .map(ThingServiceScopedContext::new);
        Disposable subscription = Flux.usingWhen(
                servicesContextSource,
                singleServiceContext -> registerThingServiceConfiguration(configurator, singleServiceContext.getThing())
                        .doOnNext(singleServiceContext::setConfiguration),
                this::deleteServiceContext
        ).subscribe();
        subscriptions.putIfAbsent(configurator.getConfiguratorPID(), subscription);
    }

    private Mono<Configuration> registerThingServiceConfiguration(ThingServiceConfigurator configurator, Thing thing) {
        return Utils.supplyAsyncWithCancel(
                () -> registerThingServiceConfigurationSync(configurator, thing),
                context.getExecutor()
        );
    }

    private Mono<Void> deleteServiceContext(ThingServiceScopedContext scopedContext) {
        return Utils.supplyAsyncWithCancel(() -> {
            try {
                scopedContext.close();
            } catch (IOException e) {
                logger.error("unable to delete context {}", scopedContext);
            }
        }, context.getExecutor());
    }

    private Configuration registerThingServiceConfigurationSync(ThingServiceConfigurator configurator, Thing thing) {
        try {
            DeclarativeServiceLaunchConfiguration launchConfiguration =
                    configurator.getServiceLaunchConfiguration(thing, context);

            var config = switch (launchConfiguration.getServiceType()) {
                case SINGLETON -> configurationAdmin.getConfiguration(
                        launchConfiguration.getServiceConfigurationPID(),
                        launchConfiguration.getBundleID()
                );
                case FACTORY -> configurationAdmin.createFactoryConfiguration(
                        launchConfiguration.getServiceConfigurationPID(),
                        launchConfiguration.getBundleID()
                );
            };
            var properties = launchConfiguration.getProperties();
            config.update(properties);
            return config;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
