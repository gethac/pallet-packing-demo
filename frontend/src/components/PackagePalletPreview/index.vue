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
  name: "PackagePalletPreview",
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
});

const canvasHostRef = ref(null);
const canvasRef = ref(null);
const isDragging = ref(false);
const currentZoom = ref(1);
const currentViewRotation = ref(Number(props.viewRotation) || 0);
const currentViewRotationByScope = ref({});

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
  event.currentTarget.setPointerCapture?.(event.pointerId);
}

function handlePointerMove(event) {
  if (!isDragging.value) {
    return;
  }
  setActiveRotation(dragStartRotation - (event.clientX - dragStartX) * 0.35);
}

function handlePointerUp(event) {
  if (!isDragging.value) {
    return;
  }
  isDragging.value = false;
  event.currentTarget.releasePointerCapture?.(event.pointerId);
  scheduleThreeRender();
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
  const rootGroup = createRootGroup(sceneData.value);
  addSceneFloor(rootGroup, rootGroup.userData.cameraFrame);
  threeScene.add(rootGroup);
  positionCameraForScene(rootGroup.userData.cameraFrame);
  renderThreeScene();

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
  scene.boxes.forEach((box) => {
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
  addCuboid(group, {
    size: [box.length, box.height, box.width],
    position: [x, y, z],
    colors: [box.colors.side, box.colors.front, box.colors.top],
    edgeColor: box.selected ? "#2447a1" : box.colors.stroke,
    edgeOpacity: box.selected ? 0.9 : 0.46,
    selected: box.selected,
  });
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
</style>
