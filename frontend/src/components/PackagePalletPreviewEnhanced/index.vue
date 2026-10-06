<template>
  <div
    class="package-pallet-preview"
    :class="{
      'is-compact': compact,
      'is-dragging': isDragging,
    }"
  >
    <div v-if="showRotateControls && sceneData" class="package-pallet-preview__rotate-actions">
      <button
        type="button"
        class="package-pallet-preview__rotate-button"
        title="向左切换视角"
        aria-label="向左切换托盘预览视角"
        @click="rotatePreview(-90)"
      >
        <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true"><path fill="currentColor" d="M12.5 8c-2.65 0-5.05 1-6.9 2.6L3 8v8h8l-2.55-2.55A7.96 7.96 0 0 1 12.5 10c3.04 0 5.64 1.78 6.89 4.35l1.95-.65C19.8 10.46 16.47 8 12.5 8z"/></svg>
      </button>
      <button
        type="button"
        class="package-pallet-preview__rotate-button"
        title="向右切换视角"
        aria-label="向右切换托盘预览视角"
        @click="rotatePreview(90)"
      >
        <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true"><path fill="currentColor" d="M11.5 8c2.65 0 5.05 1 6.9 2.6L21 8v8h-8l2.55-2.55A7.96 7.96 0 0 0 11.5 10c-3.04 0-5.64 1.78-6.89 4.35l-1.95-.65C4.2 10.46 7.53 8 11.5 8z"/></svg>
      </button>
      <button
        type="button"
        class="package-pallet-preview__rotate-button"
        :disabled="!canZoomOut"
        title="缩小"
        aria-label="缩小托盘预览"
        @click="zoomPreview(-ZOOM_STEP)"
      >
        <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true"><path fill="currentColor" d="M15.5 14h-.79l-.28-.27A6.47 6.47 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14zM7 9h5v1H7z"/></svg>
      </button>
      <button
        type="button"
        class="package-pallet-preview__rotate-button"
        title="还原缩放"
        aria-label="还原托盘预览缩放"
        @click="resetPreviewZoom"
      >
        <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true"><path fill="currentColor" d="M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46A7.93 7.93 0 0 0 20 13c0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 8.74A7.93 7.93 0 0 0 4 13c0 4.42 3.58 8 8 8v4l5-5-5-5v4z"/></svg>
      </button>
      <button
        type="button"
        class="package-pallet-preview__rotate-button"
        :disabled="!canZoomIn"
        title="放大"
        aria-label="放大托盘预览"
        @click="zoomPreview(ZOOM_STEP)"
      >
        <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true"><path fill="currentColor" d="M15.5 14h-.79l-.28-.27A6.47 6.47 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14zM10 7H9v2H7v1h2v2h1v-2h2V9h-2z"/></svg>
      </button>
    </div>

    <div ref="canvasHostRef" class="package-pallet-preview__canvas">
      <canvas
        v-show="sceneData"
        ref="canvasRef"
        class="package-pallet-preview__three-canvas"
        aria-label="托盘三维预览"
        @pointerdown="handlePointerDown"
        @pointermove="handlePointerMove"
        @pointerup="handlePointerUp"
        @pointercancel="handlePointerUp"
        @pointerleave="handlePointerUp"
        @wheel.prevent="handleWheel"
      />

      <div v-if="!sceneData" class="package-pallet-preview__empty">
        <strong class="package-pallet-preview__empty-title">暂无托盘</strong>
      </div>

      <div v-if="sceneLabels.length" class="package-pallet-preview__labels" aria-hidden="true">
        <span v-for="label in sceneLabels" :key="label">{{ label }}</span>
      </div>

      <div v-if="sceneData" class="ppe-hud">
        <div class="ppe-hud__row">
          <span>面积利用率 <b>{{ fmtPct(areaUtil) }}</b></span>
          <span>高度利用率 <b>{{ fmtPct(heightUtil) }}</b></span>
        </div>
        <div class="ppe-hud__row">
          <span>重量 <b>{{ fmtNum(totalWeightHud) }}</b>/{{ fmtNum(weightLimit) }}kg</span>
          <span>高度 <b>{{ fmtNum(stackHeightHud) }}</b>/{{ fmtNum(heightLimit) }}mm</span>
          <span>箱数 <b>{{ visibleBoxCount }}/{{ trackedBoxes.length }}</b></span>
        </div>
      </div>
      <div v-if="sceneData" class="ppe-ruler" aria-hidden="true">
        <div class="ppe-ruler__track"><div class="ppe-ruler__fill" :style="{ height: rulerFillPct + '%' }"></div></div>
        <div class="ppe-ruler__labels"><span>{{ fmtNum(heightLimit) }}</span><span>{{ fmtNum(stackHeightHud) }}</span><span>0</span></div>
      </div>
      <aside v-if="selectedInfo" class="ppe-card">
        <header>{{ selectedInfo.product }} · {{ selectedInfo.boxNo }}</header>
        <dl>
          <div><dt>尺寸</dt><dd>{{ selectedInfo.size }}</dd></div>
          <div><dt>重量</dt><dd>{{ selectedInfo.weight }} kg</dd></div>
          <div><dt>坐标</dt><dd>{{ selectedInfo.pos }}</dd></div>
          <div><dt>层号</dt><dd>{{ selectedInfo.layer }}</dd></div>
          <div><dt>包装</dt><dd>{{ selectedInfo.mode }}</dd></div>
        </dl>
        <button type="button" @click="clearBoxSelection">关闭</button>
      </aside>
    </div>

    <div v-if="sceneData" class="ppe-toolbar">
      <div class="ppe-toolbar__group">
        <button type="button" :class="{ active: playing }" @click="togglePlay">{{ playing ? '暂停' : '播放' }}</button>
        <button type="button" @click="replayAnim">重播</button>
        <label>倍速
          <select v-model.number="animSpeed">
            <option :value="0.5">0.5×</option>
            <option :value="1">1×</option>
            <option :value="2">2×</option>
            <option :value="4">4×</option>
          </select>
        </label>
      </div>
      <div class="ppe-toolbar__group">
        <button type="button" @click="setPresetView('iso')">等轴</button>
        <button type="button" @click="setPresetView('front')">正视</button>
        <button type="button" @click="setPresetView('side')">侧视</button>
        <button type="button" @click="setPresetView('top')">俯视</button>
      </div>
      <div class="ppe-toolbar__group">
        <label>分层 {{ layerFilter === 0 ? '全部' : layerFilter + '层' }}</label>
        <input type="range" min="0" :max="maxLayerHud" step="1" v-model.number="layerFilter" />
      </div>
      <div class="ppe-toolbar__group">
        <label>爆炸 {{ explodeGap.toFixed(1) }}</label>
        <input type="range" min="0" max="2" step="0.1" v-model.number="explodeGap" />
      </div>
    </div>
    <div v-if="palletTabs.length > 1" class="ppe-tabs">
      <button
        v-for="tab in palletTabs"
        :key="tab.no"
        type="button"
        :class="{ active: String(tab.no) === String(selectedPalletNo) }"
        @click="emit('select-pallet', tab.no)"
      >{{ tab.no }}<small>{{ tab.count }}箱</small></button>
    </div>
  </div>
</template>

<script setup>
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from "vue";
import * as THREE from "three";
import {
  getBrakeDiscAsset,
  isBrakeDiscAssetLoadSettled,
  loadBrakeDiscAsset,
} from "./brakeDiscAsset";
import { PALLET_COLORS, createPackagePalletPreviewSceneData } from "./geometry";

const MIN_CAMERA_ZOOM = 0.75;
const MAX_CAMERA_ZOOM = 1.6;
const ZOOM_STEP = 0.12;
const SCENE_UNIT_SCALE = 0.0026;
const SCENE_GRID_GAP_RATIO = 0.34;
const CAMERA_VIEW_DISTANCE = 11;
const CAMERA_BASE_DIRECTION = new THREE.Vector3(5.4, 4.2, 6.4).normalize();
const CAMERA_BASE_AZIMUTH = Math.atan2(CAMERA_BASE_DIRECTION.z, CAMERA_BASE_DIRECTION.x);
const CAMERA_BASE_HEIGHT_OFFSET = CAMERA_BASE_DIRECTION.y * CAMERA_VIEW_DISTANCE;
const CAMERA_BASE_HORIZONTAL_DISTANCE =
  Math.hypot(CAMERA_BASE_DIRECTION.x, CAMERA_BASE_DIRECTION.z) * CAMERA_VIEW_DISTANCE;
const SCENE_BACKGROUND_COLOR = "#eef2f6";
const SCENE_FLOOR_COLOR = "#e3e7ed";
const SCENE_GRID_COLOR = "#c2cad6";
const MAX_RENDER_PIXEL_RATIO = 1.5;
const SHADOW_MAP_SIZE = 1024;
const CARTON_OPENING_COLOR = "#4f3019";
const CARTON_TAPE_COLOR = "#ead69f";
const CARTON_TAPE_EDGE_COLOR = "#9c7137";
const CARTON_TAPE_HIGHLIGHT_COLOR = "#fff0c6";
const VIRTUAL_BOX_COLORS = Object.freeze({
  bottom: "#ffffff",
  stroke: "#ffffff",
});
const VIRTUAL_BOX_OPACITY = 0.28;
const VIRTUAL_BOX_SURFACE_OFFSET = 0.5;

const PALLET_WOOD_VARIANTS = Object.freeze([
  { top: "#efc78a", front: "#bf8148", side: "#9f6537" },
  { top: "#e4b16f", front: "#a96e3d", side: "#8c572f" },
  { top: "#f1cf98", front: "#c78b52", side: "#a66b3e" },
  { top: "#dca263", front: "#9d6235", side: "#7e4e2a" },
]);

defineOptions({
  name: "PackagePalletPreviewEnhanced",
});

const props = defineProps({
  palletLength: {
    type: [Number, String],
    default: null,
  },
  palletWidth: {
    type: [Number, String],
    default: null,
  },
  palletHeight: {
    type: [Number, String],
    default: 0,
  },
  palletItems: {
    type: Array,
    default: () => [],
  },
  boxes: {
    type: Array,
    default: () => [],
  },
  selectedPalletNo: {
    type: [String, Number],
    default: "",
  },
  selectedBoxKeys: {
    type: Array,
    default: () => [],
  },
  compact: {
    type: Boolean,
    default: false,
  },
  showLabels: {
    type: Boolean,
    default: true,
  },
  showCartonTape: {
    type: Boolean,
    default: true,
  },
  viewRotation: {
    type: [Number, String],
    default: 0,
  },
  rotationScopeKey: {
    type: [String, Number],
    default: "",
  },
  palletRotationScopes: {
    type: Object,
    default: () => ({}),
  },
  showRotateControls: {
    type: Boolean,
    default: true,
  },
  weightLimit: { type: [Number, String], default: 500 },
  heightLimit: { type: [Number, String], default: 1200 },
  areaUtil: { type: [Number, String], default: 0 },
  heightUtil: { type: [Number, String], default: 0 },
});
const emit = defineEmits(["select-pallet"]);

const canvasHostRef = ref(null);
const canvasRef = ref(null);
const isDragging = ref(false);
const currentZoom = ref(1);
const currentViewRotation = ref(Number(props.viewRotation) || 0);
const currentViewRotationByScope = ref({});
const playing = ref(false);
const animSpeed = ref(1);
const animIndex = ref(0);
const layerFilter = ref(0);
const explodeGap = ref(0);
const selectedInfo = ref(null);
const trackedBoxes = ref([]);
let boxMeshEntries = [];
let animClock = 0;
let animRaf = 0;
const raycaster = new THREE.Raycaster();
const pointerNdc = new THREE.Vector2();
let pointerDownPos = null;

const currentRotationScopeKey = computed(() => String(props.rotationScopeKey || "").trim());

let renderer = null;
let threeScene = null;
let camera = null;
let resizeObserver = null;
let frameId = 0;
let renderFrameId = 0;
let dragStartX = 0;
let dragStartRotation = 0;
let activeCameraFrame = null;
let materialCache = new Map();
let geometryCache = new Map();

const sceneData = computed(() =>
  createPackagePalletPreviewSceneData({
    palletLength: props.palletLength,
    palletWidth: props.palletWidth,
    palletHeight: props.palletHeight,
    palletItems: props.palletItems,
    boxes: props.boxes,
    selectedPalletNo: props.selectedPalletNo,
    selectedBoxKeys: props.selectedBoxKeys,
    compact: props.compact,
    showLabels: props.showLabels,
    showCartonTape: props.showCartonTape,
  })
);

const sceneLabels = computed(() =>
  (sceneData.value?.scenes || []).map((scene) => scene.label).filter(Boolean)
);
const canZoomIn = computed(() => currentZoom.value < MAX_CAMERA_ZOOM - 0.001);
const canZoomOut = computed(() => currentZoom.value > MIN_CAMERA_ZOOM + 0.001);

function fmtPct(v) {
  const n = Number(v);
  if (!Number.isFinite(n)) return "-";
  return (n <= 1 ? n * 100 : n).toFixed(1) + "%";
}
function fmtNum(v) {
  const n = Number(v);
  return Number.isFinite(n) ? String(Math.round(n * 100) / 100) : "-";
}
const totalWeightHud = computed(() =>
  trackedBoxes.value.reduce((s, b) => s + Number(b.boxWeight || 0), 0)
);
const stackHeightHud = computed(() => {
  let h = 0;
  for (const b of trackedBoxes.value) {
    h = Math.max(h, Number(b.positionZ || 0) + Number(b.occupyHeight || b.boxHeight || b.height || 0));
  }
  return h;
});
const maxLayerHud = computed(() =>
  trackedBoxes.value.reduce((m, b) => Math.max(m, Number(b.layerNo || b.layer || 1)), 1)
);
const visibleBoxCount = computed(() => Math.min(animIndex.value, trackedBoxes.value.length));
const rulerFillPct = computed(() => {
  const lim = Number(props.heightLimit) || 1;
  return Math.min(100, (Number(stackHeightHud.value) / lim) * 100);
});
const palletTabs = computed(() => {
  const map = new Map();
  for (const item of props.palletItems || []) {
    map.set(item.palletNo, { no: item.palletNo, count: item.boxCount });
  }
  if (!map.size) {
    for (const b of props.boxes || []) {
      const no = b.palletNo || "P1";
      map.set(no, { no, count: (map.get(no)?.count || 0) + 1 });
    }
  }
  return [...map.values()];
});

watch(
  () => props.viewRotation,
  (value) => {
    currentViewRotation.value = Number(value) || 0;
    applyCameraOrbit();
  }
);

watch(sceneData, () => {
  scheduleSceneRebuild();
});

watch(
  () => props.palletRotationScopes,
  () => {
    applyCameraOrbit();
  },
  { deep: true }
);

watch(currentRotationScopeKey, () => {
  applyCameraOrbit();
});


function applyEnhancementVisibility() {
  const layer = layerFilter.value;
  const explode = explodeGap.value;
  const shown = animIndex.value;
  boxMeshEntries.forEach((entry) => {
    const layerOk = layer === 0 || entry.layer <= layer;
    entry.mesh.visible = entry.index < shown && layerOk;
    if (entry.home) {
      entry.mesh.position.copy(entry.home);
      if (explode > 0) {
        entry.mesh.position.y = entry.home.y + (entry.layer - 1) * explode * 90;
      }
    }
    const selected = selectedInfo.value && selectedInfo.value.index === entry.index;
    entry.mesh.scale.setScalar(selected ? 1.035 : 1);
  });
  scheduleThreeRender();
}

function pickBoxAt(event) {
  if (!camera || !canvasRef.value || !boxMeshEntries.length) return;
  const rect = canvasRef.value.getBoundingClientRect();
  pointerNdc.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
  pointerNdc.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;
  raycaster.setFromCamera(pointerNdc, camera);
  const meshes = boxMeshEntries.filter((e) => e.mesh.visible).map((e) => e.mesh);
  const hits = raycaster.intersectObjects(meshes, false);
  if (!hits.length) {
    clearBoxSelection();
    return;
  }
  const entry = hits[0].object.userData.boxEntry;
  if (!entry) return;
  const box = entry.box;
  selectedInfo.value = {
    index: entry.index,
    product: box.productLabel || box.productKey || "-",
    boxNo: box.key || `#${entry.index + 1}`,
    size: `${box.length}×${box.width}×${box.height} mm`,
    weight: box.boxWeight ?? "-",
    pos: `(${box.x}, ${box.y}, ${box.z})`,
    layer: box.layerNo || entry.layer,
    mode: box.packageMode || "box",
  };
  applyEnhancementVisibility();
}

function clearBoxSelection() {
  selectedInfo.value = null;
  applyEnhancementVisibility();
}

function togglePlay() {
  if (playing.value) {
    playing.value = false;
    return;
  }
  if (animIndex.value >= boxMeshEntries.length) animIndex.value = 0;
  playing.value = true;
  startAnimLoop();
}

function replayAnim() {
  animIndex.value = 0;
  playing.value = true;
  applyEnhancementVisibility();
  startAnimLoop();
}

function startAnimLoop() {
  cancelAnimationFrame(animRaf);
  animClock = 0;
  const step = (ts) => {
    if (!playing.value) return;
    if (!animClock) animClock = ts;
    const interval = 260 / animSpeed.value;
    if (ts - animClock >= interval) {
      animClock = ts;
      if (animIndex.value < boxMeshEntries.length) {
        animIndex.value += 1;
        applyEnhancementVisibility();
      } else {
        playing.value = false;
        return;
      }
    }
    animRaf = requestAnimationFrame(step);
  };
  animRaf = requestAnimationFrame(step);
}

function setPresetView(name) {
  const map = { iso: 0, front: -50, side: 50, top: 180 };
  if (name === "top") currentZoom.value = Math.min(MAX_CAMERA_ZOOM, 1.4);
  else currentZoom.value = 1;
  setActiveRotation(map[name] ?? 0);
  applyCameraZoom();
  scheduleThreeRender();
}

watch([layerFilter, explodeGap], () => applyEnhancementVisibility());

onMounted(() => {
  initThreeRenderer();
  resizeObserver = new ResizeObserver(() => scheduleSceneRebuild());
  if (canvasHostRef.value) {
    resizeObserver.observe(canvasHostRef.value);
  }
  scheduleSceneRebuild();
});

onBeforeUnmount(() => {
  cancelAnimationFrame(frameId);
  cancelAnimationFrame(renderFrameId);
  cancelAnimationFrame(animRaf);
  playing.value = false;
  resizeObserver?.disconnect();
  clearThreeScene();
  renderer?.dispose();
  renderer = null;
  threeScene = null;
  camera = null;
});

function normalizeRotation(value) {
  return (((Number(value) || 0) % 360) + 360) % 360;
}

function getActiveRotation() {
  const scopeKey = currentRotationScopeKey.value;
  if (!scopeKey) {
    return currentViewRotation.value;
  }
  if (Object.prototype.hasOwnProperty.call(currentViewRotationByScope.value, scopeKey)) {
    return currentViewRotationByScope.value[scopeKey];
  }
  return currentViewRotation.value;
}

function setActiveRotation(value) {
  const nextRotation = normalizeRotation(value);
  const scopeKey = currentRotationScopeKey.value;
  if (!scopeKey) {
    if (Math.abs(nextRotation - currentViewRotation.value) < 0.001) {
      return;
    }
    currentViewRotation.value = nextRotation;
    applyCameraOrbit();
    return;
  }
  if (Math.abs(nextRotation - getActiveRotation()) < 0.001) {
    return;
  }
  currentViewRotationByScope.value[scopeKey] = nextRotation;
  applyCameraOrbit();
}

function rotatePreview(step = 90) {
  setActiveRotation(getActiveRotation() + step);
}

function handlePointerDown(event) {
  if (!sceneData.value) {
    return;
  }
  isDragging.value = true;
  dragStartX = event.clientX;
  dragStartRotation = getActiveRotation();
  pointerDownPos = { x: event.clientX, y: event.clientY };
  event.currentTarget.setPointerCapture?.(event.pointerId);
}

function handlePointerMove(event) {
  if (!isDragging.value) {
    return;
  }
  setActiveRotation(dragStartRotation - (event.clientX - dragStartX) * 0.35);
}

function handlePointerUp(event) {
  const wasDragging = isDragging.value;
  const start = pointerDownPos;
  if (isDragging.value) {
    isDragging.value = false;
    event.currentTarget.releasePointerCapture?.(event.pointerId);
  }
  pointerDownPos = null;
  scheduleThreeRender();
  if (!wasDragging || !start) return;
  if (Math.hypot(event.clientX - start.x, event.clientY - start.y) > 6) return;
  pickBoxAt(event);
}

function handleWheel(event) {
  if (!sceneData.value) {
    return;
  }
  zoomPreview(event.deltaY > 0 ? -ZOOM_STEP : ZOOM_STEP);
}

function zoomPreview(delta) {
  const nextZoom = clampCameraZoom(currentZoom.value + delta);
  if (Math.abs(nextZoom - currentZoom.value) < 0.001) {
    return;
  }
  currentZoom.value = nextZoom;
  applyCameraZoom();
  scheduleThreeRender();
}

function resetPreviewZoom() {
  if (Math.abs(currentZoom.value - 1) < 0.001) {
    return;
  }
  currentZoom.value = 1;
  applyCameraZoom();
  scheduleThreeRender();
}

function clampCameraZoom(value) {
  return Math.min(MAX_CAMERA_ZOOM, Math.max(MIN_CAMERA_ZOOM, Number(value) || 1));
}

function applyCameraZoom() {
  if (!camera) {
    return;
  }
  camera.zoom = currentZoom.value;
  camera.updateProjectionMatrix();
}

function renderThreeScene() {
  if (!renderer || !threeScene || !camera) {
    return;
  }
  renderer.render(threeScene, camera);
}

function scheduleThreeRender() {
  if (renderFrameId) {
    return;
  }
  renderFrameId = requestAnimationFrame(() => {
    renderFrameId = 0;
    renderThreeScene();
  });
}

function initThreeRenderer() {
  if (!canvasRef.value || renderer) {
    return;
  }
  renderer = new THREE.WebGLRenderer({
    canvas: canvasRef.value,
    alpha: false,
    antialias: true,
    powerPreference: "high-performance",
  });
  renderer.setClearColor(SCENE_BACKGROUND_COLOR, 1);
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, MAX_RENDER_PIXEL_RATIO));
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 0.94;
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.autoUpdate = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;

  threeScene = new THREE.Scene();
  threeScene.background = new THREE.Color(SCENE_BACKGROUND_COLOR);
  threeScene.fog = new THREE.Fog(
    SCENE_BACKGROUND_COLOR,
    CAMERA_VIEW_DISTANCE * 1.45,
    CAMERA_VIEW_DISTANCE * 3.6
  );
  camera = new THREE.PerspectiveCamera(35, 1, 0.1, 1000);
}

function scheduleSceneRebuild() {
  cancelAnimationFrame(frameId);
  frameId = requestAnimationFrame(() => {
    rebuildThreeScene();
  });
}

function rebuildThreeScene() {
  if (!renderer || !threeScene || !camera) {
    return;
  }

  updateRendererSize();
  clearThreeScene();

  if (!sceneData.value) {
    renderThreeScene();
    return;
  }

  addLights();
  boxMeshEntries = [];
  const rootGroup = createRootGroup(sceneData.value);
  addSceneFloor(rootGroup, rootGroup.userData.cameraFrame);
  threeScene.add(rootGroup);
  positionCameraForScene(rootGroup.userData.cameraFrame);
  trackedBoxes.value = boxMeshEntries.map((e) => e.box);
  selectedInfo.value = null;
  animIndex.value = 0;
  playing.value = boxMeshEntries.length > 0;
  applyEnhancementVisibility();
  renderThreeScene();
  if (playing.value) startAnimLoop();

  if (
    !isBrakeDiscAssetLoadSettled() &&
    sceneData.value.scenes.some((scene) => scene.boxes.some((box) => box.packageMode === "virtual"))
  ) {
    loadBrakeDiscAsset().then(() => scheduleSceneRebuild());
  }
}

function updateRendererSize() {
  const host = canvasHostRef.value;
  if (!host || !renderer || !camera) {
    return;
  }
  const width = Math.max(1, host.clientWidth);
  const height = Math.max(1, host.clientHeight);
  renderer.setSize(width, height, false);
  camera.aspect = width / height;
  camera.updateProjectionMatrix();
}

function clearThreeScene() {
  if (!threeScene) {
    return;
  }
  activeCameraFrame = null;
  materialCache = new Map();
  geometryCache = new Map();
  threeScene.traverse((object) => {
    disposeObject(object);
  });
  threeScene.clear();
}

function disposeObject(object) {
  const brakeDiscAsset = getBrakeDiscAsset();
  if (object.isInstancedMesh && object.geometry === brakeDiscAsset?.geometry) {
    return;
  }
  object.geometry?.dispose?.();
  if (Array.isArray(object.material)) {
    object.material.forEach((material) => material.dispose?.());
    return;
  }
  if (object.material !== brakeDiscAsset?.material) {
    object.material?.dispose?.();
  }
}

function addLights() {
  const ambientLight = new THREE.AmbientLight(0xffffff, 0.26);
  const hemisphereLight = new THREE.HemisphereLight(0xfafcff, 0x8d97a7, 0.42);

  const keyLight = new THREE.DirectionalLight(0xfff2de, 3.05);
  keyLight.position.set(4.8, 9.6, 4.2);
  keyLight.castShadow = true;
  keyLight.shadow.mapSize.set(SHADOW_MAP_SIZE, SHADOW_MAP_SIZE);
  keyLight.shadow.camera.near = 0.5;
  keyLight.shadow.camera.far = 32;
  keyLight.shadow.camera.left = -7;
  keyLight.shadow.camera.right = 7;
  keyLight.shadow.camera.top = 7;
  keyLight.shadow.camera.bottom = -7;
  keyLight.shadow.bias = -0.00025;
  keyLight.shadow.radius = 1.4;

  const fillLight = new THREE.DirectionalLight(0xdfe9ff, 0.18);
  fillLight.position.set(-4.2, 3.8, 3.4);

  const rimLight = new THREE.DirectionalLight(0xffffff, 0.52);
  rimLight.position.set(-5.6, 5.6, -4.8);

  threeScene.add(ambientLight, hemisphereLight, keyLight, fillLight, rimLight);
}

function createRootGroup(data) {
  const rootGroup = new THREE.Group();
  const frameBox = new THREE.Box3();
  const sceneCount = data.scenes.length;
  const columns = sceneCount === 1 ? 1 : 2;
  const unitScale = SCENE_UNIT_SCALE;
  const footprintMaxSize = Math.max(data.source.palletLength, data.source.palletWidth, 1);
  const layoutGap = footprintMaxSize * unitScale * SCENE_GRID_GAP_RATIO;
  const columnGap = data.source.palletLength * unitScale + layoutGap;
  const rowGap = data.source.palletWidth * unitScale + layoutGap;

  data.scenes.forEach((scene, index) => {
    const col = columns === 1 ? 0 : index % columns;
    const row = columns === 1 ? 0 : Math.floor(index / columns);
    const sceneGroup = createPalletSceneGroup(scene);
    sceneGroup.scale.setScalar(unitScale);
    sceneGroup.position.set((col - (columns - 1) / 2) * columnGap, 0, row * rowGap);
    expandStableCameraFrame(frameBox, scene, sceneGroup.position, unitScale);
    rootGroup.add(sceneGroup);
  });

  rootGroup.userData.cameraFrame = frameBox;
  return rootGroup;
}

function expandStableCameraFrame(frameBox, scene, position, unitScale) {
  const width = scene.pallet.length * unitScale;
  const depth = scene.pallet.width * unitScale;
  const height = scene.stackHeight * unitScale;
  const min = new THREE.Vector3(position.x - width / 2, 0, position.z - depth / 2);
  const max = new THREE.Vector3(position.x + width / 2, height, position.z + depth / 2);
  frameBox.expandByPoint(min);
  frameBox.expandByPoint(max);
}

function createPalletSceneGroup(scene) {
  const group = new THREE.Group();
  addPalletMeshes(group, scene.pallet);
  const ordered = scene.boxes.slice().sort((a, b) => {
    const la = Number(a.layerNo || 1);
    const lb = Number(b.layerNo || 1);
    if (la !== lb) return la - lb;
    return a.z - b.z || a.y - b.y || a.x - b.x;
  });
  ordered.forEach((box) => {
    if (box.packageMode === "virtual") {
      addVirtualPackageMesh(group, scene.pallet, box);
      return;
    }
    addPackageBoxMesh(group, scene.pallet, box);
  });
  addVirtualBrakeDiscInstances(
    group,
    scene.pallet,
    scene.boxes.filter((box) => box.packageMode === "virtual")
  );
  addCartonTopDetails(
    group,
    scene.pallet,
    scene.boxes.filter((box) => box.packageMode === "carton")
  );
  return group;
}

function addPalletMeshes(group, pallet) {
  const topDeckHeight = Math.max(12, Math.min(pallet.height * 0.18, 30));
  const bottomDeckHeight = Math.max(10, Math.min(pallet.height * 0.14, 22));
  const bearerHeight = Math.max(18, pallet.height - topDeckHeight - bottomDeckHeight);
  const topDeckCount = Math.max(8, Math.min(12, Math.round(pallet.width / 95)));
  const topDeckGap = Math.max(2, pallet.width * 0.018);
  const topDeckDepth = (pallet.width - topDeckGap * (topDeckCount - 1)) / topDeckCount;
  const topDeckLength = pallet.length * 0.98;
  const bottomRailDepth = pallet.width * 0.16;
  const blockLength = pallet.length * 0.16;
  const blockDepth = pallet.width * 0.16;
  const blockXList = [
    -pallet.length / 2 + pallet.length * 0.18,
    0,
    pallet.length / 2 - pallet.length * 0.18,
  ];
  const blockZList = [
    -pallet.width / 2 + bottomRailDepth / 2,
    0,
    pallet.width / 2 - bottomRailDepth / 2,
  ];
  const topY = pallet.height - topDeckHeight / 2;
  const bearerY = bottomDeckHeight + bearerHeight / 2;
  const bottomY = bottomDeckHeight / 2;

  blockZList.forEach((z) => {
    addWoodCuboid(group, {
      size: [topDeckLength, bottomDeckHeight, bottomRailDepth],
      position: [0, bottomY, z],
      colors: getPalletWoodColors(z, true),
      edgeColor: PALLET_COLORS.stroke,
      edgeOpacity: 0.24,
    });
  });

  blockXList.forEach((x) => {
    blockZList.forEach((z, zIndex) => {
      addWoodCuboid(group, {
        size: [blockLength, bearerHeight, blockDepth],
        position: [x, bearerY, z],
        colors: getPalletWoodColors(zIndex + x, true),
        edgeColor: PALLET_COLORS.dark,
        edgeOpacity: 0.28,
      });
    });
  });

  Array.from({ length: topDeckCount }).forEach((_, index) => {
    const z = -pallet.width / 2 + topDeckDepth / 2 + index * (topDeckDepth + topDeckGap);
    addWoodCuboid(group, {
      size: [topDeckLength, topDeckHeight, topDeckDepth],
      position: [0, topY, z],
      colors: getPalletWoodColors(index),
      edgeColor: PALLET_COLORS.stroke,
      edgeOpacity: 0.22,
      grainSeed: index,
    });
  });
}

function getPalletWoodColors(seed, support = false) {
  const index = Math.abs(Math.round(Number(seed) || 0)) % PALLET_WOOD_VARIANTS.length;
  const variant = PALLET_WOOD_VARIANTS[index];
  if (support) {
    return [variant.side, PALLET_COLORS.support, variant.front];
  }
  return [variant.side, variant.front, variant.top];
}

function addWoodCuboid(group, { size, position, colors, edgeColor, edgeOpacity, grainSeed }) {
  const mesh = addCuboid(group, {
    size,
    position,
    colors,
    edgeColor,
    edgeOpacity,
  });

  if (Number.isFinite(grainSeed)) {
    addWoodGrainLines(group, size, position, grainSeed);
  }

  return mesh;
}

function addWoodGrainLines(group, size, position, seed = 0) {
  const [width, height, depth] = size;
  const lineCount = Math.max(3, Math.min(5, Math.round(depth / 28)));
  const y = position[1] + height / 2 + 0.35;
  const xStart = position[0] - width * 0.46;
  const xEnd = position[0] + width * 0.46;

  Array.from({ length: lineCount }).forEach((_, index) => {
    const z = position[2] - depth / 2 + ((index + 1) * depth) / (lineCount + 1);
    const offset = (((seed + index) % 3) - 1) * depth * 0.018;
    const geometry = new THREE.BufferGeometry().setFromPoints([
      new THREE.Vector3(xStart, y, z),
      new THREE.Vector3(position[0], y, z + offset),
      new THREE.Vector3(xEnd, y, z + offset * 0.45),
    ]);
    const material = createLineMaterial("grain", PALLET_COLORS.grain, 0.34);
    const line = new THREE.Line(geometry, material);
    group.add(line);
  });
}

function addPackageBoxMesh(group, pallet, box) {
  const { x, y, z } = resolvePackageBoxCenter(pallet, box);
  const mesh = addCuboid(group, {
    size: [box.length, box.height, box.width],
    position: [x, y, z],
    colors: [box.colors.side, box.colors.front, box.colors.top],
    edgeColor: box.selected ? "#2447a1" : box.colors.stroke,
    edgeOpacity: box.selected ? 0.9 : 0.46,
    selected: box.selected,
  });
  // Small side sticker label — keeps original carton colors/tape intact
  const labelTex = createSideLabelTexture(box);
  const labelMat = new THREE.MeshBasicMaterial({ map: labelTex, transparent: true, depthWrite: false });
  const lw = Math.min(box.length * 0.5, 200);
  const lh = Math.min(box.height * 0.26, 80);
  const label = new THREE.Mesh(new THREE.PlaneGeometry(lw, lh), labelMat);
  label.position.set(0, box.height * 0.06, box.width / 2 + 0.9);
  label.renderOrder = 5;
  mesh.add(label);
  const entry = {
    mesh,
    box,
    layer: Number(box.layerNo || 1),
    home: mesh.position.clone(),
    index: boxMeshEntries.length,
  };
  mesh.userData.boxEntry = entry;
  boxMeshEntries.push(entry);
}

function createSideLabelTexture(box) {
  const c = document.createElement("canvas");
  c.width = 256;
  c.height = 128;
  const ctx = c.getContext("2d");
  ctx.fillStyle = "#f4efe4";
  ctx.fillRect(0, 0, 256, 128);
  ctx.strokeStyle = "#8d6a3c";
  ctx.lineWidth = 4;
  ctx.strokeRect(3, 3, 250, 122);
  ctx.fillStyle = "#6b4a28";
  ctx.fillRect(3, 3, 250, 28);
  ctx.fillStyle = "#fff";
  ctx.font = "bold 16px sans-serif";
  ctx.fillText("LABEL", 12, 22);
  ctx.fillStyle = "#2b1a0e";
  ctx.font = "bold 18px sans-serif";
  ctx.fillText(String(box.productKey || box.productLabel || "PROD").slice(0, 14), 12, 58);
  ctx.font = "14px sans-serif";
  ctx.fillText(`No.${String(box.key || "").slice(-8) || "-"}`, 12, 82);
  ctx.fillText(`${Math.round(box.length)}x${Math.round(box.width)}x${Math.round(box.height)}`, 12, 104);
  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

function addVirtualPackageMesh(group, pallet, box) {
  const { x, z } = resolvePackageBoxCenter(pallet, box);
  const bottomY = pallet.height + box.z + VIRTUAL_BOX_SURFACE_OFFSET;
  const { planeGeometry, edgeGeometry } = getCachedVirtualBottomGeometries();
  const mesh = new THREE.Mesh(
    planeGeometry,
    createTransparentMaterial(VIRTUAL_BOX_COLORS.bottom, VIRTUAL_BOX_OPACITY, box.selected)
  );
  mesh.scale.set(box.length, box.width, 1);
  mesh.rotation.x = -Math.PI / 2;
  mesh.position.set(x, bottomY, z);
  mesh.renderOrder = 2;
  mesh.castShadow = false;
  mesh.receiveShadow = true;
  group.add(mesh);

  const edgeMaterial = createLineMaterial(
    "virtual-edge",
    box.selected ? "#2447a1" : VIRTUAL_BOX_COLORS.stroke,
    box.selected ? 0.95 : 0.82
  );
  const edges = new THREE.LineSegments(edgeGeometry, edgeMaterial);
  edges.scale.copy(mesh.scale);
  edges.rotation.copy(mesh.rotation);
  edges.position.copy(mesh.position);
  edges.renderOrder = 3;
  group.add(edges);

  return { mesh, edges };
}

function createTransparentMaterial(color, opacity, selected = false) {
  const key = `transparent-face:${color}:${opacity}:${selected ? "1" : "0"}`;
  return getCachedMaterial(
    key,
    () =>
      new THREE.MeshStandardMaterial({
        color,
        transparent: true,
        opacity,
        depthWrite: false,
        roughness: 0.82,
        metalness: 0.02,
        emissive: selected ? new THREE.Color("#dbe7ff") : new THREE.Color("#000000"),
        emissiveIntensity: selected ? 0.16 : 0,
        side: THREE.DoubleSide,
      })
  );
}

function addVirtualBrakeDiscInstances(group, pallet, virtualBoxes = []) {
  const brakeDiscAsset = getBrakeDiscAsset();
  if (!brakeDiscAsset || !virtualBoxes.length) {
    return;
  }
  const mesh = new THREE.InstancedMesh(
    brakeDiscAsset.geometry,
    brakeDiscAsset.material,
    virtualBoxes.length
  );
  const matrix = new THREE.Matrix4();
  const quaternion = new THREE.Quaternion();
  const sourceSize = brakeDiscAsset.size;
  virtualBoxes.forEach((box, index) => {
    const center = resolvePackageBoxCenter(pallet, box);
    const scaleX = box.length / Math.max(sourceSize.x, 1);
    const scaleZ = box.width / Math.max(sourceSize.z, 1);
    const scaleY = box.height / Math.max(sourceSize.y, 1);
    matrix.compose(
      new THREE.Vector3(
        center.x,
        pallet.height + box.z + VIRTUAL_BOX_SURFACE_OFFSET + box.height / 2,
        center.z
      ),
      quaternion,
      new THREE.Vector3(scaleX, scaleY, scaleZ)
    );
    mesh.setMatrixAt(index, matrix);
  });
  mesh.instanceMatrix.setUsage(THREE.StaticDrawUsage);
  mesh.instanceMatrix.needsUpdate = true;
  mesh.renderOrder = 4;
  mesh.castShadow = true;
  mesh.receiveShadow = true;
  group.add(mesh);
}

function resolvePackageBoxCenter(pallet, box) {
  return {
    x: -pallet.length / 2 + box.x + box.length / 2,
    y: pallet.height + box.z + box.height / 2,
    z: -pallet.width / 2 + box.y + box.width / 2,
  };
}

function addCartonTopDetails(group, pallet, cartonBoxes = []) {
  if (!cartonBoxes.length) {
    return;
  }

  const flapStripsByColor = new Map();
  const openingStrips = [];
  const tapeStrips = [];
  const tapeEdgeStrips = [];
  const tapeHighlightStrips = [];
  const tapeEndStrips = [];
  const tapeEndEdgeStrips = [];
  const tapeEndHighlightStrips = [];

  cartonBoxes.forEach((box) => {
    const center = resolvePackageBoxCenter(pallet, box);
    const topY = center.y + box.height / 2;
    const openingWidth = Math.max(4, Math.min(box.width * 0.026, 12));
    const endGapWidth = Math.max(2, Math.min(box.length * 0.009, 5));
    const flapDepth = Math.max(1, (box.width - openingWidth) / 2);
    const flapLength = Math.max(1, box.length - endGapWidth * 2);
    const tapeWidth = Math.max(28, Math.min(box.width * 0.28, 84));
    const tapeDropHeight = Math.max(30, Math.min(box.height * 0.28, 80));

    if (!flapStripsByColor.has(box.colors.top)) {
      flapStripsByColor.set(box.colors.top, []);
    }
    [-1, 1].forEach((direction) => {
      flapStripsByColor.get(box.colors.top).push({
        position: [
          center.x,
          topY + 0.52,
          center.z + direction * (openingWidth / 2 + flapDepth / 2),
        ],
        size: [flapLength, flapDepth],
      });
    });

    openingStrips.push({
      position: [center.x, topY + 0.8, center.z],
      size: [flapLength, openingWidth],
    });
    [-1, 1].forEach((direction) => {
      openingStrips.push({
        position: [
          center.x + direction * (box.length / 2 - endGapWidth / 2),
          topY + 0.74,
          center.z,
        ],
        size: [endGapWidth, box.width],
      });
    });

    if (box.cartonTapeVisible) {
      tapeStrips.push({
        position: [center.x, topY + 1.25, center.z],
        size: [box.length + 2, tapeWidth],
      });
      [-1, 1].forEach((direction) => {
        tapeEdgeStrips.push({
          position: [center.x, topY + 1.31, center.z + direction * tapeWidth * 0.48],
          size: [box.length + 1, Math.max(1.6, tapeWidth * 0.035)],
        });
        tapeEndStrips.push({
          position: [
            center.x + direction * (box.length / 2 + 0.55),
            topY - tapeDropHeight / 2,
            center.z,
          ],
          size: [tapeWidth, tapeDropHeight],
        });
        [-1, 1].forEach((edgeDirection) => {
          tapeEndEdgeStrips.push({
            position: [
              center.x + direction * (box.length / 2 + 0.61),
              topY - tapeDropHeight / 2,
              center.z + edgeDirection * tapeWidth * 0.48,
            ],
            size: [Math.max(1.6, tapeWidth * 0.035), tapeDropHeight],
          });
        });
        tapeEndHighlightStrips.push({
          position: [
            center.x + direction * (box.length / 2 + 0.64),
            topY - tapeDropHeight / 2,
            center.z - tapeWidth * 0.28,
          ],
          size: [Math.max(2, tapeWidth * 0.1), tapeDropHeight * 0.94],
        });
      });
      tapeHighlightStrips.push({
        position: [center.x, topY + 1.34, center.z - tapeWidth * 0.28],
        size: [box.length, Math.max(2, tapeWidth * 0.1)],
      });
    }
  });

  flapStripsByColor.forEach((strips, color) => {
    addInstancedTopStrips(group, strips, {
      type: `carton-flap-${color}`,
      color,
      opacity: 1,
      renderOrder: 1,
    });
  });
  addInstancedTopStrips(group, openingStrips, {
    type: "carton-opening",
    color: CARTON_OPENING_COLOR,
    opacity: 0.9,
    renderOrder: 2,
  });
  addInstancedTopStrips(group, tapeStrips, {
    type: "carton-tape",
    color: CARTON_TAPE_COLOR,
    opacity: 0.78,
    renderOrder: 3,
  });
  addInstancedTopStrips(group, tapeEdgeStrips, {
    type: "carton-tape-edge",
    color: CARTON_TAPE_EDGE_COLOR,
    opacity: 0.72,
    renderOrder: 4,
  });
  addInstancedTopStrips(group, tapeHighlightStrips, {
    type: "carton-tape-highlight",
    color: CARTON_TAPE_HIGHLIGHT_COLOR,
    opacity: 0.72,
    renderOrder: 5,
  });
  addInstancedEndStrips(group, tapeEndStrips, {
    type: "carton-tape-end",
    color: CARTON_TAPE_COLOR,
    opacity: 0.78,
    renderOrder: 3,
  });
  addInstancedEndStrips(group, tapeEndEdgeStrips, {
    type: "carton-tape-end-edge",
    color: CARTON_TAPE_EDGE_COLOR,
    opacity: 0.72,
    renderOrder: 4,
  });
  addInstancedEndStrips(group, tapeEndHighlightStrips, {
    type: "carton-tape-end-highlight",
    color: CARTON_TAPE_HIGHLIGHT_COLOR,
    opacity: 0.72,
    renderOrder: 5,
  });
}

function addInstancedTopStrips(group, strips, { type, color, opacity = 1, renderOrder = 0 }) {
  addInstancedSurfaceStrips(
    group,
    strips,
    { type, color, opacity, renderOrder },
    new THREE.Euler(-Math.PI / 2, 0, 0)
  );
}

function addInstancedEndStrips(group, strips, { type, color, opacity = 1, renderOrder = 0 }) {
  addInstancedSurfaceStrips(
    group,
    strips,
    { type, color, opacity, renderOrder },
    new THREE.Euler(0, Math.PI / 2, 0)
  );
}

function addInstancedSurfaceStrips(
  group,
  strips,
  { type, color, opacity = 1, renderOrder = 0 },
  rotationEuler
) {
  if (!strips.length) {
    return;
  }

  const mesh = new THREE.InstancedMesh(
    getCachedUnitPlaneGeometry(),
    createTopDetailMaterial(type, color, opacity),
    strips.length
  );
  const rotation = new THREE.Quaternion().setFromEuler(rotationEuler);
  const matrix = new THREE.Matrix4();

  strips.forEach((strip, index) => {
    matrix.compose(
      new THREE.Vector3(...strip.position),
      rotation,
      new THREE.Vector3(strip.size[0], strip.size[1], 1)
    );
    mesh.setMatrixAt(index, matrix);
  });

  mesh.instanceMatrix.setUsage(THREE.StaticDrawUsage);
  mesh.instanceMatrix.needsUpdate = true;
  mesh.renderOrder = renderOrder;
  mesh.receiveShadow = true;
  mesh.computeBoundingSphere();
  group.add(mesh);
}

function addCuboid(group, { size, position, colors, edgeColor, edgeOpacity = 0.42, selected }) {
  const [width, height, depth] = size;
  const { boxGeometry, edgeGeometry } = getCachedCuboidGeometries(width, height, depth);
  const [sideColor, frontColor, topColor] = colors;
  const materials = [
    createMaterial(sideColor, selected),
    createMaterial(sideColor, selected),
    createMaterial(topColor, selected),
    createMaterial(frontColor, selected),
    createMaterial(frontColor, selected),
    createMaterial(frontColor, selected),
  ];
  const mesh = new THREE.Mesh(boxGeometry, materials);
  mesh.position.set(...position);
  mesh.castShadow = true;
  mesh.receiveShadow = true;
  group.add(mesh);

  const edgeMaterial = createLineMaterial("edge", edgeColor, edgeOpacity);
  const edges = new THREE.LineSegments(edgeGeometry, edgeMaterial);
  edges.position.copy(mesh.position);
  group.add(edges);

  return mesh;
}

function getCachedCuboidGeometries(width, height, depth) {
  const key = `${width.toFixed(3)}:${height.toFixed(3)}:${depth.toFixed(3)}`;
  if (!geometryCache.has(key)) {
    const boxGeometry = new THREE.BoxGeometry(width, height, depth);
    geometryCache.set(key, {
      boxGeometry,
      edgeGeometry: new THREE.EdgesGeometry(boxGeometry),
    });
  }
  return geometryCache.get(key);
}

function getCachedUnitPlaneGeometry() {
  const key = "unit-top-detail-plane";
  if (!geometryCache.has(key)) {
    geometryCache.set(key, {
      planeGeometry: new THREE.PlaneGeometry(1, 1),
    });
  }
  return geometryCache.get(key).planeGeometry;
}

function getCachedVirtualBottomGeometries() {
  const key = "virtual-bottom-plane";
  if (!geometryCache.has(key)) {
    const planeGeometry = new THREE.PlaneGeometry(1, 1);
    geometryCache.set(key, {
      planeGeometry,
      edgeGeometry: new THREE.EdgesGeometry(planeGeometry),
    });
  }
  return geometryCache.get(key);
}

function createMaterial(color, selected = false) {
  const key = `face:${color}:${selected ? "1" : "0"}`;
  return getCachedMaterial(
    key,
    () =>
      new THREE.MeshStandardMaterial({
        color,
        roughness: 0.9,
        metalness: 0.01,
        emissive: selected ? new THREE.Color("#dbe7ff") : new THREE.Color("#000000"),
        emissiveIntensity: selected ? 0.12 : 0,
      })
  );
}

function createLineMaterial(type, color, opacity) {
  const key = `${type}:${color}:${opacity}`;
  return getCachedMaterial(
    key,
    () =>
      new THREE.LineBasicMaterial({
        color,
        transparent: true,
        opacity,
      })
  );
}

function createTopDetailMaterial(type, color, opacity) {
  return getCachedMaterial(
    `${type}:${color}:${opacity}`,
    () =>
      new THREE.MeshStandardMaterial({
        color,
        transparent: opacity < 1,
        opacity,
        roughness: type.includes("tape") ? 0.5 : 0.94,
        metalness: 0,
        depthWrite: opacity >= 0.9,
        side: THREE.DoubleSide,
      })
  );
}

function getCachedMaterial(key, factory) {
  if (!materialCache.has(key)) {
    materialCache.set(key, factory());
  }
  return materialCache.get(key);
}

function resolveCameraOrbitAngle() {
  const scopeKey = currentRotationScopeKey.value;
  if (
    scopeKey &&
    Object.prototype.hasOwnProperty.call(currentViewRotationByScope.value, scopeKey)
  ) {
    return currentViewRotationByScope.value[scopeKey];
  }
  return currentViewRotation.value;
}

function resolveResponsiveCameraZoom() {
  const aspect = Number(camera?.aspect) || 1;
  const narrowViewportFactor = aspect < 0.72 ? Math.max(0.56, aspect / 0.72) : 1;
  return currentZoom.value * narrowViewportFactor;
}

function applyCameraOrbit({ render = true } = {}) {
  if (!camera || !activeCameraFrame) {
    return;
  }
  positionCameraForScene(activeCameraFrame);
  if (render) {
    scheduleThreeRender();
  }
}

function addSceneFloor(rootGroup, stableFrame) {
  const box =
    stableFrame instanceof THREE.Box3 ? stableFrame : new THREE.Box3().setFromObject(rootGroup);
  const size = box.getSize(new THREE.Vector3());
  const center = box.getCenter(new THREE.Vector3());
  const floorSize = Math.max(size.x, size.z, 4.8) * 2.4;
  const floorY = -0.024;
  const floorGeometry = new THREE.PlaneGeometry(floorSize, floorSize);
  const floorMaterial = new THREE.MeshStandardMaterial({
    color: SCENE_FLOOR_COLOR,
    roughness: 0.94,
    metalness: 0,
  });
  const floor = new THREE.Mesh(floorGeometry, floorMaterial);
  floor.rotation.x = -Math.PI / 2;
  floor.position.set(center.x, floorY, center.z);
  floor.receiveShadow = true;
  threeScene.add(floor);

  const grid = new THREE.GridHelper(floorSize, 18, SCENE_GRID_COLOR, SCENE_GRID_COLOR);
  grid.position.set(center.x, floorY + 0.003, center.z);
  grid.material.transparent = true;
  grid.material.opacity = 0.18;
  threeScene.add(grid);
}

function positionCameraForScene(stableFrame) {
  if (!camera || !stableFrame) {
    return;
  }
  const box =
    stableFrame instanceof THREE.Box3
      ? stableFrame.clone()
      : new THREE.Box3().setFromObject(stableFrame);
  if (box.isEmpty()) {
    return;
  }
  activeCameraFrame = box;
  const center = box.getCenter(new THREE.Vector3());
  const size = box.getSize(new THREE.Vector3());
  const orbitAngle = CAMERA_BASE_AZIMUTH - THREE.MathUtils.degToRad(resolveCameraOrbitAngle());
  const cameraX = center.x + Math.cos(orbitAngle) * CAMERA_BASE_HORIZONTAL_DISTANCE;
  const cameraZ = center.z + Math.sin(orbitAngle) * CAMERA_BASE_HORIZONTAL_DISTANCE;

  camera.position.set(cameraX, center.y + CAMERA_BASE_HEIGHT_OFFSET, cameraZ);
  camera.near = Math.max(0.01, CAMERA_VIEW_DISTANCE / 100);
  camera.far = CAMERA_VIEW_DISTANCE * 100;
  camera.lookAt(center.x, center.y + size.y * 0.08, center.z);
  camera.zoom = resolveResponsiveCameraZoom();
  camera.updateProjectionMatrix();
}
</script>

<style scoped lang="scss">
.package-pallet-preview {
  position: relative;
  display: grid;
  min-width: 0;
  height: 100%;
}

.package-pallet-preview__rotate-actions {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 3;
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 4px;
  background: rgb(255 255 255 / 88%);
  border: 1px solid #dfe6f3;
  border-radius: 999px;
  box-shadow: 0 8px 18px rgb(39 56 95 / 10%);
  backdrop-filter: blur(6px);
}

.package-pallet-preview__rotate-button {
  display: inline-grid;
  place-items: center;
  width: 26px;
  height: 26px;
  padding: 0;
  color: #405067;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 999px;
  transition:
    color 0.16s ease,
    background-color 0.16s ease;
}

.package-pallet-preview__rotate-button:hover {
  color: #395ef1;
  background: #f0f4ff;
}

.package-pallet-preview__rotate-button:disabled {
  color: #a9b2c3;
  cursor: not-allowed;
  background: transparent;
}

.package-pallet-preview__canvas {
  position: relative;
  display: flex;
  flex: 1 1 auto;
  align-items: stretch;
  justify-content: center;
  width: 100%;
  height: 100%;
  min-height: 180px;
  overflow: hidden;
  background: transparent;
}

.package-pallet-preview__three-canvas {
  position: relative;
  z-index: 1;
  display: block;
  width: 100%;
  height: 100%;
  touch-action: none;
  cursor: grab;
  outline: none;
}

.package-pallet-preview.is-dragging .package-pallet-preview__three-canvas {
  cursor: grabbing;
}

.package-pallet-preview__labels {
  position: absolute;
  bottom: 10px;
  left: 10px;
  z-index: 2;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  pointer-events: none;
}

.package-pallet-preview__labels span {
  padding: 3px 8px;
  font-size: 12px;
  line-height: 18px;
  color: #465066;
  background: rgb(255 255 255 / 78%);
  border: 1px solid rgb(213 220 232 / 72%);
  border-radius: 999px;
}

.package-pallet-preview__empty {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  min-height: 96px;
  text-align: center;
}

.package-pallet-preview__empty-title {
  font-size: 13px;
  font-weight: 400;
  line-height: 18px;
  color: #6b7280;
}

.package-pallet-preview.is-compact .package-pallet-preview__canvas {
  min-height: 92px;
}

.package-pallet-preview.is-compact .package-pallet-preview__empty {
  min-height: 72px;
}

.package-pallet-preview.is-compact .package-pallet-preview__empty-title {
  font-size: 12px;
  font-weight: 400;
  line-height: 18px;
}

.ppe-hud { position:absolute; left:10px; top:10px; z-index:4; background:rgba(15,23,42,.72); color:#f8fafc; border-radius:8px; padding:8px 10px; font-size:12px; max-width:70%; }
.ppe-hud__row { display:flex; flex-wrap:wrap; gap:10px; }
.ppe-hud__row + .ppe-hud__row { margin-top:4px; }
.ppe-hud b { color:#7dd3fc; }
.ppe-ruler { position:absolute; right:12px; top:52px; bottom:72px; width:26px; z-index:4; display:flex; gap:4px; }
.ppe-ruler__track { flex:1; background:rgba(255,255,255,.55); border:1px solid #94a3b8; border-radius:4px; position:relative; overflow:hidden; }
.ppe-ruler__fill { position:absolute; left:0; right:0; bottom:0; background:linear-gradient(180deg,#38bdf8,#0284c7); }
.ppe-ruler__labels { display:flex; flex-direction:column; justify-content:space-between; font-size:10px; color:#334155; }
.ppe-card { position:absolute; left:10px; bottom:10px; z-index:5; width:236px; background:#fff; border:1px solid #e2e8f0; border-radius:10px; box-shadow:0 8px 24px rgba(15,23,42,.12); padding:10px 12px; font-size:12px; }
.ppe-card header { font-weight:700; margin-bottom:6px; }
.ppe-card dl { margin:0; display:grid; gap:4px; }
.ppe-card dl > div { display:flex; justify-content:space-between; gap:8px; }
.ppe-card dt { color:#64748b; } .ppe-card dd { margin:0; font-weight:600; }
.ppe-card button { margin-top:8px; width:100%; border:1px solid #cbd5e1; background:#f8fafc; border-radius:6px; padding:4px; cursor:pointer; }
.ppe-toolbar { display:flex; flex-wrap:wrap; gap:10px; align-items:center; padding:8px 10px; background:#fff; border-top:1px solid #e2e8f0; font-size:12px; }
.ppe-toolbar__group { display:flex; gap:6px; align-items:center; }
.ppe-toolbar button { border:1px solid #cbd5e1; background:#f8fafc; border-radius:6px; padding:4px 8px; cursor:pointer; }
.ppe-toolbar button.active { background:#0ea5e9; color:#fff; border-color:#0284c7; }
.ppe-toolbar input[type='range'] { width:96px; }
.ppe-tabs { display:flex; gap:6px; padding:6px 10px 10px; background:#fff; overflow-x:auto; }
.ppe-tabs button { border:1px solid #cbd5e1; background:#f8fafc; border-radius:8px; padding:6px 10px; cursor:pointer; }
.ppe-tabs button small { display:block; color:#64748b; }
.ppe-tabs button.active { border-color:#0ea5e9; background:#e0f2fe; }
.package-pallet-preview { grid-template-rows: 1fr auto auto; }

</style>
