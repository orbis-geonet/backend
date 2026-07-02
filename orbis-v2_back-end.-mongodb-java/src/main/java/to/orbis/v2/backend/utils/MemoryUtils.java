package to.orbis.v2.backend.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Slf4j
@UtilityClass
public class MemoryUtils {

    public void printCurrentMemoryInfo(String prefix) {
        val runtime = Runtime.getRuntime();
        log.info("{}. Total: {} m, Free: {} m, Max: {} m", prefix, runtime.totalMemory()/(1024*1024),
                runtime.freeMemory()/(1024*1024), runtime.maxMemory()/(1024*1024));
    }
}
