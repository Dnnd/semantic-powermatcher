package ru.agentlab.semantic.powermatcher.examples;

import ru.agentlab.semantic.wot.thing.Thing;

public class ThingProvider {
    private Thing thing;
    public void ThingProvider(Thing thing) {
        this.thing = thing;
    }

    public Thing getThing() {
        return thing;
    }

    public ThingProvider setThing(Thing thing) {
        this.thing = thing;
        return this;
    }
}
