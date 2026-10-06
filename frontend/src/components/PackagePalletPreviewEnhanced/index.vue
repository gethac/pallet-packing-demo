<template>
  <div class="ppe" :class="{ 'is-empty': !hasBoxes }">
    <div ref="hostRef" class="ppe__canvas-host">
      <canvas ref="canvasRef" class="ppe__canvas" @pointerdown="onCanvasPointerDown" />
      <div v-if="!hasBoxes" class="ppe__empty">暂无托盘</div>

      <!-- HUD -->
      <div v-if="hasBoxes" class="ppe__hud">
        <div class="ppe__hud-row">
          <span>面积利用率 <b>{{ fmtPct(areaUtil) }}</b></span>
          <span>高度利用率 <b>{{ fmtPct(heightUtil) }}</b></span>
        </div>
        <div class="ppe__hud-row">
          <span>重量 <b>{{ fmtNum(totalWeight) }}</b> / {{ fmtNum(weightLimit) }} kg</span>
          <span>高度 <b>{{ fmtNum(stackHeight) }}</b> / {{ fmtNum(heightLimit) }} mm</span>
          <span>箱数 <b>{{ visibleCount }}/{{ boxesSorted.length }}</b></span>
        </div>
      </div>

      <!-- Height ruler -->
      <div v-if="hasBoxes" class="ppe__ruler" aria-hidden="true">
        <div class="ppe__ruler-track">
          <div class="ppe__ruler-fill" :style="{ height: rulerFillPct + '%' }"></div>
        </div>
        <div class="ppe__ruler-labels">
          <span>{{ fmtNum(heightLimit) }}</span>
          <span>{{ fmtNum(stackHeight) }}</span>
          <span>0</span>
        </div>
      </div>

      <!-- Info card -->
      <aside v-if="selectedInfo" class="ppe__card">
        <header>{{ selectedInfo.product }} · {{ selectedInfo.boxNo }}</header>
        <dl>
          <div><dt>尺寸</dt><dd>{{ selectedInfo.size }}</dd></div>
          <div><dt>重量</dt><dd>{{ selectedInfo.weight }} kg</dd></div>
          <div><dt>坐标</dt><dd>{{ selectedInfo.pos }}</dd></div>
          <div><dt>层号</dt><dd>{{ selectedInfo.layer }}</dd></div>
          <div><dt>包装</dt><dd>{{ selectedInfo.mode }}</dd></div>
        </dl>
        <button type="button" class="ppe__card-close" @click="clearSelection">关闭</button>
      </aside>
    </div>

    <!-- Controls -->
    <div v-if="hasBoxes" class="ppe__toolbar">
      <div class="ppe__group">
        <button type="button" :class="{ active: playing }" @click="togglePlay">{{ playing ? '暂停' : '播放' }}</button>
        <button type="button" @click="replay">重播</button>
        <label class="ppe__speed">
          倍速
          <select v-model.number="speed">
            <option :value="0.5">0.5×</option>
            <option :value="1">1×</option>
            <option :value="2">2×</option>
            <option :value="4">4×</option>
          </select>
        </label>
      </div>
      <div class="ppe__group">
        <button type="button" @click="setPreset('iso')">等轴</button>
        <button type="button" @click="setPreset('front')">正视</button>
        <button type="button" @click="setPreset('side')">侧视</button>
        <button type="button" @click="setPreset('top')">俯视</button>
      </div>
      <div class="ppe__group ppe__slider">
        <label>分层 {{ layerFilter === 0 ? '全部' : layerFilter + '层' }}</label>
        <input type="range" min="0" :max="maxLayer" step="1" v-model.number="layerFilter" />
      </div>
      <div class="ppe__group ppe__slider">
        <label>爆炸 {{ explode.toFixed(1) }}</label>
        <input type="range" min="0" max="2" step="0.1" v-model.number="explode" />
      </div>
    </div>

    <!-- Multi pallet tabs -->
    <div v-if="palletTabs.length > 1" class="ppe__tabs">
      <button
        v-for="tab in palletTabs"
        :key="tab.no"
        type="button"
        :class="{ active: tab.no === selectedPalletNo }"
        @click="$emit('select-pallet', tab.no)"
      >
        {{ tab.no }}
        <small>{{ tab.count }}箱</small>
      </button>
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
} from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { EffectComposer } from 'three/examples/jsm/postprocessing/EffectComposer.js'
import { RenderPass } from 'three/examples/jsm/postprocessing/RenderPass.js'
import { GTAOPass } from 'three/examples/jsm/postprocessing/GTAOPass.js'
import { OutputPass } from 'three/examples/jsm/postprocessing/OutputPass.js'
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js'
import { createCartonMaterial, createWoodMaterial, createBoxLabelTexture } from './materials.js'

defineOptions({ name: 'PackagePalletPreviewEnhanced' })

const props = defineProps({
  palletLength: { type: [Number, String], default: 1200 },
  palletWidth: { type: [Number, String], default: 800 },
  palletHeight: { type: [Number, String], default: 120 },
  boxes: { type: Array, default: () => [] },
  selectedPalletNo: { type: [String, Number], default: '' },
  palletItems: { type: Array, default: () => [] },
  weightLimit: { type: [Number, String], default: 500 },
  heightLimit: { type: [Number, String], default: 1200 },
  areaUtil: { type: [Number, String], default: 0 },
  heightUtil: { type: [Number, String], default: 0 },
  enableAo: { type: Boolean, default: true },
})

defineEmits(['select-pallet'])

const UNIT = 0.0025
const hostRef = ref(null)
const canvasRef = ref(null)
const playing = ref(false)
const speed = ref(1)
const layerFilter = ref(0)
const explode = ref(0)
const selectedInfo = ref(null)
const animIndex = ref(0)

let renderer, scene, camera, controls, composer, gtaoPass, rootGroup
let boxMeshes = []
let animTimer = 0
let raycaster = new THREE.Raycaster()
let pointer = new THREE.Vector2()
let envMap = null
let resizeObs = null
let woodMat = null
let cartonMats = new Map()
let labelMats = []
let needsRender = true
let rafId = 0

const boxesSorted = computed(() => {
  const list = (props.boxes || [])
    .filter((b) => String(b.palletNo || '') === String(props.selectedPalletNo || b.palletNo || ''))
    .slice()
  list.sort((a, b) => {
    const la = Number(a.layerNo || a.layer || 0)
    const lb = Number(b.layerNo || b.layer || 0)
    if (la !== lb) return la - lb
    const za = Number(a.positionZ || 0)
    const zb = Number(b.positionZ || 0)
    if (za !== zb) return za - zb
    return Number(a.sort || 0) - Number(b.sort || 0)
  })
  return list
})

const hasBoxes = computed(() => boxesSorted.value.length > 0)
const maxLayer = computed(() =>
  boxesSorted.value.reduce((m, b) => Math.max(m, Number(b.layerNo || b.layer || 1)), 1)
)
const totalWeight = computed(() =>
  boxesSorted.value.reduce((s, b) => s + Number(b.boxWeight || 0), 0)
)
const stackHeight = computed(() => {
  let h = 0
  for (const b of boxesSorted.value) {
    h = Math.max(h, Number(b.positionZ || 0) + Number(b.occupyHeight || b.boxHeight || 0))
  }
  return h
})
const visibleCount = computed(() => Math.min(animIndex.value, boxesSorted.value.length))
const rulerFillPct = computed(() => {
  const lim = Number(props.heightLimit) || 1
  return Math.min(100, (Number(stackHeight.value) / lim) * 100)
})
const palletTabs = computed(() => {
  const map = new Map()
  for (const item of props.palletItems || []) {
    map.set(item.palletNo, { no: item.palletNo, count: item.boxCount })
  }
  if (!map.size) {
    for (const b of props.boxes || []) {
      const no = b.palletNo || 'P1'
      map.set(no, { no, count: (map.get(no)?.count || 0) + 1 })
    }
  }
  return [...map.values()]
})

function fmtPct(v) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '-'
  return (n <= 1 ? n * 100 : n).toFixed(1) + '%'
}
function fmtNum(v) {
  const n = Number(v)
  return Number.isFinite(n) ? (Math.round(n * 100) / 100).toString() : '-'
}

function getCartonMat(productKey) {
  const key = String(productKey || 'default')
  if (!cartonMats.has(key)) {
    const palette = ['#d4a574', '#c9955c', '#b8895a', '#cfae7a', '#e0b888', '#bc8f5e']
    const hex = palette[cartonMats.size % palette.length]
    cartonMats.set(key, createCartonMaterial(hex))
  }
  return cartonMats.get(key)
}

function clearSceneContent() {
  if (!rootGroup || !scene) return
  scene.remove(rootGroup)
  rootGroup.traverse((o) => {
    if (o.isMesh) {
      if (o.geometry && !o.userData.sharedGeom) o.geometry.dispose()
    }
  })
  for (const m of labelMats) {
    m.map?.dispose?.()
    m.dispose?.()
  }
  labelMats = []
  boxMeshes = []
  rootGroup = null
}

function buildScene() {
  if (!scene || !renderer) return
  clearSceneContent()
  selectedInfo.value = null
  const pL = Number(props.palletLength) || 1200
  const pW = Number(props.palletWidth) || 800
  const pH = Math.max(60, Number(props.palletHeight) || 120)
  rootGroup = new THREE.Group()
  woodMat = woodMat || createWoodMaterial()

  // pallet deck boards
  const deckH = pH * 0.22
  const boardCount = 7
  const gap = pW * 0.02
  const boardW = (pW - gap * (boardCount - 1)) / boardCount
  for (let i = 0; i < boardCount; i++) {
    const geo = new THREE.BoxGeometry(pL * 0.98, deckH, boardW)
    const mesh = new THREE.Mesh(geo, woodMat)
    mesh.castShadow = true
    mesh.receiveShadow = true
    mesh.position.set(0, deckH / 2, -pW / 2 + boardW / 2 + i * (boardW + gap))
    rootGroup.add(mesh)
  }
  // stringers
  for (const z of [-pW * 0.4, 0, pW * 0.4]) {
    const geo = new THREE.BoxGeometry(pL * 0.16, pH - deckH, pW * 0.14)
    const mesh = new THREE.Mesh(geo, woodMat)
    mesh.castShadow = true
    mesh.receiveShadow = true
    mesh.position.set(-pL * 0.4, deckH + (pH - deckH) / 2, z)
    rootGroup.add(mesh)
    const mesh2 = mesh.clone()
    mesh2.position.x = 0
    rootGroup.add(mesh2)
    const mesh3 = mesh.clone()
    mesh3.position.x = pL * 0.4
    rootGroup.add(mesh3)
  }
  // bottom rails
  for (const z of [-pW * 0.42, 0, pW * 0.42]) {
    const geo = new THREE.BoxGeometry(pL * 0.98, deckH * 0.7, pW * 0.14)
    const mesh = new THREE.Mesh(geo, woodMat)
    mesh.receiveShadow = true
    mesh.position.set(0, deckH * 0.35, z)
    rootGroup.add(mesh)
  }

  const deckTop = pH
  const boxes = boxesSorted.value
  boxes.forEach((box, index) => {
    const L = Number(box.occupyLength || box.boxLength || 1)
    const W = Number(box.occupyWidth || box.boxWidth || 1)
    const H = Number(box.occupyHeight || box.boxHeight || 1)
    const x = Number(box.positionX || 0)
    const y = Number(box.positionY || 0)
    const z = Number(box.positionZ || 0)
    const product = box.productLabel || box.productKey || box.orderProductId || 'PROD'
    const mat = getCartonMat(box.productKey || product)
    const geo = new THREE.BoxGeometry(L, H, W)
    const mesh = new THREE.Mesh(geo, mat)
    mesh.castShadow = true
    mesh.receiveShadow = true
    // origin at pallet corner (0,0) → center of pallet
    const cx = x + L / 2 - pL / 2
    const cy = deckTop + z + H / 2
    const cz = y + W / 2 - pW / 2
    mesh.position.set(cx, cy, cz)
    mesh.userData = {
      index,
      box,
      baseY: cy,
      layer: Number(box.layerNo || box.layer || 1),
      home: mesh.position.clone(),
    }
    // label on +Z face via decal-like plane
    const labelTex = createBoxLabelTexture({
      product,
      boxNo: box.boxKey || box.taskKey || `#${index + 1}`,
      weight: box.boxWeight,
      size: `${box.boxLength || L}×${box.boxWidth || W}×${box.boxHeight || H}`,
    })
    const labelMat = new THREE.MeshBasicMaterial({ map: labelTex, transparent: true })
    labelMats.push(labelMat)
    const label = new THREE.Mesh(new THREE.PlaneGeometry(L * 0.85, H * 0.45), labelMat)
    label.position.set(0, H * 0.05, W / 2 + 0.6)
    mesh.add(label)
    mesh.visible = false
    rootGroup.add(mesh)
    boxMeshes.push(mesh)
  })

  // floor
  const floor = new THREE.Mesh(
    new THREE.PlaneGeometry(pL * 4, pW * 4),
    new THREE.MeshStandardMaterial({ color: '#dfe5ee', roughness: 0.95, metalness: 0 })
  )
  floor.rotation.x = -Math.PI / 2
  floor.position.y = 0
  floor.receiveShadow = true
  rootGroup.add(floor)

  // grid helper subtle
  const grid = new THREE.GridHelper(Math.max(pL, pW) * 3, 20, 0xb0b8c4, 0xc9d0da)
  grid.position.y = 0.5
  rootGroup.add(grid)

  rootGroup.scale.setScalar(UNIT)
  scene.add(rootGroup)
  fitCamera()
  animIndex.value = boxes.length
  applyVisibility()
  needsRender = true
}

function fitCamera() {
  if (!camera || !controls) return
  const pL = (Number(props.palletLength) || 1200) * UNIT
  const pW = (Number(props.palletWidth) || 800) * UNIT
  const h = (Number(stackHeight.value) + Number(props.palletHeight || 120)) * UNIT
  const dist = Math.max(pL, pW, h) * 2.6
  camera.position.set(dist * 0.9, dist * 0.75, dist * 1.05)
  controls.target.set(0, h * 0.35, 0)
  controls.update()
}

function applyVisibility() {
  const layer = layerFilter.value
  const exp = explode.value
  const shown = animIndex.value
  boxMeshes.forEach((mesh) => {
    const idx = mesh.userData.index
    const layerOk = layer === 0 || mesh.userData.layer <= layer
    mesh.visible = idx < shown && layerOk
    const home = mesh.userData.home
    if (home) {
      mesh.position.x = home.x
      mesh.position.z = home.z
      mesh.position.y = home.y + (mesh.userData.layer - 1) * exp * 80 * UNIT * 400
      // explode in mm space scaled: use 80mm * layer * explode
      mesh.position.y = home.y + (mesh.userData.layer - 1) * exp * (120 * UNIT)
    }
    // highlight
    if (selectedInfo.value && selectedInfo.value.index === idx) {
      mesh.scale.setScalar(1.03)
    } else {
      mesh.scale.setScalar(1)
    }
  })
  needsRender = true
}

function setPreset(name) {
  if (!camera || !controls) return
  const pL = (Number(props.palletLength) || 1200) * UNIT
  const pW = (Number(props.palletWidth) || 800) * UNIT
  const h = (Number(stackHeight.value) + 120) * UNIT
  const d = Math.max(pL, pW, h) * 2.4
  const map = {
    iso: [d * 0.9, d * 0.75, d * 1.05],
    front: [0, d * 0.55, d * 1.35],
    side: [d * 1.35, d * 0.55, 0],
    top: [0.01, d * 1.6, 0.01],
  }
  const p = map[name] || map.iso
  camera.position.set(p[0], p[1], p[2])
  controls.target.set(0, h * 0.3, 0)
  controls.update()
  needsRender = true
}

function togglePlay() {
  if (playing.value) {
    playing.value = false
    return
  }
  if (animIndex.value >= boxesSorted.value.length) {
    animIndex.value = 0
  }
  playing.value = true
}
function replay() {
  animIndex.value = 0
  playing.value = true
  applyVisibility()
}

function clearSelection() {
  selectedInfo.value = null
  applyVisibility()
}

function onCanvasPointerDown(ev) {
  if (!camera || !canvasRef.value || !boxMeshes.length) return
  // don't steal orbit drag: only select on quick click (handled in pointerup via controls?)
  // Use click detection with raycaster after small movement check
  const rect = canvasRef.value.getBoundingClientRect()
  pointer.x = ((ev.clientX - rect.left) / rect.width) * 2 - 1
  pointer.y = -((ev.clientY - rect.top) / rect.height) * 2 + 1
  const startX = ev.clientX
  const startY = ev.clientY
  const onUp = (up) => {
    window.removeEventListener('pointerup', onUp)
    if (Math.hypot(up.clientX - startX, up.clientY - startY) > 5) return
    raycaster.setFromCamera(pointer, camera)
    const hits = raycaster.intersectObjects(boxMeshes.filter((m) => m.visible), false)
    if (!hits.length) {
      clearSelection()
      return
    }
    const mesh = hits[0].object
    const box = mesh.userData.box
    const L = box.occupyLength || box.boxLength
    const W = box.occupyWidth || box.boxWidth
    const H = box.occupyHeight || box.boxHeight
    selectedInfo.value = {
      index: mesh.userData.index,
      product: box.productLabel || box.productKey || '-',
      boxNo: box.boxKey || box.taskKey || `#${mesh.userData.index + 1}`,
      size: `${L}×${W}×${H} mm`,
      weight: box.boxWeight ?? '-',
      pos: `(${box.positionX}, ${box.positionY}, ${box.positionZ})`,
      layer: box.layerNo || box.layer || '-',
      mode: box.packageMode || 'box',
    }
    applyVisibility()
  }
  window.addEventListener('pointerup', onUp)
}

function initThree() {
  const canvas = canvasRef.value
  const host = hostRef.value
  if (!canvas || !host) return
  renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.75))
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.0
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap

  scene = new THREE.Scene()
  scene.background = new THREE.Color('#e8edf4')
  scene.fog = new THREE.Fog('#e8edf4', 8, 28)

  const w = Math.max(1, host.clientWidth)
  const h = Math.max(1, host.clientHeight)
  camera = new THREE.PerspectiveCamera(40, w / h, 0.05, 200)
  renderer.setSize(w, h, false)

  // lights
  scene.add(new THREE.AmbientLight(0xffffff, 0.28))
  scene.add(new THREE.HemisphereLight(0xf5f8ff, 0x8a92a0, 0.45))
  const key = new THREE.DirectionalLight(0xfff2de, 2.4)
  key.position.set(4.5, 9, 3.5)
  key.castShadow = true
  key.shadow.mapSize.set(2048, 2048)
  key.shadow.bias = -0.0002
  key.shadow.radius = 2.2
  key.shadow.camera.near = 0.5
  key.shadow.camera.far = 40
  key.shadow.camera.left = -8
  key.shadow.camera.right = 8
  key.shadow.camera.top = 8
  key.shadow.camera.bottom = -8
  scene.add(key)
  const fill = new THREE.DirectionalLight(0xdde7ff, 0.35)
  fill.position.set(-4, 3, 2)
  scene.add(fill)

  // environment map
  const pmrem = new THREE.PMREMGenerator(renderer)
  envMap = pmrem.fromScene(new RoomEnvironment(), 0.04).texture
  scene.environment = envMap
  pmrem.dispose()

  controls = new OrbitControls(camera, canvas)
  controls.enableDamping = true
  controls.dampingFactor = 0.08
  controls.maxPolarAngle = Math.PI * 0.49
  controls.minDistance = 1.2
  controls.maxDistance = 18
  controls.addEventListener('change', () => { needsRender = true })

  // postprocessing AO
  composer = new EffectComposer(renderer)
  composer.addPass(new RenderPass(scene, camera))
  if (props.enableAo) {
    try {
      gtaoPass = new GTAOPass(scene, camera, w, h)
      gtaoPass.output = GTAOPass.OUTPUT.Default
      if (gtaoPass.updateGtaoMaterial) {
        gtaoPass.updateGtaoMaterial({ radius: 0.25, samples: 16 })
      }
      composer.addPass(gtaoPass)
    } catch (e) {
      console.warn('GTAO unavailable', e)
      gtaoPass = null
    }
  }
  composer.addPass(new OutputPass())

  resizeObs = new ResizeObserver(() => onResize())
  resizeObs.observe(host)
  buildScene()
  loop()
}

function onResize() {
  if (!hostRef.value || !renderer || !camera) return
  const w = Math.max(1, hostRef.value.clientWidth)
  const h = Math.max(1, hostRef.value.clientHeight)
  camera.aspect = w / h
  camera.updateProjectionMatrix()
  renderer.setSize(w, h, false)
  composer?.setSize(w, h)
  gtaoPass?.setSize?.(w, h)
  needsRender = true
}

function loop(ts = 0) {
  rafId = requestAnimationFrame(loop)
  if (playing.value) {
    if (!animTimer) animTimer = ts
    const interval = 280 / speed.value
    if (ts - animTimer >= interval) {
      animTimer = ts
      if (animIndex.value < boxesSorted.value.length) {
        animIndex.value += 1
        applyVisibility()
      } else {
        playing.value = false
      }
    }
  }
  controls?.update()
  if (needsRender || playing.value || controls?.enableDamping) {
    if (composer) composer.render()
    else renderer.render(scene, camera)
    needsRender = false
  }
}

watch(() => [props.boxes, props.selectedPalletNo, props.palletLength, props.palletWidth], () => {
  animIndex.value = 0
  playing.value = false
  buildScene()
  // auto-play once after rebuild
  if (boxesSorted.value.length) {
    animIndex.value = 0
    playing.value = true
  }
}, { deep: true })

watch([layerFilter, explode], () => applyVisibility())
watch(animIndex, () => applyVisibility())

onMounted(() => initThree())
onBeforeUnmount(() => {
  cancelAnimationFrame(rafId)
  resizeObs?.disconnect()
  clearSceneContent()
  controls?.dispose()
  composer?.dispose()
  envMap?.dispose?.()
  renderer?.dispose()
  for (const m of cartonMats.values()) m.dispose?.()
  woodMat?.dispose?.()
})
</script>

<style scoped lang="scss">
.ppe {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  min-height: 460px;
  background: #e8edf4;
  border-radius: 10px;
  overflow: hidden;
}
.ppe__canvas-host {
  position: relative;
  flex: 1;
  min-height: 420px;
}
.ppe__canvas {
  width: 100%;
  height: 100%;
  display: block;
  touch-action: none;
}
.ppe__empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: #64748b;
  font-weight: 600;
}
.ppe__hud {
  position: absolute;
  left: 10px;
  top: 10px;
  background: rgba(15, 23, 42, 0.72);
  color: #f8fafc;
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 12px;
  backdrop-filter: blur(6px);
  max-width: min(420px, 70%);
}
.ppe__hud-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  & + & { margin-top: 4px; }
  b { color: #7dd3fc; }
}
.ppe__ruler {
  position: absolute;
  right: 12px;
  top: 56px;
  bottom: 90px;
  width: 28px;
  display: flex;
  gap: 4px;
}
.ppe__ruler-track {
  flex: 1;
  background: rgba(255,255,255,0.55);
  border: 1px solid #94a3b8;
  border-radius: 4px;
  position: relative;
  overflow: hidden;
}
.ppe__ruler-fill {
  position: absolute;
  left: 0; right: 0; bottom: 0;
  background: linear-gradient(180deg, #38bdf8, #0284c7);
}
.ppe__ruler-labels {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  font-size: 10px;
  color: #334155;
}
.ppe__card {
  position: absolute;
  left: 10px;
  bottom: 10px;
  width: 240px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(15,23,42,0.12);
  padding: 10px 12px;
  font-size: 12px;
  header { font-weight: 700; margin-bottom: 6px; }
  dl { margin: 0; display: grid; gap: 4px; }
  dl > div { display: flex; justify-content: space-between; gap: 8px; }
  dt { color: #64748b; }
  dd { margin: 0; font-weight: 600; }
}
.ppe__card-close {
  margin-top: 8px;
  width: 100%;
  border: 1px solid #cbd5e1;
  background: #f8fafc;
  border-radius: 6px;
  padding: 4px;
  cursor: pointer;
}
.ppe__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding: 8px 10px;
  background: #fff;
  border-top: 1px solid #e2e8f0;
}
.ppe__group {
  display: flex;
  gap: 6px;
  align-items: center;
  button {
    border: 1px solid #cbd5e1;
    background: #f8fafc;
    border-radius: 6px;
    padding: 4px 8px;
    font-size: 12px;
    cursor: pointer;
    &.active { background: #0ea5e9; color: #fff; border-color: #0284c7; }
  }
}
.ppe__speed select { margin-left: 4px; }
.ppe__slider {
  label { font-size: 12px; color: #475569; min-width: 72px; }
  input[type='range'] { width: 100px; }
}
.ppe__tabs {
  display: flex;
  gap: 6px;
  padding: 6px 10px 10px;
  background: #fff;
  overflow-x: auto;
  button {
    border: 1px solid #cbd5e1;
    background: #f8fafc;
    border-radius: 8px;
    padding: 6px 10px;
    cursor: pointer;
    small { display: block; color: #64748b; }
    &.active { border-color: #0ea5e9; background: #e0f2fe; }
  }
}
</style>
