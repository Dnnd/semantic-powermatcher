package ru.agentlab.semantic.wot.services.api;

public interface ThingServiceConfiguratorsFactory {

    void activateThingConfigurator(ThingServiceConfiguratorConfig implementation);

    void deactivateConfigurators();
}
