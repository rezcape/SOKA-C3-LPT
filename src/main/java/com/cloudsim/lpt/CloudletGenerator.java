package com.cloudsim.lpt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;

/**
 * Membuat daftar Cloudlet (task) dari satu file dataset (GoCJ atau sintetik).
 * Panjang task (MI) diambil dari file; ukuran file transfer diacak
 * 300 KB - 10 MB dengan seed tetap supaya hasil bisa diulang.
 */
public class CloudletGenerator {

    private static final long MIN_FILE_SIZE = 300L;     // 300 KB
    private static final long MAX_FILE_SIZE = 10_000L;  // 10 MB
    private static final long SEED = 42L;

    /** Jumlah task = jumlah angka di file (mis. GoCJ_Dataset_500.txt -> 500 task). */
    public List<Cloudlet> generateCloudlets(String dataFile) {
        List<Long> lengths = readLengths(dataFile);
        Random random = new Random(SEED); // hanya untuk ukuran file
        UtilizationModel full = new UtilizationModelFull();
        List<Cloudlet> cloudlets = new ArrayList<>();

        for (int id = 0; id < lengths.size(); id++) {
            long fileSize = MIN_FILE_SIZE
                    + (long) (random.nextDouble() * (MAX_FILE_SIZE - MIN_FILE_SIZE + 1));
            // 1 task memakai 1 PE (core), tidak bisa dipecah (atomik)
            cloudlets.add(new Cloudlet(
                    id, lengths.get(id), 1, fileSize, fileSize, full, full, full));
        }
        return cloudlets;
    }

    /** Membaca semua angka (panjang task, MI) dari file. Dipakai juga untuk statistik. */
    public static List<Long> readLengths(String dataFile) {
        List<Long> result = new ArrayList<>();
        try {
            String content = new String(Files.readAllBytes(Paths.get(dataFile)), "UTF-8");
            for (String token : content.split("[\\s,;]+")) {
                if (token.isEmpty()) continue;
                result.add((long) Double.parseDouble(token));
            }
        } catch (IOException | NumberFormatException e) {
            throw new IllegalStateException("Gagal membaca " + dataFile, e);
        }
        return result;
    }
}

