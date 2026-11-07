package com.ansub.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

// Weather for up to two places on the home screen, from Open-Meteo (free, no key, no account): the
// first under the TV's own clock, the second under South Korea's clock, in Korean (Hangul). The places
// are a setting, not part of the app, sent once at install time like the app list:
//   am start -n com.ansub.tvhome.weather/com.ansub.tvhome.MainActivity --es stamp <unique> \
//       --es weather "Name,latitude,longitude;이름,latitude,longitude"
// Two decimals (about 1 km) is plenty. Without it there's no weather and nothing is requested.
// Each place's high and low are for its own day (timezone=auto).
final class Weather {
    static final int MAX = 2;
    static final long REFRESH_MS = 30 * 60 * 1000L, RETRY_MS = 5 * 60 * 1000L;

    // One place to show weather for, from the setting.
    static final class Spot {
        final String name, lat, lon;
        Spot(String name, String lat, String lon) { this.name = name; this.lat = lat; this.lon = lon; }
    }

    // One place, now. Temperatures are in Celsius, as Open-Meteo sends them.
    static final class Place {
        String name;
        double now, high, low, wind; // wind in km/h
        int code;
        boolean day, korean;

        // Windy only matters when nothing is falling: then it gets its own look and label.
        boolean windy() { return wind >= WINDY_KMH && code <= 3; }
    }

    static final double WINDY_KMH = 40; // about 25 mph

    private Weather() {}

    // "Name,lat,lon;Name,lat,lon" to places, skipping any that don't read; at most MAX.
    static java.util.List<Spot> spots(String setting) {
        java.util.List<Spot> out = new java.util.ArrayList<>();
        if (setting == null) return out;
        for (String part : setting.split(";")) {
            String[] f = part.split(",");
            if (f.length != 3 || out.size() >= MAX) continue;
            try {
                double lat = Double.parseDouble(f[1].trim()), lon = Double.parseDouble(f[2].trim());
                if (Math.abs(lat) > 90 || Math.abs(lon) > 180) continue;
                out.add(new Spot(f[0].trim(), f[1].trim(), f[2].trim()));
            } catch (NumberFormatException ignored) {}
        }
        return out;
    }

    private static String url(java.util.List<Spot> spots) {
        StringBuilder lat = new StringBuilder(), lon = new StringBuilder();
        for (Spot s : spots) {
            if (lat.length() > 0) { lat.append(','); lon.append(','); }
            lat.append(s.lat);
            lon.append(s.lon);
        }
        return "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,weather_code,is_day,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min"
                + "&timezone=auto&forecast_days=1";
    }

    // The raw reply, or null. Blocks, so call it off the main thread.
    static String fetch(java.util.List<Spot> spots) {
        if (spots.isEmpty()) return null;
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url(spots)).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            if (c.getResponseCode() != 200) return null;
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            in.close();
            return out.toString("UTF-8");
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    // The places in the setting's order, or null if the reply can't be read. The second is in Korean.
    static Place[] parse(String json, java.util.List<Spot> spots) {
        try {
            if (spots.isEmpty()) return null;
            // One place comes back as an object, several as an array.
            JSONArray places = json.trim().startsWith("[") ? new JSONArray(json)
                    : new JSONArray().put(new JSONObject(json));
            if (places.length() < spots.size()) return null;
            Place[] out = new Place[spots.size()];
            for (int i = 0; i < spots.size(); i++) {
                JSONObject p = places.getJSONObject(i);
                JSONObject now = p.getJSONObject("current");
                JSONObject day = p.getJSONObject("daily");
                Place w = new Place();
                w.name = spots.get(i).name;
                w.korean = i == 1;
                w.now = now.getDouble("temperature_2m");
                w.code = now.getInt("weather_code");
                w.day = now.optInt("is_day", 1) == 1;
                w.wind = now.optDouble("wind_speed_10m", 0);
                w.high = day.getJSONArray("temperature_2m_max").getDouble(0);
                w.low = day.getJSONArray("temperature_2m_min").getDouble(0);
                out[i] = w;
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    // Fahrenheit is worked out here from Celsius, so the two always agree.
    static long f(double c) { return Math.round(c * 9 / 5 + 32); }
    static long c(double c) { return Math.round(c); }
    static String both(double c) { return f(c) + "°F / " + c(c) + "°C"; }

    // Every WMO weather code Open-Meteo sends: code, English, Korean.
    private static final Object[][] SKY = {
        {0, "Clear", "맑음"}, {1, "Mostly clear", "대체로 맑음"}, {2, "Partly cloudy", "구름 조금"},
        {3, "Cloudy", "흐림"}, {45, "Fog", "안개"}, {48, "Freezing fog", "어는 안개"},
        {51, "Light drizzle", "약한 이슬비"}, {53, "Drizzle", "이슬비"}, {55, "Heavy drizzle", "강한 이슬비"},
        {56, "Freezing drizzle", "어는 이슬비"}, {57, "Heavy freezing drizzle", "강한 어는 이슬비"},
        {61, "Light rain", "약한 비"}, {63, "Rain", "비"}, {65, "Heavy rain", "폭우"},
        {66, "Freezing rain", "어는 비"}, {67, "Heavy freezing rain", "강한 어는 비"},
        {71, "Light snow", "약한 눈"}, {73, "Snow", "눈"}, {75, "Heavy snow", "폭설"}, {77, "Snow grains", "싸락눈"},
        {80, "Light showers", "약한 소나기"}, {81, "Showers", "소나기"}, {82, "Heavy showers", "강한 소나기"},
        {85, "Snow showers", "소낙눈"}, {86, "Heavy snow showers", "강한 소낙눈"},
        {95, "Thunderstorm", "뇌우"}, {96, "Thunderstorm, hail", "우박 뇌우"}, {99, "Thunderstorm, heavy hail", "강한 우박 뇌우"},
    };

    // The sky in English or Korean, with "windy" added when it's windy; "" for an unknown code.
    static String sky(Place p, boolean ko) {
        String text = "";
        for (Object[] row : SKY) if ((Integer) row[0] == p.code) text = (String) row[ko ? 2 : 1];
        if (p.windy()) text = text.isEmpty() ? (ko ? "바람 많음" : "Windy") : text + (ko ? ", 바람 많음" : ", windy");
        return text;
    }
}
