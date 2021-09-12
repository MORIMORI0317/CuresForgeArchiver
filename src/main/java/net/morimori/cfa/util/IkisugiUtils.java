package net.morimori.cfa.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class IkisugiUtils {
    private static long lastTime;
    private static final Gson goson = new Gson();
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";

    public static void clockTimer(int hours, int minutes, Runnable runnable) {
        Timer timer = new Timer(false);
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                if (System.currentTimeMillis() - lastTime > 60 * 60 * 1000) {
                    Date date = new Date();
                    if (date.getHours() == hours && date.getMinutes() == minutes) {
                        runnable.run();
                        lastTime = System.currentTimeMillis();
                    }
                }
            }
        };
        timer.schedule(task, 0, 1000);
    }

    public static String getURLResponse(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.addRequestProperty("user-agent", USER_AGENT);
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null)
            sb.append(inputLine).append('\n');
        in.close();
        return sb.toString();
    }

    public static JsonObject getURLJsonResponse(String url) throws IOException {
        return goson.fromJson(getURLResponse(url), JsonObject.class);
    }

    public static String jsonBuilder(String inJson) {
        return new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(JsonParser.parseString(inJson));
    }
}
