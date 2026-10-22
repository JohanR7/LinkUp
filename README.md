# LinkUp

A location-based Android app (Java) for finding nearby activities and meeting people in real life.
Someone posts a plan ("Evening chai", "Badminton doubles"), people nearby discover it on a list or
a map, join or request to join, and coordinate in a group chat.

```
project/
├── android/    Android Studio project (Java) - the app
├── backend/    Small Javalin server (Java) - shares data between phones
├── LinkUp_Project_Brief.pdf
└── topics.txt  Syllabus
```

## How it is built

```
 Phone                                       Phone
┌───────────────────────────┐              ┌──────────────────────┐
│ Activities / Fragments    │              │ ...same app...       │
│ Adapters, menus, dialogs  │              │                      │
│        ▲ read   │ write   │              │                      │
│ ┌──────┴────────▼───────┐ │              │                      │
│ │ SQLite (DbHelper +    │ │              │                      │
│ │ Repository)           │ │              │                      │
│ └──────┬────────▲───────┘ │              │                      │
│   SyncManager (POST /sync)│              │                      │
└────────┼────────┼─────────┘              └──────────┬───────────┘
         └────────┴──────► Javalin server ◄───────────┘
                           (4 routes, SQLite file)
```

* **Every screen reads from SQLite.** Nothing waits on the network, and the app keeps working offline.
* Changes are written to SQLite first with `sync_status = 1`. `SyncManager` sends them in one
  `POST /sync` and receives everything other people changed since the last sync.
* After a sync, the app compares old and new rows to spot news (a join request, an approval, a
  message) and raises a **local notification**. No Firebase or push service is involved.
* The server is deliberately tiny: `GET /health`, `POST /auth/register`, `POST /auth/login`,
  `POST /sync`. It also enforces the rules: only the host edits a plan, only members can chat,
  and an open plan stops accepting people when it is full.

## Running it

**1. Start the server** (on your laptop)

```
cd backend
run-server.bat
```

It prints the address to use. Add demo users and activities around a location with:

```
run-server.bat --seed 12.9716,77.5946
```

Demo logins: `aarav@demo.edu`, `diya@demo.edu`, `kabir@demo.edu`, all with the password `password`.

**2. Run the app**

Open the `android` folder in Android Studio and press Run. The default server address
`http://10.0.2.2:7070` already points at your laptop from the emulator.

* **Real phone:** connect it to the same Wi-Fi, then on the login screen tap the server line and enter
  the `http://192.168.x.x:7070` address the server printed. Allow Java through Windows Firewall when asked.
* **Two users at once:** start two emulators (Device Manager), log in as different people, and watch
  requests, approvals and chat move between them.
* **Emulator location:** use Extended controls (⋯) → Location to put the emulator near the demo
  activities, or set a *Home area* in Settings.

**3. Build an APK:** Build → Build App Bundle(s) / APK(s) → Build APK(s). From the command line,
run `gradlew assembleDebug`; the APK is written to `android/app/build/outputs/apk/debug/`.

## Syllabus coverage

| Topic (topics.txt) | Where in the code |
|---|---|
| **Unit 1** Project structure, running, APK | `android/` Gradle project, `AndroidManifest.xml`, build steps above |
| **Unit 2** Views and layouts | `res/layout/*`: LinearLayout, FrameLayout, ScrollView, DrawerLayout, CoordinatorLayout |
| Drawable resources | `res/drawable/`: shape (`bg_card`), gradient (`bg_header`), selector (`bg_input`, `bg_segment`), layer-list (`bg_map_card`), vectors (`ic_*`), adaptive launcher icon |
| Input controls | EditText, Button, CheckBox, RadioGroup/RadioButton, Spinner, SeekBar, Switch, NumberPicker (`PlanFormActivity`, `SettingsActivity`, `dialog_filters.xml`) |
| Input events | click/long-click listeners, `TextWatcher` (password strength in `RegisterActivity`, send button and counters), `OnSeekBarChangeListener`, `OnItemSelectedListener`, `OnEditorActionListener` |
| Toasts | `BaseActivity.toast()`, used throughout |
| ListView / GridView | Feed (`DiscoverFragment`), chat (`ChatActivity`), inbox, blocked list; category `GridView` (`CategoriesFragment`) |
| Adapters | `BaseAdapter` (`PlanListAdapter`, `MessageAdapter` with 2 view types, `CategoryGridAdapter`), `CursorAdapter` (`PlanCursorAdapter`), `SimpleCursorAdapter` + `ViewBinder` (`InboxFragment`, `SafetyFragment`), `ArrayAdapter` (spinners) |
| Action bar | Toolbar used as action bar, `SearchView` action view (`menu_discover.xml`) |
| Options, context and sub-menus | Options: Discover, Inbox, Details, Profile. Sub-menu: *Sort by*. Context menus: long-press a feed item or a chat message |
| Date and time pickers | `DatePickerDialog`, `TimePickerDialog` in `PlanFormActivity` |
| Activity and Fragment lifecycle | `BaseActivity` / `BaseFragment` log every callback (Logcat tag `Lifecycle`). `onSaveInstanceState` in Discover, the form and My activities. Map and chat polling start in `onResume` and stop in `onPause` |
| **Unit 3** Explicit intents and passing data | `PlanDetailsActivity.intent(ctx, uuid)` extras, `startActivityForResult`-style map picker (`ActivityResultLauncher` in `PlanFormActivity` / `SettingsActivity`) |
| Implicit intents | Open in maps (`geo:`), share (`ACTION_SEND`), add to calendar (`ACTION_INSERT`), dial 112 (`ACTION_DIAL`), pick a photo (`GetContent`) in `PlanActions` / `SafetyFragment` / `EditProfileActivity` |
| Navigation Drawer | `activity_main.xml` + `menu_drawer.xml` + `MainActivity` |
| Dialogs | `AlertDialog` (confirm, info, single report form, multi-choice interests), progress dialog (`Dialogs.progress`), date/time pickers |
| NotificationManager and push-style notifications | `NotificationHelper` (channels, `TaskStackBuilder` PendingIntents), `ReminderScheduler` + `ReminderReceiver` (AlarmManager), `SyncScheduler` + `SyncAlarmReceiver` (background checks every 15 min), `BootReceiver` |
| **Unit 4** SQLiteOpenHelper | `data/DbHelper.java`: 7 tables, indexes, `onCreate` / `onUpgrade` |
| Open and close a database | `Repository.describeStorage()` opens the file read-only, queries it, then closes it (Settings → Storage info) |
| Cursors | `Repository` (rawQuery, query, `moveToNext`, `getColumnIndexOrThrow`) and the CursorAdapters |
| Insert, update, delete | `Repository` (savePlan, setParticipation, block/unblock, notifications) and `SyncManager` (transactions, `insertWithOnConflict`) |

**Beyond the syllabus:** HTTP with `HttpURLConnection` + `org.json` on background threads, the
OpenStreetMap map (osmdroid), runtime permissions, `LocationManager`, and offline-first sync.

## Safety and privacy (from the brief)

* Report users or activities. An activity reported by 3 different people is hidden by the server.
* Block people. Their activities and messages disappear for you, and they are not told.
* Safety tips appear before the first join; activities must start at a public place; the Safety
  screen has a one-tap emergency dial.
* Only an activity's meeting point is shared. Your own location never leaves the phone and is used
  only for distances (this can be turned off in Settings).
* College emails (`.edu`, `.edu.in`, `.ac.in`) get a *verified* badge.
