package to.orbis.v2.backend.models.requests.validators;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = EventPostValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidEvent {
    String message() default "Event's plannedTime is invalid";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
