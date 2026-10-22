package com.linkup.app.model;

/** A person's place in a plan: asked to join, approved, declined or left. */
public class Participant {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String LEFT = "LEFT";

    public String uuid;
    public String planUuid;
    public String userUuid;
    public String userName;
    public boolean verified;
    public String status;
}
