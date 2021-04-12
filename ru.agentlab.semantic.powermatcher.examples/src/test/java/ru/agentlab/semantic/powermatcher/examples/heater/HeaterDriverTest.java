package ru.agentlab.semantic.powermatcher.examples.heater;

import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.config.SailRegistry;
import org.junit.jupiter.api.Test;
import ru.agentlab.changetracking.sail.ChangeTrackingFactory;
import ru.agentlab.changetracking.utils.EmbeddedChangetrackingRepo;

import javax.measure.Measure;
import javax.measure.unit.SI;
import java.io.IOException;
import java.lang.annotation.Annotation;


public class HeaterDriverTest {
    @Test
    void test() throws IOException, InterruptedException {
        SailRegistry.getInstance().add(new ChangeTrackingFactory());
        try (EmbeddedChangetrackingRepo repo = EmbeddedChangetrackingRepo.makeTempRepository("test");
             var conn = repo.getConnection()
        ) {
            SailRepository repository = (SailRepository) conn.getRepository();
            var hd = new HeaterDriver();
            var hsm = new HeaterProvider();
            hsm.bindSailRepository(() -> repository);
            hd.bindSailRepositoryProvider(() -> repository);
            hsm.activate(new HeaterSimulationConfig() {

                @Override
                public Class<? extends Annotation> annotationType() {
                    return getClass();
                }

                @Override
                public String resourceId() {
                    return "heater";
                }

                @Override
                public String thingIRI() {
                    return "https://example.agentlab.ru/#Heater_1";
                }

                @Override
                public String stateContext() {
                    return "https://observations.agentlab.ru/";
                }

                @Override
                public int updateFrequency() {
                    return 1000;
                }

                @Override
                public String thingContext() {
                    return "https://things.agentlab.ru/";
                }
            });
            hd.activate(new HeaterDriver.Config() {

                @Override
                public Class<? extends Annotation> annotationType() {
                    return getClass();
                }

                @Override
                public String thingIRI() {
                    return "https://example.agentlab.ru/#Heater_1";
                }

            });
            for (int i = 0; i < 5; ++i) {
                hd.handleControlParameters(() -> Measure.valueOf(1000, SI.WATT));
                Thread.sleep(1000);
            }
            hd.deactivate();
            hsm.deactivate();
        }
    }
}
