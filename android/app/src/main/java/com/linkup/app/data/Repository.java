package com.linkup.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import com.linkup.app.model.Message;
import com.linkup.app.model.Participant;
import com.linkup.app.model.Plan;
import com.linkup.app.model.User;
import com.linkup.app.util.SessionManager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.linkup.app.data.DbHelper.PENDING;
import static com.linkup.app.data.DbHelper.SYNCED;
import static com.linkup.app.data.DbHelper.T_BLOCKS;
import static com.linkup.app.data.DbHelper.T_MESSAGES;
import static com.linkup.app.data.DbHelper.T_NOTIFICATIONS;
import static com.linkup.app.data.DbHelper.T_PARTICIPANTS;
import static com.linkup.app.data.DbHelper.T_PLANS;
import static com.linkup.app.data.DbHelper.T_REPORTS;
import static com.linkup.app.data.DbHelper.T_USERS;

/**
 * All reads and writes the screens make. Screens never touch SQL directly.
 *
 * Writes are saved locally first with sync_status = PENDING; SyncManager sends them
 * to the server later, so the app keeps working while the server is unreachable.
 */
public class Repository {

    /** Shared SELECT for plans, with the host's name, head counts and my own status. */
    private static final String PLAN_SELECT =
            "SELECT p._id, p.uuid, p.host_uuid, p.title, p.category, p.description, p.start_time," +
                    " p.place_name, p.lat, p.lng, p.max_spots, p.join_mode, p.status," +
                    " u.name AS host_name, u.verified AS host_verified," +
                    " (SELECT COUNT(*) FROM participants a WHERE a.plan_uuid = p.uuid AND a.status = 'APPROVED') AS approved_count," +
                    " (SELECT COUNT(*) FROM participants a WHERE a.plan_uuid = p.uuid AND a.status = 'PENDING') AS pending_count," +
                    " (SELECT m.status FROM participants m WHERE m.plan_uuid = p.uuid AND m.user_uuid = ?) AS my_status" +
                    " FROM plans p LEFT JOIN users u ON u.uuid = p.host_uuid";

    private final Context context;
    private final DbHelper helper;
    private final String me;

    public Repository(Context context) {
        this.context = context.getApplicationContext();
        this.helper = DbHelper.getInstance(context);
        this.me = new SessionManager(context).getUserId();
    }

    public String myId() {
        return me;
    }

    private SQLiteDatabase read() {
        return helper.getReadableDatabase();
    }

    private SQLiteDatabase write() {
        return helper.getWritableDatabase();
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    // ================================================================== plans

    /** Upcoming active plans for the Discover feed and the map (hosts I blocked are left out). */
    public List<Plan> getDiscoverPlans() {
        long cutoff = System.currentTimeMillis() - Plan.GRACE_AFTER_START_MS;
        String sql = PLAN_SELECT +
                " WHERE p.status = 'ACTIVE' AND p.start_time > ?" +
                " AND p.host_uuid NOT IN (SELECT blocked_uuid FROM blocks WHERE owner_uuid = ?)" +
                " ORDER BY p.start_time";
        List<Plan> plans = new ArrayList<>();
        try (Cursor c = read().rawQuery(sql, new String[]{me, String.valueOf(cutoff), me})) {
            while (c.moveToNext()) plans.add(planFromCursor(c));
        }
        return plans;
    }

    public Plan getPlan(String uuid) {
        try (Cursor c = read().rawQuery(PLAN_SELECT + " WHERE p.uuid = ?", new String[]{me, uuid})) {
            return c.moveToFirst() ? planFromCursor(c) : null;
        }
    }

    /**
     * Cursor for the "My activities" screen (used by a CursorAdapter, which closes it).
     * hosting = true: plans I created. false: plans I joined or asked to join.
     */
    public Cursor getMyPlansCursor(boolean hosting) {
        String where = hosting
                ? " WHERE p.host_uuid = ?"
                : " WHERE p.uuid IN (SELECT plan_uuid FROM participants WHERE user_uuid = ? AND status IN ('APPROVED','PENDING'))";
        return read().rawQuery(PLAN_SELECT + where + " ORDER BY p.start_time DESC", new String[]{me, me});
    }

    /** Plans I host or was approved for that have not started yet (for reminders). */
    public List<Plan> getMyUpcomingPlans() {
        String sql = PLAN_SELECT +
                " WHERE p.status = 'ACTIVE' AND p.start_time > ? AND (p.host_uuid = ? OR p.uuid IN" +
                " (SELECT plan_uuid FROM participants WHERE user_uuid = ? AND status = 'APPROVED'))";
        List<Plan> plans = new ArrayList<>();
        try (Cursor c = read().rawQuery(sql, new String[]{me, String.valueOf(System.currentTimeMillis()), me, me})) {
            while (c.moveToNext()) plans.add(planFromCursor(c));
        }
        return plans;
    }

    public static Plan planFromCursor(Cursor c) {
        Plan p = new Plan();
        p.uuid = c.getString(c.getColumnIndexOrThrow("uuid"));
        p.hostUuid = c.getString(c.getColumnIndexOrThrow("host_uuid"));
        p.title = c.getString(c.getColumnIndexOrThrow("title"));
        p.category = c.getString(c.getColumnIndexOrThrow("category"));
        p.description = c.getString(c.getColumnIndexOrThrow("description"));
        p.startTime = c.getLong(c.getColumnIndexOrThrow("start_time"));
        p.placeName = c.getString(c.getColumnIndexOrThrow("place_name"));
        p.lat = c.getDouble(c.getColumnIndexOrThrow("lat"));
        p.lng = c.getDouble(c.getColumnIndexOrThrow("lng"));
        p.maxSpots = c.getInt(c.getColumnIndexOrThrow("max_spots"));
        p.joinMode = c.getString(c.getColumnIndexOrThrow("join_mode"));
        p.status = c.getString(c.getColumnIndexOrThrow("status"));
        int hostName = c.getColumnIndexOrThrow("host_name");
        p.hostName = c.isNull(hostName) ? "Someone" : c.getString(hostName);
        p.hostVerified = c.getInt(c.getColumnIndexOrThrow("host_verified")) == 1;
        p.approvedCount = c.getInt(c.getColumnIndexOrThrow("approved_count"));
        p.pendingCount = c.getInt(c.getColumnIndexOrThrow("pending_count"));
        int myStatus = c.getColumnIndexOrThrow("my_status");
        p.myStatus = c.isNull(myStatus) ? null : c.getString(myStatus);
        return p;
    }

    /** Inserts a new plan (uuid == null) or updates one I host. Returns the plan's uuid. */
    public String savePlan(Plan plan) {
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("title", plan.title);
        v.put("category", plan.category);
        v.put("description", plan.description);
        v.put("start_time", plan.startTime);
        v.put("place_name", plan.placeName);
        v.put("lat", plan.lat);
        v.put("lng", plan.lng);
        v.put("max_spots", plan.maxSpots);
        v.put("join_mode", plan.joinMode);
        v.put("status", plan.status);
        v.put("updated_at", now);
        v.put("sync_status", PENDING);

        if (plan.uuid == null) {
            plan.uuid = newId();
            v.put("uuid", plan.uuid);
            v.put("host_uuid", me);
            v.put("created_at", now);
            write().insert(T_PLANS, null, v);
        } else {
            write().update(T_PLANS, v, "uuid = ? AND host_uuid = ?", new String[]{plan.uuid, me});
        }
        return plan.uuid;
    }

    public void cancelPlan(String planUuid) {
        ContentValues v = new ContentValues();
        v.put("status", Plan.STATUS_CANCELLED);
        v.put("updated_at", System.currentTimeMillis());
        v.put("sync_status", PENDING);
        write().update(T_PLANS, v, "uuid = ? AND host_uuid = ?", new String[]{planUuid, me});
    }

    /** Number of upcoming active plans per category, for the category grid. */
    public Map<String, Integer> countPlansByCategory() {
        Map<String, Integer> counts = new HashMap<>();
        long cutoff = System.currentTimeMillis() - Plan.GRACE_AFTER_START_MS;
        try (Cursor c = read().rawQuery(
                "SELECT category, COUNT(*) FROM plans WHERE status = 'ACTIVE' AND start_time > ? GROUP BY category",
                new String[]{String.valueOf(cutoff)})) {
            while (c.moveToNext()) counts.put(c.getString(0), c.getInt(1));
        }
        return counts;
    }

    // ================================================================== participants

    /** People who are going or waiting for approval. */
    public List<Participant> getParticipants(String planUuid) {
        List<Participant> list = new ArrayList<>();
        try (Cursor c = read().rawQuery(
                "SELECT a.uuid, a.user_uuid, a.status, u.name, u.verified FROM participants a" +
                        " LEFT JOIN users u ON u.uuid = a.user_uuid" +
                        " WHERE a.plan_uuid = ? AND a.status IN ('APPROVED','PENDING') ORDER BY a.created_at",
                new String[]{planUuid})) {
            while (c.moveToNext()) {
                Participant p = new Participant();
                p.uuid = c.getString(0);
                p.planUuid = planUuid;
                p.userUuid = c.getString(1);
                p.status = c.getString(2);
                p.userName = c.isNull(3) ? "Someone" : c.getString(3);
                p.verified = c.getInt(4) == 1;
                list.add(p);
            }
        }
        return list;
    }

    /**
     * Sets someone's status in a plan: me joining/leaving, or me as host approving/declining.
     * Updates the existing row if there is one, otherwise inserts a new one.
     */
    public void setParticipation(String planUuid, String userUuid, String status) {
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("status", status);
        v.put("updated_at", now);
        v.put("sync_status", PENDING);
        String[] args = {planUuid, userUuid};
        int updated = write().update(T_PARTICIPANTS, v, "plan_uuid = ? AND user_uuid = ?", args);
        if (updated == 0) {
            v.put("uuid", newId());
            v.put("plan_uuid", planUuid);
            v.put("user_uuid", userUuid);
            v.put("created_at", now);
            write().insert(T_PARTICIPANTS, null, v);
        }
    }

    public int countHosted(String userUuid) {
        return (int) DatabaseUtils.queryNumEntries(read(), T_PLANS,
                "host_uuid = ? AND status != 'HIDDEN'", new String[]{userUuid});
    }

    public int countJoined(String userUuid) {
        return (int) DatabaseUtils.queryNumEntries(read(), T_PARTICIPANTS,
                "user_uuid = ? AND status = 'APPROVED'", new String[]{userUuid});
    }

    // ================================================================== chat

    public List<Message> getMessages(String planUuid) {
        List<Message> list = new ArrayList<>();
        try (Cursor c = read().rawQuery(
                "SELECT m.uuid, m.sender_uuid, m.body, m.sent_at, m.sync_status, u.name FROM messages m" +
                        " LEFT JOIN users u ON u.uuid = m.sender_uuid" +
                        " WHERE m.plan_uuid = ? AND m.sender_uuid NOT IN (SELECT blocked_uuid FROM blocks WHERE owner_uuid = ?)" +
                        " ORDER BY m.sent_at, m._id",
                new String[]{planUuid, me})) {
            while (c.moveToNext()) {
                Message m = new Message();
                m.uuid = c.getString(0);
                m.senderUuid = c.getString(1);
                m.body = c.getString(2);
                m.sentAt = c.getLong(3);
                m.pending = c.getInt(4) == PENDING;
                m.senderName = c.isNull(5) ? "Someone" : c.getString(5);
                list.add(m);
            }
        }
        return list;
    }

    public void addMessage(String planUuid, String body) {
        ContentValues v = new ContentValues();
        v.put("uuid", newId());
        v.put("plan_uuid", planUuid);
        v.put("sender_uuid", me);
        v.put("body", body);
        v.put("sent_at", System.currentTimeMillis());
        v.put("sync_status", PENDING);
        write().insert(T_MESSAGES, null, v);
    }

    // ================================================================== users

    public User getUser(String uuid) {
        try (Cursor c = read().query(T_USERS,
                new String[]{"uuid", "name", "bio", "interests", "verified", "photo_path"},
                "uuid = ?", new String[]{uuid}, null, null, null)) {
            if (!c.moveToFirst()) return null;
            User u = new User();
            u.uuid = c.getString(0);
            u.name = c.getString(1);
            u.bio = c.getString(2);
            u.interests = c.getString(3);
            u.verified = c.getInt(4) == 1;
            u.photoPath = c.isNull(5) ? null : c.getString(5);
            return u;
        }
    }

    /** Stores the profile the server returned at login/registration. */
    public void saveOwnUser(String uuid, String name, String bio, String interests, boolean verified) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("bio", bio);
        v.put("interests", interests);
        v.put("verified", verified ? 1 : 0);
        v.put("sync_status", SYNCED);
        if (write().update(T_USERS, v, "uuid = ?", new String[]{uuid}) == 0) {
            v.put("uuid", uuid);
            v.put("created_at", System.currentTimeMillis());
            write().insert(T_USERS, null, v);
        }
    }

    public void updateMyProfile(String name, String bio, String interests) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("bio", bio);
        v.put("interests", interests);
        v.put("updated_at", System.currentTimeMillis());
        v.put("sync_status", PENDING);
        write().update(T_USERS, v, "uuid = ?", new String[]{me});
    }

    /** The profile photo stays on this phone; only its file path is stored. */
    public void setMyPhoto(String path) {
        ContentValues v = new ContentValues();
        v.put("photo_path", path);
        write().update(T_USERS, v, "uuid = ?", new String[]{me});
    }

    // ================================================================== safety

    public void addReport(String reportedUserUuid, String planUuid, String reason, String details) {
        ContentValues v = new ContentValues();
        v.put("uuid", newId());
        v.put("reporter_uuid", me);
        v.put("reported_user_uuid", reportedUserUuid);
        v.put("plan_uuid", planUuid);
        v.put("reason", reason);
        v.put("details", details);
        v.put("created_at", System.currentTimeMillis());
        v.put("sync_status", PENDING);
        write().insert(T_REPORTS, null, v);
    }

    public void block(String userUuid) {
        ContentValues v = new ContentValues();
        v.put("owner_uuid", me);
        v.put("blocked_uuid", userUuid);
        v.put("created_at", System.currentTimeMillis());
        write().insertWithOnConflict(T_BLOCKS, null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void unblock(String userUuid) {
        write().delete(T_BLOCKS, "owner_uuid = ? AND blocked_uuid = ?", new String[]{me, userUuid});
    }

    public boolean isBlocked(String userUuid) {
        return DatabaseUtils.queryNumEntries(read(), T_BLOCKS,
                "owner_uuid = ? AND blocked_uuid = ?", new String[]{me, userUuid}) > 0;
    }

    /** Cursor with columns _id, blocked_uuid, name (for a SimpleCursorAdapter). */
    public Cursor getBlockedCursor() {
        return read().rawQuery(
                "SELECT b._id, b.blocked_uuid, COALESCE(u.name, 'Unknown user') AS name FROM blocks b" +
                        " LEFT JOIN users u ON u.uuid = b.blocked_uuid WHERE b.owner_uuid = ? ORDER BY b.created_at DESC",
                new String[]{me});
    }

    // ================================================================== inbox

    public long addNotification(String type, String title, String body, String planUuid) {
        ContentValues v = new ContentValues();
        v.put("owner_uuid", me);
        v.put("type", type);
        v.put("title", title);
        v.put("body", body);
        v.put("plan_uuid", planUuid);
        v.put("created_at", System.currentTimeMillis());
        return write().insert(T_NOTIFICATIONS, null, v);
    }

    /** Cursor with columns _id, title, body, created_at, is_read, plan_uuid. */
    public Cursor getNotificationsCursor() {
        return read().query(T_NOTIFICATIONS,
                new String[]{"_id", "title", "body", "created_at", "is_read", "plan_uuid"},
                "owner_uuid = ?", new String[]{me}, null, null, "created_at DESC", "100");
    }

    public int countUnread() {
        return (int) DatabaseUtils.queryNumEntries(read(), T_NOTIFICATIONS,
                "owner_uuid = ? AND is_read = 0", new String[]{me});
    }

    public void markRead(long id) {
        ContentValues v = new ContentValues();
        v.put("is_read", 1);
        write().update(T_NOTIFICATIONS, v, "_id = ?", new String[]{String.valueOf(id)});
    }

    public void markAllRead() {
        ContentValues v = new ContentValues();
        v.put("is_read", 1);
        write().update(T_NOTIFICATIONS, v, "owner_uuid = ?", new String[]{me});
    }

    public void clearNotifications() {
        write().delete(T_NOTIFICATIONS, "owner_uuid = ?", new String[]{me});
    }

    // ================================================================== storage info

    /**
     * Opens the database file separately in read-only mode, counts the rows in each table
     * and closes it again. Shown in Settings; also a plain example of open -> query -> close.
     */
    public String describeStorage() {
        File file = context.getDatabasePath(DbHelper.DB_NAME);
        StringBuilder sb = new StringBuilder();
        SQLiteDatabase db = SQLiteDatabase.openDatabase(file.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        try {
            for (String table : new String[]{T_USERS, T_PLANS, T_PARTICIPANTS, T_MESSAGES, T_REPORTS, T_BLOCKS, T_NOTIFICATIONS}) {
                long rows = DatabaseUtils.queryNumEntries(db, table);
                long pending = table.equals(T_BLOCKS) || table.equals(T_NOTIFICATIONS) ? 0
                        : DatabaseUtils.queryNumEntries(db, table, "sync_status = ?", new String[]{String.valueOf(PENDING)});
                sb.append(table).append(": ").append(rows).append(" rows");
                if (pending > 0) sb.append(" (").append(pending).append(" waiting to sync)");
                sb.append('\n');
            }
        } finally {
            db.close();
        }
        sb.append("\nFile size: ").append(file.length() / 1024).append(" KB");
        return sb.toString();
    }
}
