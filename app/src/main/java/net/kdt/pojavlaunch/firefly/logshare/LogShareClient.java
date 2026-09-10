/*
 * Pojav Glow-Worm
 * Copyright (C) 2026 Pojav Glow-Worm contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.firefly.logshare;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Minimal client for the LogShare.CN HTTP API.
 *
 * <p>See the official documentation at <a href="https://logshare.cn/api-docs">logshare.cn/api-docs</a>.
 * Only the log submission endpoint is implemented, which is all the launcher needs.</p>
 */
public final class LogShareClient {
    public static final String SHARE_BASE_URL = "https://logshare.cn/";
    private static final String SUBMIT_URL = "https://api.logshare.cn/v1/log";
    private static final int MAX_SOURCE_LENGTH = 64;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Gson GSON = new Gson();
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();

    private LogShareClient() {
    }

    public interface UploadCallback {
        void onSuccess(@NonNull String id, @NonNull String url);

        void onFailure(@Nullable String message);
    }

    /**
     * Uploads a single text log.
     *
     * @param source    optional origin tag (max 64 chars), e.g. {@code pgw/snowdrop}
     * @param filename  name reported through metadata
     * @param content   raw log content (the API accepts up to 10 MiB / 50000 lines)
     * @param sizeBytes original file size in bytes
     */
    public static void upload(@NonNull String source, @NonNull String filename,
                              @NonNull String content, long sizeBytes,
                              @NonNull UploadCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("content", content);
        if (!source.isEmpty()) {
            body.addProperty("source", source.length() > MAX_SOURCE_LENGTH
                    ? source.substring(0, MAX_SOURCE_LENGTH) : source);
        }
        JsonArray metadata = new JsonArray();
        metadata.add(metadataEntry("filename", filename, "文件名"));
        metadata.add(metadataEntry("size", Long.toString(sizeBytes), "文件大小"));
        body.add("metadata", metadata);

        Request request = new Request.Builder()
                .url(SUBMIT_URL)
                .post(RequestBody.create(JSON, GSON.toJson(body)))
                .header("Accept", "application/json")
                .build();

        CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (ResponseBody responseBody = response.body()) {
                    String text = responseBody != null ? responseBody.string() : "";
                    JsonObject json = parseObject(text);
                    if (!response.isSuccessful() || json == null
                            || !json.has("success") || !json.get("success").getAsBoolean()) {
                        callback.onFailure(extractMessage(json, response.code()));
                        return;
                    }
                    String id = json.has("id") ? json.get("id").getAsString() : "";
                    String url = json.has("url") && !json.get("url").isJsonNull()
                            ? json.get("url").getAsString() : SHARE_BASE_URL + id;
                    callback.onSuccess(id, url);
                } catch (Exception e) {
                    callback.onFailure(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                }
            }
        });
    }

    private static JsonObject metadataEntry(String key, String value, String label) {
        JsonObject entry = new JsonObject();
        entry.addProperty("key", key);
        entry.addProperty("value", value);
        entry.addProperty("label", label);
        entry.addProperty("visible", false);
        return entry;
    }

    @Nullable
    private static JsonObject parseObject(@Nullable String text) {
        if (text == null || text.isEmpty()) return null;
        try {
            JsonElement element = JsonParser.parseString(text);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
    private static String extractMessage(@Nullable JsonObject json, int code) {
        if (json != null) {
            if (json.has("error") && !json.get("error").isJsonNull()) {
                String error = json.get("error").getAsString();
                if (!error.isEmpty()) return error;
            }
            if (json.has("message") && !json.get("message").isJsonNull()) {
                String message = json.get("message").getAsString();
                if (!message.isEmpty()) return message;
            }
        }
        return "HTTP " + code;
    }
}
