package to.orbis.v2.backend.models.dto;

import lombok.Builder;
import lombok.Data;
import to.orbis.v2.backend.models.Currency;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class UserPurchaseGroupDto {
    String name;
    String purchaseKey;
    GroupPurchaseDto group;
    UserPurchaseDto user;
    BigDecimal price;
    Currency currency;
    Integer number;
    List<String> codes;

    @Data
    @Builder
    public static class GroupPurchaseDto{
        String groupKey;
        String groupName;
    }

    @Data
    @Builder
    public static class UserPurchaseDto{
        String userKey;
        String displayName;
    }
}
