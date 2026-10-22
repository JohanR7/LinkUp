package com.linkup.app.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Creates and upgrades the app's SQLite database.
 *
 * Every screen reads from these tables. Rows that came from (or must go to) the server carry:
 *   uuid         - the id shared with the server and other phones
 *   sync_status  - 0 = matches the server, 1 = changed here, waiting to be pushed
 * The integer _id column is what CursorAdapter needs.
 *
 * One instance is shared by the whole app (getInstance) so all threads use the same connection.
 */
public class DbHelper extends SQLiteOpenHelper {

    public static final String DB_NAME = "linkup.db";
    private static final int DB_VERSION = 1;

    public static final String T_USERS = "users";
    public static final String T_PLANS = "plans";
    public static final String T_PARTICIPANTS = "participants";
    public static final String T_MESSAGES = "messages";
    public static final String T_REPORTS = "reports";
    public static final String T_BLOCKS = "blocks";
    public static final String T_NOTIFICATIONS = "notifications";

    public static final int SYNCED = 0;
    public static final int PENDING = 1;

    private static DbHelper instance;

    public static synchronized DbHelper getInstance(Context context) {
        if (instance == null) instance = new DbHelper(context.getApplicationContext());
        return instance;
    }

    private DbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_USERS + " (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "uuid TEXT NOT NULL UNIQUE," +
                "name TEXT NOT NULL," +
                "bio TEXT NOT NULL DEFAULT ''," +
                "interests TEXT NOT NULL DEFAULT ''," +
                "verified INTEGER NOT NULL DEFAULT 0," +
                "photo_path TEXT," +                          // only stored on this phone
                "created_at INTEGER NOT NULL DEFAULT 0," +
                "updated_at INTEGER NOT NULL DEFAULT 0," +
                "sync_status INTEGER NOT NULL DEFAULT 0)");

        db.execSQL("CREATE TABLE " + T_PLANS + " (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "uuid TEXT NOT NULL UNIQUE," +
                "host_uuid TEXT NOT NULL," +
                "title TEXT NOT NULL," +
                "category TEXT NOT NULL," +
                "description TEXT NOT NULL DEFAULT ''," +
                "start_time INTEGER NOT NULL," +
                "place_name TEXT NOT NULL," +
                "lat REAL NOT NULL," +
                "lng REAL NOT NULL," +
                "max_spots INTEGER NOT NULL," +
                "join_mode TEXT NOT NULL," +                  // OPEN or APPROVAL
                "status TEXT NOT NULL," +                     // ACTIVE, CANCELLED or HIDDEN
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL," +
                "sync_status INTEGER NOT NULL DEFAULT 0)");

        db.execSQL("CREATE TABLE " + T_PARTICIPANTS + " (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "uuid TEXT NOT NULL UNIQUE," +
                "plan_uuid TEXT NOT NULL," +
                "user_uuid TEXT NOT NULL," +
                "status TEXT NOT NULL," +                     // PENDING, APPROVED, REJECTED or LEFT
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL," +
                "sync_status INTEGER NOT NULL DEFAULT 0," +
                "UNIQUE (plan_uuid, user_uuid))");

        db.execSQL("CREATE TABLE " + T_MESSAGES + " (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "uuid TEXT NOT NULL UNIQUE," +
                "plan_uuid TEXT NOT NULL," +
                "sender_uuid TEXT NOT NULL," +
                "body TEXT NOT NULL," +
                "sent_at INTEGER NOT NULL," +
                "sync_status INTEGER NOT NULL DEFAULT 0)");

        db.execSQL("CREATE TABLE " + T_REPORTS + " (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "uuid TEXT NOT NULL UNIQUE," +
                "reporter_uuid TEXT NOT NULL," +
                "reported_user_uuid TEXT NOT NULL," +
                "plan_uuid TEXT," +
                "reason TEXT NOT NULL," +
                "details TEXT NOT NULL DEFAULT ''," +
                "created_at INTEGER NOT NULL," +
                "sync_status INTEGER NOT NULL DEFAULT 1)");

        // Local only: who I have blocked, and my notification history (the Inbox screen)
        db.execSQL("CREATE TABLE " + T_BLOCKS + " (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "owner_uuid TEXT NOT NULL," +
                "blocked_uuid TEXT NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "UNIQUE (owner_uuid, blocked_uuid))");

        db.execSQL("CREATE TABLE " + T_NOTIFICATIONS + " (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "owner_uuid TEXT NOT NULL," +
                "type TEXT NOT NULL," +
                "title TEXT NOT NULL," +
                "body TEXT NOT NULL," +
                "plan_uuid TEXT," +
                "created_at INTEGER NOT NULL," +
                "is_read INTEGER NOT NULL DEFAULT 0)");

        db.execSQL("CREATE INDEX idx_participants_plan ON " + T_PARTICIPANTS + " (plan_uuid)");
        db.execSQL("CREATE INDEX idx_messages_plan ON " + T_MESSAGES + " (plan_uuid, sent_at)");
        db.execSQL("CREATE INDEX idx_plans_start ON " + T_PLANS + " (start_time)");
    }

    /**
     * Version 1 is the first release, so there is nothing to migrate yet.
     * Until real users exist, an upgrade simply rebuilds the tables; the data comes back
     * from the server on the next sync. Local-only tables would need a real migration later.
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        for (String table : new String[]{T_USERS, T_PLANS, T_PARTICIPANTS, T_MESSAGES, T_REPORTS, T_BLOCKS, T_NOTIFICATIONS}) {
            db.execSQL("DROP TABLE IF EXISTS " + table);
        }
        onCreate(db);
    }
}
