package com.linkup.server;

import com.fasterxml.jackson.databind.JsonNode;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side storage (a SQLite file) plus the rules for who may change what.
 *
 * Every row carries a "rev" number taken from one global counter. A client remembers the
 * highest rev it has seen and asks for "everything with rev > N" on its next sync.
 * All public methods are synchronized, so one sync request is applied as a single unit.
 */
public class Store {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Connection conn;
    private long rev;

    public Store(String path) throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:" + path);
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id TEXT PRIMARY KEY, name TEXT NOT NULL, email TEXT NOT NULL UNIQUE," +
                    "password_hash TEXT NOT NULL, salt TEXT NOT NULL, bio TEXT NOT NULL DEFAULT ''," +
                    "interests TEXT NOT NULL DEFAULT '', verified INTEGER NOT NULL DEFAULT 0," +
                    "created_at INTEGER NOT NULL, rev INTEGER NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS sessions (" +
                    "token TEXT PRIMARY KEY, user_id TEXT NOT NULL, created_at INTEGER NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS plans (" +
                    "id TEXT PRIMARY KEY, host_id TEXT NOT NULL, title TEXT NOT NULL, category TEXT NOT NULL," +
                    "description TEXT NOT NULL DEFAULT '', start_time INTEGER NOT NULL, place_name TEXT NOT NULL," +
                    "lat REAL NOT NULL, lng REAL NOT NULL, max_spots INTEGER NOT NULL, join_mode TEXT NOT NULL," +
                    "status TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, rev INTEGER NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS participants (" +
                    "id TEXT PRIMARY KEY, plan_id TEXT NOT NULL, user_id TEXT NOT NULL, status TEXT NOT NULL," +
                    "created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, rev INTEGER NOT NULL," +
                    "UNIQUE (plan_id, user_id))");
            st.execute("CREATE TABLE IF NOT EXISTS messages (" +
                    "id TEXT PRIMARY KEY, plan_id TEXT NOT NULL, sender_id TEXT NOT NULL, body TEXT NOT NULL," +
                    "sent_at INTEGER NOT NULL, rev INTEGER NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS reports (" +
                    "id TEXT PRIMARY KEY, reporter_id TEXT NOT NULL, reported_user_id TEXT NOT NULL," +
                    "plan_id TEXT, reason TEXT NOT NULL, details TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL)");
        }
        rev = queryLong("SELECT MAX(r) FROM (" +
                "SELECT MAX(rev) AS r FROM users UNION ALL SELECT MAX(rev) FROM plans " +
                "UNION ALL SELECT MAX(rev) FROM participants UNION ALL SELECT MAX(rev) FROM messages)");
    }

    // ------------------------------------------------------------------ accounts

    public synchronized Map<String, Object> createUser(String name, String email, String password) throws SQLException {
        if (queryOne("SELECT id FROM users WHERE email = ?", email) != null) {
            throw new ApiException(409, "An account with this email already exists");
        }
        String id = UUID.randomUUID().toString();
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        update("INSERT INTO users (id, name, email, password_hash, salt, verified, created_at, rev) VALUES (?,?,?,?,?,?,?,?)",
                id, name, email, hash(password, salt), Base64.getEncoder().encodeToString(salt),
                isCampusEmail(email) ? 1 : 0, System.currentTimeMillis(), ++rev);
        return ownProfile(id);
    }

    /** Returns the user's own profile if the password matches, otherwise null. */
    public synchronized Map<String, Object> checkLogin(String email, String password) throws SQLException {
        Map<String, Object> row = queryOne("SELECT id, password_hash, salt FROM users WHERE email = ?", email);
        if (row == null) return null;
        byte[] salt = Base64.getDecoder().decode((String) row.get("salt"));
        boolean ok = MessageDigest.isEqual(hash(password, salt).getBytes(), ((String) row.get("password_hash")).getBytes());
        return ok ? ownProfile((String) row.get("id")) : null;
    }

    public synchronized String createSession(String userId) throws SQLException {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        update("INSERT INTO sessions (token, user_id, created_at) VALUES (?,?,?)", token, userId, System.currentTimeMillis());
        return token;
    }

    public synchronized String userForToken(String token) throws SQLException {
        Map<String, Object> row = queryOne("SELECT user_id FROM sessions WHERE token = ?", token);
        return row == null ? null : (String) row.get("user_id");
    }

    private Map<String, Object> ownProfile(String id) throws SQLException {
        return queryOne("SELECT id, name, email, bio, interests, verified, created_at, rev FROM users WHERE id = ?", id);
    }

    /** Accounts on a college domain get the "verified" badge. */
    private static boolean isCampusEmail(String email) {
        return email.endsWith(".edu") || email.endsWith(".edu.in") || email.endsWith(".ac.in");
    }

    private static String hash(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 60_000, 256);
            byte[] out = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ sync

    /**
     * Applies the client's pending changes, then returns everything that changed after "since".
     * Changes that break a rule are skipped and their ids are listed in "rejected".
     */
    public synchronized Map<String, Object> sync(String me, long since, JsonNode changes) throws SQLException {
        List<String> rejected = new ArrayList<>();
        conn.setAutoCommit(false);
        try {
            if (changes.path("profile").isObject()) updateProfile(me, changes.get("profile"));
            for (JsonNode n : changes.path("plans")) {
                if (!upsertPlan(me, n)) rejected.add(n.path("id").asText());
            }
            for (JsonNode n : changes.path("participants")) {
                if (!upsertParticipant(me, n)) rejected.add(n.path("id").asText());
            }
            for (JsonNode n : changes.path("messages")) {
                if (!insertMessage(me, n)) rejected.add(n.path("id").asText());
            }
            for (JsonNode n : changes.path("reports")) {
                if (!insertReport(me, n)) rejected.add(n.path("id").asText());
            }
            conn.commit();
        } catch (SQLException | RuntimeException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }

        Map<String, Object> out = changesSince(me, since);
        out.put("rejected", rejected);
        return out;
    }

    private void updateProfile(String me, JsonNode p) throws SQLException {
        String name = p.path("name").asText("").trim();
        if (name.isEmpty() || name.length() > 50) return;
        update("UPDATE users SET name = ?, bio = ?, interests = ?, rev = ? WHERE id = ?",
                name, limit(p.path("bio").asText(""), 300), limit(p.path("interests").asText(""), 300), ++rev, me);
    }

    private boolean upsertPlan(String me, JsonNode p) throws SQLException {
        String id = p.path("id").asText("");
        String title = p.path("title").asText("").trim();
        String place = p.path("place_name").asText("").trim();
        String joinMode = p.path("join_mode").asText("");
        String status = p.path("status").asText("");
        int maxSpots = p.path("max_spots").asInt(0);
        double lat = p.path("lat").asDouble(999), lng = p.path("lng").asDouble(999);
        long startTime = p.path("start_time").asLong(0);

        if (id.isEmpty() || title.isEmpty() || title.length() > 80 || place.isEmpty() || startTime <= 0
                || maxSpots < 1 || maxSpots > 50 || Math.abs(lat) > 90 || Math.abs(lng) > 180
                || !(joinMode.equals("OPEN") || joinMode.equals("APPROVAL"))
                || !(status.equals("ACTIVE") || status.equals("CANCELLED"))) {
            return false;
        }

        long now = System.currentTimeMillis();
        Map<String, Object> existing = queryOne("SELECT host_id, status FROM plans WHERE id = ?", id);
        if (existing == null) {
            update("INSERT INTO plans (id, host_id, title, category, description, start_time, place_name, lat, lng," +
                            " max_spots, join_mode, status, created_at, updated_at, rev) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    id, me, title, p.path("category").asText("Other"), limit(p.path("description").asText(""), 500),
                    startTime, place, lat, lng, maxSpots, joinMode, status, now, now, ++rev);
            return true;
        }
        if (!me.equals(existing.get("host_id"))) return false;   // only the host can edit a plan
        if ("HIDDEN".equals(existing.get("status"))) status = "HIDDEN";   // a moderation decision sticks
        update("UPDATE plans SET title = ?, category = ?, description = ?, start_time = ?, place_name = ?, lat = ?," +
                        " lng = ?, max_spots = ?, join_mode = ?, status = ?, updated_at = ?, rev = ? WHERE id = ?",
                title, p.path("category").asText("Other"), limit(p.path("description").asText(""), 500), startTime,
                place, lat, lng, maxSpots, joinMode, status, now, ++rev, id);
        return true;
    }

    /**
     * Join rules:
     *  - a user may ask to join (PENDING) or leave (LEFT) a plan they do not host;
     *    an OPEN plan approves them straight away if there is room
     *  - the host may APPROVE or REJECT someone who asked
     */
    private boolean upsertParticipant(String me, JsonNode p) throws SQLException {
        String id = p.path("id").asText("");
        String planId = p.path("plan_id").asText("");
        String userId = p.path("user_id").asText("");
        String wanted = p.path("status").asText("");

        Map<String, Object> plan = queryOne("SELECT host_id, status, join_mode, max_spots FROM plans WHERE id = ?", planId);
        if (plan == null || id.isEmpty()) return false;
        String host = (String) plan.get("host_id");
        boolean active = "ACTIVE".equals(plan.get("status"));
        boolean full = queryLong("SELECT COUNT(*) FROM participants WHERE plan_id = ? AND status = 'APPROVED'", planId)
                >= ((Number) plan.get("max_spots")).intValue();

        Map<String, Object> existing = queryOne("SELECT id, status FROM participants WHERE plan_id = ? AND user_id = ?", planId, userId);
        String current = existing == null ? null : (String) existing.get("status");
        String result;

        if (userId.equals(me) && !me.equals(host)) {
            if (wanted.equals("LEFT")) {
                if (existing == null) return false;
                result = "LEFT";
            } else if (wanted.equals("PENDING") || wanted.equals("APPROVED")) {
                // "APPROVED" is the app's optimistic guess for an OPEN plan; the server decides here
                if ("APPROVED".equals(current) || "PENDING".equals(current)) {
                    result = current;   // already in, nothing to change
                } else {
                    if (!active || "REJECTED".equals(current)) return false;
                    if (plan.get("join_mode").equals("OPEN")) {
                        if (full) return false;
                        result = "APPROVED";
                    } else {
                        result = "PENDING";
                    }
                }
            } else {
                return false;
            }
        } else if (me.equals(host) && !userId.equals(me) && existing != null) {
            if (wanted.equals("APPROVED")) {
                if (full && !"APPROVED".equals(current)) return false;
                result = "APPROVED";
            } else if (wanted.equals("REJECTED")) {
                result = "REJECTED";
            } else {
                return false;
            }
        } else {
            return false;
        }

        long now = System.currentTimeMillis();
        if (existing == null) {
            update("INSERT INTO participants (id, plan_id, user_id, status, created_at, updated_at, rev) VALUES (?,?,?,?,?,?,?)",
                    id, planId, userId, result, now, now, ++rev);
        } else {
            update("UPDATE participants SET status = ?, updated_at = ?, rev = ? WHERE id = ?",
                    result, now, ++rev, existing.get("id"));
        }
        return true;
    }

    /** Only the host and approved participants can post in a plan's chat. */
    private boolean insertMessage(String me, JsonNode m) throws SQLException {
        String id = m.path("id").asText("");
        String planId = m.path("plan_id").asText("");
        String body = m.path("body").asText("").trim();
        if (id.isEmpty() || body.isEmpty() || body.length() > 1000) return false;
        if (queryOne("SELECT id FROM messages WHERE id = ?", id) != null) return true;   // already stored

        boolean member = queryOne("SELECT id FROM plans WHERE id = ? AND host_id = ?", planId, me) != null
                || queryOne("SELECT id FROM participants WHERE plan_id = ? AND user_id = ? AND status = 'APPROVED'", planId, me) != null;
        if (!member) return false;

        update("INSERT INTO messages (id, plan_id, sender_id, body, sent_at, rev) VALUES (?,?,?,?,?,?)",
                id, planId, me, body, System.currentTimeMillis(), ++rev);
        return true;
    }

    /** Stores a report. A plan reported by 3 different people is hidden until a moderator looks at it. */
    private boolean insertReport(String me, JsonNode r) throws SQLException {
        String id = r.path("id").asText("");
        String reported = r.path("reported_user_id").asText("");
        String reason = r.path("reason").asText("").trim();
        String planId = r.path("plan_id").asText("");
        if (id.isEmpty() || reported.isEmpty() || reason.isEmpty()) return false;

        update("INSERT OR IGNORE INTO reports (id, reporter_id, reported_user_id, plan_id, reason, details, created_at)" +
                        " VALUES (?,?,?,?,?,?,?)", id, me, reported, planId.isEmpty() ? null : planId, reason,
                limit(r.path("details").asText(""), 500), System.currentTimeMillis());
        System.out.println("[moderation] report against user " + reported + ": " + reason);

        if (!planId.isEmpty()
                && queryLong("SELECT COUNT(DISTINCT reporter_id) FROM reports WHERE plan_id = ?", planId) >= 3) {
            int hidden = update("UPDATE plans SET status = 'HIDDEN', updated_at = ?, rev = ? WHERE id = ? AND status != 'HIDDEN'",
                    System.currentTimeMillis(), rev + 1, planId);
            if (hidden > 0) {
                rev++;
                System.out.println("[moderation] plan " + planId + " hidden after 3 reports");
            }
        }
        return true;
    }

    private Map<String, Object> changesSince(String me, long since) throws SQLException {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rev", rev);
        out.put("users", queryList("SELECT id, name, bio, interests, verified, created_at, rev FROM users WHERE rev > ?", since));
        out.put("plans", queryList("SELECT * FROM plans WHERE rev > ?", since));
        out.put("participants", queryList("SELECT * FROM participants WHERE rev > ?", since));
        // Chats are private: only plans I host or was approved for. If I was approved after
        // "since", I also get the older messages I have never seen.
        out.put("messages", queryList(
                "SELECT * FROM messages WHERE plan_id IN (" +
                        "  SELECT id FROM plans WHERE host_id = ?" +
                        "  UNION SELECT plan_id FROM participants WHERE user_id = ? AND status = 'APPROVED')" +
                        " AND (rev > ? OR plan_id IN (" +
                        "  SELECT plan_id FROM participants WHERE user_id = ? AND status = 'APPROVED' AND rev > ?))" +
                        " ORDER BY rev",
                me, me, since, me, since));
        return out;
    }

    // ------------------------------------------------------------------ demo data

    /** Creates a few demo users and plans around the given point (only once). */
    public synchronized void seedDemo(double lat, double lng) throws SQLException {
        if (queryOne("SELECT id FROM users WHERE email = 'aarav@demo.edu'") != null) {
            System.out.println("Demo data already present.");
            return;
        }
        String[][] people = {
                {"Aarav", "aarav@demo.edu", "Badminton, chai, startups"},
                {"Diya", "diya@demo.edu", "Reading, cafes, photography"},
                {"Kabir", "kabir@demo.edu", "Running, football, coding"},
        };
        List<String> ids = new ArrayList<>();
        for (String[] p : people) {
            Map<String, Object> u = createUser(p[0], p[1], "password");
            update("UPDATE users SET interests = ?, rev = ? WHERE id = ?", p[2], ++rev, u.get("id"));
            ids.add((String) u.get("id"));
        }
        long hour = 3_600_000L, now = System.currentTimeMillis();
        Object[][] plans = {
                {0, "Evening chai & chat", "Chai & Coffee", "Quick chai at the canteen, anyone welcome!", 2 * hour, "Campus canteen", 0.004, 0.003, 5, "OPEN"},
                {1, "Badminton doubles", "Sports", "Need 2 more for doubles. Bring your own racket if you have one.", 26 * hour, "Indoor sports hall", -0.006, 0.008, 3, "APPROVAL"},
                {2, "DSA study group", "Study", "Revising graphs and DP before the exam.", 5 * hour, "Central library, 2nd floor", 0.010, -0.004, 6, "APPROVAL"},
                {0, "Sunday city walk", "Explore", "Walking tour of the old market area, ending with street food.", 72 * hour, "Main gate", -0.012, -0.010, 8, "OPEN"},
        };
        for (Object[] p : plans) {
            update("INSERT INTO plans (id, host_id, title, category, description, start_time, place_name, lat, lng," +
                            " max_spots, join_mode, status, created_at, updated_at, rev) VALUES (?,?,?,?,?,?,?,?,?,?,?,'ACTIVE',?,?,?)",
                    UUID.randomUUID().toString(), ids.get((Integer) p[0]), p[1], p[2], p[3], now + (Long) p[4], p[5],
                    lat + (Double) p[6], lng + (Double) p[7], p[8], p[9], now, now, ++rev);
        }
        System.out.println("Demo data added. Log in as aarav@demo.edu / diya@demo.edu / kabir@demo.edu with password 'password'.");
    }

    // ------------------------------------------------------------------ JDBC helpers

    private static String limit(String s, int max) {
        s = s.trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    private PreparedStatement prepare(String sql, Object... args) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
        return ps;
    }

    private int update(String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = prepare(sql, args)) {
            return ps.executeUpdate();
        }
    }

    private long queryLong(String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = prepare(sql, args); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private Map<String, Object> queryOne(String sql, Object... args) throws SQLException {
        List<Map<String, Object>> rows = queryList(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<Map<String, Object>> queryList(String sql, Object... args) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = prepare(sql, args); ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData meta = rs.getMetaData();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int c = 1; c <= meta.getColumnCount(); c++) row.put(meta.getColumnLabel(c), rs.getObject(c));
                rows.add(row);
            }
        }
        return rows;
    }
}
