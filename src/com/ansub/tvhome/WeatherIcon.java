package com.ansub.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

// Colour weather icons, drawn on a 100x100 grid like LineIcon, so the app still needs no images:
// a warm sun (or a pale moon at night), soft white clouds, blue rain, white snow, a yellow bolt.
final class WeatherIcon extends Drawable {
    private static final int SUN = 0xFFFFC94A, MOON = 0xFFE9E4FF, CLOUD = 0xFFF2F5FA, DARK_CLOUD = 0xFFC3CCD8,
            RAIN = 0xFF6CB8FF, SNOW = 0xFFFFFFFF, BOLT = 0xFFFFD34E, FOG = 0xB3E6EBF2;
    private static final int CLEAR = 0, PARTLY = 1, CLOUDY = 2, FOGGY = 3, RAINY = 4, SNOWY = 5, STORMY = 6;

    private final int kind;
    private final boolean day;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path(), moon = new Path(), cut = new Path();
    private final RectF r = new RectF();
    private float s;

    WeatherIcon(int code, boolean day) {
        this.day = day;
        kind = kind(code);
        fill.setStyle(Paint.Style.FILL);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    public void draw(Canvas c) {
        s = getBounds().width() / 100f;
        c.save();
        c.translate(getBounds().left, getBounds().top);
        switch (kind) {
            case CLEAR:
                sky(c, 50, 50, 22);
                break;
            case PARTLY:
                sky(c, 38, 38, 16);
                cloud(c, 8, 10, CLOUD);
                break;
            case CLOUDY:
                cloud(c, 0, 4, CLOUD);
                break;
            case FOGGY:
                cloud(c, 0, -8, CLOUD);
                stroke(FOG, 6);
                for (int[] l : new int[][] {{18, 76, 82}, {28, 89, 72}}) c.drawLine(l[0] * s, l[1] * s, l[2] * s, l[1] * s, line);
                break;
            case RAINY:
                cloud(c, 0, -8, CLOUD);
                stroke(RAIN, 6);
                for (int x : new int[] {34, 52, 70}) c.drawLine(x * s, 74 * s, (x - 6) * s, 90 * s, line);
                break;
            case SNOWY:
                cloud(c, 0, -8, CLOUD);
                fill.setColor(SNOW);
                for (int[] d : new int[][] {{32, 78}, {52, 86}, {72, 78}, {42, 94}, {62, 94}}) c.drawCircle(d[0] * s, d[1] * s, 4 * s, fill);
                break;
            default: // STORMY
                cloud(c, 0, -8, DARK_CLOUD);
                fill.setColor(BOLT);
                path.reset();
                path.setFillType(Path.FillType.WINDING);
                path.moveTo(54 * s, 62 * s);
                path.lineTo(40 * s, 82 * s);
                path.lineTo(50 * s, 82 * s);
                path.lineTo(44 * s, 98 * s);
                path.lineTo(64 * s, 74 * s);
                path.lineTo(53 * s, 74 * s);
                path.lineTo(60 * s, 62 * s);
                path.close();
                c.drawPath(path, fill);
        }
        c.restore();
    }

    private static int kind(int code) {
        if (code <= 0) return CLEAR;
        if (code <= 2) return PARTLY;
        if (code == 3) return CLOUDY;
        if (code == 45 || code == 48) return FOGGY;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return SNOWY;
        if (code >= 95) return STORMY;
        return RAINY; // drizzle, rain, showers
    }

    // The colour a weather card glows with: golden sun, indigo night, slate cloud, blue rain, icy snow,
    // violet storm.
    static int tint(int code, boolean day) {
        switch (kind(code)) {
            case CLEAR: case PARTLY: return day ? 0xFFFFB648 : 0xFF7B6CFF;
            case CLOUDY: return 0xFF9FB0C8;
            case FOGGY: return 0xFFB8C0CC;
            case SNOWY: return 0xFFBFE6FF;
            case STORMY: return 0xFF9B6CFF;
            default: return 0xFF4C9BFF;
        }
    }

    // The sun by day, with eight short rays; a crescent moon by night.
    private void sky(Canvas c, float cx, float cy, float rad) {
        if (day) {
            fill.setColor(SUN);
            c.drawCircle(cx * s, cy * s, rad * s, fill);
            stroke(SUN, rad / 4);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i;
                float in = rad + rad * 0.35f, out = rad + rad * 0.7f;
                c.drawLine((float) (cx + Math.cos(a) * in) * s, (float) (cy + Math.sin(a) * in) * s,
                        (float) (cx + Math.cos(a) * out) * s, (float) (cy + Math.sin(a) * out) * s, line);
            }
        } else {
            // Its own path: op() can switch a path to even-odd filling, which reset() keeps.
            fill.setColor(MOON);
            moon.reset();
            moon.addCircle(cx * s, cy * s, rad * 1.15f * s, Path.Direction.CW);
            cut.reset();
            cut.addCircle((cx + rad * 0.65f) * s, (cy - rad * 0.45f) * s, rad * s, Path.Direction.CW);
            moon.op(cut, Path.Op.DIFFERENCE);
            c.drawPath(moon, fill);
        }
    }

    // A soft cloud: three puffs on a rounded base, moved by (dx, dy). Drawn as separate solid shapes,
    // not one path, so where they overlap they can't cancel out into a checkered look.
    private void cloud(Canvas c, float dx, float dy, int color) {
        fill.setColor(color);
        c.drawCircle((32 + dx) * s, (56 + dy) * s, 15 * s, fill);
        c.drawCircle((52 + dx) * s, (45 + dy) * s, 21 * s, fill);
        c.drawCircle((71 + dx) * s, (57 + dy) * s, 14 * s, fill);
        r.set((17 + dx) * s, (55 + dy) * s, (86 + dx) * s, (71 + dy) * s);
        c.drawRoundRect(r, 8 * s, 8 * s, fill);
    }

    private void stroke(int color, float width) {
        line.setColor(color);
        line.setStrokeWidth(width * s);
    }

    @Override public void setAlpha(int a) { fill.setAlpha(a); line.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) { fill.setColorFilter(cf); line.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return 100; }
    @Override public int getIntrinsicHeight() { return 100; }
}
