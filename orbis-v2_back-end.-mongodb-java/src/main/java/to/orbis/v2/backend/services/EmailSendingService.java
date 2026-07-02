package to.orbis.v2.backend.services;

import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.model.*;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.configuration.EmailSendingConfiguration;
import to.orbis.v2.backend.models.dto.email.CreateEmailTemplateRequest;
import to.orbis.v2.backend.models.dto.email.EmailMessage;
import to.orbis.v2.backend.models.dto.email.EmailType;
import to.orbis.v2.backend.models.entity.Group;
import to.orbis.v2.backend.models.entity.UserPurchase;
import to.orbis.v2.backend.models.entity.UserSubscription;
import to.orbis.v2.backend.repositories.SubscriptionRepository;

import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSendingService {
    private final AmazonSimpleEmailService amazonSimpleEmailService;
    private final JavaMailSender javaMailSender;
    private final UsersService usersService;
    private final GroupsService groupsService;
    private final SubscriptionRepository subscriptionRepository;
    private final EmailSendingConfiguration configuration;

//    @PostConstruct
    //TODO
    public void uploadEmails() {
        Arrays.stream(EmailType.values())
                        .forEach(email -> {
                            try  {
                                URL url = this.getClass().getResource(email.getFilePath());
                                if (Objects.isNull(url)) {
                                    throw new RuntimeException(String.format("There is no file: %s", email.getTemplateName()));
                                }
                                Path filePath = Path.of(url.getPath());
                                String body = Files.readString(filePath);

                                createTemplate(
                                        CreateEmailTemplateRequest.builder()
                                                .body(body)
                                                .templateName(email.getTemplateName())
                                                .subject(email.getSubject())
                                                .build()
                                );
                            } catch (IOException e) {
                                log.error("Cannot upload email template: {}. Error: {}", email.getTemplateName(), e.getMessage());
                                throw new RuntimeException(String.format("Cannot upload email template: %s. Error: %s", email.getTemplateName(), e.getMessage()));
                            }
                        });
    }

    public Mono<Void> sendEmails(UserPurchase userPurchase) {
        return groupsService.findGroup(userPurchase.getGroupKey())
                .flatMap(group -> sendPurchaseEmail(group, userPurchase, EmailType.USER)
                        .then(sendPurchaseEmail(group, userPurchase, EmailType.ADMIN)));
    }

    public Mono<Void> sendEmails(UserSubscription userSubscription) {
        return groupsService.findGroup(userSubscription.getGroupKey())
                .flatMap(group -> sendSubscriptionEmail(group, userSubscription, EmailType.USER)
                        .then(sendSubscriptionEmail(group, userSubscription, EmailType.ADMIN)));
    }

    public Mono<Void> sendPurchaseEmail(Group group, UserPurchase userPurchase, EmailType type) {
        return sendUserEmail(
                group,
                userPurchase.getUserKey(),
                userPurchase.getPurchaseKey(),
                userPurchase.getNumber(),
                userPurchase.getCodes(),
                type
        );
    }

    public Mono<Void> sendSubscriptionEmail(Group group, UserSubscription userSubscription, EmailType type) {
        return sendUserEmail(
                group,
                userSubscription.getUserKey(),
                userSubscription.getSubscriptionKey(),
                1,
                userSubscription.getCodes(),
                type
        );
    }

    public Mono<Void> sendUserEmail(Group group, String userKey, String subscriptionKey, Integer number, List<String> codes, EmailType type) {
        return usersService.findUser(group.getMainAdmin(), "Cannot find user")
                .flatMap(mainAdmin -> usersService.findUser(userKey, "Cannot find user")
                        .flatMap(user -> subscriptionRepository.findOneBySubscriptionKey(subscriptionKey)
                                .flatMap(purchase -> {
                                    var emailSendRequestBuilder = EmailMessage.builder()
                                            .emailType(type)
                                            .userName(user.getDisplayName())
                                            .userEmail(user.getEmail())
                                            .purchaseName(purchase.getName())
                                            .quantity(number)
                                            .nameOfGroup(group.getName())
                                            .codes(String.join(",", codes));

                                    switch (type) {
                                        case USER:
                                            emailSendRequestBuilder.to(user.getEmail());
                                            emailSendRequestBuilder.contactName(mainAdmin.getDisplayName());
                                            emailSendRequestBuilder.contactEmail(mainAdmin.getEmail());
                                            break;
                                        case ADMIN:
                                            emailSendRequestBuilder.to(mainAdmin.getEmail());
                                            break;
                                    }
                                    var request = emailSendRequestBuilder.build();
                                    var result = sendOneMessage(emailSendRequestBuilder.build());;
                                    log.info("Send email to {} email: {}", type, request.getTo());
                                    return result;
                                })
                        )
                );
    }

    public void createTemplate(CreateEmailTemplateRequest createEmailTemplateRequest) {
        var requestDelete = new DeleteTemplateRequest();
        requestDelete.setTemplateName(createEmailTemplateRequest.getTemplateName());
        amazonSimpleEmailService.deleteTemplate(requestDelete);


        var template = new Template();
        template.setTemplateName(createEmailTemplateRequest.getTemplateName());
        template.setHtmlPart(createEmailTemplateRequest.getBody());
        template.setSubjectPart(createEmailTemplateRequest.getSubject());

        var request = new CreateTemplateRequest();
        request.setTemplate(template);

        amazonSimpleEmailService.createTemplate(request);
    }

    @SneakyThrows
    public Mono<Void> sendOneMessage(EmailMessage emailMessage) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");
        if (configuration.isTestMode()) {
            helper.setTo(configuration.getTestModeReceiver());
        } else {
            helper.setTo(emailMessage.getTo());
        }

        helper.setSubject(emailMessage.getEmailType().getSubject().replace("{{groupName}}", emailMessage.getNameOfGroup()));
        helper.setText(createBody(emailMessage), true);


        helper.setFrom(configuration.getFrom());
        javaMailSender.send(mimeMessage);
        return Mono.just("").then();
    }

    private Mono<Void> sendOneMessageSes(EmailMessage emailMessage){
        var destination = new Destination();
        if (configuration.isTestMode()) {
            destination.setToAddresses(List.of(configuration.getTestModeReceiver()));
        } else {
            destination.setToAddresses(List.of(emailMessage.getTo()));
        }
        var templateData = createTemplateData(emailMessage);

        var request = new SendTemplatedEmailRequest();

        request.setTemplate(emailMessage.getEmailType().getTemplateName());
        request.setDestination(destination);
        request.setTemplateData(templateData);
        request.setSource(String.format("\"%s\" <%s>", configuration.getFromName(), configuration.getFrom()));
        request.setConfigurationSetName(configuration.getAmazonConfigurationSetName());

        try {
            return Mono.just(amazonSimpleEmailService.sendTemplatedEmail(request))
                    .then();
        } catch (Exception e) {
            return Mono.empty();
        }
    }

    private String createTemplateData(EmailMessage emailMessage) {
        return String.format(
                "{ \"%s\":\"%s\", \"%s\":\"%s\", \"%s\":\"%s\", \"%s\":\"%s\", \"%s\":\"%s\", \"%s\":\"%s\", \"%s\":\"%s\", \"%s\":\"%s\"}",
                EmailMessage.Fields.userName.name(), emailMessage.getUserName(),
                EmailMessage.Fields.userEmail.name(), emailMessage.getUserEmail(),
                EmailMessage.Fields.purchaseName.name(), emailMessage.getPurchaseName(),
                EmailMessage.Fields.quantity.name(), emailMessage.getQuantity(),
                EmailMessage.Fields.contactName.name(), emailMessage.getContactName(),
                EmailMessage.Fields.contactEmail.name(), emailMessage.getContactEmail(),
                EmailMessage.Fields.nameOfGroup.name(), emailMessage.getNameOfGroup(),
                EmailMessage.Fields.codes.name(), emailMessage.getCodes()
        );
    }

    private String createBody(EmailMessage emailMessage) {
        var body = emailMessage.getEmailType().equals(EmailType.ADMIN) ? ADMIN_EMAIL : USER_EMAIL;


        return body
                .replace("{{" + EmailMessage.Fields.userName.name() + "}}", Objects.nonNull(emailMessage.getUserName()) ? emailMessage.getUserName() : "")
                .replace("{{" + EmailMessage.Fields.userEmail.name() + "}}", Objects.nonNull(emailMessage.getUserEmail()) ? emailMessage.getUserEmail() : "")
                .replace("{{" + EmailMessage.Fields.purchaseName.name() + "}}", Objects.nonNull(emailMessage.getPurchaseName()) ? emailMessage.getPurchaseName() : "")
                .replace("{{" + EmailMessage.Fields.quantity.name() + "}}", Objects.nonNull(emailMessage.getQuantity()) ? emailMessage.getQuantity().toString() : "")
                .replace("{{" + EmailMessage.Fields.contactName.name() + "}}", Objects.nonNull(emailMessage.getContactName()) ? emailMessage.getContactName() : "")
                .replace("{{" + EmailMessage.Fields.contactEmail.name() + "}}", Objects.nonNull(emailMessage.getContactEmail()) ? emailMessage.getContactEmail() : "")
                .replace("{{" + EmailMessage.Fields.nameOfGroup.name() + "}}", Objects.nonNull(emailMessage.getNameOfGroup()) ? emailMessage.getNameOfGroup() : "")
                .replace("{{" + EmailMessage.Fields.codes.name() + "}}", Objects.nonNull(emailMessage.getCodes()) ? emailMessage.getCodes() : "");
    }


    private final static String USER_EMAIL = "<!DOCTYPE html>\n" +
            "<html lang=\"es-Es\">\n" +
            "<head>\n" +
            "    <title>Informações da compra</title>\n" +
            "</head>\n" +
            "<body>\n" +
            "<div style=\"font-family: 'Arial Unicode MS', sans-serif; max-width: 600px; margin: 0 auto;\">\n" +
            "    <h2 style=\"text-align: center;\">Informações da compra</h2>\n" +
            "    <p>Aqui estão os detalhes da sua compra:</p>\n" +
            "    <ul>\n" +
            "        <li><strong>Nome:</strong> {{userName}}</li>\n" +
            "        <li><strong>Email:</strong> {{userEmail}}</li>\n" +
            "        <li><strong>Nome do item:</strong> {{purchaseName}}</li>\n" +
            "        <li><strong>Quantidade:</strong> {{quantity}}]</li>\n" +
            "        <li><strong>Nome do grupo:</strong> {{nameOfGroup}}</li>\n" +
            "        <li><strong>Nome do vendedor:</strong> {{contactName}}</li>\n" +
            "        <li><strong>Email do vendedor:</strong> {{contactEmail}}</li>\n" +
            "        <li><strong>Codigo de identificação única da compra:</strong> {{codes}}</li>\n" +
            "    </ul>\n" +
            "    <p>Sinta-se livre para entrar em contato direto com o administrador do grupo onde a compra foi realizada.</p>\n" +
            "    <p>Muito obrigado,<br>\n" +
            "        Orbis Rede Geo-Social\n" +
            "    </p>\n" +
            "</div>\n" +
            "</body>\n" +
            "</html>";


    private final static String ADMIN_EMAIL = "<!DOCTYPE html>\n" +
            "<html lang=\"es-Es\">\n" +
            "<head>\n" +
            "    <title>Informações da compra</title>\n" +
            "</head>\n" +
            "<body>\n" +
            "<div style=\"font-family: 'Arial Unicode MS', sans-serif; max-width: 600px; margin: 0 auto;\">\n" +
            "    <h2 style=\"text-align: center;\">Informações da venda</h2>\n" +
            "    <p>Aqui estão os detalhes da sua venda:</p>\n" +
            "    <ul>\n" +
            "        <li><strong>Nome do comprador:</strong> {{userName}}</li>\n" +
            "        <li><strong>Email do comprador:</strong> {{userEmail}}</li>\n" +
            "        <li><strong>Nome do item:</strong> {{purchaseName}}</li>\n" +
            "        <li><strong>Quantidade:</strong> {{quantity}}</li>\n" +
            "        <li><strong>Nome do grupo:</strong> {{nameOfGroup}}</li>\n" +
            "        <li><strong>Codigo de identificação única da compra:</strong> {{codes}}</li>\n" +
            "    </ul>\n" +
            "    <p>Sinta-se livre para entrar em contato direto com o comprador via o email listado acima.</p>\n" +
            "    <p>Muito obrigado,<br>\n" +
            "        Orbis Rede Geo-Social\n" +
            "    </p>\n" +
            "</div>\n" +
            "</body>\n" +
            "</html>";
}
