package com.calpano.uxlens.perception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The FFT underneath the saliency map")
class FftTest {

    @Test
    void a_forward_then_inverse_transform_returns_the_input() {
        int n = 64;
        Random random = new Random(7);
        double[] re = new double[n], im = new double[n];
        double[] originalRe = new double[n];
        for (int i = 0; i < n; i++) originalRe[i] = re[i] = random.nextDouble();

        Fft.transform1d(re, im, false);
        Fft.transform1d(re, im, true);
        for (int i = 0; i < n; i++) {
            assertEquals(originalRe[i], re[i], 1e-9, "sample " + i);
            assertEquals(0.0, im[i], 1e-9, "imaginary part of sample " + i);
        }
    }

    @Test
    void the_zero_frequency_bin_holds_the_sum() {
        double[] re = {1, 2, 3, 4}, im = new double[4];
        Fft.transform1d(re, im, false);
        assertEquals(10.0, re[0], 1e-9);
    }

    @Test
    void a_pure_sine_concentrates_in_one_bin() {
        int n = 32;
        double[] re = new double[n], im = new double[n];
        for (int i = 0; i < n; i++) re[i] = Math.sin(2 * Math.PI * 4 * i / n);
        Fft.transform1d(re, im, false);
        double atFour = Math.hypot(re[4], im[4]);
        double atFive = Math.hypot(re[5], im[5]);
        assertTrue(atFour > 10 * Math.max(atFive, 1e-12),
                "expected energy at bin 4, got " + atFour + " vs " + atFive);
    }

    @Test
    void two_dimensional_transform_round_trips() {
        int n = 16;
        double[][] re = new double[n][n], im = new double[n][n];
        double[][] original = new double[n][n];
        Random random = new Random(11);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) original[y][x] = re[y][x] = random.nextDouble();
        }
        Fft.transform2d(re, im, false);
        Fft.transform2d(re, im, true);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) assertEquals(original[y][x], re[y][x], 1e-9);
        }
    }

    @Test
    void a_non_power_of_two_length_is_rejected_with_a_clear_message() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Fft.transform1d(new double[6], new double[6], false));
        assertTrue(e.getMessage().contains("power of two"), e.getMessage());
    }
}
