package com.linkup.app.sync;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.Nullable;

import com.linkup.app.LinkUpApp;
import com.linkup.app.data.DbHelper;
import com.linkup.app.data.Repository;
import com.linkup.app.model.Participant;
import com.linkup.app.model.Plan;
import com.linkup.app.notify.NotificationHelper;
import com.linkup.app.notify.ReminderScheduler;
import com.linkup.app.util.SessionManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.linkup.app.data.DbHelper.PENDING;
import static com.linkup.app.data.DbHelper.SYNCED;
import static com.linkup.app.data.DbHelper.T_MESSAGES;
import static com.linkup.app.data.DbHelper.T_PARTICIPANTS;
import static com.linkup.app.data.DbHelper.T_PLANS;
import static com.linkup.app.data.DbHelper.T_REPORTS;
import static com.linkup.app.data.DbHelper.T_USERS;

/**
 * Keeps the local SQLite database and the server in step.
 *
 * One sync = one POST /sync request:
 *   1. collect every row with sync_status = PENDING (my new plans, join requests, messages, ...)
 *   2. send them together with the last revision number we have seen
 *   3. the server applies what is allowed and answers with everything newer than that revision
 *   4. write the answer into SQLite in one transaction
 *   5. compare old and new rows to spot news (join request, approval, message) and notify
 */
public final class SyncManager {

    public interface Callback {
        /** error is null when the sync worked. Runs on the UI thread. */
        void onSyncFinished(@Nullable ApiException error);
    }

    private static final Object LOCK = new Object();

    /** The chat currently on screen; its new messages do not raise a notification. */
    private static volatile String openChatPlanUuid;

    private SyncManager() {
    }

    public static void setOpenChat(@Nullable String planUuid) {
        openChatPlanUuid = planUuid;
    }

    /** Syncs on a background thread and reports back on the UI thread. */
    public static void syncAsync(Context context, @Nullable Callback callback) {
        Context app = context.getApplicationContext();
        LinkUpApp.runInBackground(() -> {
            ApiException error = null;
            try {
                syncNow(app);
            } catch (ApiException e) {
                error = e;
            }
            if (callback != null) {
                ApiException result = error;
                LinkUpApp.runOnMain(() -> callback.onSyncFinished(result));
            }
        });
    }

    /** Blocking sync. Must be called from a background thread. */
    public static void syncNow(Context context) throws ApiException {
        synchronized (LOCK) {
            SessionManager session = new SessionManager(context);
            if (!session.isLoggedIn()) return;
            String me = session.getUserId();
            long since = session.getLastRev();
            SQLiteDatabase db = DbHelper.getInstance(context).getWritableDatabase();
            List<Event> events = new ArrayList<>();
            boolean hadRejections;

            try {
                Outbox outbox = collectPending(db, me);
                JSONObject request = new JSONObject().put("since", since).put("changes", outbox.changes);
                JSONObject response = new ApiClient(session).post("/sync", request);

                JSONArray rejected = response.optJSONArray("rejected");
                hadRejections = rejected != null && rejected.length() > 0;
                boolean notify = since > 0;   // the first download after logging in is not news

                db.beginTransaction();
                try {
                    outbox.markSynced(db);
                    if (hadRejections) removeRejected(db, me, rejected, events);
                    applyUsers(db, me, response.getJSONArray("users"));
                    applyPlans(db, me, response.getJSONArray("plans"), notify, events);
                    applyParticipants(db, me, response.getJSONArray("participants"), notify, events);
                    applyMessages(db, me, response.getJSONArray("messages"), notify, events);
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
                // After a rejection, download everything again so local rows match the server
                session.setLastRev(hadRejections ? 0 : response.getLong("rev"));
            } catch (JSONException e) {
                throw new ApiException(0, "Unexpected data from the server");
            }

            publish(context, events);
            if (hadRejections) syncNow(context);
            ReminderScheduler.refresh(context);
        }
    }

    // ------------------------------------------------------------------ push

    /** Pending rows to send, and how to mark them as synced once the server has them. */
    private static class Outbox {
        final JSONObject changes = new JSONObject();
        /** {table, uuid, updated_at}: only marked synced if not edited again meanwhile */
        final List<String[]> versioned = new ArrayList<>();
        /** {table, uuid}: rows that never change after creation */
        final List<String[]> immutable = new ArrayList<>();

        void markSynced(SQLiteDatabase db) {
            ContentValues v = new ContentValues();
            v.put("sync_status", SYNCED);
            for (String[] row : versioned) {
                db.update(row[0], v, "uuid = ? AND updated_at = ?", new String[]{row[1], row[2]});
            }
            for (String[] row : immutable) {
                db.update(row[0], v, "uuid = ?", new String[]{row[1]});
            }
        }
    }

    private static Outbox collectPending(SQLiteDatabase db, String me) throws JSONException {
        Outbox out = new Outbox();
        String pending = String.valueOf(PENDING);

        try (Cursor c = db.rawQuery("SELECT name, bio, interests, updated_at FROM users WHERE uuid = ? AND sync_status = ?",
                new String[]{me, pending})) {
            if (c.moveToFirst()) {
                out.changes.put("profile", new JSONObject()
                        .put("name", c.getString(0))
                        .put("bio", c.getString(1))
                        .put("interests", c.getString(2)));
                out.versioned.add(new String[]{T_USERS, me, c.getString(3)});
            }
        }

        JSONArray plans = new JSONArray();
        try (Cursor c = db.rawQuery("SELECT uuid, title, category, description, start_time, place_name, lat, lng," +
                " max_spots, join_mode, status, updated_at FROM plans WHERE sync_status = ? AND host_uuid = ?", new String[]{pending, me})) {
            while (c.moveToNext()) {
                plans.put(new JSONObject()
                        .put("id", c.getString(0))
                        .put("title", c.getString(1))
                        .put("category", c.getString(2))
                        .put("description", c.getString(3))
                        .put("start_time", c.getLong(4))
                        .put("place_name", c.getString(5))
                        .put("lat", c.getDouble(6))
                        .put("lng", c.getDouble(7))
                        .put("max_spots", c.getInt(8))
                        .put("join_mode", c.getString(9))
                        .put("status", c.getString(10)));
                out.versioned.add(new String[]{T_PLANS, c.getString(0), c.getString(11)});
            }
        }
        out.changes.put("plans", plans);

        // My own join/leave, plus approve/decline decisions in plans I host
        JSONArray participants = new JSONArray();
        try (Cursor c = db.rawQuery("SELECT uuid, plan_uuid, user_uuid, status, updated_at FROM participants" +
                        " WHERE sync_status = ? AND (user_uuid = ? OR plan_uuid IN (SELECT uuid FROM plans WHERE host_uuid = ?))",
                new String[]{pending, me, me})) {
            while (c.moveToNext()) {
                participants.put(new JSONObject()
                        .put("id", c.getString(0))
                        .put("plan_id", c.getString(1))
                        .put("user_id", c.getString(2))
                        .put("status", c.getString(3)));
                out.versioned.add(new String[]{T_PARTICIPANTS, c.getString(0), c.getString(4)});
            }
        }
        out.changes.put("participants", participants);

        JSONArray messages = new JSONArray();
        try (Cursor c = db.rawQuery("SELECT uuid, plan_uuid, body FROM messages WHERE sync_status = ? AND sender_uuid = ?",
                new String[]{pending, me})) {
            while (c.moveToNext()) {
                messages.put(new JSONObject()
                        .put("id", c.getString(0))
                        .put("plan_id", c.getString(1))
                        .put("body", c.getString(2)));
                out.immutable.add(new String[]{T_MESSAGES, c.getString(0)});
            }
        }
        out.changes.put("messages", messages);

        JSONArray reports = new JSONArray();
        try (Cursor c = db.rawQuery("SELECT uuid, reported_user_uuid, plan_uuid, reason, details FROM reports" +
                " WHERE sync_status = ? AND reporter_uuid = ?", new String[]{pending, me})) {
            while (c.moveToNext()) {
                reports.put(new JSONObject()
                        .put("id", c.getString(0))
                        .put("reported_user_id", c.getString(1))
                        .put("plan_id", c.isNull(2) ? "" : c.getString(2))
                        .put("reason", c.getString(3))
                        .put("details", c.getString(4)));
                out.immutable.add(new String[]{T_REPORTS, c.getString(0)});
            }
        }
        out.changes.put("reports", reports);
        return out;
    }

    /** Drops changes the server refused. The full download that follows restores the server's version. */
    private static void removeRejected(SQLiteDatabase db, String me, JSONArray rejected, List<Event> events) throws JSONException {
        for (int i = 0; i < rejected.length(); i++) {
            String[] id = {rejected.getString(i)};
            try (Cursor c = db.rawQuery("SELECT a.plan_uuid, p.title FROM participants a LEFT JOIN plans p ON p.uuid = a.plan_uuid" +
                    " WHERE a.uuid = ? AND a.user_uuid = ?", new String[]{id[0], me})) {
                if (c.moveToFirst()) {
                    events.add(new Event(NotificationHelper.TYPE_UPDATE, "Couldn't join " + c.getString(1),
                            "The activity is full or no longer taking people.", c.getString(0), false));
                }
            }
            for (String table : new String[]{T_PLANS, T_PARTICIPANTS, T_MESSAGES, T_REPORTS}) {
                db.delete(table, "uuid = ?", id);
            }
        }
    }

    // ------------------------------------------------------------------ pull

    private static boolean isPending(SQLiteDatabase db, String table, String where, String... args) {
        try (Cursor c = db.query(table, new String[]{"sync_status"}, where, args, null, null, null)) {
            return c.moveToFirst() && c.getInt(0) == PENDING;
        }
    }

    private static void applyUsers(SQLiteDatabase db, String me, JSONArray users) throws JSONException {
        for (int i = 0; i < users.length(); i++) {
            JSONObject u = users.getJSONObject(i);
            String id = u.getString("id");
            if (id.equals(me) && isPending(db, T_USERS, "uuid = ?", id)) continue;   // my unsent edit wins

            ContentValues v = new ContentValues();
            v.put("name", u.getString("name"));
            v.put("bio", u.optString("bio", ""));
            v.put("interests", u.optString("interests", ""));
            v.put("verified", u.optInt("verified", 0));
            v.put("created_at", u.optLong("created_at", 0));
            v.put("sync_status", SYNCED);
            // update-then-insert keeps the local-only photo_path column
            if (db.update(T_USERS, v, "uuid = ?", new String[]{id}) == 0) {
                v.put("uuid", id);
                db.insert(T_USERS, null, v);
            }
        }
    }

    private static void applyPlans(SQLiteDatabase db, String me, JSONArray plans, boolean notify, List<Event> events)
            throws JSONException {
        for (int i = 0; i < plans.length(); i++) {
            JSONObject p = plans.getJSONObject(i);
            String id = p.getString("id");

            String oldStatus = null, oldPlace = null;
            long oldStart = 0;
            try (Cursor c = db.rawQuery("SELECT status, start_time, place_name, sync_status FROM plans WHERE uuid = ?", new String[]{id})) {
                if (c.moveToFirst()) {
                    if (c.getInt(3) == PENDING) continue;   // my unsent edit wins
                    oldStatus = c.getString(0);
                    oldStart = c.getLong(1);
                    oldPlace = c.getString(2);
                }
            }

            ContentValues v = new ContentValues();
            v.put("uuid", id);
            v.put("host_uuid", p.getString("host_id"));
            v.put("title", p.getString("title"));
            v.put("category", p.getString("category"));
            v.put("description", p.optString("description", ""));
            v.put("start_time", p.getLong("start_time"));
            v.put("place_name", p.getString("place_name"));
            v.put("lat", p.getDouble("lat"));
            v.put("lng", p.getDouble("lng"));
            v.put("max_spots", p.getInt("max_spots"));
            v.put("join_mode", p.getString("join_mode"));
            v.put("status", p.getString("status"));
            v.put("created_at", p.getLong("created_at"));
            v.put("updated_at", p.getLong("updated_at"));
            v.put("sync_status", SYNCED);
            db.insertWithOnConflict(T_PLANS, null, v, SQLiteDatabase.CONFLICT_REPLACE);

            if (!notify || oldStatus == null) continue;
            String title = p.getString("title");
            String status = p.getString("status");
            if (me.equals(p.getString("host_id"))) {
                if (Plan.STATUS_HIDDEN.equals(status) && !Plan.STATUS_HIDDEN.equals(oldStatus)) {
                    events.add(new Event(NotificationHelper.TYPE_UPDATE, "\"" + title + "\" was hidden",
                            "Several people reported this activity. It is hidden while it is reviewed.", id, false));
                }
            } else if (isInvolved(db, id, me)) {
                if (Plan.STATUS_CANCELLED.equals(status) && !Plan.STATUS_CANCELLED.equals(oldStatus)) {
                    events.add(new Event(NotificationHelper.TYPE_UPDATE, "Cancelled: " + title,
                            "The host cancelled this activity.", id, false));
                } else if (oldStart != p.getLong("start_time") || !Objects.equals(oldPlace, p.getString("place_name"))) {
                    events.add(new Event(NotificationHelper.TYPE_UPDATE, "Updated: " + title,
                            "The host changed the time or meeting place. Check the details.", id, false));
                }
            }
        }
    }

    private static boolean isInvolved(SQLiteDatabase db, String planUuid, String me) {
        try (Cursor c = db.rawQuery("SELECT 1 FROM participants WHERE plan_uuid = ? AND user_uuid = ? AND status IN ('APPROVED','PENDING')",
                new String[]{planUuid, me})) {
            return c.moveToFirst();
        }
    }

    private static void applyParticipants(SQLiteDatabase db, String me, JSONArray rows, boolean notify, List<Event> events)
            throws JSONException {
        for (int i = 0; i < rows.length(); i++) {
            JSONObject a = rows.getJSONObject(i);
            String planId = a.getString("plan_id");
            String userId = a.getString("user_id");
            String status = a.getString("status");

            String oldStatus = null;
            try (Cursor c = db.rawQuery("SELECT status, sync_status FROM participants WHERE plan_uuid = ? AND user_uuid = ?",
                    new String[]{planId, userId})) {
                if (c.moveToFirst()) {
                    if (c.getInt(1) == PENDING) continue;   // my unsent change wins
                    oldStatus = c.getString(0);
                }
            }

            ContentValues v = new ContentValues();
            v.put("uuid", a.getString("id"));
            v.put("plan_uuid", planId);
            v.put("user_uuid", userId);
            v.put("status", status);
            v.put("created_at", a.getLong("created_at"));
            v.put("updated_at", a.getLong("updated_at"));
            v.put("sync_status", SYNCED);
            // REPLACE also resolves a clash on (plan_uuid, user_uuid) if the server kept an older row id
            db.insertWithOnConflict(T_PARTICIPANTS, null, v, SQLiteDatabase.CONFLICT_REPLACE);

            if (!notify || status.equals(oldStatus)) continue;
            String host = null, title = null, joinMode = null;
            try (Cursor c = db.rawQuery("SELECT host_uuid, title, join_mode FROM plans WHERE uuid = ?", new String[]{planId})) {
                if (c.moveToFirst()) {
                    host = c.getString(0);
                    title = c.getString(1);
                    joinMode = c.getString(2);
                }
            }
            if (host == null) continue;
            String name = userName(db, userId);

            if (me.equals(host) && !userId.equals(me)) {
                if (Participant.PENDING.equals(status)) {
                    events.add(new Event(NotificationHelper.TYPE_REQUEST, name + " wants to join",
                            "\"" + title + "\" - open the activity to approve or decline.", planId, false));
                } else if (Participant.APPROVED.equals(status) && Plan.MODE_OPEN.equals(joinMode)) {
                    events.add(new Event(NotificationHelper.TYPE_JOIN, name + " joined " + title,
                            "Say hi in the activity chat!", planId, false));
                } else if (Participant.LEFT.equals(status) && Participant.APPROVED.equals(oldStatus)) {
                    events.add(new Event(NotificationHelper.TYPE_JOIN, name + " left " + title,
                            "A spot opened up.", planId, false));
                }
            } else if (userId.equals(me) && !me.equals(host)) {
                if (Participant.APPROVED.equals(status) && Participant.PENDING.equals(oldStatus)) {
                    events.add(new Event(NotificationHelper.TYPE_APPROVAL, "You're in! " + title,
                            "The host approved your request. Coordinate in the chat.", planId, false));
                } else if (Participant.REJECTED.equals(status)) {
                    events.add(new Event(NotificationHelper.TYPE_APPROVAL, "Request declined",
                            "The host couldn't take you for \"" + title + "\" this time.", planId, false));
                }
            }
        }
    }

    private static void applyMessages(SQLiteDatabase db, String me, JSONArray rows, boolean notify, List<Event> events)
            throws JSONException {
        Map<String, Integer> newCount = new LinkedHashMap<>();
        Map<String, String> lastLine = new LinkedHashMap<>();

        for (int i = 0; i < rows.length(); i++) {
            JSONObject m = rows.getJSONObject(i);
            String id = m.getString("id");
            String planId = m.getString("plan_id");
            String sender = m.getString("sender_id");

            boolean existed;
            try (Cursor c = db.rawQuery("SELECT 1 FROM messages WHERE uuid = ?", new String[]{id})) {
                existed = c.moveToFirst();
            }

            ContentValues v = new ContentValues();
            v.put("uuid", id);
            v.put("plan_uuid", planId);
            v.put("sender_uuid", sender);
            v.put("body", m.getString("body"));
            v.put("sent_at", m.getLong("sent_at"));
            v.put("sync_status", SYNCED);
            db.insertWithOnConflict(T_MESSAGES, null, v, SQLiteDatabase.CONFLICT_REPLACE);

            if (notify && !existed && !sender.equals(me) && !planId.equals(openChatPlanUuid) && !isBlocked(db, me, sender)) {
                newCount.put(planId, newCount.getOrDefault(planId, 0) + 1);
                lastLine.put(planId, userName(db, sender) + ": " + m.getString("body"));
            }
        }

        for (Map.Entry<String, Integer> e : newCount.entrySet()) {
            String planId = e.getKey();
            String title = "New message";
            try (Cursor c = db.rawQuery("SELECT title FROM plans WHERE uuid = ?", new String[]{planId})) {
                if (c.moveToFirst()) title = c.getString(0);
            }
            String body = e.getValue() == 1 ? lastLine.get(planId) : e.getValue() + " new messages - " + lastLine.get(planId);
            events.add(new Event(NotificationHelper.TYPE_CHAT, title, body, planId, true));
        }
    }

    private static boolean isBlocked(SQLiteDatabase db, String me, String other) {
        try (Cursor c = db.rawQuery("SELECT 1 FROM blocks WHERE owner_uuid = ? AND blocked_uuid = ?", new String[]{me, other})) {
            return c.moveToFirst();
        }
    }

    private static String userName(SQLiteDatabase db, String uuid) {
        try (Cursor c = db.rawQuery("SELECT name FROM users WHERE uuid = ?", new String[]{uuid})) {
            return c.moveToFirst() ? c.getString(0) : "Someone";
        }
    }

    // ------------------------------------------------------------------ notify

    /** Something worth telling the user about after a sync. */
    private static class Event {
        final String type, title, body, planUuid;
        final boolean openChat;

        Event(String type, String title, String body, String planUuid, boolean openChat) {
            this.type = type;
            this.title = title;
            this.body = body;
            this.planUuid = planUuid;
            this.openChat = openChat;
        }
    }

    /** Saves each event in the Inbox table and shows it as a system notification. */
    private static void publish(Context context, List<Event> events) {
        if (events.isEmpty()) return;
        Repository repo = new Repository(context);
        for (Event e : events) {
            long rowId = repo.addNotification(e.type, e.title, e.body, e.planUuid);
            // Chat notifications for one plan replace each other instead of piling up
            int notificationId = e.openChat ? NotificationHelper.chatNotificationId(e.planUuid) : (int) rowId;
            NotificationHelper.showPlanNotification(context, notificationId, NotificationHelper.channelFor(e.type),
                    e.title, e.body, e.planUuid, e.openChat);
        }
    }
}
