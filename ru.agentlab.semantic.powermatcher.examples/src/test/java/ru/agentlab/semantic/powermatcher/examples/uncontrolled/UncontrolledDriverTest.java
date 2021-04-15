package ru.agentlab.semantic.powermatcher.examples.uncontrolled;


import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.config.SailRegistry;
import org.junit.jupiter.api.Test;
import ru.agentlab.changetracking.sail.ChangeTrackingFactory;
import ru.agentlab.changetracking.utils.EmbeddedChangetrackingRepo;

import java.io.IOException;
import java.lang.annotation.Annotation;

import static org.eclipse.rdf4j.model.util.Values.iri;

public class UncontrolledDriverTest {

    @Test
    public void test() throws IOException, InterruptedException {
        SailRegistry.getInstance().add(new ChangeTrackingFactory());
        IRI thingIRI = iri("https://example.agentlab.ru/#WindGeneratorModel_1");
        try (EmbeddedChangetrackingRepo repo = EmbeddedChangetrackingRepo.makeTempRepository("test"); var conn = repo.getConnection()) {
            SemanticUncontrolledResourceSimulation simulation = new SemanticUncontrolledResourceSimulation();
            UncontrolledSemanticResourceDriver driver = new UncontrolledSemanticResourceDriver();
            SailRepository repository = (SailRepository) conn.getRepository();
            driver.bindRepository(() -> repository);
            simulation.bindSailRepository(() -> repository);

            simulation.activate(new SemanticUncontrolledResourceSimulation.Config() {

                @Override
                public Class<? extends Annotation> annotationType() {
                    return getClass();
                }

                @Override
                public String thingIRI() {
                    return thingIRI.stringValue();
                }

                @Override
                public float from() {
                    return 0;
                }

                @Override
                public float to() {
                    return 1000;
                }

                @Override
                public int intervalMsec() {
                    return 500;
                }

                @Override
                public String thingContext() {
                    return "https://things.agentlab.ru";
                }

                @Override
                public String stateContext() {
                    return "https://observations.agentlab.ru";
                }
            });
            driver.activate(new UncontrolledSemanticResourceDriver.Config() {

                @Override
                public Class<? extends Annotation> annotationType() {
                    return getClass();
                }

                @Override
                public String thingIRI() {
                    return thingIRI.stringValue();
                }
            });
            Thread.sleep(10000);
            simulation.deactivate();
            driver.deactivate();
        }
    }
}
