package com.ansub.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

// Colour weather icons with a little depth, drawn on a 100x100 grid, so the app still needs no images.
// One look for nearly every WMO code Open-Meteo sends, by day and by night: sun or moon and stars,
// clouds with a soft shadow, light/normal/heavy rain, drizzle, freezing rain and drizzle, snow, snow
// grains, showers, fog and freezing fog, thunder with and without hail, and wind.
// Every shape is drawn solid on its own (never one path of overlapping shapes), and shadows go through
// a single layer, so overlaps can't cancel out or darken twice on any renderer.
final class WeatherIcon extends Drawable {
    private static final int CLEAR = 0, MOSTLY_CLEAR = 1, PARTLY = 2, CLOUDY = 3, WINDY = 4, FOG = 5, FREEZING_FOG = 6,
            DRIZZLE = 7, FREEZING_DRIZZLE = 8, RAIN = 9, HEAVY_RAIN = 10, FREEZING_RAIN = 11, SHOWERS = 12,
            HEAVY_SHOWERS = 13, SNOW = 14, HEAVY_SNOW = 15, SNOW_GRAINS = 16, SNOW_SHOWERS = 17, STORM = 18, HAIL = 19;

    private final int kind, level; // level: 1 light, 2 normal, 3 heavy, within a kind
    private final boolean day;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path(), cut = new Path();
    private final RectF r = new RectF();
    private float s;

    WeatherIcon(int code, boolean day) { this(code, day, false); }

    // windy: wind of WINDY_KMH or more with nothing falling (see Weather.Place.windy).
    WeatherIcon(int code, boolean day, boolean windy) {
        this.day = day;
        kind = windy ? WINDY : kind(code);
        level = level(code);
        fill.setStyle(Paint.Style.FILL);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
    }

    // WMO weather codes, as Open-Meteo sends them.
    private static int kind(int code) {
        switch (code) {
            case 0: return CLEAR;
            case 1: return MOSTLY_CLEAR;
            case 2: return PARTLY;
            case 3: return CLOUDY;
            case 45: return FOG;
            case 48: return FREEZING_FOG;
            case 51: case 53: case 55: return DRIZZLE;
            case 56: case 57: return FREEZING_DRIZZLE;
            case 61: case 63: return RAIN;
            case 65: return HEAVY_RAIN;
            case 66: case 67: return FREEZING_RAIN;
            case 71: case 73: return SNOW;
            case 75: return HEAVY_SNOW;
            case 77: return SNOW_GRAINS;
            case 80: case 81: return SHOWERS;
            case 82: return HEAVY_SHOWERS;
            case 85: case 86: return SNOW_SHOWERS;
            case 96: case 99: return HAIL;
            default: return code >= 95 ? STORM : code > 3 ? RAIN : CLOUDY;
        }
    }

    private static int level(int code) {
        switch (code) {
            case 51: case 56: case 61: case 66: case 71: case 80: case 85: case 96: return 1;
            case 55: case 57: case 65: case 67: case 75: case 82: case 86: case 99: return 3;
            default: return 2;
        }
    }

    // The colour a weather card glows with.
    static int tint(int code, boolean day) { return tint(code, day, false); }

    static int tint(int code, boolean day, boolean windy) {
        switch (windy ? WINDY : kind(code)) {
            case CLEAR: case MOSTLY_CLEAR: case PARTLY: return day ? 0xFFFFB648 : 0xFF7B6CFF; // gold / indigo
            case CLOUDY: return 0xFF9FB0C8;                                                   // slate
            case WINDY: return 0xFF5FD0BE;                                                    // teal
            case FOG: return 0xFFB8C0CC;                                                      // grey
            case FREEZING_FOG: case FREEZING_DRIZZLE: case FREEZING_RAIN: return 0xFF7FE0FF;  // ice
            case HEAVY_RAIN: case HEAVY_SHOWERS: return 0xFF2D5BFF;                           // deep blue
            case SNOW: case HEAVY_SNOW: case SNOW_GRAINS: case SNOW_SHOWERS: return 0xFFBFE6FF; // snow
            case STORM: return 0xFF9B6CFF;                                                    // violet
            case HAIL: return 0xFF8A7CFF;                                                     // violet blue
            default: return 0xFF4C9BFF;                                                       // rain blue
        }
    }

    @Override
    public void draw(Canvas c) {
        s = getBounds().width() / 100f;
        c.save();
        c.translate(getBounds().left, getBounds().top);
        switch (kind) {
            case CLEAR:
                sky(c, 50, 50, 21, true);
                break;
            case MOSTLY_CLEAR:
                sky(c, 42, 40, 18, true);
                cloud(c, 22, 20, 0.58f, false);
                break;
            case PARTLY:
                sky(c, 36, 36, 16, false);
                cloud(c, 8, 10, 1f, false);
                break;
            case CLOUDY:
                cloud(c, -12, -12, 0.74f, true); // a darker cloud behind
                cloud(c, 6, 6, 1f, false);
                break;
            case WINDY:
                cloud(c, 16, -18, 0.6f, false);
                gust(c, 50, 8, 56, 7, true);
                gust(c, 64, 18, 76, 8, true);
                gust(c, 78, 8, 50, 6, false);
                break;
            case FOG:
            case FREEZING_FOG:
                boolean icy = kind == FREEZING_FOG;
                cloud(c, 0, -12, 0.92f, false);
                mist(c, 76, 16, 84, icy);
                mist(c, 86, 24, 76, icy);
                mist(c, 96, 32, 68, icy);
                if (icy) {
                    star(c, 14, 70, 4.5f, 0xFFE6F7FF);
                    star(c, 88, 82, 3.6f, 0xFFE6F7FF);
                    star(c, 80, 66, 2.6f, 0xFFE6F7FF);
                }
                break;
            case DRIZZLE: {
                cloud(c, 0, -10, 1f, false);
                int[][] at = level == 1 ? new int[][] {{34, 84}, {54, 88}, {44, 97}}
                        : level == 2 ? new int[][] {{30, 80}, {48, 86}, {66, 80}, {39, 95}, {57, 95}}
                        : new int[][] {{26, 80}, {42, 84}, {58, 80}, {74, 84}, {34, 95}, {50, 96}, {66, 95}};
                for (int[] d : at) drop(c, d[0], d[1], level == 3 ? 2.4f : 2.6f, 15, false);
                break;
            }
            case FREEZING_DRIZZLE:
                cloud(c, 0, -10, 1f, false);
                for (int[] d : new int[][] {{28, 82}, {46, 88}, {36, 97}, {56, 97}}) drop(c, d[0], d[1], 2.6f, 15, true);
                if (level == 3) for (int[] d : new int[][] {{18, 92}, {64, 82}}) drop(c, d[0], d[1], 2.6f, 15, true);
                flake(c, 76, 90, 6.5f);
                break;
            case RAIN:
                cloud(c, 0, -10, 1f, false);
                if (level == 1) for (int[] d : new int[][] {{40, 86}, {62, 86}}) drop(c, d[0], d[1], 4.2f, 15, false);
                else for (int[] d : new int[][] {{32, 84}, {51, 90}, {70, 84}}) drop(c, d[0], d[1], 4.2f, 15, false);
                break;
            case HEAVY_RAIN:
                cloud(c, 0, -14, 1f, true);
                for (int[] d : new int[][] {{20, 78}, {36, 88}, {52, 78}, {68, 88}, {84, 78}, {28, 96}, {60, 96}})
                    drop(c, d[0], d[1], 4f, 25, false);
                break;
            case FREEZING_RAIN:
                cloud(c, 0, -10, 1f, false);
                drop(c, 30, 84, 4.2f, 15, true);
                drop(c, 64, 86, 4.2f, 15, true);
                flake(c, 47, 92, 6f);
                flake(c, 81, 94, 5f);
                if (level == 3) { drop(c, 16, 94, 3.6f, 15, true); flake(c, 22, 80, 4.5f); }
                break;
            case SHOWERS:
                sky(c, 30, 30, 14, false);
                cloud(c, 6, -2, 0.92f, false);
                if (level == 1) drop(c, 54, 92, 3.8f, 15, false);
                else for (int[] d : new int[][] {{44, 90}, {64, 94}}) drop(c, d[0], d[1], 3.8f, 15, false);
                break;
            case HEAVY_SHOWERS:
                sky(c, 28, 28, 13, false);
                cloud(c, 6, -4, 0.92f, true);
                for (int[] d : new int[][] {{34, 86}, {50, 94}, {66, 86}, {82, 94}}) drop(c, d[0], d[1], 4f, 25, false);
                break;
            case SNOW:
                cloud(c, 0, -10, 1f, false);
                if (level == 1) { flake(c, 40, 86, 7); flake(c, 64, 91, 7); }
                else { flake(c, 32, 82, 7); flake(c, 53, 91, 7.5f); flake(c, 72, 80, 6); }
                break;
            case HEAVY_SNOW:
                cloud(c, 0, -14, 1f, true);
                for (float[] f : new float[][] {{22, 78, 5.5f}, {44, 78, 5.5f}, {66, 78, 5.5f}, {86, 80, 4.5f},
                        {33, 93, 5.5f}, {55, 93, 5.5f}, {77, 94, 4.5f}}) flake(c, f[0], f[1], f[2]);
                break;
            case SNOW_GRAINS:
                cloud(c, 0, -10, 1f, false);
                for (int[] g : new int[][] {{26, 80}, {40, 86}, {54, 80}, {68, 86}, {82, 80}, {33, 94}, {47, 97},
                        {61, 94}, {75, 97}}) grain(c, g[0], g[1], 2.6f);
                break;
            case SNOW_SHOWERS:
                sky(c, 30, 30, 14, false);
                cloud(c, 6, -2, 0.92f, false);
                flake(c, 48, 90, 6);
                flake(c, 70, 94, 5.5f);
                if (level == 3) flake(c, 28, 94, 5);
                break;
            case HAIL:
                cloud(c, 0, -12, 1f, true);
                bolt(c);
                hail(c, 26, 84, 4.2f);
                hail(c, 77, 84, 4.2f);
                if (level == 3) { hail(c, 18, 96, 3.4f); hail(c, 86, 96, 3.4f); }
                break;
            default: // STORM
                cloud(c, 0, -12, 1f, true);
                bolt(c);
                drop(c, 28, 86, 3.4f, 15, false);
                drop(c, 76, 86, 3.4f, 15, false);
        }
        c.restore();
    }

    // The sun by day: a soft glow, eight rays and a disc lit from the top left. By night: a crescent moon
    // with a faint glow, and (if stars) a few twinkles.
    private void sky(Canvas c, float cx, float cy, float rad, boolean stars) {
        if (day) {
            glow(c, cx, cy, rad * 2.3f, 0x66FFC23A);
            line.setShader(null);
            line.setColor(0xFFFFB13D);
            line.setStrokeWidth(rad * 0.24f * s);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i + Math.PI / 8;
                float in = rad * 1.32f, out = rad * 1.72f;
                c.drawLine((float) (cx + Math.cos(a) * in) * s, (float) (cy + Math.sin(a) * in) * s,
                        (float) (cx + Math.cos(a) * out) * s, (float) (cy + Math.sin(a) * out) * s, line);
            }
            fill.setShader(new RadialGradient((cx - rad * 0.35f) * s, (cy - rad * 0.4f) * s, rad * 1.5f * s,
                    new int[] {0xFFFFF6B8, 0xFFFFC93D, 0xFFFF9A1F}, new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(cx * s, cy * s, rad * s, fill);
            fill.setShader(null);
        } else {
            glow(c, cx, cy, rad * 2f, 0x4D9C8CFF);
            path.reset();
            path.setFillType(Path.FillType.WINDING);
            path.addCircle(cx * s, cy * s, rad * 1.08f * s, Path.Direction.CW);
            cut.reset();
            cut.addCircle((cx + rad * 0.62f) * s, (cy - rad * 0.42f) * s, rad * 0.94f * s, Path.Direction.CW);
            path.op(cut, Path.Op.DIFFERENCE);
            fill.setShader(new LinearGradient((cx - rad) * s, (cy - rad) * s, (cx + rad) * s, (cy + rad) * s,
                    0xFFFFF8E1, 0xFFC9BCFF, Shader.TileMode.CLAMP));
            c.drawPath(path, fill);
            fill.setShader(null);
            if (stars) {
                star(c, cx + rad * 1.35f, cy - rad * 1.15f, rad * 0.32f, 0xFFFFF1C2);
                star(c, cx - rad * 1.25f, cy - rad * 0.95f, rad * 0.22f, 0xFFFFF1C2);
                star(c, cx + rad * 1.5f, cy + rad * 0.9f, rad * 0.18f, 0xFFFFF1C2);
            }
        }
    }

    // A cloud: three puffs on a rounded base, moved by (dx, dy) and scaled by k around its middle,
    // with a soft shadow below it. dark: a grey rain or storm cloud (or the cloud behind in Cloudy).
    private void cloud(Canvas c, float dx, float dy, float k, boolean dark) {
        c.save();
        c.translate(dx * s, dy * s);
        c.scale(k, k, 52 * s, 58 * s);
        // Shadow: drawn solid into one layer that is then faded, so its overlaps don't darken twice.
        r.set(0, 0, 100 * s, 100 * s);
        int layer = c.saveLayerAlpha(r, dark ? 70 : 60);
        c.translate(1.5f * s, 4f * s);
        fill.setShader(null);
        fill.setColor(0xFF101826);
        cloudShapes(c);
        c.restoreToCount(layer);
        fill.setShader(dark
                ? new LinearGradient(0, 26 * s, 0, 72 * s, 0xFFD3DAE5, 0xFF8995AA, Shader.TileMode.CLAMP)
                : new LinearGradient(0, 26 * s, 0, 72 * s, 0xFFFFFFFF, 0xFFCCD7E6, Shader.TileMode.CLAMP));
        cloudShapes(c);
        fill.setShader(null);
        c.restore();
    }

    private void cloudShapes(Canvas c) {
        c.drawCircle(32 * s, 56 * s, 15 * s, fill);
        c.drawCircle(52 * s, 45 * s, 21 * s, fill);
        c.drawCircle(71 * s, 57 * s, 14 * s, fill);
        r.set(17 * s, 55 * s, 86 * s, 71 * s);
        c.drawRoundRect(r, 8 * s, 8 * s, fill);
    }

    // A teardrop, round end down, tilted by angle as if falling: light to deep blue, or pale ice blue.
    private void drop(Canvas c, float x, float y, float size, float angle, boolean icy) {
        c.save();
        c.rotate(angle, x * s, y * s);
        fill.setShader(new LinearGradient(0, (y - size * 2.3f) * s, 0, (y + size) * s,
                icy ? 0xFFFFFFFF : 0xFFA8DCFF, icy ? 0xFF7FD3F2 : 0xFF2F7DF0, Shader.TileMode.CLAMP));
        c.drawCircle(x * s, y * s, size * s, fill);
        path.reset();
        path.setFillType(Path.FillType.WINDING);
        path.moveTo((x - size * 0.93f) * s, (y - size * 0.35f) * s);
        path.lineTo(x * s, (y - size * 2.4f) * s);
        path.lineTo((x + size * 0.93f) * s, (y - size * 0.35f) * s);
        path.close();
        c.drawPath(path, fill);
        fill.setShader(null);
        c.restore();
    }

    // A six-armed snowflake with little side branches.
    private void flake(Canvas c, float x, float y, float rad) {
        line.setShader(null);
        line.setColor(0xFFEFF8FF);
        line.setStrokeWidth(rad * 0.26f * s);
        for (int i = 0; i < 3; i++) {
            double a = Math.PI / 3 * i + Math.PI / 2;
            float ex = (float) Math.cos(a) * rad, ey = (float) Math.sin(a) * rad;
            c.drawLine((x - ex) * s, (y - ey) * s, (x + ex) * s, (y + ey) * s, line);
        }
        line.setStrokeWidth(rad * 0.18f * s);
        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 3 * i + Math.PI / 2;
            float bx = x + (float) Math.cos(a) * rad * 0.6f, by = y + (float) Math.sin(a) * rad * 0.6f;
            for (double side : new double[] {-0.6, 0.6}) {
                c.drawLine(bx * s, by * s, (bx + (float) Math.cos(a + side) * rad * 0.35f) * s,
                        (by + (float) Math.sin(a + side) * rad * 0.35f) * s, line);
            }
        }
    }

    // A snow grain: a small white pellet.
    private void grain(Canvas c, float x, float y, float rad) {
        fill.setShader(new RadialGradient((x - rad * 0.3f) * s, (y - rad * 0.3f) * s, rad * 1.3f * s,
                0xFFFFFFFF, 0xFFCFE6F7, Shader.TileMode.CLAMP));
        c.drawCircle(x * s, y * s, rad * s, fill);
        fill.setShader(null);
    }

    // A hailstone: an icy ball, lit from the top left, with a faint edge.
    private void hail(Canvas c, float x, float y, float rad) {
        fill.setShader(new RadialGradient((x - rad * 0.35f) * s, (y - rad * 0.35f) * s, rad * 1.4f * s,
                new int[] {0xFFFFFFFF, 0xFFCDEBFF, 0xFF8CC2EA}, new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(x * s, y * s, rad * s, fill);
        fill.setShader(null);
        line.setShader(null);
        line.setColor(0x995C8FB8);
        line.setStrokeWidth(0.8f * s);
        c.drawCircle(x * s, y * s, rad * s, line);
    }

    // A mist bar that fades out at both ends; icy for freezing fog.
    private void mist(Canvas c, float y, float x0, float x1, boolean icy) {
        int col = icy ? 0xE6CDEBFF : 0xE6E3E9F2;
        line.setShader(new LinearGradient(x0 * s, 0, x1 * s, 0, new int[] {col & 0x00FFFFFF, col, col & 0x00FFFFFF},
                new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        line.setStrokeWidth(6 * s);
        c.drawLine(x0 * s, y * s, x1 * s, y * s, line);
        line.setShader(null);
    }

    // A gust: a line that ends in a curl, up or down, white fading into teal.
    private void gust(Canvas c, float y, float x0, float x1, float curl, boolean up) {
        path.reset();
        path.setFillType(Path.FillType.WINDING);
        path.moveTo(x0 * s, y * s);
        path.lineTo(x1 * s, y * s);
        if (up) {
            r.set((x1 - curl) * s, (y - 2 * curl) * s, (x1 + curl) * s, y * s);
            path.arcTo(r, 90, -270, false);
        } else {
            r.set((x1 - curl) * s, y * s, (x1 + curl) * s, (y + 2 * curl) * s);
            path.arcTo(r, -90, 270, false);
        }
        line.setShader(new LinearGradient(x0 * s, 0, (x1 + curl) * s, 0, 0x99FFFFFF, 0xFF8FE8D8, Shader.TileMode.CLAMP));
        line.setStrokeWidth(5 * s);
        c.drawPath(path, line);
        line.setShader(null);
    }

    // A lightning bolt: a warm glow behind, yellow to orange, with a darker edge.
    private void bolt(Canvas c) {
        glow(c, 52, 80, 22, 0x59FFD34E);
        path.reset();
        path.setFillType(Path.FillType.WINDING);
        path.moveTo(56 * s, 58 * s);
        path.lineTo(40 * s, 81 * s);
        path.lineTo(51 * s, 81 * s);
        path.lineTo(44 * s, 99 * s);
        path.lineTo(66 * s, 72 * s);
        path.lineTo(54 * s, 72 * s);
        path.lineTo(62 * s, 58 * s);
        path.close();
        fill.setShader(new LinearGradient(0, 58 * s, 0, 99 * s, 0xFFFFF27A, 0xFFFFA200, Shader.TileMode.CLAMP));
        c.drawPath(path, fill);
        fill.setShader(null);
        line.setShader(null);
        line.setColor(0xFFD97A00);
        line.setStrokeWidth(1.4f * s);
        c.drawPath(path, line);
    }

    // A four-pointed twinkle.
    private void star(Canvas c, float x, float y, float rad, int color) {
        float w = rad * 0.28f;
        path.reset();
        path.setFillType(Path.FillType.WINDING);
        path.moveTo(x * s, (y - rad) * s);
        path.lineTo((x + w) * s, (y - w) * s);
        path.lineTo((x + rad) * s, y * s);
        path.lineTo((x + w) * s, (y + w) * s);
        path.lineTo(x * s, (y + rad) * s);
        path.lineTo((x - w) * s, (y + w) * s);
        path.lineTo((x - rad) * s, y * s);
        path.lineTo((x - w) * s, (y - w) * s);
        path.close();
        fill.setShader(null);
        fill.setColor(color);
        c.drawPath(path, fill);
    }

    // A soft round glow that fades to nothing.
    private void glow(Canvas c, float cx, float cy, float rad, int color) {
        fill.setShader(new RadialGradient(cx * s, cy * s, rad * s, color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(cx * s, cy * s, rad * s, fill);
        fill.setShader(null);
    }

    @Override public void setAlpha(int a) { fill.setAlpha(a); line.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) { fill.setColorFilter(cf); line.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return 100; }
    @Override public int getIntrinsicHeight() { return 100; }
}
