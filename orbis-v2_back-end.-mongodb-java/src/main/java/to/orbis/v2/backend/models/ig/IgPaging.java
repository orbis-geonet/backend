package to.orbis.v2.backend.models.ig;

import lombok.Data;

@Data
public class IgPaging {

    @Data
    public static class IgCursor {
        String before;
        String after;
    }

    IgCursor cursors;
    String next;
    String previous;
}
