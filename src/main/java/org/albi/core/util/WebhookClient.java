package org.albi.core.util;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public final class WebhookClient {
    private WebhookClient() {}

    public static boolean postJson(URI endpoint, JsonObject payload) throws IOException {
        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
        return post(endpoint, "application/json", output -> output.write(body));
    }

    /** Send on an async task. Callers should log status without printing the secret endpoint URL. */
    public static boolean post(URI endpoint, String contentType, Body body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) endpoint.toURL().openConnection();
        try {
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", contentType);
            connection.setDoOutput(true);
            try (OutputStream output = connection.getOutputStream()) {
                body.write(output);
            }
            int status = connection.getResponseCode();
            return status >= 200 && status < 300;
        } finally {
            connection.disconnect();
        }
    }

    @FunctionalInterface
    public interface Body {
        void write(OutputStream output) throws IOException;
    }
}
