const DEFAULT_BOX_COLORS = Object.freeze({
  top: "#f3cf96",
  front: "#dfaa69",
  side: "#c98d50",
  stroke: "#a8753f",
});

// 彩盒：明确品牌色（蓝白 / 绿白），纸箱：牛皮纸深浅两档
const BOX_COLOR_PALETTE = Object.freeze([
  { top: "#eef5ff", front: "#3b82f6", side: "#2563eb", stroke: "#1d4ed8" }, // 蓝彩盒
  { top: "#eaf8f0", front: "#22c55e", side: "#16a34a", stroke: "#15803d" }, // 绿彩盒
  { top: "#fff7ed", front: "#f97316", side: "#ea580c", stroke: "#c2410c" }, // 橙彩盒
  { top: "#f5f3ff", front: "#8b5cf6", side: "#7c3aed", stroke: "#6d28d9" }, // 紫彩盒
]);

const CARTON_COLOR_PALETTE = Object.freeze([
  { top: "#e6c48a", front: "#c99552", side: "#a87434", stroke: "#7a4e1c" }, // 浅牛皮
  { top: "#b07a3c", front: "#8a5524", side: "#6b3f18", stroke: "#3f240e" }, // 深牛皮
  { top: "#d4a86a", front: "#ae7540", side: "#8f5b30", stroke: "#65401f" },
  { top: "#9a6840", front: "#7a4c28", side: "#5c3818", stroke: "#3a2410" },
]);

/** 固定规格色，确保混装场景一眼可辨 */
export const SPEC_COLOR_BY_KEY = Object.freeze({
  "PROD-BOX-S": { top: "#eef5ff", front: "#3b82f6", side: "#2563eb", stroke: "#1d4ed8" },
  "PROD-BOX-M": { top: "#eaf8f0", front: "#22c55e", side: "#16a34a", stroke: "#15803d" },
  "PROD-CTN-M": { top: "#e8c990", front: "#c99555", side: "#a87435", stroke: "#7a4e1f" },
  "PROD-CTN-L": { top: "#a87438", front: "#7a4c22", side: "#5c3818", stroke: "#3a220e" },
  "PROD-S": { top: "#eef5ff", front: "#3b82f6", side: "#2563eb", stroke: "#1d4ed8" },
  "PROD-M": { top: "#eaf8f0", front: "#22c55e", side: "#16a34a", stroke: "#15803d" },
  "PROD-L": { top: "#e8c990", front: "#c99555", side: "#a87435", stroke: "#7a4e1f" },
  "PROD-XL": { top: "#a87438", front: "#7a4c22", side: "#5c3818", stroke: "#3a220e" },
});

export const LAYER_COLOR_PALETTE = Object.freeze([
  { top: "#f3cf96", front: "#dfaa69", side: "#c98d50", stroke: "#a8753f" },
  { top: "#cfe6fb", front: "#9fc4ea", side: "#7aa8d4", stroke: "#4f7eae" },
  { top: "#cfead8", front: "#9ecfad", side: "#7ab48c", stroke: "#4f8a62" },
  { top: "#f5cdd9", front: "#e8a0b4", side: "#d48098", stroke: "#b05870" },
  { top: "#ddd0f5", front: "#b8a0e0", side: "#9880c8", stroke: "#7058a0" },
  { top: "#ffe0b8", front: "#f0c080", side: "#d8a058", stroke: "#a87830" },
]);

export const PALLET_COLORS = Object.freeze({
  top: "#e4b77a",
  front: "#b77b45",
  side: "#9a6236",
  support: "#8a5730",
  stroke: "#704726",
  dark: "#4c2f1b",
  grain: "#7b4f2d",
  gap: "#2f2117",
});

function toNumber(value, fallback = 0) {
  if (value === null || value === undefined || value === "") {
    return fallback;
  }
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : fallback;
}

function toPositiveNumber(value, fallback = 0) {
  const numberValue = toNumber(value, fallback);
  return numberValue > 0 ? numberValue : fallback;
}

function normalizeRotation(value) {
  return (((Number(value) || 0) % 360) + 360) % 360;
}

function normalizePackageMode(value) {
  const mode = String(value || "")
    .trim()
    .toLowerCase();
  return mode === "carton" || mode === "virtual" ? mode : "box";
}

function toBoolean(value, fallback = false) {
  if (value === null || value === undefined || value === "") {
    return fallback;
  }
  if (typeof value === "string") {
    const normalizedValue = value.trim().toLowerCase();
    if (["0", "false", "no", "off"].includes(normalizedValue)) {
      return false;
    }
    if (["1", "true", "yes", "on"].includes(normalizedValue)) {
      return true;
    }
  }
  return Boolean(value);
}

function resolvePalletViewRotation(viewRotationByPalletNo, palletNo, fallbackRotation = 0) {
  if (!viewRotationByPalletNo || typeof viewRotationByPalletNo !== "object") {
    return normalizeRotation(fallbackRotation);
  }
  const key = String(palletNo ?? "").trim();
  if (!Object.prototype.hasOwnProperty.call(viewRotationByPalletNo, key)) {
    return normalizeRotation(fallbackRotation);
  }
  return normalizeRotation(viewRotationByPalletNo[key]);
}

function resolvePalletNo(value, index) {
  const text = String(value ?? "").trim();
  return text || `P${index + 1}`;
}

function resolveBoxKey(box = {}, index = 0) {
  return (
    String(
      box.boxKey ||
        box.id ||
        box.taskKey ||
        `${box.palletNo || "pallet"}-${box.orderProductId || "product"}-${index}`
    ).trim() || `box-${index + 1}`
  );
}

function resolveBoxProductKey(box = {}) {
  const productKey = String(box.productKey || box.product_key || "").trim();
  if (productKey) {
    return productKey;
  }
  const customerNo = String(box.customerNo || box.customer_no || "").trim();
  const internalNo = String(box.internalNo || box.internal_no || "").trim();
  if (customerNo || internalNo) {
    return [customerNo, internalNo].filter(Boolean).join("|");
  }
  const orderProductId = String(box.orderProductId || box.order_product_id || "").trim();
  if (orderProductId) {
    return orderProductId;
  }
  const productKeys = String(box.productKeys || box.product_keys || "").trim();
  if (productKeys && !productKeys.includes(",") && !productKeys.includes("、")) {
    return productKeys;
  }
  return "default";
}

function getColorByKey(colorMap, key = "", palette = BOX_COLOR_PALETTE) {
  if (colorMap[key]) {
    return colorMap[key];
  }
  const nextColor = palette[Object.keys(colorMap).length % palette.length];
  colorMap[key] = nextColor;
  return nextColor;
}

function normalizeBoxLayout(box = {}) {
  const length = toPositiveNumber(box.occupyLength, toPositiveNumber(box.boxLength, 0));
  const width = toPositiveNumber(box.occupyWidth, toPositiveNumber(box.boxWidth, 0));
  const height = toPositiveNumber(box.occupyHeight, toPositiveNumber(box.boxHeight, 0));

  return {
    x: toNumber(box.positionX),
    y: toNumber(box.positionY),
    z: toNumber(box.positionZ),
    length,
    width,
    height,
  };
}

function buildPalletNos(itemList = [], boxList = []) {
  const palletNoSet = new Set();
  const palletNos = [];

  itemList.forEach((item, index) => {
    const palletNo = resolvePalletNo(item?.palletNo, index);
    if (!palletNoSet.has(palletNo)) {
      palletNoSet.add(palletNo);
      palletNos.push(palletNo);
    }
  });

  boxList.forEach((box, index) => {
    const palletNo = resolvePalletNo(box?.palletNo, index);
    if (!palletNoSet.has(palletNo)) {
      palletNoSet.add(palletNo);
      palletNos.push(palletNo);
    }
  });

  return palletNos;
}

export function createPackagePalletPreviewSceneData({
  palletLength,
  palletWidth,
  palletHeight,
  palletItems = [],
  boxes = [],
  selectedPalletNo = "",
  selectedBoxKeys = [],
  compact = false,
  showLabels = true,
  showCartonTape = true,
  viewRotation = 0,
  viewRotationByPalletNo = null,
} = {}) {
  const lengthValue = toPositiveNumber(palletLength, 0);
  const widthValue = toPositiveNumber(palletWidth, 0);

  if (!lengthValue || !widthValue) {
    return null;
  }

  const visualPalletHeight = Math.max(60, toPositiveNumber(palletHeight, 0));
  const itemList = Array.isArray(palletItems) ? palletItems : [];
  const boxList = Array.isArray(boxes) ? boxes : [];
  const allPalletNos = buildPalletNos(itemList, boxList);

  if (!allPalletNos.length) {
    return null;
  }

  const selectedNo = String(selectedPalletNo || "").trim();
  const palletNos =
    selectedNo && allPalletNos.includes(selectedNo) ? [selectedNo] : allPalletNos.slice(0, 4);
  const selectedBoxKeySet = new Set(
    (selectedBoxKeys || []).map((item) => String(item || "").trim()).filter(Boolean)
  );
  const colorMap = {};

  const scenes = palletNos.map((palletNo) => {
    const sceneBoxes = boxList
      .filter((box) => String(box?.palletNo || "").trim() === String(palletNo))
      .map((box, index) => {
        const boxKey = resolveBoxKey(box, index);
        const productKey = resolveBoxProductKey(box);
        const packageMode = normalizePackageMode(box.packageMode || box.package_mode);
        const layout = normalizeBoxLayout(box);
        const colors = SPEC_COLOR_BY_KEY[productKey]
          ? SPEC_COLOR_BY_KEY[productKey]
          : getColorByKey(
              colorMap,
              `${packageMode}:${productKey}`,
              packageMode === "carton" ? CARTON_COLOR_PALETTE : BOX_COLOR_PALETTE
            );
        const cartonTapeVisible = toBoolean(
          box.cartonTapeVisible ?? box.carton_tape_visible,
          toBoolean(showCartonTape, true)
        );

        return {
          ...layout,
          key: boxKey,
          productKey,
          packageMode,
          cartonTapeVisible: packageMode === "carton" && cartonTapeVisible,
          colors,
          selected: selectedBoxKeySet.has(boxKey),
          layerNo: toNumber(box.layerNo ?? box.layer_no ?? box.layer, 1) || 1,
          boxWeight: toNumber(box.boxWeight ?? box.box_weight, 0),
          productLabel: String(box.productLabel || box.product_label || "").trim(),
          positionX: layout.x,
          positionY: layout.y,
          positionZ: layout.z,
          occupyHeight: layout.height,
          boxLength: layout.length,
          boxWidth: layout.width,
          boxHeight: layout.height,
        };
      })
      .filter((box) => box.length > 0 && box.width > 0 && box.height > 0)
      .sort((left, right) => left.z - right.z || left.y - right.y || left.x - right.x);

    const stackHeight = sceneBoxes.reduce(
      (height, box) => Math.max(height, visualPalletHeight + box.z + box.height),
      visualPalletHeight
    );

    return {
      palletNo,
      selected: selectedNo ? String(selectedNo) === String(palletNo) : false,
      label: compact || !showLabels ? "" : `托盘 ${palletNo}`,
      rotation: resolvePalletViewRotation(viewRotationByPalletNo, palletNo, viewRotation),
      pallet: {
        length: lengthValue,
        width: widthValue,
        height: visualPalletHeight,
      },
      boxes: sceneBoxes,
      stackHeight,
    };
  });

  if (!scenes.length) {
    return null;
  }

  return {
    scenes,
    source: {
      palletLength: lengthValue,
      palletWidth: widthValue,
      palletHeight: visualPalletHeight,
    },
  };
}
