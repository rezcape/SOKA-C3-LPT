package com.cloudsim.lpt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Statistik panjang task (MI) satu dataset, termasuk persentase task pendek,
 * sedang, dan panjang berdasarkan dua batas yang ditentukan pemanggil.
 */
public class DatasetStats {

    public final int count;
    public final long min;
    public final long p25;
    public final long median;
    public final long p75;
    public final long p90;
    public final long max;
    public final long total;
    public final double mean;
    public final double pctShort;
    public final double pctMedium;
    public final double pctLong;

    private DatasetStats(
            int count,
            long min,
            long p25,
            long median,
            long p75,
            long p90,
            long max,
            long total,
            double mean,
            double pctShort,
            double pctMedium,
            double pctLong) {
        this.count = count;
        this.min = min;
        this.p25 = p25;
        this.median = median;
        this.p75 = p75;
        this.p90 = p90;
        this.max = max;
        this.total = total;
        this.mean = mean;
        this.pctShort = pctShort;
        this.pctMedium = pctMedium;
        this.pctLong = pctLong;
    }

    public static DatasetStats of(List<Long> lengths, long shortMax, long mediumMax) {
        if (lengths.isEmpty()) {
            throw new IllegalArgumentException("Dataset kosong");
        }
        List<Long> sorted = new ArrayList<>(lengths);
        Collections.sort(sorted);
        int n = sorted.size();

        long total = 0;
        int nShort = 0;
        int nMedium = 0;
        int nLong = 0;
        for (long value : sorted) {
            total += value;
            if (value <= shortMax) {
                nShort++;
            } else if (value <= mediumMax) {
                nMedium++;
            } else {
                nLong++;
            }
        }

        return new DatasetStats(
                n,
                sorted.get(0),
                percentile(sorted, 0.25),
                percentile(sorted, 0.50),
                percentile(sorted, 0.75),
                percentile(sorted, 0.90),
                sorted.get(n - 1),
                total,
                (double) total / n,
                100.0 * nShort / n,
                100.0 * nMedium / n,
                100.0 * nLong / n);
    }

    private static long percentile(List<Long> sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        index = Math.max(0, Math.min(sorted.size() - 1, index));
        return sorted.get(index);
    }
}