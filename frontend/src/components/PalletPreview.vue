<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'

const props = defineProps({
  palletLength: { type: Number, default: 1200 },
  palletWidth: { type: Number, default: 800 },
  boxes: { type: Array, default: () => [] },
})

const hostRef = ref(null)
let renderer, scene, camera, animationId
const SCALE = 0.0025

function clearScene() {
  if (!scene) return
  const keep = new Set(['ambient', 'dir', 'floor', 'grid'])
  ;[...scene.children].forEach((obj) => {
    if (!keep.has(obj.name)) {
      scene.remove(obj)
      obj.geometry?.dispose?.()
      if (obj.material) {
        if (Array.isArray(obj.material)) obj.material.forEach((m) => m.dispose())
        else obj.material.dispose()
      }
    }
  })
}

function build() {
  if (!scene) return
  clearScene()
  const L = props.palletLength * SCALE
  const W = props.palletWidth * SCALE
  const palletGeo = new THREE.BoxGeometry(L, 0.08, W)
  const palletMat = new THREE.MeshStandardMaterial({ color: '#b77b45' })
  const pallet = new THREE.Mesh(palletGeo, palletMat)
  pallet.position.set(L / 2, 0.04, W / 2)
  pallet.castShadow = true
  pallet.receiveShadow = true
  scene.add(pallet)

  const palette = ['#dfaa69', '#9fb5c1', '#a6bd9e', '#bd9276', '#c9955c']
  props.boxes.forEach((box, idx) => {
    const ol = Number(box.occupyLength || box.boxLength || 0) * SCALE
    const ow = Number(box.occupyWidth || box.boxWidth || 0) * SCALE
    const oh = Number(box.occupyHeight || box.boxHeight || 0) * SCALE
    const x = Number(box.positionX || 0) * SCALE
    const y = Number(box.positionY || 0) * SCALE
    const z = Number(box.positionZ || 0) * SCALE
    const geo = new THREE.BoxGeometry(ol, oh, ow)
    const mat = new THREE.MeshStandardMaterial({
      color: palette[idx % palette.length],
      roughness: 0.7,
    })
    const mesh = new THREE.Mesh(geo, mat)
    mesh.position.set(x + ol / 2, 0.08 + z + oh / 2, y + ow / 2)
    mesh.castShadow = true
    mesh.receiveShadow = true
    scene.add(mesh)
  })

  camera.position.set(L * 1.6, Math.max(L, W) * 1.2, W * 1.8)
  camera.lookAt(L / 2, 0.3, W / 2)
}

function init() {
  const host = hostRef.value
  const width = host.clientWidth || 480
  const height = host.clientHeight || 420
  scene = new THREE.Scene()
  scene.background = new THREE.Color('#eef2f6')
  camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 100)
  renderer = new THREE.WebGLRenderer({ antialias: true })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.5))
  renderer.setSize(width, height)
  renderer.shadowMap.enabled = true
  host.innerHTML = ''
  host.appendChild(renderer.domElement)

  const ambient = new THREE.AmbientLight(0xffffff, 0.7)
  ambient.name = 'ambient'
  scene.add(ambient)
  const dir = new THREE.DirectionalLight(0xffffff, 0.9)
  dir.position.set(4, 8, 3)
  dir.castShadow = true
  dir.name = 'dir'
  scene.add(dir)

  const floor = new THREE.Mesh(
    new THREE.PlaneGeometry(20, 20),
    new THREE.MeshStandardMaterial({ color: '#e3e7ed' }),
  )
  floor.rotation.x = -Math.PI / 2
  floor.position.y = 0
  floor.receiveShadow = true
  floor.name = 'floor'
  scene.add(floor)

  build()
  const animate = () => {
    animationId = requestAnimationFrame(animate)
    renderer.render(scene, camera)
  }
  animate()
}

function onResize() {
  if (!hostRef.value || !renderer || !camera) return
  const width = hostRef.value.clientWidth
  const height = hostRef.value.clientHeight
  camera.aspect = width / height
  camera.updateProjectionMatrix()
  renderer.setSize(width, height)
}

onMounted(() => {
  init()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  if (animationId) cancelAnimationFrame(animationId)
  renderer?.dispose()
})

watch(() => [props.boxes, props.palletLength, props.palletWidth], () => build(), { deep: true })
</script>

<template>
  <div ref="hostRef" class="preview-host">
    <div v-if="!boxes.length" class="empty">暂无托盘</div>
  </div>
</template>

<style scoped>
.preview-host {
  position: relative;
  width: 100%;
  height: 420px;
  border-radius: 8px;
  overflow: hidden;
  background: #eef2f6;
}
.empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: #64748b;
  font-weight: 600;
  pointer-events: none;
}
</style>
