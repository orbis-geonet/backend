package to.orbis.v2.backend.services;

import to.orbis.v2.backend.models.dto.email.EmailMessage;
import to.orbis.v2.backend.models.dto.email.EmailType;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class PaymentReceiptMessages {
    private static final String PATH = "/paymentReceipt/";
    private static final String DEFAULT_LANGUAGE = "en";
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of(
            "en", "zh", "hi", "es", "fr", "ar", "bn", "pt", "ru", "ur", "in", "de", "ja", "sw", "mr", "te",
            "tr", "ta", "vi", "ko", "fa", "ha", "jv", "it", "pa", "gu", "kn", "th", "am", "ml", "pl", "uk",
            "tl", "yo", "or", "my", "su", "ro", "nl", "ig", "ne", "si", "km", "ps", "el", "hu", "ms", "zu"
    );
    private static final ConcurrentHashMap<String, Properties> CACHE = new ConcurrentHashMap<>();

    private PaymentReceiptMessages() {
    }

    static String subject(EmailMessage emailMessage) {
        var receipt = receipt(emailMessage.getLanguage());
        var key = emailMessage.getEmailType() == EmailType.ADMIN ? "admin.subject" : "user.subject";
        return fill(receipt.text(key), emailMessage);
    }

    static String body(EmailMessage emailMessage) {
        var receipt = receipt(emailMessage.getLanguage());
        var admin = emailMessage.getEmailType() == EmailType.ADMIN;
        var title = html(fill(receipt.text(admin ? "admin.title" : "user.title"), emailMessage));
        return "<!DOCTYPE html>\n" +
                "<html lang=\"" + html(receipt.language) + "\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <title>" + title + "</title>\n" +
                "</head>\n" +
                "<body>\n" +
                "<div style=\"font-family: 'Arial Unicode MS', sans-serif; max-width: 600px; margin: 0 auto;\">\n" +
                "    <h2 style=\"text-align: center;\">" + title + "</h2>\n" +
                "    <p>" + html(receipt.text(admin ? "admin.intro" : "user.intro")) + "</p>\n" +
                "    <ul>\n" +
                (admin ? adminRows(receipt, emailMessage) : userRows(receipt, emailMessage)) +
                "    </ul>\n" +
                "    <p>" + html(receipt.text(admin ? "admin.closing" : "user.closing")) + "</p>\n" +
                "    <p>" + html(receipt.text("thanks")) + "<br>\n" +
                "        " + html(receipt.text("signature")) + "\n" +
                "    </p>\n" +
                "</div>\n" +
                "</body>\n" +
                "</html>";
    }

    private static String userRows(ReceiptCopy receipt, EmailMessage emailMessage) {
        return row(receipt, "label.name", emailMessage.getUserName()) +
                row(receipt, "label.email", emailMessage.getUserEmail()) +
                row(receipt, "label.itemName", emailMessage.getPurchaseName()) +
                row(receipt, "label.quantity", Objects.toString(emailMessage.getQuantity(), "")) +
                row(receipt, "label.groupName", emailMessage.getNameOfGroup()) +
                row(receipt, "label.sellerName", emailMessage.getContactName()) +
                row(receipt, "label.sellerEmail", emailMessage.getContactEmail()) +
                row(receipt, "label.purchaseCode", emailMessage.getCodes()) +
                paymentRows(receipt, emailMessage);
    }

    private static String adminRows(ReceiptCopy receipt, EmailMessage emailMessage) {
        return row(receipt, "label.buyerName", emailMessage.getUserName()) +
                row(receipt, "label.buyerEmail", emailMessage.getUserEmail()) +
                row(receipt, "label.itemName", emailMessage.getPurchaseName()) +
                row(receipt, "label.quantity", Objects.toString(emailMessage.getQuantity(), "")) +
                row(receipt, "label.groupName", emailMessage.getNameOfGroup()) +
                row(receipt, "label.purchaseCode", emailMessage.getCodes()) +
                paymentRows(receipt, emailMessage);
    }

    private static String row(ReceiptCopy receipt, String labelKey, String value) {
        return "        <li><strong>" + html(receipt.text(labelKey)) + ":</strong> " + html(value) + "</li>\n";
    }

    private static String paymentRows(ReceiptCopy receipt, EmailMessage emailMessage) {
        var rows = new StringBuilder();
        if (!blank(emailMessage.getSolscanUrl())) {
            rows.append(linkRow(receipt, "label.transaction", emailMessage.getSolscanUrl()));
        } else if (!blank(emailMessage.getTxSignature())) {
            rows.append(row(receipt, "label.transaction", emailMessage.getTxSignature()));
        }
        if (!blank(emailMessage.getTreasuryCutOrbis())) {
            rows.append(row(receipt, "label.treasuryCut", orbis(emailMessage.getTreasuryCutOrbis())));
        }
        if (!blank(emailMessage.getCloneCutOrbis())) {
            rows.append(row(receipt, "label.cloneOperatorCut", orbis(emailMessage.getCloneCutOrbis())));
        }
        if (!blank(emailMessage.getOwnerCutOrbis())) {
            rows.append(row(receipt, "label.tribeOwnerCut", orbis(emailMessage.getOwnerCutOrbis())));
        }
        return rows.toString();
    }

    private static String linkRow(ReceiptCopy receipt, String labelKey, String value) {
        return "        <li><strong>" + html(receipt.text(labelKey)) + ":</strong> <a href=\"" + html(value) + "\">" + html(value) + "</a></li>\n";
    }

    private static String orbis(String value) {
        return value(value) + " ORBIS";
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static ReceiptCopy receipt(String language) {
        var code = normalize(language);
        var selected = properties(code);
        var fallback = DEFAULT_LANGUAGE.equals(code) ? selected : properties(DEFAULT_LANGUAGE);
        return new ReceiptCopy(selected.getProperty("language", code), selected, fallback);
    }

    private static Properties properties(String language) {
        return CACHE.computeIfAbsent(language, PaymentReceiptMessages::load);
    }

    private static Properties load(String language) {
        var properties = new Properties();
        try (var input = PaymentReceiptMessages.class.getResourceAsStream(PATH + language + ".properties")) {
            if (input == null) {
                if (!DEFAULT_LANGUAGE.equals(language)) {
                    return properties;
                }
                throw new IllegalStateException("Missing payment receipt translation for " + language);
            }
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            return properties;
        } catch (Exception e) {
            if (!DEFAULT_LANGUAGE.equals(language)) {
                return properties;
            }
            throw new IllegalStateException("Cannot load payment receipt translation for " + language, e);
        }
    }

    private static String normalize(String language) {
        if (language == null || language.isBlank() || "dd".equalsIgnoreCase(language)) {
            return DEFAULT_LANGUAGE;
        }
        var normalized = language.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        var dash = normalized.indexOf('-');
        if (dash > 0) {
            normalized = normalized.substring(0, dash);
        }
        return SUPPORTED_LANGUAGES.contains(normalized) ? normalized : DEFAULT_LANGUAGE;
    }

    private static String fill(String source, EmailMessage emailMessage) {
        return source
                .replace("{{userName}}", value(emailMessage.getUserName()))
                .replace("{{userEmail}}", value(emailMessage.getUserEmail()))
                .replace("{{purchaseName}}", value(emailMessage.getPurchaseName()))
                .replace("{{quantity}}", Objects.toString(emailMessage.getQuantity(), ""))
                .replace("{{contactName}}", value(emailMessage.getContactName()))
                .replace("{{contactEmail}}", value(emailMessage.getContactEmail()))
                .replace("{{nameOfGroup}}", value(emailMessage.getNameOfGroup()))
                .replace("{{groupName}}", value(emailMessage.getNameOfGroup()))
                .replace("{{codes}}", value(emailMessage.getCodes()))
                .replace("{{txSignature}}", value(emailMessage.getTxSignature()))
                .replace("{{solscanUrl}}", value(emailMessage.getSolscanUrl()))
                .replace("{{ownerCutOrbis}}", value(emailMessage.getOwnerCutOrbis()))
                .replace("{{cloneCutOrbis}}", value(emailMessage.getCloneCutOrbis()))
                .replace("{{treasuryCutOrbis}}", value(emailMessage.getTreasuryCutOrbis()));
    }

    private static String html(String value) {
        return value(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static final class ReceiptCopy {
        final String language;
        final Properties selected;
        final Properties fallback;

        private ReceiptCopy(String language, Properties selected, Properties fallback) {
            this.language = language;
            this.selected = selected;
            this.fallback = fallback;
        }

        private String text(String key) {
            return selected.getProperty(key, fallback.getProperty(key, ""));
        }
    }
}
