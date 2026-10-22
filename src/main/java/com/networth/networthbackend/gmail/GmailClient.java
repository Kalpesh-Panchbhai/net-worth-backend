package com.networth.networthbackend.gmail;

import com.fasterxml.jackson.databind.JsonNode;
import com.networth.networthbackend.common.Jsons;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Minimal Gmail REST client (readonly scope) over plain HTTP, so the shaded jar does not pull in
 * the Google SDK. Auth is a long-lived OAuth refresh token from env, exchanged for short-lived
 * access tokens that are cached for the life of the Lambda container.
 */
@Slf4j
public class GmailClient {

    static final String ENV_CLIENT_ID = "GMAIL_CLIENT_ID";
    static final String ENV_CLIENT_SECRET = "GMAIL_CLIENT_SECRET";
    static final String ENV_REFRESH_TOKEN = "GMAIL_REFRESH_TOKEN";
    static final String ENV_PUBSUB_TOPIC = "GMAIL_PUBSUB_TOPIC";
    /** Name of the Gmail label to watch (a filter applies it to bank emails); INBOX when unset. */
    static final String ENV_WATCH_LABEL = "GMAIL_WATCH_LABEL";

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String API = "https://gmail.googleapis.com/gmail/v1/users/me";

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    private static volatile String watchLabelId;
    private static volatile String accessToken;
    private static volatile Instant accessTokenExpiry = Instant.EPOCH;

    /** Thrown when Gmail no longer has history from the requested id (older than about a week). */
    public static class HistoryExpiredException extends IOException {
        public HistoryExpiredException(String msg) {
            super(msg);
        }
    }

    /** The changes since {@code startHistoryId}: ids of newly added messages and the mailbox's current history id. */
    public record HistoryPage(List<String> addedMessageIds, String historyId) {
    }

    /** Starts (or renews) push notifications for the watched label to the configured Pub/Sub topic. Returns the current history id. */
    public String watch() throws IOException, InterruptedException {
        String topic = requireEnv(ENV_PUBSUB_TOPIC);
        String body = Jsons.MAPPER.writeValueAsString(java.util.Map.of(
                "topicName", topic,
                "labelIds", List.of(watchLabelId()),
                "labelFilterBehavior", "INCLUDE"));
        JsonNode res = send(authed(API + "/watch").POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json").build());
        log.info("Gmail watch active until {}", Instant.ofEpochMilli(res.path("expiration").asLong()));
        return res.path("historyId").asText();
    }

    /** All messages with the watched label added after {@code startHistoryId}, following pagination. */
    public HistoryPage addedSince(String startHistoryId) throws IOException, InterruptedException {
        List<String> ids = new ArrayList<>();
        String pageToken = null;
        String latest = startHistoryId;
        do {
            String url = API + "/history?historyTypes=messageAdded&labelId=" + enc(watchLabelId()) + "&startHistoryId=" + enc(startHistoryId)
                    + (pageToken == null ? "" : "&pageToken=" + enc(pageToken));
            JsonNode res = send(authed(url).GET().build());
            for (JsonNode h : res.path("history")) {
                for (JsonNode added : h.path("messagesAdded")) {
                    String id = added.path("message").path("id").asText(null);
                    if (id != null && !ids.contains(id)) ids.add(id);
                }
            }
            if (res.hasNonNull("historyId")) latest = res.get("historyId").asText();
            pageToken = res.path("nextPageToken").asText(null);
        } while (pageToken != null);
        return new HistoryPage(ids, latest);
    }

    /** Headers only (From, Subject, Authentication-Results) — the body is not downloaded. Null if the message is gone. */
    public GmailMessage headers(String id) throws IOException, InterruptedException {
        String url = API + "/messages/" + enc(id) + "?format=metadata"
                + "&metadataHeaders=From&metadataHeaders=Subject&metadataHeaders=Authentication-Results";
        JsonNode res = sendOrNull(authed(url).GET().build());
        if (res == null) return null;
        JsonNode headers = res.path("payload").path("headers");
        return new GmailMessage(id, header(headers, "From"), header(headers, "Subject"),
                header(headers, "Authentication-Results"),
                Instant.ofEpochMilli(res.path("internalDate").asLong()), null, List.of());
    }

    /** The message's body as plain text: text/plain parts if present, else HTML with tags stripped. */
    public String body(String id) throws IOException, InterruptedException {
        JsonNode res = send(authed(API + "/messages/" + enc(id) + "?format=full").GET().build());
        StringBuilder plain = new StringBuilder();
        StringBuilder html = new StringBuilder();
        collectParts(res.path("payload"), plain, html);
        if (plain.length() > 0) return plain.toString();
        return html.toString()
                .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ");
    }

    /** Raw bytes of every PDF attachment on the message. */
    public List<byte[]> pdfAttachments(String id) throws IOException, InterruptedException {
        JsonNode res = send(authed(API + "/messages/" + enc(id) + "?format=full").GET().build());
        List<String> attachmentIds = new ArrayList<>();
        collectPdfIds(res.path("payload"), attachmentIds);
        List<byte[]> pdfs = new ArrayList<>();
        for (String aid : attachmentIds) {
            JsonNode att = send(authed(API + "/messages/" + enc(id) + "/attachments/" + enc(aid)).GET().build());
            pdfs.add(Base64.getUrlDecoder().decode(att.path("data").asText()));
        }
        return pdfs;
    }

    /** Id of the label named in {@value #ENV_WATCH_LABEL} (the API wants ids like "Label_12"), or INBOX when unset. */
    private String watchLabelId() throws IOException, InterruptedException {
        if (watchLabelId != null) return watchLabelId;
        String name = System.getenv(ENV_WATCH_LABEL);
        if (name == null || name.isBlank()) return watchLabelId = "INBOX";
        for (JsonNode l : send(authed(API + "/labels").GET().build()).path("labels")) {
            if (name.equalsIgnoreCase(l.path("name").asText())) return watchLabelId = l.path("id").asText();
        }
        throw new IOException("Gmail label '" + name + "' not found");
    }

    private static void collectPdfIds(JsonNode part, List<String> ids) {
        String mime = part.path("mimeType").asText("");
        String name = part.path("filename").asText("");
        String aid = part.path("body").path("attachmentId").asText(null);
        if (aid != null && (mime.equalsIgnoreCase("application/pdf") || name.toLowerCase().endsWith(".pdf"))) ids.add(aid);
        for (JsonNode child : part.path("parts")) collectPdfIds(child, ids);
    }

    private static void collectParts(JsonNode part, StringBuilder plain, StringBuilder html) {
        String mime = part.path("mimeType").asText("");
        String data = part.path("body").path("data").asText(null);
        if (data != null) {
            String text = new String(Base64.getUrlDecoder().decode(data), StandardCharsets.UTF_8);
            if (mime.equalsIgnoreCase("text/plain")) plain.append(text).append('\n');
            else if (mime.equalsIgnoreCase("text/html")) html.append(text).append('\n');
        }
        for (JsonNode child : part.path("parts")) collectParts(child, plain, html);
    }

    private static String header(JsonNode headers, String name) {
        for (JsonNode h : headers) {
            if (name.equalsIgnoreCase(h.path("name").asText())) return h.path("value").asText();
        }
        return null;
    }

    private HttpRequest.Builder authed(String url) throws IOException, InterruptedException {
        return HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT)
                .header("Authorization", "Bearer " + accessToken());
    }

    private JsonNode send(HttpRequest req) throws IOException, InterruptedException {
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 404 && req.uri().getPath().endsWith("/history")) {
            throw new HistoryExpiredException("Gmail history id too old");
        }
        if (res.statusCode() / 100 != 2) {
            throw new IOException("Gmail " + req.uri().getPath() + " -> HTTP " + res.statusCode() + ": " + res.body());
        }
        return Jsons.MAPPER.readTree(res.body());
    }

    private JsonNode sendOrNull(HttpRequest req) throws IOException, InterruptedException {
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 404) return null;
        if (res.statusCode() / 100 != 2) {
            throw new IOException("Gmail " + req.uri().getPath() + " -> HTTP " + res.statusCode() + ": " + res.body());
        }
        return Jsons.MAPPER.readTree(res.body());
    }

    private static String accessToken() throws IOException, InterruptedException {
        if (accessToken != null && Instant.now().isBefore(accessTokenExpiry)) return accessToken;
        synchronized (GmailClient.class) {
            if (accessToken != null && Instant.now().isBefore(accessTokenExpiry)) return accessToken;
            String form = "grant_type=refresh_token"
                    + "&client_id=" + enc(requireEnv(ENV_CLIENT_ID))
                    + "&client_secret=" + enc(requireEnv(ENV_CLIENT_SECRET))
                    + "&refresh_token=" + enc(requireEnv(ENV_REFRESH_TOKEN));
            HttpRequest req = HttpRequest.newBuilder(URI.create(TOKEN_URL)).timeout(TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                // Body deliberately not logged: it can echo request details.
                throw new IOException("Gmail token refresh failed: HTTP " + res.statusCode());
            }
            JsonNode json = Jsons.MAPPER.readTree(res.body());
            accessToken = json.path("access_token").asText();
            // Renew a minute early so a token never expires mid-request.
            accessTokenExpiry = Instant.now().plusSeconds(Math.max(60, json.path("expires_in").asLong(3600) - 60));
            return accessToken;
        }
    }

    private static String requireEnv(String name) throws IOException {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) throw new IOException(name + " is not set");
        return v;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
