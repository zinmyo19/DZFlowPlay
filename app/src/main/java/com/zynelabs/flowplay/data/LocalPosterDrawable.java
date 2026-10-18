package com.zynelabs.flowplay.data;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import com.zynelabs.flowplay.R;

/** Offline substitute, deliberately labelled so it cannot be mistaken for provider artwork.
 * Uses only the visible title and media kind: never a stream URL, account, or portal credential.
 * Kept separate from ImageLoader's genuine-artwork caches; no network or disk work on the UI thread.
 */
public final class LocalPosterDrawable extends Drawable {
    private final Context context;
    private final String title;
    private final int kind;
    private final boolean compact;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private StaticLayout layout;
    private Drawable emergencyLogo;
    private boolean failed;
    private final int accent;

    public static Drawable create(Context context, String title, int kind, boolean compact) {
        try { return new LocalPosterDrawable(context.getApplicationContext(), title, kind, compact); }
        catch (RuntimeException | OutOfMemoryError ignored) {
            return context.getResources().getDrawable(R.drawable.ic_launcher, null);
        }
    }

    private LocalPosterDrawable(Context context, String title, int kind, boolean compact) {
        this.context = context;
        // Bound text layout cost even for malformed playlist metadata. Support all Android scripts.
        String value = title == null ? "" : title.replaceAll("[\\p{Cntrl}]", " ").trim();
        if (value.length() > 240) {
            int end = 240;
            if (Character.isHighSurrogate(value.charAt(end - 1))) end--;
            value = value.substring(0, end);
        }
        this.title = value.isEmpty() ? (kind == Channel.SERIES ? "Series" : "Movie") : value;
        this.kind = kind;
        this.compact = compact;
        float hue = (this.title.hashCode() & 0x7fffffff) % 360;
        this.accent = Color.HSVToColor(new float[]{hue, 0.64f, 0.65f});
    }

    @Override public int getIntrinsicWidth() { return 384; }
    @Override public int getIntrinsicHeight() { return compact ? 288 : 512; }

    @Override public void draw(Canvas canvas) {
        if (getBounds().isEmpty()) return;
        int save = canvas.save();
        try {
            canvas.clipRect(getBounds());
            if (failed) { drawLogo(canvas); return; }
            canvas.translate(getBounds().left, getBounds().top);
            canvas.scale(getBounds().width() / 384f, getBounds().height() / (float) getIntrinsicHeight());
            drawPoster(canvas);
        } catch (RuntimeException | OutOfMemoryError ignored) {
            failed = true;
        } finally { canvas.restoreToCount(save); }
        // Last-resort asset is the existing launcher PNG, never remote artwork.
        if (failed) drawLogo(canvas);
    }

    private void drawPoster(Canvas canvas) {
        int height = getIntrinsicHeight();
        paint.setShader(new LinearGradient(0, 0, 384, height,
                new int[]{accent, Color.rgb(20, 22, 34), Color.rgb(11, 11, 16)},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, 384, height, paint);
        paint.setShader(null);
        paint.setColor(0x20FFFFFF);
        canvas.drawCircle(320, compact ? 35 : 100, compact ? 95 : 155, paint);
        paint.setColor(0x16FFFFFF);
        canvas.drawCircle(320, compact ? 35 : 100, compact ? 130 : 195, paint);
        paint.setColor(0xCCFFFFFF);
        Path play = new Path();
        float top = compact ? 66 : 138;
        play.moveTo(40, top); play.lineTo(40, top + 48); play.lineTo(80, top + 24); play.close();
        canvas.drawPath(play, paint);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setTextSize(14); text.setColor(0xCCFFFFFF);
        canvas.drawText("LOCAL ART", 28, 32, text);
        if (layout == null) {
            text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            text.setTextSize(compact ? 30 : 36); text.setColor(Color.WHITE);
            layout = StaticLayout.Builder.obtain(title, 0, title.length(), text, 328)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
                    .setMaxLines(compact ? 2 : 4).setEllipsize(TextUtils.TruncateAt.END).build();
        }
        // StaticLayout retains its TextPaint, so reset title styling after the small label above.
        text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        text.setTextSize(compact ? 30 : 36); text.setColor(Color.WHITE);
        int save = canvas.save();
        canvas.translate(28, height - 54 - layout.getHeight());
        layout.draw(canvas); canvas.restoreToCount(save);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setTextSize(13); text.setColor(0xBFFFFFFF);
        canvas.drawText(kind == Channel.SERIES ? "SERIES  /  FLOWPLAY" : "MOVIES  /  FLOWPLAY", 28, height - 24, text);
    }

    private void drawLogo(Canvas canvas) {
        if (emergencyLogo == null) emergencyLogo = context.getResources().getDrawable(R.drawable.ic_launcher, null).mutate();
        canvas.drawColor(Color.rgb(20, 21, 28));
        int side = Math.min(getBounds().width(), getBounds().height()) * 2 / 3;
        int left = getBounds().centerX() - side / 2;
        int top = getBounds().centerY() - side / 2;
        emergencyLogo.setBounds(left, top, left + side, top + side);
        emergencyLogo.draw(canvas);
    }

    @Override public void setAlpha(int alpha) { /* Opaque local artwork. */ }
    @Override public void setColorFilter(ColorFilter filter) { /* Preserve poster palette. */ }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
