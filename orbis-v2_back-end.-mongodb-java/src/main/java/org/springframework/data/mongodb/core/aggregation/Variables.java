package org.springframework.data.mongodb.core.aggregation;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import org.bson.Document;

import java.util.List;

@Getter
public class Variables {

    @Builder
    public Variables(@Singular List<Variable> variables) {
        this.variables = variables;
    }

    List<Variable> variables;

    public Document toDocument() {
        Document res = new Document();
        variables.forEach(v -> v.appendTo(res));
        return res;
    }
}
