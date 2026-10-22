package com.linkup.app.ui.plan;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;

import androidx.annotation.NonNull;

import com.linkup.app.R;
import com.linkup.app.model.Categories;
import com.linkup.app.model.Message;
import com.linkup.app.model.Plan;
import com.linkup.app.notify.NotificationHelper;
import com.linkup.app.sync.SyncManager;
import com.linkup.app.ui.adapters.MessageAdapter;
import com.linkup.app.ui.common.BaseActivity;
import com.linkup.app.ui.common.PlanActions;
import com.linkup.app.ui.profile.UserProfileActivity;

/**
 * Group chat for one activity (host + approved people).
 * Messages are saved to SQLite first, then sent. While the screen is visible it syncs every
 * 5 seconds with a Handler; the polling starts in onResume and stops in onPause.
 */
public class ChatActivity extends BaseActivity {

    public static final String EXTRA_PLAN_UUID = "plan_uuid";
    private static final long POLL_INTERVAL_MS = 5000;

    private String planUuid;
    private MessageAdapter adapter;
    private ListView list;
    private EditText input;
    private ImageButton sendButton;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            SyncManager.syncAsync(ChatActivity.this, error -> {
                if (!isFinishing()) loadMessages();
            });
            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    public static Intent intent(Context context, String planUuid) {
        return new Intent(context, ChatActivity.class).putExtra(EXTRA_PLAN_UUID, planUuid);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (redirectIfLoggedOut()) return;
        setContentView(R.layout.activity_chat);
        setupToolbar(true);

        planUuid = getIntent().getStringExtra(EXTRA_PLAN_UUID);
        Plan plan = planUuid == null ? null : repo().getPlan(planUuid);
        if (plan == null) {
            finish();
            return;
        }
        if (!plan.isHostedBy(repo().myId()) && !plan.amGoing()) {
            toast(R.string.chat_members_only);
            finish();
            return;
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(Categories.emojiFor(plan.category) + "  " + plan.title);
            getSupportActionBar().setSubtitle(getResources().getQuantityString(
                    R.plurals.people_count, plan.approvedCount + 1, plan.approvedCount + 1));
        }

        list = findViewById(R.id.chat_list);
        input = findViewById(R.id.chat_input);
        sendButton = findViewById(R.id.chat_send);

        adapter = new MessageAdapter(repo().myId());
        list.setAdapter(adapter);
        list.setEmptyView(findViewById(R.id.chat_empty));
        registerForContextMenu(list);

        // The send button is only active while there is text
        sendButton.setEnabled(false);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                boolean hasText = s.toString().trim().length() > 0;
                sendButton.setEnabled(hasText);
                sendButton.setAlpha(hasText ? 1f : 0.4f);
            }
        });
        sendButton.setAlpha(0.4f);
        sendButton.setOnClickListener(v -> send());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing()) return;
        SyncManager.setOpenChat(planUuid);   // no notifications for the chat on screen
        NotificationHelper.cancel(this, NotificationHelper.chatNotificationId(planUuid));
        loadMessages();
        handler.post(poll);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(poll);
        SyncManager.setOpenChat(null);
        super.onPause();
    }

    private void loadMessages() {
        int before = adapter.getCount();
        adapter.setMessages(repo().getMessages(planUuid));
        if (adapter.getCount() > before) list.setSelection(adapter.getCount() - 1);
    }

    private void send() {
        String body = input.getText().toString().trim();
        if (body.isEmpty()) return;
        if (body.length() > 1000) {
            toast(R.string.chat_too_long);
            return;
        }
        repo().addMessage(planUuid, body);
        input.setText("");
        loadMessages();
        SyncManager.syncAsync(this, error -> {
            if (isFinishing()) return;
            loadMessages();
            if (error != null && error.isOffline()) toast(R.string.chat_offline);
        });
    }

    // ------------------------------------------------------------------ context menu (long-press a message)

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        int position = ((AdapterView.AdapterContextMenuInfo) menuInfo).position;
        Message message = adapter.getItem(position);
        getMenuInflater().inflate(R.menu.menu_message_context, menu);
        menu.setHeaderTitle(message.senderName);
        if (adapter.isMine(position)) {
            menu.removeItem(R.id.msg_profile);
            menu.removeItem(R.id.msg_report);
            menu.removeItem(R.id.msg_block);
        }
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        Message message = adapter.getItem(((AdapterView.AdapterContextMenuInfo) item.getMenuInfo()).position);
        int id = item.getItemId();
        if (id == R.id.msg_copy) {
            ClipboardManager clipboard = getSystemService(ClipboardManager.class);
            clipboard.setPrimaryClip(ClipData.newPlainText("message", message.body));
            toast(R.string.chat_copied);
        } else if (id == R.id.msg_profile) {
            startActivity(UserProfileActivity.intent(this, message.senderUuid));
        } else if (id == R.id.msg_report) {
            PlanActions.report(this, message.senderUuid, planUuid, message.senderName, null);
        } else if (id == R.id.msg_block) {
            PlanActions.block(this, message.senderUuid, message.senderName, this::loadMessages);
        } else {
            return super.onContextItemSelected(item);
        }
        return true;
    }
}
