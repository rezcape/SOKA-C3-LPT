package com.cloudsim.lpt;

import java.util.ArrayList;
import java.util.List;
import org.cloudbus.cloudsim.CloudletSchedulerSpaceShared;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.Vm;

public class VmFactory {

    // Pembagian id VM (dipakai LptScheduler untuk menghitung biaya)
    public static final int COMPUTE_COUNT = 10;   // id 0-9
    public static final int STANDARD_COUNT = 20;  // id 10-29
    public static final int LIGHT_COUNT = 10;     // id 30-39

    // Bandwidth VM Standard. Di prompt awal 5000 Mbps, tapi host Standard
    // cuma 1000 Mbps dan total bandwidth tidak cukup, jadi sebagian VM gagal dibuat.
    // Diturunkan ke 500. Tanyakan ke dosen kalau angka aslinya wajib.
    // Nilai yang dipakai 250: host Standard berisi 2 VM Standard + 1 VM Light,
    // 2 x 250 + 250 = 750 <= 1000 Mbps (dengan 500 butuh 1250 Mbps, ada VM gagal dibuat).
    private static final long STANDARD_BW = 250L;

    private static final long IMAGE_SIZE_MB = 10_000L;
    private static final String VMM = "Xen";

    public List<Vm> createVms(DatacenterBroker broker) {
        int userId = broker.getId();
        List<Vm> vms = new ArrayList<>();
        int id = 0;

        // Compute-Intensive dibuat PALING DULU supaya kebagian slot di host
        // High Performance sebelum VM lain memenuhinya.
        // 2 vCPU @ 2500 MIPS, RAM 8 GB, BW 1 Gbps
        for (int i = 0; i < COMPUTE_COUNT; i++) {
            vms.add(new Vm(id++, userId, 2500, 2, 8192, 1000, IMAGE_SIZE_MB,
                    VMM, new CloudletSchedulerSpaceShared()));
        }

        // Standard: 1 vCPU @ 1500 MIPS, RAM 4 GB
        for (int i = 0; i < STANDARD_COUNT; i++) {
            vms.add(new Vm(id++, userId, 1500, 1, 4096, STANDARD_BW, IMAGE_SIZE_MB,
                    VMM, new CloudletSchedulerSpaceShared()));
        }

        // Light-weight: 1 vCPU @ 800 MIPS, RAM 2 GB, BW 250 Mbps
        for (int i = 0; i < LIGHT_COUNT; i++) {
            vms.add(new Vm(id++, userId, 800, 1, 2048, 250, IMAGE_SIZE_MB,
                    VMM, new CloudletSchedulerSpaceShared()));
        }
        return vms;
    }

    /** Jenis VM berdasarkan id, untuk perhitungan biaya. */
    public static String typeOf(int vmId) {
        if (vmId < COMPUTE_COUNT) return "Compute-Intensive";
        if (vmId < COMPUTE_COUNT + STANDARD_COUNT) return "Standard";
        return "Light-weight";
    }

    /** Spesifikasi jenis VM: {vCPU, MIPS/vCPU, RAM MB, BW Mbps}. Dipakai untuk laporan alokasi. */
    public static int[] specOf(int vmId) {
        switch (typeOf(vmId)) {
            case "Compute-Intensive": return new int[]{2, 2500, 8192, 1000};
            case "Standard": return new int[]{1, 1500, 4096, (int) STANDARD_BW};
            default: return new int[]{1, 800, 2048, 250};
        }
    }

    /** Biaya per detik (G$/s) berdasarkan jenis VM. */
    public static double costPerSecond(int vmId) {
        switch (typeOf(vmId)) {
            case "Compute-Intensive": return 150;
            case "Standard": return 40;
            default: return 18;
        }
    }
}