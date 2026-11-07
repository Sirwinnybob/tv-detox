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

// Colour weather icons with a little depth, drawn on a 100x100 grid, so the app still needs no images:
// a glowing sun (a crescent moon and stars at night), clouds with a soft shadow and a white-to-cool
// gradient, teardrop rain, six-armed snowflakes, mist bars and a glowing bolt.
// Every shape is drawn solid on its own (never one path of overlapping shapes), and shadows go through
// a single layer, so overlaps can't cancel out or darken twice on any renderer.
final class WeatherIcon extends Drawable {
    private static final int CLEAR = 0, MOSTLY_CLEAR = 1, PARTLY = 2, CLOUDY = 3, FOG = 4, DRIZZLE = 5, RAIN = 6,
            SHOWERS = 7, SNOW = 8, STORM = 9;

    private final int kind;
    private final boolean day;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path(), cut = new Path();
    private final RectF r = new RectF();
    private float s;

    WeatherIcon(int code, boolean day) {
        this.day = day;
        kind = kind(code);
        fill.setStyle(Paint.Style.FILL);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    // WMO weather codes, as Open-Meteo sends them.
    private static int kind(int code) {
        if (code <= 0) return CLEAR;
        if (code == 1) return MOSTLY_CLEAR;
        if (code == 2) return PARTLY;
        if (code == 3) return CLOUDY;
        if (code == 45 || code == 48) return FOG;
        if (code >= 51 && code <= 57) return DRIZZLE;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return SNOW;
        if (code >= 80 && code <= 82) return SHOWERS;
        if (code >= 95) return STORM;
        return RAIN; // 61 to 67, including freezing rain
    }

    // The colour a weather card glows with: golden sun, indigo night, slate cloud, blue rain, icy snow,
    // violet storm.
    static int tint(int code, boolean day) {
        switch (kind(code)) {
            case CLEAR: case MOSTLY_CLEAR: case PARTLY: return day ? 0xFFFFB648 : 0xFF7B6CFF;
            case CLOUDY: return 0xFF9FB0C8;
            case FOG: return 0xFFB8C0CC;
            case SNOW: return 0xFFBFE6FF;
            case STORM: return 0xFF9B6CFF;
            default: return 0xFF4C9BFF; // drizzle, rain, showers
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
            case FOG:
                cloud(c, 0, -12, 0.92f, false);
                mist(c, 76, 16, 84);
                mist(c, 86, 24, 76);
                mist(c, 96, 32, 68);
                break;
            case DRIZZLE:
                cloud(c, 0, -10, 1f, false);
                for (int[] d : new int[][] {{30, 80}, {48, 86}, {66, 80}, {39, 95}, {57, 95}}) drop(c, d[0], d[1], 2.6f);
                break;
            case RAIN:
                cloud(c, 0, -10, 1f, false);
                for (int[] d : new int[][] {{32, 84}, {51, 90}, {70, 84}}) drop(c, d[0], d[1], 4.2f);
                break;
            case SHOWERS:
                sky(c, 30, 30, 14, false);
                cloud(c, 6, -2, 0.92f, false);
                for (int[] d : new int[][] {{44, 90}, {64, 94}}) drop(c, d[0], d[1], 3.8f);
                break;
            case SNOW:
                cloud(c, 0, -10, 1f, false);
                flake(c, 32, 82, 7);
                flake(c, 53, 91, 7.5f);
                flake(c, 72, 80, 6);
                break;
            default: // STORM
                cloud(c, 0, -12, 1f, true);
                bolt(c);
                drop(c, 28, 86, 3.4f);
                drop(c, 76, 86, 3.4f);
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
                star(c, cx + rad * 1.35f, cy - rad * 1.15f, rad * 0.32f);
                star(c, cx - rad * 1.25f, cy - rad * 0.95f, rad * 0.22f);
                star(c, cx + rad * 1.5f, cy + rad * 0.9f, rad * 0.18f);
            }
        }
    }

    // A cloud: three puffs on a rounded base, moved by (dx, dy) and scaled by k around its middle,
    // with a soft shadow below it. dark: the grey storm cloud (or the cloud behind in Cloudy).
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

    // A teardrop, round end down, tilted as if falling, light blue at the top to deep blue.
    private void drop(Canvas c, float x, float y, float size) {
        c.save();
        c.rotate(15, x * s, y * s);
        fill.setShader(new LinearGradient(0, (y - size * 2.3f) * s, 0, (y + size) * s,
                0xFFA8DCFF, 0xFF2F7DF0, Shader.TileMode.CLAMP));
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

    // A mist bar that fades out at both ends.
    private void mist(Canvas c, float y, float x0, float x1) {
        line.setShader(new LinearGradient(x0 * s, 0, x1 * s, 0, new int[] {0x00E3E9F2, 0xE6E3E9F2, 0x00E3E9F2},
                new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        line.setStrokeWidth(6 * s);
        c.drawLine(x0 * s, y * s, x1 * s, y * s, line);
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
        line.setStrokeJoin(Paint.Join.ROUND);
        c.drawPath(path, line);
    }

    // A four-pointed twinkle.
    private void star(Canvas c, float x, float y, float rad) {
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
        fill.setColor(0xFFFFF1C2);
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
