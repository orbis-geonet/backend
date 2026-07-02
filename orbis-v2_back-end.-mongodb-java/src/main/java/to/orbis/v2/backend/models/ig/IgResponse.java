package to.orbis.v2.backend.models.ig;

import lombok.Data;

import java.util.List;

@Data
public class IgResponse<T> {
    List<T> data;
    IgPaging paging;
}
