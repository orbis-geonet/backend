package to.orbis.v2.backend.services;

import lombok.val;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;

class PostServiceTest {

    @Test
    void testConditionalShifts() {
        val f1 = Flux.just(1, 2, 3, 4, 5);
        val f2 = Flux.just(4, 6, 9, 10);

        f1.switchOnFirst((e1, sf1) -> f2.switchOnFirst((e2, sf2) -> {
            val shared1 = sf1.share();
            val shared2 = sf2.share();
                            final Flux<List<Integer>> head = (e1.get() < e2.get()) ? shared1.takeUntil(ce -> ce >= e2.get()).buffer(2)
                                    : shared2.takeUntil(ce -> ce >= e1.get()).buffer(2);
                            val tail = ((e1.get() < e2.get())
                                    ? Flux.zip(appendEmpty(shared2.buffer(2)), appendEmpty(shared1.skipWhile(ce -> ce <= e2.get()).buffer(2)))
                                    : Flux.zip(appendEmpty(shared1.buffer(2)), appendEmpty(shared2.skipWhile(ce -> ce <= e1.get()).buffer(2))))
                                    .takeWhile(t -> !t.getT1().isEmpty() || !t.getT2().isEmpty())
                                    .flatMapSequential(t -> Flux.just(t.getT1(), t.getT2()))
                                    .filter(c -> !c.isEmpty());
                            return Flux.mergeSequential(head, tail);
                        }
                        ))
    .subscribe(System.out::println);
    }

    Flux<List<Integer>> appendEmpty(Flux<List<Integer>> in) {
        return Flux.mergeSequential(in, Flux.just(Collections.<Integer>emptyList()).cache().repeat());
    }
}
