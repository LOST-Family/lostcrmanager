package datawrapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;

import datautil.Connection;
import datautil.DBUtil;

public class TrackChannel {

    private int id;
    private String name;
    private String channelId;
    private OffsetDateTime timestamp;

    public TrackChannel(int id) {
        this.id = id;
        load();
    }

    public TrackChannel(int id, String name, String channelId, OffsetDateTime timestamp) {
        this.id = id;
        this.name = name;
        this.channelId = channelId;
        this.timestamp = timestamp;
    }

    private void load() {
        String sql = "SELECT name, channelid, timestamp FROM trackchannels WHERE id = ?";
        try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    this.name = rs.getString("name");
                    this.channelId = rs.getString("channelid");
                    this.timestamp = rs.getObject("timestamp", OffsetDateTime.class);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getChannelId() {
        return channelId;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
        DBUtil.executeUpdate("UPDATE trackchannels SET timestamp = ? WHERE id = ?", timestamp, id);
    }

    public static void add(String name, String channelId) {
        DBUtil.executeUpdate("INSERT INTO trackchannels (name, channelid) VALUES (?, ?)", name, channelId);
    }

    public static void remove(int id) {
        DBUtil.executeUpdate("DELETE FROM trackchannels WHERE id = ?", id);
    }

    public static ArrayList<TrackChannel> getAll() {
        ArrayList<TrackChannel> list = new ArrayList<>();
        String sql = "SELECT id, name, channelid, timestamp FROM trackchannels ORDER BY id ASC";
        try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new TrackChannel(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("channelid"),
                            rs.getObject("timestamp", OffsetDateTime.class)));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static TrackChannel getById(int id) {
        TrackChannel tc = new TrackChannel(id);
        if (tc.getName() == null) {
            return null;
        }
        return tc;
    }
}
