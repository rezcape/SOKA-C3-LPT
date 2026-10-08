package com.cloudsim.lpt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;


public class LptScheduler {

    /** Assign tiap cloudlet ke VM dengan algoritma LPT. */
    public void scheduleLPT(List<Cloudlet> cloudlets, List<Vm> vms) {
        // 1. Urutkan dari cloudlet terpanjang
        List<Cloudlet> sorted = new ArrayList<>(cloudlets);
        sorted.sort((a, b) -> Long.compare(b.getCloudletLength(), a.getCloudletLength()));

        // 2. Satu "slot" per vCPU (VM Compute-Intensive punya 2 slot)
        List<Vm> slotVm = new ArrayList<>();
        for (Vm vm : vms) {
            for (int p = 0; p < vm.getNumberOfPes(); p++) {
                slotVm.add(vm);
            }
        }
        double[] freeAt = new double[slotVm.size()]; // kapan slot kosong

        // 3. Tiap cloudlet ke slot yang paling cepat menyelesaikannya
        for (Cloudlet c : sorted) {
            int best = 0;
            double bestFinish = Double.MAX_VALUE;
            for (int s = 0; s < slotVm.size(); s++) {
                double finish = freeAt[s] + (double) c.getCloudletLength() / slotVm.get(s).getMips();
                if (finish < bestFinish) {
                    bestFinish = finish;
                    best = s;
                }
            }
            freeAt[best] = bestFinish;
            c.setVmId(slotVm.get(best).getId());
        }
    }

    /** Hitung metrik dari cloudlet yang sudah selesai dan VM yang berhasil dibuat. */
    public Map<String, Double> calculateMetrics(List<Cloudlet> finished, List<Vm> vms) {
        Map<String, Double> metrics = new LinkedHashMap<>();
        double makespan = 0;
        double busyTime = 0;
        int done = 0;
        Map<Integer, Double> vmFinish = new HashMap<>();

        for (Cloudlet c : finished) {
            if (c.getCloudletStatus() != Cloudlet.SUCCESS) continue;
            done++;
            makespan = Math.max(makespan, c.getFinishTime());
            busyTime += c.getActualCPUTime();
            vmFinish.merge(c.getVmId(), c.getFinishTime(), Math::max);
        }
        if (done == 0 || vms.isEmpty()) return metrics;

        double cost = 0, capacity = 0;
        double maxT = 0, minT = Double.MAX_VALUE, sumT = 0;
        for (Vm vm : vms) {
            double t = vmFinish.getOrDefault(vm.getId(), 0.0);
            cost += t * VmFactory.costPerSecond(vm.getId());
            capacity += vm.getNumberOfPes() * makespan;
            maxT = Math.max(maxT, t);
            minT = Math.min(minT, t);
            sumT += t;
        }
        double avgT = sumT / vms.size();

        metrics.put("Cloudlet selesai", (double) done);
        metrics.put("Makespan (s)", makespan);
        metrics.put("Total Cost (G$)", cost);
        metrics.put("Resource Utilization (%)", busyTime / capacity * 100.0);
        metrics.put("Degree of Imbalance", (maxT - minT) / avgT);
        metrics.put("Throughput (task/s)", done / makespan);
        return metrics;
    }
}