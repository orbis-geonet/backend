package to.orbis.v2.backend.utils;

import lombok.val;
import org.springframework.beans.BeanUtils;

import java.beans.FeatureDescriptor;
import java.util.Arrays;
import java.util.stream.Stream;

public class OrbisBeanUtils {

    @SuppressWarnings("rawtypes")
    @SafeVarargs
    public static <T, F extends Enum> void copyNotNullPropertiesSkipping(
            Class<T> clz,
            T existingObject,
            T incomingObject,
            F... skipFields) {

        val skippedSet = Arrays.stream(skipFields).map(Enum::name);

        final String[] ignoreProperties = Stream.concat(Arrays.stream(BeanUtils.getPropertyDescriptors(clz))
                .filter(pd -> {
                    try {
                        return pd.getReadMethod().invoke(incomingObject) == null;
                    } catch (Exception e) {
                        return false;
                    }
                }).map(FeatureDescriptor::getName), skippedSet).distinct().toArray(String[]::new);

        BeanUtils.copyProperties(incomingObject, existingObject, ignoreProperties);
    }
}
