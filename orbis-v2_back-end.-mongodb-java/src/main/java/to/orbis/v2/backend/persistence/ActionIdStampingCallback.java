package to.orbis.v2.backend.persistence;

import org.reactivestreams.Publisher;
import org.springframework.data.mongodb.core.mapping.event.ReactiveBeforeConvertCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.filters.ActionIdWebFilter;
import to.orbis.v2.backend.models.entity.Entity;

@Component
public class ActionIdStampingCallback implements ReactiveBeforeConvertCallback<Entity> {

    @Override
    public Publisher<Entity> onBeforeConvert(Entity entity, String collection) {
        return Mono.deferContextual(ctx -> {
            if (ctx.hasKey(ActionIdWebFilter.ACTION_ID_CONTEXT_KEY)) {
                entity.setNetworkActionId(ctx.get(ActionIdWebFilter.ACTION_ID_CONTEXT_KEY));
            }
            return Mono.just(entity);
        });
    }
}
