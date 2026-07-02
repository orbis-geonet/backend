package org.springframework.data.mongodb.core.aggregation;

import org.bson.Document;
import org.springframework.data.mongodb.core.aggregation.FieldsExposingAggregationOperation.InheritsFieldsAggregationOperation;
import org.springframework.util.Assert;

import java.util.List;

public class PipelineLookupOperation implements FieldsExposingAggregationOperation, InheritsFieldsAggregationOperation {

    Field from;
    Field as;
    Variables let;
    List<AggregationOperation> pipeline;

    public PipelineLookupOperation(String from, String as, Variables let, AggregationOperation... pipeline) {

        Assert.notNull(from, "from must not be null!");
        Assert.notNull(pipeline, "pipeline must not be null!");
        Assert.notNull(as, "as must not be null!");

        this.from = Fields.field(from);
        this.pipeline = List.of(pipeline);
        this.as = Fields.field(as);
        this.let = let;
    }

    @Override
    public ExposedFields getFields() {
        return ExposedFields.synthetic(Fields.from(as));
    }

    @Override
    public Document toDocument(AggregationOperationContext context) {

        Document lookupObject = new Document();

        lookupObject.append("from", from.getTarget());
        lookupObject.append("let", let.toDocument());
        lookupObject.append("as", as.getTarget());
        lookupObject.append("pipeline", new AggregationPipeline(pipeline).toDocuments(context));

        return new Document(getOperator(), lookupObject);
    }

    @Override
    public String getOperator() {
        return "$lookup";
    }
}
