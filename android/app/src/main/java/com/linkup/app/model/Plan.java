package com.linkup.app.model;

/**
 * An activity someone posted ("Evening chai", "Badminton doubles", ...).
 * Called Plan in code so it is not confused with Android's Activity class.
 */
public class Plan {

    public static final String MODE_OPEN = "OPEN";
    public static final String MODE_APPROVAL = "APPROVAL";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_HIDDEN = "HIDDEN";

    /** A plan stays visible in the feed until this long after it started. */
    public static final long GRACE_AFTER_START_MS = 60 * 60 * 1000L;

    public String uuid;
    public String hostUuid;
    public String hostName;
    public boolean hostVerified;
    public String title;
    public String category;
    public String description;
    public long startTime;
    public String placeName;
    public double lat;
    public double lng;
    public int maxSpots;
    public String joinMode = MODE_OPEN;
    public String status = STATUS_ACTIVE;

    /** Filled in by queries */
    public int approvedCount;
    public int pendingCount;
    /** My participation status, or null if I have not asked to join */
    public String myStatus;
    /** Distance from me in km, or -1 if my location is unknown */
    public double distanceKm = -1;

    public int spotsLeft() {
        return Math.max(0, maxSpots - approvedCount);
    }

    public boolean isFull() {
        return spotsLeft() == 0;
    }

    public boolean isCancelled() {
        return STATUS_CANCELLED.equals(status);
    }

    public boolean isOver() {
        return System.currentTimeMillis() > startTime + GRACE_AFTER_START_MS;
    }

    public boolean isHostedBy(String userUuid) {
        return hostUuid != null && hostUuid.equals(userUuid);
    }

    public boolean amGoing() {
        return Participant.APPROVED.equals(myStatus);
    }

    public boolean needsApproval() {
        return MODE_APPROVAL.equals(joinMode);
    }
}
