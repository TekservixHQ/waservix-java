package com.tekservix.waservix;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class WAservixTest {
    static HttpServer server;
    static String lastMethod, lastPath, lastAuth;
    static JsonObject lastBody;
    static WAservix client;

    @BeforeClass
    public static void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            lastMethod = ex.getRequestMethod();
            lastPath = ex.getRequestURI().getRawPath();
            lastAuth = ex.getRequestHeaders().getFirst("Authorization");
            String body = read(ex.getRequestBody());
            lastBody = body.isEmpty() ? null : JsonParser.parseString(body).getAsJsonObject();
            int status = 200;
            String reply = "{\"success\":true,\"data\":{\"msgId\":58213,\"status\":\"in_progress\"}}";
            if (lastPath.startsWith("/down")) {
                status = 503;
                reply = "{\"success\":false,\"message\":\"Device reconnecting\"}";
            }
            byte[] b = reply.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, b.length);
            try (OutputStream o = ex.getResponseBody()) { o.write(b); }
        });
        server.start();
        client = new WAservix("wpk_test", "http://127.0.0.1:" + server.getAddress().getPort(), 5000);
    }

    @AfterClass
    public static void stop() { server.stop(0); }

    static String read(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    @Test
    public void sendText() {
        assertEquals(58213, client.sendText("+1555", "hi").getAsJsonObject().get("msgId").getAsInt());
        assertEquals("POST", lastMethod);
        assertEquals("/send-message", lastPath);
        assertEquals("Bearer wpk_test", lastAuth);
        assertEquals(2, lastBody.size());
        assertEquals("+1555", lastBody.get("to").getAsString());
        assertEquals("hi", lastBody.get("text").getAsString());
    }

    @Test
    public void mediaAndPaths() {
        client.sendImage("+1", "https://x/y.jpg", null);
        assertEquals(2, lastBody.size());
        assertEquals("https://x/y.jpg", lastBody.get("imageUrl").getAsString());
        client.sendDocument("+1", "https://x/a.pdf", "a.pdf", "c");
        assertEquals("a.pdf", lastBody.get("fileName").getAsString());
        client.getMessageInfo(5);
        assertEquals("/messages/5/info", lastPath);
        client.editMessage(5, "t");
        assertEquals("PUT", lastMethod);
        assertEquals("t", lastBody.get("text").getAsString());
        client.onWhatsApp("+1555");
        assertEquals("/on-whatsapp/%2B1555", lastPath);
        client.getGroupMetadata("1203@g.us");
        assertEquals("/groups/1203%40g.us/metadata", lastPath);
        client.saveContact("+1", "A");
        assertEquals("A", lastBody.get("name").getAsString());
        client.connectSession(3);
        assertEquals("qr", lastBody.get("method").getAsString());
    }

    @Test
    public void errors() {
        WAservix c = new WAservix("k", "http://127.0.0.1:" + server.getAddress().getPort() + "/down", 5000);
        try {
            c.getStatus();
            fail("expected exception");
        } catch (WAservixException e) {
            assertEquals(503, e.getStatus());
            assertEquals("Device reconnecting", e.getMessage());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void requiresTo() { client.sendText("", "x"); }

    @Test
    public void webhook() {
        assertTrue(WAservix.verifyWebhook("abc", "abc"));
        assertFalse(WAservix.verifyWebhook("abd", "abc"));
        assertFalse(WAservix.verifyWebhook(null, "abc"));
    }
}
