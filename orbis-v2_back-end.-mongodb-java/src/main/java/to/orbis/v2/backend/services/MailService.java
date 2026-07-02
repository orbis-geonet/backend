package to.orbis.v2.backend.services;

import org.springframework.beans.factory.annotation.Value;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

// @Service
public class MailService {

    // JavaMailSender javaMailSender;
    String adminMail;
    String fromMail;

    public MailService(
            //JavaMailSender javaMailSender,
            @Value("${orbis.adminMail}") String adminMail,
            @Value("${orbis.fromMail}") String fromMail
    ) {
        //this.javaMailSender = javaMailSender;
        this.adminMail = adminMail;
        this.fromMail = fromMail;
    }

    public Mono<String> sendToAdmin(String title, String body) {
        /*SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromMail);
        message.setTo(adminMail);
        message.setSubject(title);
        message.setText(body);*/

        return Mono.fromFuture(CompletableFuture.supplyAsync(() -> {
                    //          javaMailSender.send(message);
                    return "Sent";
                }))
                .switchIfEmpty(Mono.just("Sent"));
    }
}
