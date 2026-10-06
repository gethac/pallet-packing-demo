const DEFAULT_BOX_COLORS = Object.freeze({
  top: "#f3cf96",
  front: "#dfaa69",
  side: "#c98d50",
  stroke: "#a8753f",
});

// 彩盒/白卡：偏亮色调，便于与瓦楞纸箱区分
const BOX_COLOR_PALETTE = Object.freeze([
  { top: "#f7f1e8", front: "#efe4d4", side: "#e2d3be", stroke: "#b59b7a" },
  { top: "#fde8ef", front: "#f5cdd9", side: "#e8b0c2", stroke: "#c47a93" },
  { top: "#e8f4ff", front: "#cfe6fb", side: "#b3d4f0", stroke: "#7aa3c4" },
  { top: "#e9f8ee", front: "#cfead8", side: "#b3d9c0", stroke: "#7aaa88" },
  { top: "#fff3e0", front: "#ffe0b8", side: "#f5c98f", stroke: "#c99655" },
  { top: "#f0e8ff", front: "#ddd0f5", side: "#c7b6e8", stroke: "#9580c0" },
  { top: "#e8e3d8", front: "#cfc8ba", side: "#b8afa0", stroke: "#8e8475" },
]);

// 纸箱：保留瓦楞纸真实棕色，规格间做明显色调偏移
const CARTON_COLOR_PALETTE = Object.freeze([
  { top: "#e8c48a", front: "#c98a48", side: "#a86a32", stroke: "#70451f" },
  { top: "#d4a86a", front: "#a86f38", side: "#885528", stroke: "#5c3818" },
  { top: "#c9b07a", front: "#a88850", side: "#886838", stroke: "#5a4424" },
  { top: "#efb870", front: "#d08a40", side: "#b06a28", stroke: "#7a4818" },
  { top: "#b89560", front: "#957040", side: "#785828", stroke: "#4e3818" },
  { top: "#dbc070", front: "#b89045", side: "#987030", stroke: "#684820" },
]);

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
        const colors = getColorByKey(
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
