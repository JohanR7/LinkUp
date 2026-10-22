package com.linkup.app.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.linkup.app.R;
import com.linkup.app.model.Message;
import com.linkup.app.util.TimeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Chat list with two row types: my messages (right, teal) and other people's (left, grey).
 * getViewTypeCount/getItemViewType make ListView recycle each type separately.
 */
public class MessageAdapter extends BaseAdapter {

    private static final int TYPE_MINE = 0;
    private static final int TYPE_THEIRS = 1;

    private final List<Message> messages = new ArrayList<>();
    private final String me;

    public MessageAdapter(String me) {
        this.me = me;
    }

    public void setMessages(List<Message> list) {
        messages.clear();
        messages.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return messages.size();
    }

    @Override
    public Message getItem(int position) {
        return messages.get(position);
    }

    @Override
    public long getItemId(int position) {
        return messages.get(position).uuid.hashCode();
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return me.equals(getItem(position).senderUuid) ? TYPE_MINE : TYPE_THEIRS;
    }

    public boolean isMine(int position) {
        return getItemViewType(position) == TYPE_MINE;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        boolean mine = isMine(position);
        View row = convertView;
        if (row == null) {
            int layout = mine ? R.layout.item_message_mine : R.layout.item_message_theirs;
            row = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        }
        Message m = getItem(position);
        ((TextView) row.findViewById(R.id.message_body)).setText(m.body);

        TextView meta = row.findViewById(R.id.message_meta);
        meta.setText(m.pending ? parent.getContext().getString(R.string.chat_sending) : TimeUtils.chatTime(m.sentAt));

        if (!mine) {
            // Show the sender's name only on the first of several messages in a row from them
            TextView sender = row.findViewById(R.id.message_sender);
            boolean sameAsPrevious = position > 0 && getItem(position - 1).senderUuid.equals(m.senderUuid);
            sender.setVisibility(sameAsPrevious ? View.GONE : View.VISIBLE);
            sender.setText(m.senderName);
        }
        return row;
    }
}
