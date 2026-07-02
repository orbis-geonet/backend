package to.orbis.v2.backend.models.dto.email;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum EmailType {
    USER("email_user_purchase_name", "/email/email_user.html", "Confirmação de compra {{groupName}}"),
    ADMIN("email_admin_purchase_name", "/email/email_admin.html", "Confirmação de venda {{groupName}}");

    private final String templateName;
    private final String filePath;
    private final String subject;
}
