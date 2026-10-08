package com.cloudsim.lpt;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Membuat dataset sintetik dan menyimpannya ke data/synthetic/Synthetic_<n>.txt
 * (satu angka per baris = panjang task dalam MI), agar data yang sama dipakai
 * pada setiap run.
 *
 * Komposisi task: 30% kecil, 40% sedang, dan 30% besar.
 * Kecil: 1.000-10.000 MI; sedang: 10.001-30.000 MI;
 * besar: 30.001-50.000 MI.
 */
public class DatasetGenerator {

    public static final String SYNTHETIC_DIR = "data/synthetic";

    public static final long SMALL_MIN = 1_000L;
    public static final long SMALL_MAX = 10_000L;
    public static final long MEDIUM_MIN = 10_001L;
    public static final long MEDIUM_MAX = 30_000L;
    public static final long LARGE_MIN = 30_001L;
    public static final long LARGE_MAX = 50_000L;

    public static final double SMALL_SHARE = 0.30;
    public static final double MEDIUM_SHARE = 0.40;

    private static final long SEED = 42L;

    /**
     * Returns the synthetic dataset path. If the file already exists, it is
     * reused unchanged so runs remain reproducible.
     */
    public static String ensure(int taskCount) throws IOException {
        File dir = new File(SYNTHETIC_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Tidak dapat membuat direktori " + SYNTHETIC_DIR);
        }

        File file = new File(dir, "Synthetic_" + taskCount + ".txt");
        if (!file.exists()) {
            List<Long> lengths = generate(taskCount);
            try (PrintWriter writer = new PrintWriter(file, "UTF-8")) {
                for (long length : lengths) {
                    writer.println(length);
                }
            }
        }
        return file.getPath();
    }

    static List<Long> generate(int n) {
        Random random = new Random(SEED + n);
        int nSmall = (int) Math.round(n * SMALL_SHARE);
        int nMedium = (int) Math.round(n * MEDIUM_SHARE);
        int nLarge = n - nSmall - nMedium;

        List<Long> lengths = new ArrayList<>();
        addRandom(lengths, random, nSmall, SMALL_MIN, SMALL_MAX);
        addRandom(lengths, random, nMedium, MEDIUM_MIN, MEDIUM_MAX);
        addRandom(lengths, random, nLarge, LARGE_MIN, LARGE_MAX);
        Collections.shuffle(lengths, random);
        return lengths;
    }

    private static void addRandom(
            List<Long> list, Random random, int count, long min, long max) {
        for (int i = 0; i < count; i++) {
            list.add(min + (long) (random.nextDouble() * (max - min + 1)));
        }
    }
}