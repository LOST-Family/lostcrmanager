package webserver.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import datawrapper.Player;
import datawrapper.Clan;
import datawrapper.User;
import datawrapper.Kickpoint;
import java.util.ArrayList;
import java.util.List;

public class PlayerDTO {

    @JsonProperty("tag")
    private String tag;

    @JsonProperty("nameDB")
    private String nameDB;

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("roleInClan")
    private String roleInClan;

    @JsonProperty("isHidden")
    private Boolean isHidden;

    @JsonProperty("clanDB")
    private ClanDTO clanDB;

    @JsonProperty("totalKickpoints")
    private Long totalKickpoints;

    @JsonProperty("activeKickpoints")
    private List<KickpointDTO> activeKickpoints;

    public PlayerDTO() {}

    public PlayerDTO(Player player) {
        this.tag = player.getTag();

        try { this.nameDB = player.getNameDB(); } catch (Exception e) { this.nameDB = null; }

        User user = player.getUser();
        this.userId = user != null ? user.getUserID() : null;

        Player.RoleType roleDB = player.getRole();
        if (roleDB != null) { this.roleInClan = roleDB.name(); } else { this.roleInClan = "NOTINCLAN"; }

        this.isHidden = player.isHiddenColeader();

        Clan clanDbObj = player.getClanDB();
        this.clanDB = clanDbObj != null ? new ClanDTO(clanDbObj) : null;

        try { this.totalKickpoints = player.getTotalKickpoints(); } catch (Exception e) { this.totalKickpoints = null; }

        try {
            ArrayList<Kickpoint> kickpoints = player.getActiveKickpoints();
            if (kickpoints != null && !kickpoints.isEmpty()) {
                this.activeKickpoints = new ArrayList<>();
                for (Kickpoint kp : kickpoints) { this.activeKickpoints.add(new KickpointDTO(kp)); }
            }
        } catch (Exception e) { this.activeKickpoints = null; }
    }

    // Getters and setters
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public String getNameDB() { return nameDB; }
    public void setNameDB(String nameDB) { this.nameDB = nameDB; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getRoleInClan() { return roleInClan; }
    public void setRoleInClan(String roleInClan) { this.roleInClan = roleInClan; }
    public Boolean getIsHidden() { return isHidden; }
    public void setIsHidden(Boolean isHidden) { this.isHidden = isHidden; }
    public ClanDTO getClanDB() { return clanDB; }
    public void setClanDB(ClanDTO clanDB) { this.clanDB = clanDB; }
    public Long getTotalKickpoints() { return totalKickpoints; }
    public void setTotalKickpoints(Long totalKickpoints) { this.totalKickpoints = totalKickpoints; }
    public List<KickpointDTO> getActiveKickpoints() { return activeKickpoints; }
    public void setActiveKickpoints(List<KickpointDTO> activeKickpoints) { this.activeKickpoints = activeKickpoints; }
}
