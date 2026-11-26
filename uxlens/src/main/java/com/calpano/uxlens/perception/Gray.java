package com.calpano.uxlens.perception;

import java.awt.image.BufferedImage;

/**
 * A single-channel image as {@code double} in 0..1, with the few operations the
 * perceptual pipeline needs. Row-major, {@code v[y][x]}.
 *
 * <p>Deliberately dependency-free: an image-processing library would dwarf the
 * rest of this project for the sake of four functions.
 */
public final class Gray {

    public final int width, height;
    public final double[][] v;

    public Gray(int width, int height) {
        this.width = width;
        this.height = height;
        this.v = new double[height][width];
    }

    /**
     * Gamma-encoded luma of each pixel: the Rec.709 weights applied to the sRGB values as
     * stored, <em>without</em> linearising them.
     *
     * <p>This is deliberately not WCAG relative luminance. Luma tracks perceived brightness,
     * which is what the saliency and squint passes want; relative luminance is a physical
     * quantity and is what contrast ratios need. The two differ by a lot -- a mid blue is
     * 0.29 as luma and 0.10 as relative luminance -- so never compare a value from here
     * against {@link com.calpano.uxlens.model.Rgba#relativeLuminance()}. Use
     * {@link com.calpano.uxlens.model.Rgba#luma()} to get a comparable reference.
     */
    public static Gray luminanceOf(BufferedImage img) {
        Gray g = new Gray(img.getWidth(), img.getHeight());
        for (int y = 0; y < g.height; y++) {
            for (int x = 0; x < g.width; x++) {
                int rgb = img.getRGB(x, y);
                double r = ((rgb >> 16) & 0xff) / 255.0;
                double gg = ((rgb >> 8) & 0xff) / 255.0;
                double b = (rgb & 0xff) / 255.0;
                g.v[y][x] = 0.2126 * r + 0.7152 * gg + 0.0722 * b;
            }
        }
        return g;
    }

    public double at(int x, int y) {
        return v[Math.max(0, Math.min(height - 1, y))][Math.max(0, Math.min(width - 1, x))];
    }

    /** Bilinear resample. */
    public Gray resizeTo(int nw, int nh) {
        Gray out = new Gray(nw, nh);
        double sx = (double) width / nw, sy = (double) height / nh;
        for (int y = 0; y < nh; y++) {
            double fy = (y + 0.5) * sy - 0.5;
            int y0 = (int) Math.floor(fy);
            double wy = fy - y0;
            for (int x = 0; x < nw; x++) {
                double fx = (x + 0.5) * sx - 0.5;
                int x0 = (int) Math.floor(fx);
                double wx = fx - x0;
                out.v[y][x] = at(x0, y0) * (1 - wx) * (1 - wy)
                        + at(x0 + 1, y0) * wx * (1 - wy)
                        + at(x0, y0 + 1) * (1 - wx) * wy
                        + at(x0 + 1, y0 + 1) * wx * wy;
            }
        }
        return out;
    }

    /** Separable Gaussian blur. {@code sigma} in pixels. */
    public Gray blur(double sigma) {
        if (sigma <= 0) return copy();
        int r = Math.max(1, (int) Math.ceil(sigma * 3));
        double[] k = new double[2 * r + 1];
        double sum = 0;
        for (int i = -r; i <= r; i++) {
            k[i + r] = Math.exp(-(i * i) / (2 * sigma * sigma));
            sum += k[i + r];
        }
        for (int i = 0; i < k.length; i++) k[i] /= sum;

        Gray tmp = new Gray(width, height), out = new Gray(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double acc = 0;
                for (int i = -r; i <= r; i++) acc += at(x + i, y) * k[i + r];
                tmp.v[y][x] = acc;
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double acc = 0;
                for (int i = -r; i <= r; i++) acc += tmp.at(x, y + i) * k[i + r];
                out.v[y][x] = acc;
            }
        }
        return out;
    }

    /** Rescales values so the minimum is 0 and the maximum 1. A flat image becomes all zeros. */
    public Gray normalized() {
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (double[] row : v) for (double d : row) { min = Math.min(min, d); max = Math.max(max, d); }
        Gray out = new Gray(width, height);
        double span = max - min;
        if (span < 1e-12) return out;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) out.v[y][x] = (v[y][x] - min) / span;
        return out;
    }

    public Gray copy() {
        Gray out = new Gray(width, height);
        for (int y = 0; y < height; y++) System.arraycopy(v[y], 0, out.v[y], 0, width);
        return out;
    }

    /** Mean value inside a box, clipped to the image. Returns 0 for an empty box. */
    public double meanIn(int x0, int y0, int w, int h) {
        int xs = Math.max(0, x0), ys = Math.max(0, y0);
        int xe = Math.min(width, x0 + w), ye = Math.min(height, y0 + h);
        if (xe <= xs || ye <= ys) return 0;
        double sum = 0;
        for (int y = ys; y < ye; y++) for (int x = xs; x < xe; x++) sum += v[y][x];
        return sum / ((xe - xs) * (double) (ye - ys));
    }

    public double maxIn(int x0, int y0, int w, int h) {
        int xs = Math.max(0, x0), ys = Math.max(0, y0);
        int xe = Math.min(width, x0 + w), ye = Math.min(height, y0 + h);
        double max = 0;
        for (int y = ys; y < ye; y++) for (int x = xs; x < xe; x++) max = Math.max(max, v[y][x]);
        return max;
    }

    public double sum() {
        double s = 0;
        for (double[] row : v) for (double d : row) s += d;
        return s;
    }

    /** Renders as an 8-bit grayscale image. */
    public BufferedImage toImage() {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int g = (int) Math.round(Math.max(0, Math.min(1, v[y][x])) * 255);
                img.setRGB(x, y, (g << 16) | (g << 8) | g);
            }
        }
        return img;
    }

    /** Renders as a heat map (blue -> cyan -> yellow -> red), for saliency overlays. */
    public BufferedImage toHeatMap() {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                img.setRGB(x, y, heat(Math.max(0, Math.min(1, v[y][x]))));
            }
        }
        return img;
    }

    private static int heat(double t) {
        double r = Math.max(0, Math.min(1, 1.5 - Math.abs(4 * t - 3)));
        double g = Math.max(0, Math.min(1, 1.5 - Math.abs(4 * t - 2)));
        double b = Math.max(0, Math.min(1, 1.5 - Math.abs(4 * t - 1)));
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }
}
