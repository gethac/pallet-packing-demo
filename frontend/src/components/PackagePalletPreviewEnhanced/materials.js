import * as THREE from 'three'

/** Procedural corrugated cardboard albedo + normal (shared caches). */
const cache = new Map()

function makeCorrugatedAlbedo(base = '#d4a574', size = 256) {
  const key = `albedo:${base}:${size}`
  if (cache.has(key)) return cache.get(key)
  const c = document.createElement('canvas')
  c.width = c.height = size
  const ctx = c.getContext('2d')
  ctx.fillStyle = base
  ctx.fillRect(0, 0, size, size)
  // vertical flute lines
  for (let x = 0; x < size; x += 4) {
    const shade = 0.92 + ((x / 4) % 2) * 0.08
    ctx.fillStyle = `rgba(0,0,0,${0.04 + ((x / 4) % 3) * 0.015})`
    ctx.fillRect(x, 0, 2, size)
    ctx.fillStyle = `rgba(255,255,255,${0.03 * shade})`
    ctx.fillRect(x + 1, 0, 1, size)
  }
  // subtle fiber noise
  const img = ctx.getImageData(0, 0, size, size)
  for (let i = 0; i < img.data.length; i += 4) {
    const n = (Math.random() - 0.5) * 12
    img.data[i] = Math.min(255, Math.max(0, img.data[i] + n))
    img.data[i + 1] = Math.min(255, Math.max(0, img.data[i + 1] + n * 0.9))
    img.data[i + 2] = Math.min(255, Math.max(0, img.data[i + 2] + n * 0.7))
  }
  ctx.putImageData(img, 0, 0)
  const tex = new THREE.CanvasTexture(c)
  tex.colorSpace = THREE.SRGBColorSpace
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping
  tex.anisotropy = 4
  cache.set(key, tex)
  return tex
}

function makeCorrugatedNormal(size = 256) {
  const key = `normal:${size}`
  if (cache.has(key)) return cache.get(key)
  const c = document.createElement('canvas')
  c.width = c.height = size
  const ctx = c.getContext('2d')
  // flat normal base #8080ff
  ctx.fillStyle = '#8080ff'
  ctx.fillRect(0, 0, size, size)
  const img = ctx.getImageData(0, 0, size, size)
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const i = (y * size + x) * 4
      // corrugation along X → bump on X
      const wave = Math.sin((x / size) * Math.PI * 64) * 0.35
      const nx = 128 + wave * 80
      const ny = 128
      const nz = 255
      img.data[i] = nx
      img.data[i + 1] = ny
      img.data[i + 2] = nz
      img.data[i + 3] = 255
    }
  }
  ctx.putImageData(img, 0, 0)
  const tex = new THREE.CanvasTexture(c)
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping
  cache.set(key, tex)
  return tex
}

export function createCartonMaterial(hex = '#d4a574') {
  const map = makeCorrugatedAlbedo(hex)
  const normalMap = makeCorrugatedNormal()
  const mat = new THREE.MeshStandardMaterial({
    map,
    normalMap,
    normalScale: new THREE.Vector2(0.55, 0.55),
    roughness: 0.82,
    metalness: 0.02,
    envMapIntensity: 0.55,
  })
  mat.userData.repeat = [2.2, 1.6]
  map.repeat.set(2.2, 1.6)
  normalMap.repeat.set(2.2, 1.6)
  return mat
}

export function createWoodMaterial() {
  const key = 'wood'
  if (cache.has(key)) return cache.get(key).clone()
  const c = document.createElement('canvas')
  c.width = 256
  c.height = 128
  const ctx = c.getContext('2d')
  const g = ctx.createLinearGradient(0, 0, 256, 0)
  g.addColorStop(0, '#c48a4a')
  g.addColorStop(0.35, '#e0b06a')
  g.addColorStop(0.7, '#b67840')
  g.addColorStop(1, '#9a6234')
  ctx.fillStyle = g
  ctx.fillRect(0, 0, 256, 128)
  for (let i = 0; i < 40; i++) {
    ctx.strokeStyle = `rgba(80,40,10,${0.08 + Math.random() * 0.12})`
    ctx.beginPath()
    const y = Math.random() * 128
    ctx.moveTo(0, y)
    ctx.bezierCurveTo(80, y + (Math.random() - 0.5) * 10, 160, y + (Math.random() - 0.5) * 10, 256, y)
    ctx.stroke()
  }
  const map = new THREE.CanvasTexture(c)
  map.colorSpace = THREE.SRGBColorSpace
  map.wrapS = map.wrapT = THREE.RepeatWrapping
  const mat = new THREE.MeshStandardMaterial({
    map,
    roughness: 0.78,
    metalness: 0.05,
    envMapIntensity: 0.4,
  })
  cache.set(key, mat)
  return mat.clone()
}

/** Face label: product / box no / weight via CanvasTexture */
export function createBoxLabelTexture({ product, boxNo, weight, size = 'LxWxH' }) {
  const c = document.createElement('canvas')
  c.width = 512
  c.height = 256
  const ctx = c.getContext('2d')
  ctx.fillStyle = '#f7f1e6'
  ctx.fillRect(0, 0, 512, 256)
  ctx.strokeStyle = '#8b5a2b'
  ctx.lineWidth = 8
  ctx.strokeRect(8, 8, 496, 240)
  ctx.fillStyle = '#a67c3a'
  ctx.fillRect(8, 8, 496, 48)
  ctx.fillStyle = '#fff'
  ctx.font = 'bold 28px sans-serif'
  ctx.fillText('PALLET BOX', 24, 42)
  ctx.fillStyle = '#2c1810'
  ctx.font = 'bold 36px sans-serif'
  ctx.fillText(String(product || 'PROD').slice(0, 18), 24, 110)
  ctx.font = '24px sans-serif'
  ctx.fillText(`箱号 ${boxNo || '-'}`, 24, 155)
  ctx.fillText(`重量 ${weight ?? '-'} kg`, 24, 190)
  ctx.fillText(`尺寸 ${size}`, 24, 225)
  const tex = new THREE.CanvasTexture(c)
  tex.colorSpace = THREE.SRGBColorSpace
  return tex
}

export function disposeMaterialCache() {
  for (const v of cache.values()) {
    if (v.isTexture) v.dispose()
    else if (v.map) {
      v.map?.dispose?.()
      v.dispose?.()
    }
  }
  cache.clear()
}
