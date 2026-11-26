package com.calpano.uxlens.perception;

/**
 * Minimal in-place complex FFT, radix-2. Sizes must be powers of two; the saliency
 * pipeline works at 64x64, so that is never a constraint in practice.
 */
final class Fft {

    private Fft() { }

    static boolean isPowerOfTwo(int n) { return n > 0 && (n & (n - 1)) == 0; }

    /** 1-D transform. {@code inverse} applies the 1/n scaling. */
    static void transform1d(double[] re, double[] im, boolean inverse) {
        int n = re.length;
        if (!isPowerOfTwo(n)) throw new IllegalArgumentException("FFT length must be a power of two: " + n);

        for (int i = 1, j = 0; i < n; i++) {           // bit-reversal permutation
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double tr = re[i]; re[i] = re[j]; re[j] = tr;
                double ti = im[i]; im[i] = im[j]; im[j] = ti;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = 2 * Math.PI / len * (inverse ? 1 : -1);
            double wr = Math.cos(ang), wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cr = 1, ci = 0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k, b = i + k + len / 2;
                    double xr = re[b] * cr - im[b] * ci;
                    double xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr; im[b] = im[a] - xi;
                    re[a] += xr;        im[a] += xi;
                    double ncr = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = ncr;
                }
            }
        }
        if (inverse) for (int i = 0; i < n; i++) { re[i] /= n; im[i] /= n; }
    }

    /** 2-D transform, rows then columns, in place on {@code [y][x]} arrays. */
    static void transform2d(double[][] re, double[][] im, boolean inverse) {
        int h = re.length, w = re[0].length;
        for (int y = 0; y < h; y++) transform1d(re[y], im[y], inverse);
        double[] cr = new double[h], ci = new double[h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) { cr[y] = re[y][x]; ci[y] = im[y][x]; }
            transform1d(cr, ci, inverse);
            for (int y = 0; y < h; y++) { re[y][x] = cr[y]; im[y][x] = ci[y]; }
        }
    }
}
