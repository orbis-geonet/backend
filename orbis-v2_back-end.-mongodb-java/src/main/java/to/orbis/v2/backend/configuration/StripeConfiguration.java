package to.orbis.v2.backend.configuration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

import javax.validation.constraints.NotNull;

@Getter
@ConstructorBinding
@ConfigurationProperties(prefix = "stripe")
@RequiredArgsConstructor
public class StripeConfiguration {
    Boolean enable;

    @NotNull
    String orbisCommission;

    String partnerCommission;

    @NotNull
    String stripeCommission;

    @NotNull
    String stripeAdditionFee;

    String redirectUrl;

    @NotNull
    String stripeSecretToken;

    @NotNull
    String stripePublicToken;

    @NotNull
    String stripePaymentWebhookSecret;

    @NotNull
    String stripeSubscriptionWebhookSecret;

    @NotNull
    String stripeConnectWebhookSecret;

    @NotNull
    Boolean testModeEnable;
}
