package to.orbis.v2.backend.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CodeUtils {

    public static String createRandomCode(){
        return RandomStringUtils.random(14, true, true);
    }
}
