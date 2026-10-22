package com.linkup.app.model;

public class Message {
    public String uuid;
    public String senderUuid;
    public String senderName;
    public String body;
    public long sentAt;
    /** True until the server has accepted it */
    public boolean pending;
}
