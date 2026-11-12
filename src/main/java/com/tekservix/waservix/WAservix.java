package com.tekservix.waservix;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client for the WAservix WhatsApp API Gateway.
 *
 * <pre>{@code
 * WAservix wa = new WAservix(System.getenv("WASERVIX_KEY"));
 * wa.sendText("+15551234567", "Hello Aline, your order is ready");
 * }</pre>
 *
 * Docs: https://tekservix.com/waservix/docs/
 */
public class WAservix {
    public static final String VERSION = "1.0.4";
    public static final String DEFAULT_BASE_URL = "https://tekservix.com/waservix/api";

    private static final Gson GSON = new Gson();

    private final String apiKey;
    private final String baseUrl;
    private final int timeoutMs;

    /** @param apiKey device API key (wpk_...) or a personal access token for session management. */
    public WAservix(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL, 30000);
    }

    public WAservix(String apiKey, String baseUrl, int timeoutMs) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("WAservix: apiKey is required (Devices -> API key in the WAservix dashboard)");
        }
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.timeoutMs = timeoutMs;
    }

    /**
     * Call any documented endpoint, e.g. {@code request("GET", "/groups", null)}.
     *
     * @param body a Map, POJO or JsonElement (serialized with Gson), or null
     * @return the {@code data} field of the answer
     */
    public JsonElement request(String method, String path, Object body) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(baseUrl + path).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "waservix-java/" + VERSION);
            if (body != null) {
                byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(bytes);
                }
            }
            int status = conn.getResponseCode();
            String text = read(status >= 400 ? conn.getErrorStream() : conn.getInputStream());
            JsonElement json = null;
            try {
                json = text.isEmpty() ? null : JsonParser.parseString(text);
            } catch (RuntimeException ignored) {
                // not JSON
            }
            JsonObject obj = json != null && json.isJsonObject() ? json.getAsJsonObject() : null;
            boolean failed = status >= 400
                    || (obj != null && obj.has("success") && obj.get("success").isJsonPrimitive() && !obj.get("success").getAsBoolean());
            if (failed) {
                String msg = obj != null && obj.has("message") && !obj.get("message").isJsonNull()
                        ? obj.get("message").getAsString() : "HTTP " + status;
                String retry = conn.getHeaderField("Retry-After");
                Double retryAfter = null;
                if (retry != null) {
                    try {
                        retryAfter = Double.valueOf(retry.trim());
                    } catch (NumberFormatException ignored) {
                        // HTTP date form, ignore
                    }
                }
                throw new WAservixException(msg, status, obj != null ? obj.get("errors") : null, text, retryAfter);
            }
            if (obj != null && obj.has("data")) {
                return obj.get("data");
            }
            return json;
        } catch (IOException e) {
            throw new WAservixException("WAservix: request failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // Sending

    /**
     * Send any message type to a number or group JID (...@g.us).
     *
     * @param fields API field names: text, imageUrl, videoUrl, audioUrl, documentUrl, fileName,
     *               stickerUrl, contact, location, poll, replyTo, mentions, viewOnce
     * @return {"msgId": ..., "jid": ..., "status": "in_progress"}
     */
    public JsonElement sendMessage(String to, Map<String, ?> fields) {
        if (to == null || to.isEmpty()) {
            throw new IllegalArgumentException("sendMessage: \"to\" is required (international number or group JID)");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("to", to);
        if (fields != null) {
            for (Map.Entry<String, ?> e : fields.entrySet()) {
                if (e.getValue() != null) body.put(e.getKey(), e.getValue());
            }
        }
        return request("POST", "/send-message", body);
    }

    public JsonElement sendText(String to, String text) {
        return sendMessage(to, fields("text", text));
    }

    public JsonElement sendImage(String to, String imageUrl, String caption) {
        return sendMessage(to, fields("imageUrl", imageUrl, "text", caption));
    }

    public JsonElement sendDocument(String to, String documentUrl, String fileName, String caption) {
        return sendMessage(to, fields("documentUrl", documentUrl, "fileName", fileName, "text", caption));
    }

    // Messages

    /** Status: queued, sent, delivered, read, played or failed. */
    public JsonElement getMessageInfo(long msgId) { return request("GET", "/messages/" + msgId + "/info", null); }

    /** Edit one of your own text messages (about 15 minutes after sending). */
    public JsonElement editMessage(long msgId, String text) { return request("PUT", "/messages/" + msgId, fields("text", text)); }

    public JsonElement deleteMessage(long msgId) { return request("DELETE", "/messages/" + msgId, null); }

    public JsonElement resendMessage(long msgId) { return request("POST", "/messages/" + msgId + "/resend", null); }

    // Account, contacts and groups

    /** Is this number on WhatsApp? Returns {"exists": bool, "jid": ...}. */
    public JsonElement onWhatsApp(String phone) { return request("GET", "/on-whatsapp/" + enc(phone), null); }

    /** connected, connecting, need_scan, disconnected, logged_out or expired. */
    public JsonElement getStatus() { return request("GET", "/status", null); }

    public JsonElement getUser() { return request("GET", "/user", null); }

    public JsonElement listContacts() { return request("GET", "/contacts", null); }

    public JsonElement getContact(String phone) { return request("GET", "/contacts/" + enc(phone), null); }

    public JsonElement saveContact(String phone, String name) { return request("PUT", "/contacts", fields("phone", phone, "name", name)); }

    public JsonElement listGroups() { return request("GET", "/groups", null); }

    public JsonElement getGroupMetadata(String groupJid) { return request("GET", "/groups/" + enc(groupJid) + "/metadata", null); }

    // Sessions (need a personal access token, not a device key)

    public JsonElement listSessions() { return request("GET", "/whatsapp-sessions", null); }

    public JsonElement getSession(long id) { return request("GET", "/whatsapp-sessions/" + id, null); }

    public JsonElement createSession(Map<String, ?> params) { return request("POST", "/whatsapp-sessions", params); }

    /** Link by QR code. */
    public JsonElement connectSession(long id) { return connectSession(id, "qr", null); }

    /** @param method "qr", or "code" with a phone number for pairing codes */
    public JsonElement connectSession(long id, String method, String phone) {
        Map<String, Object> body = fields("method", method == null ? "qr" : method);
        if (phone != null) body.put("phone", phone);
        return request("POST", "/whatsapp-sessions/" + id + "/connect", body);
    }

    public JsonElement getSessionQrCode(long id) { return request("GET", "/whatsapp-sessions/" + id + "/qrcode", null); }

    /** Constant-time check of the X-Webhook-Signature header against your device's webhook secret. */
    public static boolean verifyWebhook(String signature, String secret) {
        if (signature == null || signature.isEmpty() || secret == null || secret.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(signature.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8));
    }

    static Map<String, Object> fields(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (kv[i + 1] != null) m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = s.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
