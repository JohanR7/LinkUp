package com.linkup.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Map;

/**
 * LinkUp relay server.
 *
 * The Android app keeps all of its data in SQLite and only talks to this server to
 * share changes with other devices. There are just four routes:
 *
 *   GET  /health          - quick check that the server is reachable
 *   POST /auth/register   - create an account, returns a session token
 *   POST /auth/login      - log in, returns a session token
 *   POST /sync            - push local changes, pull everything newer than "since"
 *
 * Run: gradlew run            (or run-server.bat on Windows)
 * Options: --port 7070  --seed 12.9716,77.5946  (adds demo users/plans around that point)
 */
public class Main {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        int port = 7070;
        Store store = new Store("linkup.db");

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--port")) {
                port = Integer.parseInt(args[++i]);
            } else if (args[i].equals("--seed")) {
                // Accepts "12.97,77.59" or "12.97 77.59" (PowerShell turns the comma form into two words)
                String[] latLng = args[++i].split(",");
                String lng = latLng.length > 1 ? latLng[1] : args[++i];
                store.seedDemo(Double.parseDouble(latLng[0].trim()), Double.parseDouble(lng.trim()));
            }
        }

        Javalin app = Javalin.create(config -> config.showJavalinBanner = false);

        app.after(ctx -> System.out.println(ctx.method() + " " + ctx.path() + " -> " + ctx.status().getCode()));

        app.get("/health", ctx -> ctx.json(Map.of("ok", true, "time", System.currentTimeMillis())));

        app.post("/auth/register", ctx -> {
            JsonNode body = readBody(ctx);
            String name = body.path("name").asText("").trim();
            String email = body.path("email").asText("").trim().toLowerCase();
            String password = body.path("password").asText("");
            if (name.isEmpty() || name.length() > 50) throw new ApiException(400, "Please enter your name");
            if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new ApiException(400, "Please enter a valid email");
            if (password.length() < 6) throw new ApiException(400, "Password must be at least 6 characters");

            Map<String, Object> user = store.createUser(name, email, password);
            ctx.json(Map.of("token", store.createSession((String) user.get("id")), "user", user));
        });

        app.post("/auth/login", ctx -> {
            JsonNode body = readBody(ctx);
            String email = body.path("email").asText("").trim().toLowerCase();
            Map<String, Object> user = store.checkLogin(email, body.path("password").asText(""));
            if (user == null) throw new ApiException(401, "Wrong email or password");
            ctx.json(Map.of("token", store.createSession((String) user.get("id")), "user", user));
        });

        app.post("/sync", ctx -> {
            String me = requireUser(ctx, store);
            JsonNode body = readBody(ctx);
            ctx.json(store.sync(me, body.path("since").asLong(0), body.path("changes")));
        });

        app.exception(ApiException.class, (e, ctx) -> ctx.status(e.status).json(Map.of("error", e.getMessage())));
        app.exception(Exception.class, (e, ctx) -> {
            e.printStackTrace();
            ctx.status(500).json(Map.of("error", "Server error: " + e.getMessage()));
        });

        app.start(port);
        printAddresses(port);
    }

    private static JsonNode readBody(Context ctx) {
        try {
            return JSON.readTree(ctx.body());
        } catch (Exception e) {
            throw new ApiException(400, "Request body must be JSON");
        }
    }

    private static String requireUser(Context ctx, Store store) throws Exception {
        String header = ctx.header("Authorization");
        if (header == null || !header.startsWith("Bearer ")) throw new ApiException(401, "Not logged in");
        String userId = store.userForToken(header.substring(7).trim());
        if (userId == null) throw new ApiException(401, "Session expired, please log in again");
        return userId;
    }

    /** Prints the URLs to type into the app's Settings screen. */
    private static void printAddresses(int port) throws Exception {
        System.out.println();
        System.out.println("LinkUp server is running.");
        System.out.println("  Android emulator : http://10.0.2.2:" + port);
        for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) continue;
            for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
                if (addr instanceof Inet4Address) {
                    System.out.println("  Phone on same Wi-Fi: http://" + addr.getHostAddress() + ":" + port
                            + "  (" + nif.getDisplayName() + ")");
                }
            }
        }
        System.out.println();
    }
}
