package com.linkup.app.sync;

import com.linkup.app.util.SessionManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Minimal JSON-over-HTTP client built on HttpURLConnection and org.json (both part of Android).
 * Calls block, so they must run on a background thread; Android throws
 * NetworkOnMainThreadException if they run on the UI thread.
 */
public class ApiClient {

    private final String baseUrl;
    private final String token;

    public ApiClient(SessionManager session) {
        this(session.getServerUrl(), session.getToken());
    }

    public ApiClient(String baseUrl, String token) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.token = token;
    }

    public JSONObject get(String path) throws ApiException {
        return request("GET", path, null);
    }

    public JSONObject post(String path, JSONObject body) throws ApiException {
        return request("POST", path, body);
    }

    private JSONObject request(String method, String path, JSONObject body) throws ApiException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(baseUrl + path).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Accept", "application/json");
            if (token != null) conn.setRequestProperty("Authorization", "Bearer " + token);

            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }

            int code = conn.getResponseCode();
            String text = readAll(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            JSONObject json = text.isEmpty() ? new JSONObject() : new JSONObject(text);
            if (code >= 400) {
                throw new ApiException(code, json.optString("error", "Server error (" + code + ")"));
            }
            return json;
        } catch (IOException e) {
            throw new ApiException(0, "Can't reach the LinkUp server at " + baseUrl + ". Is it running?");
        } catch (JSONException e) {
            throw new ApiException(0, "The server sent an unexpected response");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
