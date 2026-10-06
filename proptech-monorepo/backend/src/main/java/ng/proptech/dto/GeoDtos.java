package ng.proptech.dto;

public final class GeoDtos {

    private GeoDtos() {}

    public record StateSummary(String id, String name, String code) {}

    public record LgaSummary(String id, String name, boolean isUrban) {}

    public record SettlementSummary(String id, String name) {}

    public record EstateSummary(String id, String name, boolean isGated) {}
}
