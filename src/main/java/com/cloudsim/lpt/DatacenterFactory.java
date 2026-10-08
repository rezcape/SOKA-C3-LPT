package com.cloudsim.lpt;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.VmSchedulerSpaceShared;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;


/**
 * Membuat 1 datacenter berisi 10 host.
 *
 * GRADE HOST
 *   Host id 0-3 : "High grade" = Server High Performance
 *                 8 core @3000 MIPS, 32 GB RAM, 1 TB storage, 10 Gbps
 *   Host id 4-9 : "Low grade"  = Server Standard
 *                 4 core @1500 MIPS, 16 GB RAM, 500 GB storage, 1 Gbps
 *
 * ALOKASI VM KE HOST (komentar sesuai revisi)
 *   Kebijakan: VmAllocationPolicySimple bawaan CloudSim 3.0.3. Untuk tiap VM,
 *   kebijakan ini memilih host yang punya PE (core) bebas PALING BANYAK;
 *   kalau seri, host dengan id terkecil. Jadi ini BUKAN First Fit dan BUKAN
 *   Best Fit (Best Fit memilih sisa resource paling sedikit; ini kebalikannya,
 *   menyebar beban).
 *   Contoh dari log: VM #0-#3 -> Host #0-#3 (semua 8 core bebas, seri -> id kecil,
 *   lalu menyebar), VM #4 kembali ke Host #0 (keempatnya sama-sama 6 core bebas).
 *
 *   Constraint "Compute-Intensive hanya di High Performance" tidak ditulis sebagai
 *   aturan. Ia terpenuhi karena vCPU VM itu butuh 2500 MIPS, sedangkan core host
 *   Standard hanya 1500 MIPS, dan VmSchedulerSpaceShared menolaknya.
 *
 *   Hasil penempatan tiap VM dicatat di PLACEMENT (vmId -> hostId) untuk laporan.
 *
 * HASIL ALOKASI (selalu sama di setiap run, karena spesifikasi VM, host,
 * dan urutan pembuatan VM tidak berubah)
 *   Compute = id 0-9, Standard = id 10-29, Light = id 30-39
 *   Host 0 : VM 0, 4, 8 (Compute) + VM 26 (Standard) + VM 36 (Light)        -> 8/8 core
 *   Host 1 : VM 1, 5, 9 (Compute) + VM 27 (Standard) + VM 37 (Light)        -> 8/8 core
 *   Host 2 : VM 2, 6 (Compute) + VM 10, 18, 28 (Standard) + VM 38 (Light)   -> 8/8 core
 *   Host 3 : VM 3, 7 (Compute) + VM 11, 19, 29 (Standard) + VM 39 (Light)   -> 8/8 core
 *   Host 4-9 : masing-masing 2 VM Standard + 1 VM Light                     -> 3/4 core
 *     host 4: VM 12, 20, 30 | host 5: VM 13, 21, 31 | host 6: VM 14, 22, 32
 *     host 7: VM 15, 23, 33 | host 8: VM 16, 24, 34 | host 9: VM 17, 25, 35
 *   Semua host terisi, tidak ada host yang menganggur.
 *   Detail per VM disimpan di results/vm_placement_detail.csv.
 */
public class DatacenterFactory {

    /** vmId -> hostId, diisi saat VM berhasil ditempatkan. Dikosongkan tiap datacenter baru. */
    public static final Map<Integer, Integer> PLACEMENT = new TreeMap<>();

    public static final int HIGH_GRADE_HOSTS = 4; // host id 0-3

    public static String gradeOf(int hostId) {
        return hostId < HIGH_GRADE_HOSTS ? "High grade" : "Low grade";
    }

    /** Spesifikasi host: {core, MIPS/core, RAM MB, BW Mbps}. Dipakai untuk laporan alokasi. */
    public static int[] specOf(int hostId) {
        return hostId < HIGH_GRADE_HOSTS
                ? new int[]{8, 3_000, 32 * 1024, 10_000}
                : new int[]{4, 1_500, 16 * 1024, 1_000};
    }

    public Datacenter createDatacenter() {
        PLACEMENT.clear();
        List<Host> hostList = new ArrayList<>();

        for (int hostId = 0; hostId < 10; hostId++) {
            boolean highPerformance = hostId < HIGH_GRADE_HOSTS;
            int cores = highPerformance ? 8 : 4;
            int mipsPerCore = highPerformance ? 3_000 : 1_500;
            int ramMb = highPerformance ? 32 * 1024 : 16 * 1024;
            long storageMb = highPerformance ? 1_000_000L : 500_000L;
            long bandwidthMbps = highPerformance ? 10_000L : 1_000L;

            List<Pe> peList = new ArrayList<>();
            for (int peId = 0; peId < cores; peId++) {
                peList.add(new Pe(peId, new PeProvisionerSimple(mipsPerCore)));
            }

            Host host = new Host(
                    hostId,
                    new RamProvisionerSimple(ramMb),
                    new BwProvisionerSimple(bandwidthMbps),
                    storageMb,
                    peList,
                    new VmSchedulerSpaceShared(peList));
            hostList.add(host);
        }

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                "x86",
                "Linux",
                "Xen",
                hostList,
                10.0,
                3.0,
                0.05,
                0.001,
                0.0);

        List<Storage> storageList = new LinkedList<>();
        try {
            return new Datacenter(
                    "Datacenter_0",
                    characteristics,
                    new PlacementPolicy(hostList),
                    storageList,
                    0.0);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create Datacenter_0", exception);
        }
    }

    /** Sama persis dengan VmAllocationPolicySimple, hanya ditambah pencatatan host tiap VM. */
    public static class PlacementPolicy extends VmAllocationPolicySimple {

        public PlacementPolicy(List<? extends Host> list) {
            super(list);
        }

        @Override
        public boolean allocateHostForVm(Vm vm) {
            boolean placed = super.allocateHostForVm(vm);
            if (placed) {
                PLACEMENT.put(vm.getId(), getHost(vm).getId());
            }
            return placed;
        }
    }
}