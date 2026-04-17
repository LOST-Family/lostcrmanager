package datawrapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import datautil.Connection;
import datautil.DBUtil;

public class MemberSignoff {
    private Long id;
    private final String playerTag;
    private Timestamp startDate;
    private Timestamp endDate; // null = unlimited
    private String reason;
    private String createdByDiscordId;
    private Timestamp createdAt;

    private boolean receivePings;

    public MemberSignoff(String playerTag) {
        this.playerTag = playerTag;
        loadFromDB();
    }

    private MemberSignoff(ResultSet rs) throws SQLException {
        this.id = rs.getLong("id");
        this.playerTag = rs.getString("player_tag");
        this.startDate = rs.getTimestamp("start_date");
        this.endDate = rs.getTimestamp("end_date");
        this.reason = rs.getString("reason");
        this.createdByDiscordId = rs.getString("created_by_discord_id");
        this.createdAt = rs.getTimestamp("created_at");
        this.receivePings = rs.getBoolean("receive_pings");
    }

    private void loadFromDB() {
        String sql = "SELECT id, start_date, end_date, reason, created_by_discord_id, created_at, receive_pings FROM member_signoffs WHERE player_tag = ?";
        try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, playerTag);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    this.id = rs.getLong("id");
                    this.startDate = rs.getTimestamp("start_date");
                    this.endDate = rs.getTimestamp("end_date");
                    this.reason = rs.getString("reason");
                    this.createdByDiscordId = rs.getString("created_by_discord_id");
                    this.createdAt = rs.getTimestamp("created_at");
                    this.receivePings = rs.getBoolean("receive_pings");
                }
            }
        } catch (final SQLException e) {
        }
    }

    public boolean exists() {
        return id != null;
    }

    public boolean isActive() {
        if (!exists()) {
            return false;
        }
        
        Timestamp now = Timestamp.from(Instant.now());
        // Must have started already
        if (startDate != null && startDate.after(now)) {
            return false;
        }

        // If end_date is null, it's unlimited/permanent
        if (endDate == null) {
            return true;
        }
        // Otherwise check if current time is before end date
        return now.before(endDate);
    }

    /**
     * Static method to check if a player is signed off without creating a full
     * instance. More efficient for quick checks.
     */
    public static boolean isSignedOff(String playerTag) {
        String sql = "SELECT COUNT(*) FROM member_signoffs WHERE player_tag = ? AND start_date <= NOW() AND (end_date IS NULL OR end_date > NOW())";
        Long count = null;
        try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, playerTag);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    count = rs.getLong(1);
                }
            }
        } catch (final SQLException e) {
        }
        return count != null && count > 0;
    }

    /**
     * Static method to check if a player is signed off and should NOT receive
     * pings. Returns true if the player is signed off AND has receive_pings =
     * false.
     */
    public static boolean shouldSkipPings(String playerTag) {
        MemberSignoff signoff = new MemberSignoff(playerTag);
        return signoff.isActive() && !signoff.isReceivePings();
    }

    public Long getId() {
        return id;
    }

    public String getPlayerTag() {
        return playerTag;
    }

    public Timestamp getStartDate() {
        return startDate;
    }

    public Timestamp getEndDate() {
        return endDate;
    }

    public String getReason() {
        return reason;
    }

    public String getCreatedByDiscordId() {
        return createdByDiscordId;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public boolean isReceivePings() {
        return receivePings;
    }

    public boolean isUnlimited() {
        return exists() && endDate == null;
    }

    public static void create(String playerTag, Timestamp startDate, Timestamp endDate, String reason, String createdByDiscordId,
            boolean receivePings) {
        String sql = "INSERT INTO member_signoffs (player_tag, start_date, end_date, reason, created_by_discord_id, receive_pings) "
            + "VALUES (?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT (player_tag) DO UPDATE SET "
            + "start_date = EXCLUDED.start_date, "
            + "end_date = EXCLUDED.end_date, "
            + "reason = EXCLUDED.reason, "
            + "created_by_discord_id = EXCLUDED.created_by_discord_id, "
            + "receive_pings = EXCLUDED.receive_pings, "
            + "created_at = NOW()";
        DBUtil.executeUpdate(sql, playerTag, startDate, endDate, reason, createdByDiscordId, receivePings);
    }

    public static void remove(String playerTag) {
        String sql = "UPDATE member_signoffs SET end_date = NOW() WHERE player_tag = ? AND (end_date IS NULL OR end_date > NOW())";
        DBUtil.executeUpdate(sql, playerTag);
    }

    /**
     * Returns all signoffs that overlap with the given month.
     * A signoff overlaps if it started before the end of the month AND
     * (has no end date OR ended on/after the start of the month).
     */
    public static List<MemberSignoff> getSignoffsForMonth(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        Timestamp monthStart = Timestamp.valueOf(ym.atDay(1).atStartOfDay());
        Timestamp monthEnd = Timestamp.valueOf(ym.plusMonths(1).atDay(1).atStartOfDay());

        String sql = "SELECT id, player_tag, start_date, end_date, reason, created_by_discord_id, created_at, receive_pings "
                + "FROM member_signoffs "
                + "WHERE start_date < ? AND (end_date IS NULL OR end_date >= ?) "
                + "ORDER BY start_date ASC";

        List<MemberSignoff> results = new ArrayList<>();
        try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
            pstmt.setTimestamp(1, monthEnd);
            pstmt.setTimestamp(2, monthStart);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new MemberSignoff(rs));
                }
            }
        } catch (final SQLException e) {
        }
        return results;
    }

    public void update(Timestamp newEndDate) {
        if (!exists()) {
            return;
        }
        String sql = "UPDATE member_signoffs SET end_date = ? WHERE player_tag = ?";
        DBUtil.executeUpdate(sql, newEndDate, playerTag);
        this.endDate = newEndDate;
    }
}
