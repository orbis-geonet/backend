package to.orbis.v2.backend.models.dto;

import java.util.List;

public class GroupedPolygon {
    private String firstId;
    private List<String> ids;

    public String getFirstId() {
        return firstId;
    }

    public void setFirstId(String firstId) {
        this.firstId = firstId;
    }

    public List<String> getIds() {
        return ids;
    }

    public void setIds(List<String> ids) {
        this.ids = ids;
    }
}
