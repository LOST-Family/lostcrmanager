package webserver.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import datawrapper.KickpointReason;

public class KickpointReasonDTO {

    @JsonProperty("name")
    private String name;

    @JsonProperty("clanTag")
    private String clanTag;

    @JsonProperty("amount")
    private Integer amount;

    public KickpointReasonDTO() {}

    public KickpointReasonDTO(KickpointReason reason) {
        try { this.name = reason.getReason(); } catch (Exception e) { this.name = null; }
        try { this.clanTag = reason.getClanTag(); } catch (Exception e) { this.clanTag = null; }
        try { this.amount = reason.getAmount(); } catch (Exception e) { this.amount = null; }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getClanTag() { return clanTag; }
    public void setClanTag(String clanTag) { this.clanTag = clanTag; }
    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }
}
