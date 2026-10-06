package ng.proptech.dto;

import java.math.BigDecimal;
import java.util.UUID;
import ng.proptech.domain.ListingStatus;

public final class AgentDtos {

    private AgentDtos() {}

    public record MyListingSummary(UUID listingId, UUID propertyId, String propertyTitle,
                                    BigDecimal annualRent, BigDecimal agencyFee, ListingStatus status) {}

    public record WalletSummary(BigDecimal balance) {}
}
