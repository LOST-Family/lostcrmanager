package webserver.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import datawrapper.Clan;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object for Clan in REST API
 */
public class ClanDTO {

    @JsonProperty("tag")
    private String tag;

    @JsonProperty("index")
    private Long index;

    @JsonProperty("nameDB")
    private String nameDB;


    @JsonProperty("description")
    private String description;

    @JsonProperty("maxKickpoints")
    private Long maxKickpoints;

    @JsonProperty("kickpointsExpireAfterDays")
    private Integer kickpointsExpireAfterDays;

    @JsonProperty("kickpointReasons")
    private List<KickpointReasonDTO> kickpointReasons;

    public ClanDTO() {
        // Default constructor for Jackson
    }

    public ClanDTO(Clan clan) {
        this.tag = clan.getTag();

        try {
            this.index = clan.getIndex();
        } catch (Exception e) {
            this.index = null;
        }

        try {
            this.nameDB = clan.getNameDB();
        } catch (Exception e) {
            this.nameDB = null;
        }


        try {
            this.description = clan.getDescriptionDB();
        } catch (Exception e) {
            this.description = null;
        }

        try {
            this.maxKickpoints = clan.getMaxKickpoints();
        } catch (Exception e) {
            this.maxKickpoints = null;
        }


        try {
            this.kickpointsExpireAfterDays = clan.getDaysKickpointsExpireAfter();
        } catch (Exception e) {
            this.kickpointsExpireAfterDays = null;
        }

        try {
            ArrayList<datawrapper.KickpointReason> reasons = clan.getKickpointReasons();
            if (reasons != null) {
                this.kickpointReasons = new ArrayList<>();
                for (datawrapper.KickpointReason reason : reasons) {
                    this.kickpointReasons.add(new KickpointReasonDTO(reason));
                }
            }
        } catch (Exception e) {
            this.kickpointReasons = null;
        }
    }

    // Getters and setters omitted for brevity (kept as in original)
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public Long getIndex() { return index; }
    public void setIndex(Long index) { this.index = index; }
    public String getNameDB() { return nameDB; }
    public void setNameDB(String nameDB) { this.nameDB = nameDB; }
    // badgeUrl removed (not used in lostcrmanager)
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getMaxKickpoints() { return maxKickpoints; }
    public void setMaxKickpoints(Long maxKickpoints) { this.maxKickpoints = maxKickpoints; }
    // minSeasonWins removed
    public Integer getKickpointsExpireAfterDays() { return kickpointsExpireAfterDays; }
    public void setKickpointsExpireAfterDays(Integer kickpointsExpireAfterDays) { this.kickpointsExpireAfterDays = kickpointsExpireAfterDays; }
    public List<KickpointReasonDTO> getKickpointReasons() { return kickpointReasons; }
    public void setKickpointReasons(List<KickpointReasonDTO> kickpointReasons) { this.kickpointReasons = kickpointReasons; }
}
