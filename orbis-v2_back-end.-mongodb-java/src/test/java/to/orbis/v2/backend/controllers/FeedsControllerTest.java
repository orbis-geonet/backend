package to.orbis.v2.backend.controllers;

import lombok.val;
import org.junit.jupiter.api.Test;
import to.orbis.v2.backend.models.PostComponentType;
import to.orbis.v2.backend.models.PostType;
import to.orbis.v2.backend.models.dto.PostComponentDto;
import to.orbis.v2.backend.models.dto.PostDto;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static to.orbis.v2.backend.models.PostComponentType.POST;
import static to.orbis.v2.backend.models.PostComponentType.SLIDER;

class FeedsControllerTest {

    @Test
    void regroup_keepAsIs() {

        val src = createComponents(SLIDER, POST, POST, POST, POST, POST, POST, POST, POST, POST, POST, SLIDER, POST);

        val regrouped = getFeedsController().regroup(src);

        assertThat(regrouped).map(PostComponentDto::getType).containsExactly(SLIDER, POST, POST, POST, POST, POST, POST, POST, POST, POST, POST, SLIDER, POST);
        assertThat(regrouped).filteredOn(postComponentDto -> postComponentDto.getType() == SLIDER).map(PostComponentDto::getSlider).map(List::size).containsExactly(5, 5);

    }

    @Test
    void regroup_collapseOne() {

        val src = createComponents(SLIDER, POST, POST, POST, POST, POST, POST, POST, POST, POST, SLIDER, POST, POST);

        val regrouped = getFeedsController().regroup(src);

        assertThat(regrouped).map(PostComponentDto::getType).containsExactly(SLIDER, POST, POST, POST, POST, POST, POST, POST, POST, POST, POST, POST);
        assertThat(regrouped).filteredOn(postComponentDto -> postComponentDto.getType() == SLIDER).map(PostComponentDto::getSlider).map(List::size).containsExactly(10);

    }

    @Test
    void regroup_collapseSeveral() {

        val src = createComponents(SLIDER, POST, POST, POST, SLIDER, POST, POST, POST, POST, POST, SLIDER, POST, POST, SLIDER, POST);

        val regrouped = getFeedsController().regroup(src);

        assertThat(regrouped).map(PostComponentDto::getType).containsExactly(SLIDER, POST, POST, POST, POST, POST, POST, POST, POST, POST, POST, SLIDER, POST);
        assertThat(regrouped).filteredOn(postComponentDto -> postComponentDto.getType() == SLIDER).map(PostComponentDto::getSlider).map(List::size).containsExactly(15, 5);

    }

    private FeedsController getFeedsController() {
        return new FeedsController(null, null, null, null, null, null, null, null, null);
    }

    private List<PostComponentDto> createComponents(PostComponentType... types) {
        return Arrays.stream(types).map(this::createComponent).collect(Collectors.toList());
    }

    private PostComponentDto createComponent(PostComponentType postComponentType) {
        switch (postComponentType) {
            case POST:
                return new PostComponentDto().setType(postComponentType);
            case SLIDER:
                return new PostComponentDto().setType(postComponentType).setSlider(createSlider(5));
        }

        throw new RuntimeException("Should not get there");
    }

    private List<PostDto> createSlider(int numPosts) {
        return IntStream.range(0, numPosts).mapToObj(idx -> new PostDto().setPostKey(String.valueOf(idx)).setType(PostType.CHECK_IN)).collect(Collectors.toList());
    }
}
