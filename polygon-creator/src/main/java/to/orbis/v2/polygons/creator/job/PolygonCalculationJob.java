package to.orbis.v2.polygons.creator.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import to.orbis.v2.polygons.creator.configuration.config.PolygonCreatorConfiguration;
import to.orbis.v2.polygons.creator.models.constant.PolygonSchedulerCoordinateType;
import to.orbis.v2.polygons.creator.services.PolygonSchedulerService;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.polygon-calculation.enable", havingValue = "true")
public class PolygonCalculationJob {
    private final PolygonSchedulerService polygonSchedulerService;
    private final PolygonCreatorConfiguration polygonCreatorConfiguration;
    private ScheduledExecutorService triggerPointsExecutor;
    private ScheduledExecutorService checkinPointsExecutor;

    public PolygonCalculationJob(
            PolygonSchedulerService polygonSchedulerService, PolygonCreatorConfiguration polygonCreatorConfiguration) {
        this.polygonSchedulerService = polygonSchedulerService;
        this.polygonCreatorConfiguration = polygonCreatorConfiguration;
        this.triggerPointsExecutor = Executors.newScheduledThreadPool(
                polygonCreatorConfiguration.getTriggerPointThreadNumber(),
                new CustomThreadFactory(PolygonSchedulerCoordinateType.TRIGGER)
        );
        this.checkinPointsExecutor = Executors.newScheduledThreadPool(
                polygonCreatorConfiguration.getCheckInPointThreadNumber(),
                new CustomThreadFactory(PolygonSchedulerCoordinateType.CHECKIN)
        );
    }

    @PostConstruct
    public void init() {
        initTriggerPointsJob();
        initCheckingPointsJob();
    }

    private void initTriggerPointsJob() {
        triggerPointsExecutor.scheduleAtFixedRate(
                () -> triggerPointsExecutor.submit(polygonSchedulerService::calculateTriggerPoints),
                0,
                polygonCreatorConfiguration.getDelayInSec(),
                TimeUnit.SECONDS
        );
        log.info("initTriggerPointsJob: TRIGGER job is on. Threads={}", polygonCreatorConfiguration.getTriggerPointThreadNumber());
    }

    private void initCheckingPointsJob() {
        checkinPointsExecutor.scheduleAtFixedRate(
                () -> checkinPointsExecutor.submit(polygonSchedulerService::calculateCheckinPoints),
                0,
                polygonCreatorConfiguration.getDelayInSec(),
                TimeUnit.SECONDS
        );
        log.info("initTriggerPointsJob: CHECK-IN job is on. Threads={}", polygonCreatorConfiguration.getCheckInPointThreadNumber());
    }

    @PreDestroy
    public void shutdown() {
        triggerPointsExecutor.shutdown();
        checkinPointsExecutor.shutdown();
    }
}
