package com.calpano.uxlens.perception;

import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.Rect;
import com.calpano.uxlens.model.Severity;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Draws the mathematics onto the screenshot.
 *
 * <p>A vision model has no reference points in a raw screenshot: it cannot tell 120px
 * from 122px. Given a grid to measure against, or numbered boxes it can cross-reference
 * with exact coordinates, it stops guessing. Every overlay here exists to turn a
 * question of perception into a question of reading.
 *
 * <p>All element coordinates are CSS pixels in document space; {@code scale} converts
 * them to screenshot pixels (the device pixel ratio the capture was taken at).
 */
public final class Overlays {

    private static final Color GRID_MINOR = new Color(0, 120, 255, 40);
    private static final Color GRID_MAJOR = new Color(0, 90, 220, 90);
    private static final Color BOX        = new Color(220, 30, 90, 150);
    private static final Color BADGE      = new Color(220, 30, 90, 230);

    private Overlays() { }

    /**
     * A measuring grid. Minor lines every {@code step} CSS px, a stronger line every
     * fifth, so the model can read "this edge sits one minor line left of that one".
     */
    public static BufferedImage grid(BufferedImage src, double step, double scale) {
        BufferedImage out = copy(src);
        Graphics2D g = graphics(out);
        try {
            double px = step * scale;
            int i = 0;
            for (double x = 0; x < out.getWidth(); x += px, i++) {
                g.setColor(i % 5 == 0 ? GRID_MAJOR : GRID_MINOR);
                g.setStroke(new BasicStroke(1));
                g.draw(new Line2D.Double(x, 0, x, out.getHeight()));
            }
            i = 0;
            for (double y = 0; y < out.getHeight(); y += px, i++) {
                g.setColor(i % 5 == 0 ? GRID_MAJOR : GRID_MINOR);
                g.draw(new Line2D.Double(0, y, out.getWidth(), y));
            }
            // label the major lines so the model can name a coordinate
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, (int) Math.max(9, 10 * scale)));
            g.setColor(new Color(0, 60, 160, 180));
            i = 0;
            for (double x = 0; x < out.getWidth(); x += px * 5, i++) {
                g.drawString(String.valueOf((int) Math.round(x / scale)), (float) x + 2, 12f * (float) scale);
            }
            for (double y = px * 5; y < out.getHeight(); y += px * 5) {
                g.drawString(String.valueOf((int) Math.round(y / scale)), 2f, (float) y - 2);
            }
        } finally {
            g.dispose();
        }
        return out;
    }

    /**
     * Set-of-Mark: outline each element and stamp its id, so a JSON list of exact
     * rectangles can be cross-referenced with what is on screen. "#14 sits 2px left of
     * #12" becomes a fact the model reads rather than a judgement it makes.
     */
    public static BufferedImage setOfMark(BufferedImage src, List<Elem> elements, double scale) {
        BufferedImage out = copy(src);
        Graphics2D g = graphics(out);
        try {
            g.setStroke(new BasicStroke((float) Math.max(1, scale)));
            int fontSize = (int) Math.max(10, 11 * scale);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
            for (Elem e : elements) {
                Rect r = e.rect();
                int x = (int) Math.round(r.x() * scale), y = (int) Math.round(r.y() * scale);
                int w = (int) Math.round(r.w() * scale), h = (int) Math.round(r.h() * scale);
                g.setColor(BOX);
                g.drawRect(x, y, Math.max(1, w), Math.max(1, h));

                String label = String.valueOf(e.id);
                int tw = g.getFontMetrics().stringWidth(label) + 6;
                int th = fontSize + 4;
                g.setColor(BADGE);
                g.fillRect(x, Math.max(0, y - th), tw, th);
                g.setColor(Color.WHITE);
                g.drawString(label, x + 3, Math.max(0, y - th) + fontSize + 1);
            }
        } finally {
            g.dispose();
        }
        return out;
    }

    /** Numbered gaze path over the page: where the eye goes, in order. */
    public static BufferedImage scanpath(BufferedImage src, List<Saliency.Fixation> path, double scale) {
        BufferedImage out = copy(src);
        Graphics2D g = graphics(out);
        try {
            double radius = Math.max(16, 22 * scale);
            g.setStroke(new BasicStroke((float) Math.max(2, 2.5 * scale)));
            Saliency.Fixation prev = null;
            for (Saliency.Fixation f : path) {
                if (prev != null) {
                    g.setColor(new Color(255, 200, 0, 200));
                    g.draw(new Line2D.Double(prev.x(), prev.y(), f.x(), f.y()));
                }
                prev = f;
            }
            int fontSize = (int) Math.max(12, 16 * scale);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
            for (Saliency.Fixation f : path) {
                g.setColor(new Color(255, 60, 0, 190));
                g.fillOval((int) (f.x() - radius), (int) (f.y() - radius), (int) (radius * 2), (int) (radius * 2));
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke((float) Math.max(2, 2 * scale)));
                g.drawOval((int) (f.x() - radius), (int) (f.y() - radius), (int) (radius * 2), (int) (radius * 2));
                String n = String.valueOf(f.order());
                int tw = g.getFontMetrics().stringWidth(n);
                g.drawString(n, (float) (f.x() - tw / 2.0), (float) (f.y() + fontSize / 3.0));
            }
        } finally {
            g.dispose();
        }
        return out;
    }

    /** Highlights each finding's focus region, tinted by severity. */
    public static BufferedImage findings(BufferedImage src, List<Finding> findings, double scale) {
        BufferedImage out = copy(src);
        Graphics2D g = graphics(out);
        try {
            int fontSize = (int) Math.max(10, 12 * scale);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
            for (Finding f : findings) {
                if (f.focus == null) continue;
                Color c = colorFor(f.severity);
                Rect r = f.focus;
                int x = (int) Math.round(r.x() * scale), y = (int) Math.round(r.y() * scale);
                int w = (int) Math.max(1, Math.round(r.w() * scale)), h = (int) Math.max(1, Math.round(r.h() * scale));
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.18f));
                g.setColor(c);
                g.fillRect(x, y, w, h);
                g.setComposite(AlphaComposite.SrcOver);
                g.setStroke(new BasicStroke((float) Math.max(2, 2 * scale)));
                g.setColor(c);
                g.drawRect(x, y, w, h);

                String label = f.ruleId;
                int tw = g.getFontMetrics().stringWidth(label) + 8;
                int th = fontSize + 6;
                g.fillRect(x, Math.max(0, y - th), tw, th);
                g.setColor(Color.WHITE);
                g.drawString(label, x + 4, Math.max(0, y - th) + fontSize + 2);
            }
        } finally {
            g.dispose();
        }
        return out;
    }

    /** Saliency as a heat map blended over the screenshot. */
    public static BufferedImage saliencyOver(BufferedImage src, Saliency saliency, float strength) {
        BufferedImage heat = saliency.map().toHeatMap();
        BufferedImage out = copy(src);
        Graphics2D g = graphics(out);
        try {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, strength));
            g.drawImage(heat, 0, 0, out.getWidth(), out.getHeight(), null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static Color colorFor(Severity s) {
        return switch (s) {
            case HIGH -> new Color(220, 20, 40);
            case MEDIUM -> new Color(240, 130, 0);
            case LOW -> new Color(200, 180, 0);
            case INFO -> new Color(60, 140, 220);
        };
    }

    private static BufferedImage copy(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    private static Graphics2D graphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }
}
