package com.verum.omnis.core;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public final class GemmaChatStore {

    public static final class Message {
        public final String speaker;
        public final String text;

        public Message(String speaker, String text) {
            this.speaker = speaker;
            this.text = text;
        }
    }

    public static final class Session {
        public final String id;
        public String title;
        public final long createdAt;
        public long updatedAt;
        public final List<Message> messages = new ArrayList<>();

        public Session(String id, String title, long createdAt, long updatedAt) {
            this.id = id;
            this.title = title;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
    }

    private GemmaChatStore() {}

    public static Session loadLatestOrCreate(Context context) {
        List<Session> sessions = listSessions(context);
        return sessions.isEmpty() ? createSession(context) : load(context, sessions.get(0).id);
    }

    public static Session createSession(Context context) {
        long now = System.currentTimeMillis();
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date(now));
        Session session = new Session("chat-" + stamp, "New chat", now, now);
        save(context, session);
        return session;
    }

    public static List<Session> listSessions(Context context) {
        File[] files = getChatDir(context).listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            return new ArrayList<>();
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        List<Session> sessions = new ArrayList<>();
        for (File file : files) {
            Session session = loadFromFile(file);
            if (session != null) {
                sessions.add(session);
            }
        }
        return sessions;
    }

    public static Session load(Context context, String id) {
        File file = new File(getChatDir(context), id + ".json");
        Session session = loadFromFile(file);
        return session != null ? session : createSession(context);
    }

    public static void append(Context context, Session session, String speaker, String text) {
        if (session == null || text == null || text.trim().isEmpty()) {
            return;
        }
        session.messages.add(new Message(speaker, text.trim()));
        session.updatedAt = System.currentTimeMillis();
        if ("You".equalsIgnoreCase(speaker) && ("New chat".equals(session.title) || session.title.trim().isEmpty())) {
            session.title = summarizeTitle(text);
        }
        save(context, session);
    }

    public static void save(Context context, Session session) {
        try {
            JSONObject root = new JSONObject();
            root.put("id", session.id);
            root.put("title", session.title);
            root.put("createdAt", session.createdAt);
            root.put("updatedAt", session.updatedAt);
            JSONArray messages = new JSONArray();
            for (Message message : session.messages) {
                JSONObject item = new JSONObject();
                item.put("speaker", message.speaker);
                item.put("text", message.text);
                messages.put(item);
            }
            root.put("messages", messages);

            File outFile = new File(getChatDir(context), session.id + ".json");
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(root.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    private static Session loadFromFile(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        try (Scanner scanner = new Scanner(file, StandardCharsets.UTF_8.name())) {
            String json = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
            JSONObject root = new JSONObject(json);
            Session session = new Session(
                    root.optString("id", file.getName().replace(".json", "")),
                    root.optString("title", "Gemma chat"),
                    root.optLong("createdAt", file.lastModified()),
                    root.optLong("updatedAt", file.lastModified())
            );
            JSONArray messages = root.optJSONArray("messages");
            if (messages != null) {
                for (int i = 0; i < messages.length(); i++) {
                    JSONObject item = messages.optJSONObject(i);
                    if (item == null) continue;
                    session.messages.add(new Message(
                            item.optString("speaker", "Gemma"),
                            item.optString("text", "")
                    ));
                }
            }
            return session;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String summarizeTitle(String text) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 42) {
            return normalized;
        }
        return normalized.substring(0, 42).trim() + "...";
    }

    private static File getChatDir(Context context) {
        File dir = new File(context.getFilesDir(), "gemma_chats");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }
}
