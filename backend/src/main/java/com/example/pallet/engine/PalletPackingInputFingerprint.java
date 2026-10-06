package com.example.pallet.engine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/** 计算输入指纹（简化版）：参数/箱子集合变化则哈希变化 */
public final class PalletPackingInputFingerprint {
    private PalletPackingInputFingerprint() {}

    public static String hash(PalletPackingModel.PackingRequest request) {
        StringBuilder sb = new StringBuilder();
        PalletPackingModel.PalletSpec spec = request.getPalletSpec();
        sb.append(spec.getPalletStandardId()).append('|')
                .append(spec.getLength()).append('|')
                .append(spec.getWidth()).append('|')
                .append(spec.getWeightLimit()).append('|')
                .append(spec.getCargoHeightLimit()).append('|')
                .append(request.getAllowMixedPallet()).append('|')
                .append(request.getAllowMixedPackagePallet()).append('|')
                .append(request.getAllowMixedNoBoxPallet()).append('|');
        List<PalletPackingModel.BoxTask> tasks = request.getBoxTaskList().stream()
                .sorted(Comparator.comparing(t -> t.getTaskKey() == null ? "" : t.getTaskKey()))
                .collect(Collectors.toList());
        for (PalletPackingModel.BoxTask t : tasks) {
            sb.append(t.getTaskKey()).append(',')
                    .append(t.getProductKey()).append(',')
                    .append(t.getPackageMode()).append(',')
                    .append(t.getBoxLength()).append('x')
                    .append(t.getBoxWidth()).append('x')
                    .append(t.getBoxHeight()).append(',')
                    .append(t.getBoxWeight()).append(';');
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
