package to.orbis.v2.polygons.creator.job;

import to.orbis.v2.polygons.creator.models.constant.PolygonSchedulerCoordinateType;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomThreadFactory implements ThreadFactory {
    private final String baseName;
    private final AtomicInteger threadNumber = new AtomicInteger(1);

    public CustomThreadFactory(PolygonSchedulerCoordinateType baseName) {
        this.baseName = baseName.name();
    }

    public static int getThreadNumber(String threadName) {
        String intString = threadName
                .replace(PolygonSchedulerCoordinateType.TRIGGER.name(), "")
                .replace(PolygonSchedulerCoordinateType.CHECKIN.name(), "")
                .replace("-", "");
        try {
            return Integer.parseInt(intString) - 1;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r);
        t.setName(baseName + "-" + threadNumber.getAndIncrement());
        return t;
    }
}
