package ru.agentlab.semantic.powermatcher.examples.uncontrolled;

import org.eclipse.rdf4j.repository.sail.SailRepository;

public interface SailRepositoryProvider {
    SailRepository getRepository();
}
