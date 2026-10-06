package com.example.pallet.service;

import com.example.pallet.dto.CalculateRequest;
import com.example.pallet.engine.PackingException;
import com.example.pallet.engine.PalletPackingEngine;
import com.example.pallet.engine.PalletPackingInputFingerprint;
import com.example.pallet.engine.PalletPackingModel;
import com.example.pallet.engine.PalletPackingResult;
import com.example.pallet.entity.PalletGroup;
import com.example.pallet.entity.PalletItem;
import com.example.pallet.entity.PalletPlan;
import com.example.pallet.entity.PalletStandard;
import com.example.pallet.repository.PalletGroupRepository;
import com.example.pallet.repository.PalletItemRepository;
import com.example.pallet.repository.PalletPlanRepository;
import com.example.pallet.repository.PalletStandardRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PalletPlanService {

    private final PalletPackingEngine engine = new PalletPackingEngine();
    private final PalletStandardRepository standardRepository;
    private final PalletPlanRepository planRepository;
    private final PalletGroupRepository groupRepository;
    private final PalletItemRepository itemRepository;
    private final MockOrderDataService mockOrderDataService;
    private final PalletSaveLock saveLock;
    private final ObjectMapper objectMapper;

    public PalletPlanService(PalletStandardRepository standardRepository,
                             PalletPlanRepository planRepository,
                             PalletGroupRepository groupRepository,
                             PalletItemRepository itemRepository,
                             MockOrderDataService mockOrderDataService,
                             PalletSaveLock saveLock,
                             ObjectMapper objectMapper) {
        this.standardRepository = standardRepository;
        this.planRepository = planRepository;
        this.groupRepository = groupRepository;
        this.itemRepository = itemRepository;
        this.mockOrderDataService = mockOrderDataService;
        this.saveLock = saveLock;
        this.objectMapper = objectMapper;
    }

    public List<PalletStandard> listEnabledStandards() {
        return standardRepository.findByEnabledOrderByLengthAscWidthAsc("1");
    }

    public Map<String, Object> getCalculation(String orderId, String batchId, boolean refresh) {
        String bid = normalizeBatch(batchId);
        PalletPlan saved = planRepository.findByOrderIdAndBatchId(orderId, bid).orElse(null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", orderId);
        body.put("batchId", bid);
        body.put("batches", mockOrderDataService.listBatchIds(orderId));
        body.putAll(mockOrderDataService.productRows(orderId, bid));
        if (saved != null && !refresh) {
            enrich(saved);
            body.put("plan", saved);
            body.put("fromCache", true);
        } else {
            body.put("plan", saved);
            body.put("fromCache", false);
        }
        return body;
    }

    public Map<String, Object> getView(String orderId, String batchId) {
        String bid = normalizeBatch(batchId);
        PalletPlan saved = planRepository.findByOrderIdAndBatchId(orderId, bid)
                .orElseThrow(() -> new PackingException("未找到已保存的托盘方案"));
        enrich(saved);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", orderId);
        body.put("batchId", bid);
        body.put("plan", saved);
        body.putAll(mockOrderDataService.productRows(orderId, bid));
        return body;
    }

    public List<Map<String, Object>> getPreview(String orderId, String batchId, String palletNo) {
        String bid = normalizeBatch(batchId);
        PalletPlan plan = planRepository.findByOrderIdAndBatchId(orderId, bid)
                .orElseThrow(() -> new PackingException("未找到托盘方案"));
        PalletItem item = itemRepository.findByPlanIdAndPalletNo(plan.getId(), palletNo.trim())
                .orElseThrow(() -> new PackingException("未找到托盘 " + palletNo));
        return parseBoxLayout(item.getBoxLayout());
    }

    @Transactional
    public Map<String, Object> calculate(String orderId, String batchId, CalculateRequest req) {
        return saveLock.withLock(orderId, batchId, () -> doCalculateAndSave(orderId, batchId, req));
    }

    @Transactional
    public Map<String, Object> saveDraft(String orderId, String batchId, CalculateRequest req) {
        // 草稿：若无变化指纹则保留，否则重算保存
        return calculate(orderId, batchId, req);
    }

    private Map<String, Object> doCalculateAndSave(String orderId, String batchId, CalculateRequest req) {
        String bid = normalizeBatch(batchId);
        PalletStandard standard = standardRepository.findById(req.getPalletStandardId())
                .orElseThrow(() -> new PackingException("所选托盘标准不存在"));
        if (!"1".equals(standard.getEnabled())) {
            throw new PackingException("托盘标准未启用");
        }
        List<PalletPackingModel.BoxTask> tasks = mockOrderDataService.buildBoxTasks(orderId, bid);
        PalletPackingModel.PackingRequest packingRequest = buildRequest(standard, req, tasks);
        String fingerprint = PalletPackingInputFingerprint.hash(packingRequest);

        PalletPlan existing = planRepository.findByOrderIdAndBatchId(orderId, bid).orElse(null);
        if (existing != null && fingerprint.equals(existing.getCalculationInputHash())) {
            enrich(existing);
            return Map.of("plan", existing, "reused", true);
        }

        PalletPackingResult result = engine.pack(packingRequest);
        PalletPlan plan = persist(orderId, bid, standard, req, packingRequest, result, fingerprint);
        enrich(plan);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("plan", plan);
        body.put("reused", false);
        body.putAll(mockOrderDataService.productRows(orderId, bid));
        return body;
    }

    private PalletPlan persist(String orderId, String batchId, PalletStandard standard,
                               CalculateRequest req, PalletPackingModel.PackingRequest packingRequest,
                               PalletPackingResult result, String fingerprint) {
        planRepository.findByOrderIdAndBatchId(orderId, batchId).ifPresent(old -> {
            itemRepository.deleteByPlanId(old.getId());
            groupRepository.deleteByPlanId(old.getId());
            planRepository.delete(old);
            planRepository.flush();
        });

        PalletPlan plan = new PalletPlan();
        plan.setId(UUID.randomUUID().toString().replace("-", ""));
        plan.setOrderId(orderId);
        plan.setBatchId(batchId);
        plan.setPalletStandardId(standard.getId());
        plan.setPalletStandardCode(standard.getCode());
        plan.setPalletLength(standard.getLength());
        plan.setPalletWidth(standard.getWidth());
        plan.setWeightLimit(req.getWeightLimit());
        plan.setCargoHeightLimit(req.getCargoHeightLimit());
        plan.setTotalPalletCount(result.getTotalPalletCount());
        plan.setTotalBoxCount(result.getTotalBoxCount());
        plan.setPackageMode(resolvePackageMode(result));
        plan.setTotalProductWeight(result.getTotalProductWeight());
        plan.setHasMixedGroup(result.getHasMixedGroup());
        plan.setAllowMixedPallet(flag(req.getAllowMixedPallet()));
        plan.setAllowMixedPackagePallet(flag(req.getAllowMixedPackagePallet()));
        plan.setAllowMixedNoBoxPallet(flag(req.getAllowMixedNoBoxPallet()));
        plan.setAvgAreaUtilization(result.getAvgAreaUtilization());
        plan.setAvgHeightUtilization(result.getAvgHeightUtilization());
        plan.setCalculatedTime(LocalDateTime.now());
        plan.setCalculationInputHash(fingerprint);
        planRepository.save(plan);

        Map<String, String> groupIdByNo = new LinkedHashMap<>();
        for (PalletPackingResult.GroupResult g : result.getGroupList()) {
            PalletGroup group = new PalletGroup();
            group.setId(UUID.randomUUID().toString().replace("-", ""));
            group.setPlanId(plan.getId());
            group.setOrderId(orderId);
            group.setGroupNo(g.getGroupNo());
            group.setGroupType(g.getGroupType());
            group.setProductKeys(g.getProductKeys());
            group.setPalletCount(g.getPalletCount());
            group.setBoxCount(g.getBoxCount());
            group.setTotalWeight(g.getTotalWeight());
            group.setAreaUtilization(g.getAreaUtilization());
            group.setHeightUtilization(g.getHeightUtilization());
            group.setSortNo(g.getSort());
            groupRepository.save(group);
            groupIdByNo.put(g.getGroupNo(), group.getId());
        }

        Map<String, List<PalletPackingResult.BoxResult>> boxesByPallet = result.getBoxList().stream()
                .collect(Collectors.groupingBy(PalletPackingResult.BoxResult::getPalletNo, LinkedHashMap::new, Collectors.toList()));

        for (PalletPackingResult.ItemResult itemResult : result.getItemList()) {
            PalletItem item = new PalletItem();
            item.setId(UUID.randomUUID().toString().replace("-", ""));
            item.setPlanId(plan.getId());
            item.setGroupId(groupIdByNo.get(itemResult.getGroupNo()));
            item.setOrderId(orderId);
            item.setPalletNo(itemResult.getPalletNo());
            item.setProductKeys(itemResult.getProductKeys());
            item.setProductComposition(buildComposition(boxesByPallet.getOrDefault(itemResult.getPalletNo(), List.of())));
            item.setBoxLayout(writeJson(boxesByPallet.getOrDefault(itemResult.getPalletNo(), List.of())));
            item.setLayerCount(itemResult.getLayerCount());
            item.setBoxCount(itemResult.getBoxCount());
            item.setTotalWeight(itemResult.getTotalWeight());
            item.setTotalHeight(itemResult.getTotalHeight());
            item.setAreaUtilization(itemResult.getAreaUtilization());
            item.setHeightUtilization(itemResult.getHeightUtilization());
            item.setStabilityScore(itemResult.getStabilityScore());
            item.setSortNo(itemResult.getSort());
            itemRepository.save(item);
        }
        return plan;
    }

    private PalletPackingModel.PackingRequest buildRequest(PalletStandard standard, CalculateRequest req,
                                                          List<PalletPackingModel.BoxTask> tasks) {
        PalletPackingModel.PalletSpec spec = new PalletPackingModel.PalletSpec();
        spec.setPalletStandardId(standard.getId());
        spec.setPalletStandardCode(standard.getCode());
        spec.setLength(standard.getLength());
        spec.setWidth(standard.getWidth());
        spec.setWeightLimit(req.getWeightLimit());
        spec.setCargoHeightLimit(req.getCargoHeightLimit());

        PalletPackingModel.PackingRequest packingRequest = new PalletPackingModel.PackingRequest();
        packingRequest.setPalletSpec(spec);
        packingRequest.setBoxTaskList(tasks);
        packingRequest.setAllowMixedPallet("1".equals(flag(req.getAllowMixedPallet())));
        packingRequest.setAllowMixedPackagePallet("1".equals(flag(req.getAllowMixedPackagePallet())));
        packingRequest.setAllowMixedNoBoxPallet("1".equals(flag(req.getAllowMixedNoBoxPallet())));
        return packingRequest;
    }

    private void enrich(PalletPlan plan) {
        plan.setGroupList(groupRepository.findByPlanIdOrderBySortNoAsc(plan.getId()));
        plan.setItemList(itemRepository.findByPlanIdOrderBySortNoAsc(plan.getId()));
    }

    private List<Map<String, Object>> parseBoxLayout(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new PackingException("盒子布局解析失败");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new PackingException("盒子布局序列化失败");
        }
    }

    private String buildComposition(List<PalletPackingResult.BoxResult> boxes) {
        Map<String, Long> counts = boxes.stream()
                .collect(Collectors.groupingBy(PalletPackingResult.BoxResult::getProductKey, Collectors.counting()));
        return counts.entrySet().stream()
                .map(e -> e.getKey() + "x" + e.getValue())
                .collect(Collectors.joining(","));
    }

    private String resolvePackageMode(PalletPackingResult result) {
        return result.getBoxList().stream()
                .map(PalletPackingResult.BoxResult::getPackageMode)
                .distinct()
                .reduce((a, b) -> "mixed")
                .orElse("box");
    }

    private static String normalizeBatch(String batchId) {
        return batchId == null ? "" : batchId.trim();
    }

    private static String flag(String v) {
        if (v == null) {
            return "0";
        }
        String s = v.trim();
        return ("1".equals(s) || "true".equalsIgnoreCase(s)) ? "1" : "0";
    }
}
