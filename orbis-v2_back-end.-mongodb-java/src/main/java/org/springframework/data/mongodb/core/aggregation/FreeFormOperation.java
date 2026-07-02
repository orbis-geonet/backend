package org.springframework.data.mongodb.core.aggregation;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import lombok.val;
import org.bson.Document;

import java.util.Arrays;
import java.util.stream.Collectors;

public class FreeFormOperation implements AggregationOperation {

    String operator;
    DBObject query;

    public FreeFormOperation(String operator, DBObject query) {
        this.operator = operator;
        this.query = query;
    }

    public FreeFormOperation(String operator, String query) {
        this.operator = operator;
        if (query.trim().startsWith("[")) {
            val cheatedQuery = "{ \"arr\": " + query + " }";
            this.query = (DBObject) BasicDBObject.parse(cheatedQuery).get("arr");
            return;
        }
        this.query = BasicDBObject.parse(query);
    }

    public FreeFormOperation(String operator, String... query) {
        this.operator = operator;
        val cheatedQuery = "{ \"arr\": [" + Arrays.stream(query).map(elem -> String.format("\"%s\"", elem)).collect(Collectors.joining(", ")) + "] }";
        this.query = (DBObject) BasicDBObject.parse(cheatedQuery).get("arr");
    }

    @Override
    public Document toDocument(AggregationOperationContext context) {
        return new Document(operator, query);
    }
}
