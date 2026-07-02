package to.orbis.v2.backend.utils;

import org.junit.jupiter.api.Test;

class CodeUtilsTest {

    @Test
    public void createRandomCode() {
        var code = CodeUtils.createRandomCode();
        System.out.println(code);
    }
}