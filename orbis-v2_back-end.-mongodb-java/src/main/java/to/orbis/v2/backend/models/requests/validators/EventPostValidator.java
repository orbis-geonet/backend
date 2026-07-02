package to.orbis.v2.backend.models.requests.validators;

import lombok.val;
import to.orbis.v2.backend.models.PostType;
import to.orbis.v2.backend.models.requests.posts.CreatePostRequest;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class EventPostValidator implements ConstraintValidator<ValidEvent, CreatePostRequest> {

    @Override
    public boolean isValid(CreatePostRequest value, ConstraintValidatorContext context) {
        if (value.getType() != PostType.EVENT) {
            return true;
        }

        // to avoid race conditions make sure any event is planned at least 30 seconds upfront
        val halfAMinuteInFuture = Instant.now().plus(30, ChronoUnit.SECONDS);

        if (value.getPlannedTime() == null) {
            context.buildConstraintViolationWithTemplate("Event requires plannedTime")
                    .addPropertyNode("plannedTime")
                    .addConstraintViolation();

            return false;
        }

        if (value.getPlannedTime().isBefore(halfAMinuteInFuture)) {
            context.buildConstraintViolationWithTemplate("Event needs to be planned in future")
                    .addPropertyNode("plannedTime")
                    .addConstraintViolation();
            return false;
        }

        if (value.getPlannedEndTime() != null && value.getPlannedEndTime().isBefore(value.getPlannedTime())) {
            context.buildConstraintViolationWithTemplate("Event end time must be after event's start time")
                    .addPropertyNode("plannedEndTime")
                    .addConstraintViolation();
            return false;
        }

        if (value.getAddress() == null || value.getAddress().isBlank()) {
            context.buildConstraintViolationWithTemplate("Event needs to take place at some address")
                    .addPropertyNode("address")
                    .addConstraintViolation();
            return false;
        }

        return true;
    }
}
