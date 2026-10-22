package com.linkup.app.model;

import java.util.ArrayList;
import java.util.List;

public class User {

    public String uuid;
    public String name;
    public String bio;
    public String interests;
    public boolean verified;
    public String photoPath;

    /** Interests are stored as one comma separated string. */
    public List<String> interestList() {
        return splitInterests(interests);
    }

    public static List<String> splitInterests(String interests) {
        List<String> out = new ArrayList<>();
        if (interests == null) return out;
        for (String part : interests.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    /** "Riya Sharma" -> "RS", used for avatar circles when there is no photo. */
    public static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase();
    }
}
