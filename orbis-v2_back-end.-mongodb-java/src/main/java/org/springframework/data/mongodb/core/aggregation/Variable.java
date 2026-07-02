package org.springframework.data.mongodb.core.aggregation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.bson.Document;

@Builder
@Data
@AllArgsConstructor(staticName = "from")
public class Variable {
    String name;
    Object value;

    public void appendTo(Document document) {
        document.put(name, value);
    }
}
