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
        double now, high, low;
        int code;
        boolean day, korean;
    }

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
                + "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min"
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

    // WMO weather codes, as Open-Meteo documents them, in English or Korean.
    static String sky(int code, boolean ko) {
        String en = sky(code);
        if (!ko) return en;
        switch (en) {
            case "Clear": return "맑음";
            case "Mostly clear": return "대체로 맑음";
            case "Partly cloudy": return "구름 조금";
            case "Cloudy": return "흐림";
            case "Fog": return "안개";
            case "Drizzle": return "이슬비";
            case "Rain": return "비";
            case "Snow": return "눈";
            case "Showers": return "소나기";
            case "Snow showers": return "소낙눈";
            case "Thunderstorm": return "뇌우";
            default: return "";
        }
    }

    private static String sky(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95) return "Thunderstorm";
        return "";
    }
}
