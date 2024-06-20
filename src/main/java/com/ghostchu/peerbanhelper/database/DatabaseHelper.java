package com.ghostchu.peerbanhelper.database;

import com.ghostchu.peerbanhelper.module.RuleUpdateType;
import com.ghostchu.peerbanhelper.text.Lang;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.sql.Date;
import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
public class DatabaseHelper {
    private final DatabaseManager manager;

    public DatabaseHelper(DatabaseManager manager) throws SQLException {
        this.manager = manager;
        try {
            createTables();
            performUpgrade();
        } catch (SQLException e) {
            log.warn(Lang.DATABASE_SETUP_FAILED, e);
            throw e;
        }
    }

    private void performUpgrade() throws SQLException {
        try (Connection connection = manager.getConnection()) {
            int v = 0;
            String version = getMetadata("version");
            if (version != null) {
                v = Integer.parseInt(version);
            }
            if (v == 0) {
                try {
                    // 升级 peer_id / peer_clientname 段可空
                    connection.prepareStatement("ALTER TABLE ban_logs RENAME TO ban_logs_v1_old_backup").execute();
                    connection.prepareStatement("DROP INDEX ban_logs_idx").execute();
                    createTables();
                } catch (Exception ignored) {
                }
                v++;
            }
            setMetadata("version", String.valueOf(v));
        }

    }

    public int setMetadata(String key, String value) throws SQLException {
        try (Connection connection = manager.getConnection()) {
            @Cleanup
            PreparedStatement ps = connection.prepareStatement("REPLACE INTO metadata (key, value) VALUES (?,?)");
            ps.setString(1, key);
            ps.setString(2, value);
            return ps.executeUpdate();
        }
    }

    public String getMetadata(String key) throws SQLException {
        try (Connection connection = manager.getConnection()) {
            @Cleanup
            PreparedStatement ps = connection.prepareStatement("SELECT * FROM metadata WHERE `key` = ? LIMIT 1");
            @Cleanup
            ResultSet set = ps.executeQuery();
            if (set.next()) {
                return set.getString("key");
            } else {
                return null;
            }
        }
    }

    public long queryBanLogsCount() throws SQLException {
        try (Connection connection = manager.getConnection()) {
            @Cleanup
            ResultSet set = connection.createStatement().executeQuery("SELECT COUNT(*) AS count FROM ban_logs");
            return set.getLong("count");
        }
    }

    public List<BanLog> queryBanLogs(Date from, Date to, int pageIndex, int pageSize) throws SQLException {
        try (Connection connection = manager.getConnection()) {
            PreparedStatement ps;
            if (from == null && to == null) {
                ps = connection.prepareStatement("SELECT * FROM ban_logs ORDER BY id DESC LIMIT " + (pageIndex * pageSize) + ", " + pageSize);
            } else {
                if (from == null || to == null) {
                    throw new IllegalArgumentException("from or null cannot be null if any provided");
                } else {
                    ps = connection.prepareStatement("SELECT * FROM ban_logs WHERE ban_at >= ? AND ban_at <= ? ORDER BY id DESC LIMIT " + (pageIndex * pageSize) + ", " + pageSize);
                }
            }
            try (ps) {
                if (from != null) {
                    ps.setDate(1, from);
                    ps.setDate(2, to);
                }
                try (ResultSet set = ps.executeQuery()) {
                    List<BanLog> logs = new LinkedList<>(); // 尽可能节约内存，不使用 ArrayList
                    while (set.next()) {
                        BanLog banLog = new BanLog(
                                set.getLong("ban_at"),
                                set.getLong("unban_at"),
                                set.getString("peer_ip"),
                                set.getInt("peer_port"),
                                set.getString("peer_id"),
                                set.getString("peer_clientname"),
                                set.getLong("peer_uploaded"),
                                set.getLong("peer_downloaded"),
                                set.getDouble("peer_progress"),
                                set.getString("torrent_infohash"),
                                set.getString("torrent_name"),
                                set.getLong("torrent_size"),
                                set.getString("module"),
                                set.getString("description")
                        );
                        logs.add(banLog);
                    }
                    return logs;
                }
            }
        }
    }

    public Map<String, Long> findMaxBans(int n) throws SQLException {
        try (Connection connection = manager.getConnection()) {
            @Cleanup
            PreparedStatement ps = connection.prepareStatement("SELECT peer_ip, COUNT(*) AS count " +
                    "FROM ban_logs WHERE ban_at >= ?" +
                    "GROUP BY peer_ip " +
                    "ORDER BY count DESC LIMIT " + n);
            ps.setTimestamp(1, new Timestamp(Instant.now().minus(14, ChronoUnit.DAYS).toEpochMilli()));
            @Cleanup
            ResultSet set = ps.executeQuery();
            Map<String, Long> map = new LinkedHashMap<>();
            while (set.next()) {
                map.put(set.getString("peer_ip"), set.getLong("count"));
            }
            return map;
        }
    }

//    public int cleanOutdatedBanLogs(int days) {
//        try (Connection connection = manager.getConnection()) {
//            @Cleanup
//            PreparedStatement ps = connection.prepareStatement("DELETE FROM ban_logs where DATE(ban_at) <= DATE(DATE_SUB(NOW(),INTERVAL "+days+" day))");
//            return ps.executeUpdate();
//        } catch (SQLException e) {
//            throw new RuntimeException(e);
//        }
//    }

    public int insertBanLogs(BanLog banLog) throws SQLException {
        try (Connection connection = manager.getConnection()) {
            @Cleanup
            PreparedStatement ps = connection.prepareStatement("INSERT INTO ban_logs (ban_at, unban_at, peer_ip, peer_port, peer_id, peer_clientname," +
                    " peer_downloaded, peer_uploaded, peer_progress, torrent_infohash, torrent_name, torrent_size, module, description)" +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
            ps.setLong(1, banLog.banAt());
            ps.setLong(2, banLog.unbanAt());
            ps.setString(3, banLog.peerIp());
            ps.setInt(4, banLog.peerPort());
            String peerId = banLog.peerId();
            ps.setString(5, peerId);
            String clientName = banLog.peerClientName();
            ps.setString(6, clientName);
            ps.setLong(7, banLog.peerDownloaded());
            ps.setLong(8, banLog.peerUploaded());
            ps.setDouble(9, banLog.peerProgress());
            ps.setString(10, banLog.torrentInfoHash());
            ps.setString(11, banLog.torrentName());
            ps.setLong(12, banLog.torrentSize());
            ps.setString(13, banLog.module());
            ps.setString(14, banLog.description());
            return ps.executeUpdate();
        }
    }

    public void createTables() throws SQLException {
        try (Connection connection = manager.getConnection()) {
            if (!hasTable("ban_logs")) {
                @Cleanup
                PreparedStatement ps = connection.prepareStatement("""
                                            CREATE TABLE ban_logs (
                                              "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                                              "ban_at" integer NOT NULL,
                                              "unban_at" integer NOT NULL,
                                              "peer_ip" TEXT NOT NULL,
                                              "peer_port" integer NOT NULL,
                                              "peer_id" TEXT NULL,
                                              "peer_clientname" TEXT NULL,
                                              "peer_downloaded" integer NOT NULL,
                                              "peer_uploaded" integer NOT NULL,
                                              "peer_progress" real NOT NULL,
                                              "torrent_infohash" TEXT NOT NULL,
                                              "torrent_name" TEXT NOT NULL,
                                              "torrent_size" integer NOT NULL,
                                              "module" TEXT,
                                              "description" TEXT NOT NULL
                                            );
                        """);
                ps.executeUpdate();
                ps = connection.prepareStatement("""
                                        CREATE INDEX ban_logs_idx
                                        ON ban_logs (
                                          "id",
                                          "ban_at",
                                          "peer_ip",
                                          "torrent_infohash",
                                          "module"
                                        );
                        """);
                ps.executeUpdate();
            }
            if (!hasTable("metadata")) {
                @Cleanup
                PreparedStatement ps = connection.prepareStatement("""
                                            CREATE TABLE metadata (
                                              "key" TEXT NOT NULL PRIMARY KEY,
                                              "value" TEXT
                                            );
                        """);
                ps.executeUpdate();
            }
            if (!hasTable("rule_sub_logs")) {
                @Cleanup
                PreparedStatement ps = connection.prepareStatement("""
                                                          create table rule_sub_logs
                                                                  (
                                                                          id         integer not null
                                                          constraint rule_sub_logs_pk
                                                          primary key autoincrement,
                                                                  rule_id    integer not null,
                                                                  update_time integer,
                                                                  ent_count  integer,
                                                                  update_type  TEXT
                                              );
                        """);
                ps.executeUpdate();
            }
            if (hasTable("rule_sub_logs")) {
                @Cleanup
                PreparedStatement ps1 = connection.prepareStatement("""
                        update rule_sub_logs set update_type = 'AUTO' where update_type = '自动更新';
                        """);
                ps1.executeUpdate();
                @Cleanup
                PreparedStatement ps2 = connection.prepareStatement("""
                        update rule_sub_logs set update_type = 'MANUAL' where update_type = '手动更新';
                        """);
                ps2.executeUpdate();
            }
        }
    }


    /**
     * Returns true if the given table has the given column
     *
     * @param table  The table
     * @param column The column
     * @return True if the given table has the given column
     * @throws SQLException If the database isn't connected
     */
    public boolean hasColumn(@NotNull String table, @NotNull String column) throws SQLException {
        if (!hasTable(table)) {
            return false;
        }
        String query = "SELECT * FROM " + table + " LIMIT 1";
        boolean match = false;
        try (Connection connection = manager.getConnection(); PreparedStatement ps = connection.prepareStatement(query); ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData metaData = rs.getMetaData();
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                if (metaData.getColumnLabel(i).equals(column)) {
                    match = true;
                    break;
                }
            }
        } catch (SQLException e) {
            return match;
        }
        return match; // Uh, wtf.
    }


    /**
     * Returns true if the table exists
     *
     * @param table The table to check for
     * @return True if the table is found
     * @throws SQLException Throw exception when failed execute somethins on SQL
     */
    public boolean hasTable(@NotNull String table) throws SQLException {
        Connection connection = manager.getConnection();
        boolean match = false;
        try (ResultSet rs = connection.getMetaData().getTables(null, null, "%", null)) {
            while (rs.next()) {
                if (table.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                    match = true;
                    break;
                }
            }
        } finally {
            connection.close();
        }
        return match;
    }

    /**
     * 查询订阅规则更新日志数量
     *
     * @param ruleId 订阅规则ID
     * @return 日志数量
     * @throws SQLException SQL异常
     */
    public int countRuleSubLogs(String ruleId) throws SQLException {
        try (Connection connection = manager.getConnection()) {
            PreparedStatement ps;
            boolean idNotEmpty = null != ruleId && !ruleId.isEmpty();
            String sql = "SELECT count(1) as count FROM rule_sub_logs " +
                    (idNotEmpty ? "WHERE rule_id = ? " : "");
            ps = connection.prepareStatement(sql);
            if (idNotEmpty) {
                ps.setString(1, ruleId);
            }
            try (ps) {
                try (ResultSet set = ps.executeQuery()) {
                    int count = 0;
                    while (set.next()) {
                        count = set.getInt("count");
                    }
                    return count;
                }
            }
        }
    }

    /**
     * 查询订阅规则更新日志
     *
     * @param ruleId    订阅规则ID
     * @param pageIndex 页码
     * @param pageSize  每页数量
     * @return 日志列表
     * @throws SQLException SQL异常
     */
    public List<RuleSubLog> queryRuleSubLogs(String ruleId, int pageIndex, int pageSize) throws SQLException {
        try (Connection connection = manager.getConnection()) {
            PreparedStatement ps;
            boolean idNotEmpty = null != ruleId && !ruleId.isEmpty();
            String sql = "SELECT * FROM rule_sub_logs " +
                    (idNotEmpty ? "WHERE rule_id = ? " : "") +
                    "ORDER BY id DESC LIMIT " + pageIndex * pageSize + ", " + pageSize;
            ps = connection.prepareStatement(sql);
            if (idNotEmpty) {
                ps.setString(1, ruleId);
            }
            try (ps) {
                try (ResultSet set = ps.executeQuery()) {
                    List<RuleSubLog> infos = new ArrayList<>();
                    while (set.next()) {
                        infos.add(new RuleSubLog(
                                set.getString("rule_id"),
                                set.getLong("update_time"),
                                set.getInt("ent_count"),
                                RuleUpdateType.valueOf(set.getString("update_type"))
                        ));
                    }
                    return infos;
                }
            }
        }
    }

    /**
     * 插入订阅规则更新日志
     *
     * @param ruleId     订阅规则ID
     * @param count      更新数量
     * @param updateType 更新类型
     * @throws SQLException SQL异常
     */
    public void insertRuleSubLog(String ruleId, int count, RuleUpdateType updateType) throws SQLException {
        try (Connection connection = manager.getConnection()) {
            PreparedStatement ps;
            ps = connection.prepareStatement("INSERT INTO rule_sub_logs (rule_id, update_time, ent_count, update_type) VALUES (?,?,?,?)");
            ps.setString(1, ruleId);
            ps.setLong(2, System.currentTimeMillis());
            ps.setInt(3, count);
            ps.setString(4, updateType.toString());
            ps.executeUpdate();
        }
    }

}
