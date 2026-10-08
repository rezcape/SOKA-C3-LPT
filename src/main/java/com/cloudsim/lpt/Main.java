package com.cloudsim.lpt;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;


/**
 * Menjalankan LPT untuk:
 *   - semua file data/GoCJ_Dataset_*.txt
 *   - dataset sintetik 1000, 2000, ..., 10000 task (komposisi 30/40/30)
 * Tiap dataset dijalankan REPEATS kali, lalu dirata-rata dan dicek apakah hasilnya sama.
 *
 * Keluaran (folder results/):
 *   dataset_stats.csv     statistik panjang task + persen pendek/sedang/panjang
 *   hasil_lpt.csv         rata-rata per dataset
 *   hasil_lpt_runs.csv    hasil tiap run (bukti)
 *   host_vm_placement.csv jumlah VM per host
 *   vm_placement_detail.csv tiap VM ditaruh di host mana + core/RAM/BW terpakai per host
 *   task_category_counts.csv jumlah task kecil/sedang/besar per dataset
 */
public class Main {

    private static final int REPEATS = 3;
    private static final int[] SYNTHETIC_SIZES =
            {1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000, 10000};

    // Untuk membuktikan alokasi VM ke host sama di setiap run
    private static Map<Integer, Integer> referencePlacement;
    private static boolean placementAlwaysSame = true;

    // Batas kelas panjang task GoCJ (MI): pendek <= 100.000 < sedang <= 350.000 < panjang.
    // Ini definisi kita; cek dataset_stats.csv (p25/median/p75/p90) lalu sesuaikan bila perlu.
    private static final long GOCJ_SHORT_MAX = 100_000L;
    private static final long GOCJ_MEDIUM_MAX = 350_000L;

    private static final String DATA_DIR = "data";
    private static final String RESULT_DIR = "results";

    private static final String K_MAKESPAN = "Makespan (s)";
    private static final String K_COST = "Total Cost (G$)";
    private static final String K_UTIL = "Resource Utilization (%)";
    private static final String K_IMB = "Degree of Imbalance";
    private static final String K_THR = "Throughput (task/s)";
    private static final String K_SCHED = "Scheduling (ms)";
    private static final String K_DONE = "Cloudlet selesai";
    private static final String K_TASKS = "Tasks";
    private static final String K_PCT_C = "Pct Compute";
    private static final String K_PCT_S = "Pct Standard";
    private static final String K_PCT_L = "Pct Light";

    private static class Dataset {
        final String name, source, path;
        final long shortMax, mediumMax;

        Dataset(String name, String source, String path, long shortMax, long mediumMax) {
            this.name = name;
            this.source = source;
            this.path = path;
            this.shortMax = shortMax;
            this.mediumMax = mediumMax;
        }
    }

    public static void main(String[] args) throws Exception {
        Log.disable(); // matikan log CloudSim yang panjang
        new File(RESULT_DIR).mkdirs();

        List<Dataset> datasets = collectDatasets();
        if (datasets.isEmpty()) {
            System.out.println("Tidak ada dataset. Taruh GoCJ_Dataset_*.txt di folder " + DATA_DIR);
            return;
        }

        printAndSaveStats(datasets);
        printAndSaveTaskCounts(datasets);

        System.out.println("\n===== HASIL LPT (rata-rata " + REPEATS + " run per dataset) =====");
        System.out.printf("%-22s %-12s %6s %12s %16s %9s %10s %12s %10s %7s%n",
                "Dataset", "Beban", "Task", "Makespan(s)", "Cost(G$)", "Util(%)",
                "Imbalance", "Thrput(t/s)", "Sched(ms)", "Sama?");

        try (PrintWriter mean = new PrintWriter(RESULT_DIR + "/hasil_lpt.csv", "UTF-8");
             PrintWriter runs = new PrintWriter(RESULT_DIR + "/hasil_lpt_runs.csv", "UTF-8")) {

            mean.println("dataset,source,workload,tasks,runs,makespan_s,total_cost_gd,"
                    + "utilization_pct,imbalance,throughput_tps,scheduling_ms_mean,"
                    + "scheduling_ms_std,pct_tasks_compute,pct_tasks_standard,pct_tasks_light,"
                    + "identical_across_runs");
            runs.println("dataset,run,makespan_s,total_cost_gd,utilization_pct,imbalance,"
                    + "throughput_tps,scheduling_ms");

            for (Dataset d : datasets) {
                List<Map<String, Double>> results = new ArrayList<>();
                boolean failed = false;

                for (int r = 1; r <= REPEATS; r++) {
                    Map<String, Double> m = runOne(d.path);
                    checkPlacement();
                    if (!m.containsKey(K_MAKESPAN)) {
                        failed = true;
                        break;
                    }
                    results.add(m);
                    runs.println(String.format(Locale.US, "%s,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f",
                            d.name, r, m.get(K_MAKESPAN), m.get(K_COST), m.get(K_UTIL),
                            m.get(K_IMB), m.get(K_THR), m.get(K_SCHED)));
                }
                if (failed) {
                    System.out.println(d.name + ": GAGAL (metrik kosong)");
                    continue;
                }

                int tasks = results.get(0).get(K_TASKS).intValue();
                int done = results.get(0).get(K_DONE).intValue();
                if (done != tasks) {
                    System.out.println("PERINGATAN: " + d.name + " hanya selesai " + done
                            + " dari " + tasks);
                }

                // Metrik deterministik harus sama di semua run (Scheduling ms boleh beda)
                boolean same = sameInAllRuns(results, K_MAKESPAN, K_COST, K_UTIL, K_IMB, K_THR);
                String workload = workloadOf(tasks);

                System.out.printf(Locale.US, "%-22s %-12s %6d %12.2f %16.2f %9.2f %10.3f %12.3f %10.3f %7s%n",
                        d.name, workload, tasks, mean(results, K_MAKESPAN), mean(results, K_COST),
                        mean(results, K_UTIL), mean(results, K_IMB), mean(results, K_THR),
                        mean(results, K_SCHED), same ? "Ya" : "TIDAK");

                mean.println(String.format(Locale.US,
                        "%s,%s,%s,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.2f,%.2f,%.2f,%s",
                        d.name, d.source, workload, tasks, REPEATS,
                        mean(results, K_MAKESPAN), mean(results, K_COST), mean(results, K_UTIL),
                        mean(results, K_IMB), mean(results, K_THR),
                        mean(results, K_SCHED), std(results, K_SCHED),
                        mean(results, K_PCT_C), mean(results, K_PCT_S), mean(results, K_PCT_L),
                        same ? "yes" : "no"));
            }
        }

        printAndSavePlacement();
        printAndSavePlacementDetail();

        System.out.println("\nFile hasil ada di folder " + RESULT_DIR + "/ :");
        System.out.println("  dataset_stats.csv, hasil_lpt.csv, hasil_lpt_runs.csv, host_vm_placement.csv");
        System.out.println("  vm_placement_detail.csv, task_category_counts.csv");
        System.out.println("Dataset sintetik tersimpan di " + DatasetGenerator.SYNTHETIC_DIR + "/");
    }

    /** Satu simulasi lengkap untuk satu file dataset. */
    private static Map<String, Double> runOne(String dataFile) throws Exception {
        CloudSim.init(1, Calendar.getInstance(), false);

        new DatacenterFactory().createDatacenter();
        DatacenterBroker broker = new DatacenterBroker("Broker");

        List<Vm> vms = new VmFactory().createVms(broker);
        List<Cloudlet> cloudlets = new CloudletGenerator().generateCloudlets(dataFile);
        for (Cloudlet c : cloudlets) {
            c.setUserId(broker.getId());
        }

        // Penjadwalan LPT, waktunya diukur sebagai "Convergence Speed"
        LptScheduler scheduler = new LptScheduler();
        long t0 = System.nanoTime();
        scheduler.scheduleLPT(cloudlets, vms);
        double schedulingMs = (System.nanoTime() - t0) / 1_000_000.0;

        // Kirim ke broker dengan urutan LPT (terpanjang dulu), supaya urutan antrean
        // di tiap VM sama dengan urutan yang dihitung penjadwal.
        cloudlets.sort((a, b) -> Long.compare(b.getCloudletLength(), a.getCloudletLength()));

        broker.submitVmList(vms);
        broker.submitCloudletList(cloudlets);

        CloudSim.startSimulation();
        CloudSim.stopSimulation();

        List<Cloudlet> finished = broker.getCloudletReceivedList();
        Map<String, Double> metrics =
                new LinkedHashMap<>(scheduler.calculateMetrics(finished, vms));
        metrics.put(K_TASKS, (double) cloudlets.size());
        metrics.put(K_SCHED, schedulingMs);

        // Berapa persen task yang mendarat di tiap jenis VM (untuk analisis cost)
        int nCompute = 0, nStandard = 0, nLight = 0;
        for (Cloudlet c : finished) {
            String type = VmFactory.typeOf(c.getVmId());
            if ("Compute-Intensive".equals(type)) nCompute++;
            else if ("Standard".equals(type)) nStandard++;
            else nLight++;
        }
        double n = Math.max(1, finished.size());
        metrics.put(K_PCT_C, 100.0 * nCompute / n);
        metrics.put(K_PCT_S, 100.0 * nStandard / n);
        metrics.put(K_PCT_L, 100.0 * nLight / n);
        return metrics;
    }

    // ---------- Dataset ----------

    private static List<Dataset> collectDatasets() throws IOException {
        List<Dataset> list = new ArrayList<>();

        File[] gocj = new File(DATA_DIR).listFiles(
                (dir, name) -> name.matches("GoCJ_Dataset_\\d+\\.txt"));
        if (gocj != null) {
            Arrays.sort(gocj, Comparator.comparingInt((File f) -> numberOf(f.getName())));
            for (File f : gocj) {
                list.add(new Dataset(f.getName().replace(".txt", ""), "GoCJ", f.getPath(),
                        GOCJ_SHORT_MAX, GOCJ_MEDIUM_MAX));
            }
        }
        for (int n : SYNTHETIC_SIZES) {
            list.add(new Dataset("Synthetic_" + n, "Sintetik", DatasetGenerator.ensure(n),
                    DatasetGenerator.SMALL_MAX, DatasetGenerator.MEDIUM_MAX));
        }
        return list;
    }

    private static void printAndSaveStats(List<Dataset> datasets) throws IOException {
        System.out.println("===== STATISTIK DATASET (persen task pendek / sedang / panjang) =====");
        System.out.printf("%-22s %-9s %6s %9s %9s %9s %10s %8s %8s %8s%n",
                "Dataset", "Sumber", "Task", "Min(MI)", "Median", "Max(MI)", "Mean",
                "%Pendek", "%Sedang", "%Panjang");

        try (PrintWriter csv = new PrintWriter(RESULT_DIR + "/dataset_stats.csv", "UTF-8")) {
            csv.println("dataset,source,tasks,min_mi,p25_mi,median_mi,p75_mi,p90_mi,max_mi,"
                    + "mean_mi,total_mi,short_max_mi,medium_max_mi,pct_short,pct_medium,pct_long");
            for (Dataset d : datasets) {
                DatasetStats s = DatasetStats.of(
                        CloudletGenerator.readLengths(d.path), d.shortMax, d.mediumMax);
                System.out.printf(Locale.US, "%-22s %-9s %6d %9d %9d %9d %10.0f %8.1f %8.1f %8.1f%n",
                        d.name, d.source, s.count, s.min, s.median, s.max, s.mean,
                        s.pctShort, s.pctMedium, s.pctLong);
                csv.println(String.format(Locale.US,
                        "%s,%s,%d,%d,%d,%d,%d,%d,%d,%.2f,%d,%d,%d,%.2f,%.2f,%.2f",
                        d.name, d.source, s.count, s.min, s.p25, s.median, s.p75, s.p90, s.max,
                        s.mean, s.total, d.shortMax, d.mediumMax,
                        s.pctShort, s.pctMedium, s.pctLong));
            }
        }
    }

    /** Jumlah task kecil / sedang / besar di tiap dataset (bukan hanya persen). */
    private static void printAndSaveTaskCounts(List<Dataset> datasets) throws IOException {
        System.out.println("\n===== JUMLAH TASK PER KATEGORI (kecil / sedang / besar) =====");
        System.out.printf("%-22s %6s %8s %8s %8s   %s%n",
                "Dataset", "Task", "Kecil", "Sedang", "Besar", "Batas kategori (MI)");

        try (PrintWriter csv = new PrintWriter(RESULT_DIR + "/task_category_counts.csv", "UTF-8")) {
            csv.println("dataset,source,tasks,small_max_mi,medium_max_mi,n_small,n_medium,n_large");
            for (Dataset d : datasets) {
                List<Long> lengths = CloudletGenerator.readLengths(d.path);
                int nSmall = 0, nMedium = 0, nLarge = 0;
                for (long len : lengths) {
                    if (len <= d.shortMax) nSmall++;
                    else if (len <= d.mediumMax) nMedium++;
                    else nLarge++;
                }
                System.out.printf("%-22s %6d %8d %8d %8d   kecil <= %,d < sedang <= %,d < besar%n",
                        d.name, lengths.size(), nSmall, nMedium, nLarge, d.shortMax, d.mediumMax);
                csv.println(d.name + "," + d.source + "," + lengths.size() + "," + d.shortMax + ","
                        + d.mediumMax + "," + nSmall + "," + nMedium + "," + nLarge);
            }
        }
    }

    // ---------- Penempatan VM ke host ----------

    private static void printAndSavePlacement() throws IOException {
        Map<Integer, Integer> placement = DatacenterFactory.PLACEMENT; // sama di semua run
        int[][] count = new int[10][3]; // [host][0=Compute, 1=Standard, 2=Light]
        for (Map.Entry<Integer, Integer> e : placement.entrySet()) {
            String type = VmFactory.typeOf(e.getKey());
            int col = "Compute-Intensive".equals(type) ? 0 : "Standard".equals(type) ? 1 : 2;
            count[e.getValue()][col]++;
        }

        System.out.println("\n===== PENEMPATAN VM KE HOST (" + placement.size() + " dari 40 VM) =====");
        System.out.printf("%-6s %-11s %6s %9s %9s %7s%n",
                "Host", "Grade", "Total", "Compute", "Standard", "Light");
        try (PrintWriter csv = new PrintWriter(RESULT_DIR + "/host_vm_placement.csv", "UTF-8")) {
            csv.println("host_id,grade,total_vm,compute_intensive,standard,light_weight");
            for (int h = 0; h < 10; h++) {
                int total = count[h][0] + count[h][1] + count[h][2];
                System.out.printf("%-6d %-11s %6d %9d %9d %7d%n", h,
                        DatacenterFactory.gradeOf(h), total, count[h][0], count[h][1], count[h][2]);
                csv.println(h + "," + DatacenterFactory.gradeOf(h) + "," + total + ","
                        + count[h][0] + "," + count[h][1] + "," + count[h][2]);
            }
        }
    }

    /** Bandingkan alokasi VM run ini dengan run pertama. */
    private static void checkPlacement() {
        Map<Integer, Integer> now = new java.util.TreeMap<>(DatacenterFactory.PLACEMENT);
        if (referencePlacement == null) {
            referencePlacement = now;
        } else if (!referencePlacement.equals(now)) {
            placementAlwaysSame = false;
        }
    }

    /** ID VM tiap host + core/RAM/BW terpakai, dan bukti alokasi sama di semua run. */
    private static void printAndSavePlacementDetail() throws IOException {
        int[][] used = new int[10][3]; // [host][0=core, 1=RAM MB, 2=BW Mbps]
        List<List<Integer>> vmsOnHost = new ArrayList<>();
        for (int h = 0; h < 10; h++) vmsOnHost.add(new ArrayList<>());

        try (PrintWriter csv = new PrintWriter(RESULT_DIR + "/vm_placement_detail.csv", "UTF-8")) {
            csv.println("vm_id,vm_type,vcpu,mips_per_vcpu,ram_mb,bw_mbps,cost_per_s,host_id,host_grade");
            for (Map.Entry<Integer, Integer> e : referencePlacement.entrySet()) {
                int vm = e.getKey(), h = e.getValue();
                int[] spec = VmFactory.specOf(vm);
                used[h][0] += spec[0];
                used[h][1] += spec[2];
                used[h][2] += spec[3];
                vmsOnHost.get(h).add(vm);
                csv.println(vm + "," + VmFactory.typeOf(vm) + "," + spec[0] + "," + spec[1] + ","
                        + spec[2] + "," + spec[3] + "," + (int) VmFactory.costPerSecond(vm) + ","
                        + h + "," + DatacenterFactory.gradeOf(h));
            }
        }

        System.out.println("\n===== DETAIL ALOKASI VM KE HOST =====");
        System.out.println("Kebijakan: VmAllocationPolicySimple (host dengan core bebas terbanyak)");
        System.out.printf("%-6s %8s %9s %12s  %s%n", "Host", "Core", "RAM(GB)", "BW(Mbps)", "ID VM");
        for (int h = 0; h < 10; h++) {
            int[] hs = DatacenterFactory.specOf(h);
            String ids = vmsOnHost.get(h).toString().replaceAll("[\\[\\],]", "");
            System.out.printf("%-6d %8s %9s %12s  %s%n", h,
                    used[h][0] + "/" + hs[0], (used[h][1] / 1024) + "/" + (hs[2] / 1024),
                    used[h][2] + "/" + hs[3], ids);
        }
        System.out.println("Alokasi VM ke host sama di semua run & dataset? "
                + (placementAlwaysSame ? "Ya" : "TIDAK"));
    }

    // ---------- Helper ----------

    /** Label beban kerja berdasarkan jumlah task (definisi kita, bisa disesuaikan). */
    private static String workloadOf(int tasks) {
        if (tasks <= 300) return "Ringan";
        if (tasks <= 700) return "Sedang";
        if (tasks <= 1000) return "Berat";
        return "Sangat berat";
    }

    private static int numberOf(String fileName) {
        return Integer.parseInt(fileName.replaceAll("\\D", ""));
    }

    private static double mean(List<Map<String, Double>> rs, String key) {
        double sum = 0;
        for (Map<String, Double> r : rs) sum += r.get(key);
        return sum / rs.size();
    }

    private static double std(List<Map<String, Double>> rs, String key) {
        double mu = mean(rs, key), sum = 0;
        for (Map<String, Double> r : rs) sum += (r.get(key) - mu) * (r.get(key) - mu);
        return Math.sqrt(sum / rs.size());
    }

    private static boolean sameInAllRuns(List<Map<String, Double>> rs, String... keys) {
        for (String key : keys) {
            double first = rs.get(0).get(key);
            for (Map<String, Double> r : rs) {
                if (Math.abs(r.get(key) - first) > 1e-6) return false;
            }
        }
        return true;
    }
}