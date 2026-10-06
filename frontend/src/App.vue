<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import PalletPreview from './components/PalletPreview.vue'

const orderId = ref('ORDER-SINGLE')
const batchId = ref('')
const standards = ref([])
const standardId = ref('')
const weightLimit = ref(500)
const cargoHeightLimit = ref(1200)
const allowMixed = ref(false)
const allowMixedPackage = ref(false)
const allowMixedNoBox = ref(false)
const loading = ref(false)
const message = ref('')
const productRows = ref([])
const plan = ref(null)
const selectedPalletNo = ref('')
const previewBoxes = ref([])

const orderOptions = [
  { id: 'ORDER-SINGLE', label: '单批次示例订单' },
  { id: 'ORDER-MIX', label: '混托演示订单' },
  { id: 'ORDER-TAIL', label: '尾托合并演示' },
  { id: 'ORDER-HEAVY', label: '超重校验订单' },
  { id: 'ORDER-TALL', label: '超高校验订单' },
  { id: 'ORDER-EMPTY', label: '空包装订单' },
]

const items = computed(() => plan.value?.itemList || [])
const groups = computed(() => plan.value?.groupList || [])

async function api(path, options = {}) {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options,
  })
  const json = await res.json()
  if (!json.success) {
    throw new Error(json.message || '请求失败')
  }
  return json.data
}

async function loadStandards() {
  standards.value = await api('/api/pallet-standard/listEnabled', { method: 'POST' })
  if (!standardId.value && standards.value.length) {
    standardId.value = standards.value[0].id
  }
}

async function loadCalculation() {
  loading.value = true
  message.value = ''
  try {
    const q = new URLSearchParams({ batchId: batchId.value || '' })
    const data = await api(`/api/sale-order/pallet/${orderId.value}?${q}`)
    productRows.value = data.productRows || []
    plan.value = data.plan || null
    if (plan.value?.itemList?.length) {
      selectedPalletNo.value = plan.value.itemList[0].palletNo
      await loadPreview()
    } else {
      selectedPalletNo.value = ''
      previewBoxes.value = []
    }
  } catch (e) {
    message.value = e.message
  } finally {
    loading.value = false
  }
}

async function calculate() {
  loading.value = true
  message.value = ''
  try {
    const q = new URLSearchParams({ batchId: batchId.value || '' })
    const body = {
      palletStandardId: standardId.value,
      weightLimit: Number(weightLimit.value),
      cargoHeightLimit: Number(cargoHeightLimit.value),
      allowMixedPallet: allowMixed.value ? '1' : '0',
      allowMixedPackagePallet: allowMixedPackage.value ? '1' : '0',
      allowMixedNoBoxPallet: allowMixedNoBox.value ? '1' : '0',
    }
    const data = await api(`/api/sale-order/pallet/${orderId.value}/calculate?${q}`, {
      method: 'POST',
      body: JSON.stringify(body),
    })
    plan.value = data.plan
    productRows.value = data.productRows || productRows.value
    message.value = data.reused ? '输入未变化，复用已有方案' : '计算完成并已保存'
    if (plan.value?.itemList?.length) {
      selectedPalletNo.value = plan.value.itemList[0].palletNo
      await loadPreview()
    }
  } catch (e) {
    message.value = e.message
    previewBoxes.value = []
  } finally {
    loading.value = false
  }
}

async function saveDraft() {
  loading.value = true
  message.value = ''
  try {
    const q = new URLSearchParams({ batchId: batchId.value || '' })
    const body = {
      palletStandardId: standardId.value,
      weightLimit: Number(weightLimit.value),
      cargoHeightLimit: Number(cargoHeightLimit.value),
      allowMixedPallet: allowMixed.value ? '1' : '0',
      allowMixedPackagePallet: allowMixedPackage.value ? '1' : '0',
      allowMixedNoBoxPallet: allowMixedNoBox.value ? '1' : '0',
    }
    await api(`/api/sale-order/pallet/${orderId.value}/draft?${q}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    })
    message.value = '草稿已保存'
    await loadCalculation()
  } catch (e) {
    message.value = e.message
  } finally {
    loading.value = false
  }
}

async function loadPreview() {
  if (!selectedPalletNo.value) {
    previewBoxes.value = []
    return
  }
  const q = new URLSearchParams({
    batchId: batchId.value || '',
    palletNo: selectedPalletNo.value,
  })
  previewBoxes.value = await api(`/api/sale-order/pallet/${orderId.value}/preview?${q}`)
}

watch(selectedPalletNo, () => {
  loadPreview().catch((e) => {
    message.value = e.message
  })
})

onMounted(async () => {
  await loadStandards()
  await loadCalculation()
})
</script>

<template>
  <div class="page">
    <header class="header">
      <div>
        <h1>托盘摆放 Demo</h1>
        <p>从 MES 销售订单托盘功能抽取的可独立运行最小实现（模拟数据）</p>
      </div>
      <div class="actions">
        <button class="primary" :disabled="loading" @click="calculate">计算托盘</button>
        <button :disabled="loading" @click="saveDraft">保存草稿</button>
        <button :disabled="loading" @click="loadCalculation">刷新</button>
      </div>
    </header>

    <section class="panel params">
      <label>
        示例订单
        <select v-model="orderId" @change="loadCalculation">
          <option v-for="o in orderOptions" :key="o.id" :value="o.id">{{ o.label }}</option>
        </select>
      </label>
      <label>
        批次
        <input v-model="batchId" placeholder="空=隐式单批次" />
      </label>
      <label>
        托盘标准
        <select v-model="standardId">
          <option v-for="s in standards" :key="s.id" :value="s.id">
            {{ s.code }} ({{ s.length }}×{{ s.width }})
          </option>
        </select>
      </label>
      <label>
        货物限重(kg)
        <input v-model.number="weightLimit" type="number" min="1" />
      </label>
      <label>
        货物限高(mm)
        <input v-model.number="cargoHeightLimit" type="number" min="1" />
      </label>
      <label class="check"><input v-model="allowMixed" type="checkbox" /> 混托</label>
      <label class="check"><input v-model="allowMixedPackage" type="checkbox" /> 盒箱混托</label>
      <label class="check"><input v-model="allowMixedNoBox" type="checkbox" /> 无盒混托</label>
    </section>

    <p v-if="message" class="msg">{{ message }}</p>

    <div class="grid">
      <section class="panel">
        <h2>产品装箱</h2>
        <table>
          <thead>
            <tr>
              <th>产品</th><th>包装</th><th>尺寸(mm)</th><th>单箱重</th><th>箱数</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in productRows" :key="row.productKey">
              <td>{{ row.productLabel || row.productKey }}</td>
              <td>{{ row.packageMode }}</td>
              <td>{{ row.boxLength }}×{{ row.boxWidth }}×{{ row.boxHeight }}</td>
              <td>{{ row.boxWeight }}</td>
              <td>{{ row.boxCount }}</td>
            </tr>
            <tr v-if="!productRows.length"><td colspan="5">暂无数据</td></tr>
          </tbody>
        </table>

        <h2>托盘清单</h2>
        <p v-if="plan" class="summary">
          总托数 {{ plan.totalPalletCount }} · 总箱数 {{ plan.totalBoxCount }} ·
          总重 {{ plan.totalProductWeight }}kg ·
          面积利用率 {{ plan.avgAreaUtilization }} ·
          高度利用率 {{ plan.avgHeightUtilization }}
        </p>
        <table>
          <thead>
            <tr>
              <th>托盘号</th><th>类型</th><th>产品</th><th>层数</th><th>箱数</th><th>重量</th><th>高度</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in items"
              :key="item.palletNo"
              :class="{ active: item.palletNo === selectedPalletNo }"
              @click="selectedPalletNo = item.palletNo"
            >
              <td>{{ item.palletNo }}</td>
              <td>{{ item.groupType }}</td>
              <td>{{ item.productKeys }}</td>
              <td>{{ item.layerCount }}</td>
              <td>{{ item.boxCount }}</td>
              <td>{{ item.totalWeight }}</td>
              <td>{{ item.totalHeight }}</td>
            </tr>
            <tr v-if="!items.length"><td colspan="7">暂无托盘</td></tr>
          </tbody>
        </table>
      </section>

      <section class="panel preview-panel">
        <h2>三维预览 {{ selectedPalletNo ? `· ${selectedPalletNo}` : '' }}</h2>
        <PalletPreview
          :pallet-length="plan?.palletLength || 1200"
          :pallet-width="plan?.palletWidth || 800"
          :boxes="previewBoxes"
        />
      </section>
    </div>
  </div>
</template>

<style scoped>
.page { max-width: 1280px; margin: 0 auto; padding: 20px; }
.header { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; }
.header h1 { margin: 0 0 4px; font-size: 24px; }
.header p { margin: 0; color: #64748b; }
.actions { display: flex; gap: 8px; }
.panel {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 14px;
  margin-top: 14px;
}
.params { display: flex; flex-wrap: wrap; gap: 12px; align-items: end; }
.params label { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: #64748b; }
.params input, .params select { min-width: 140px; padding: 6px 8px; border: 1px solid #cbd5e1; border-radius: 6px; }
.params .check { flex-direction: row; align-items: center; gap: 6px; color: #1f2937; font-size: 13px; }
.grid { display: grid; grid-template-columns: 1.2fr 1fr; gap: 14px; }
.msg { color: #b45309; background: #fffbeb; border: 1px solid #fcd34d; padding: 8px 12px; border-radius: 8px; }
.summary { color: #475569; font-size: 13px; }
tr.active { background: #eff6ff; }
.preview-panel { min-height: 480px; }
h2 { margin: 0 0 10px; font-size: 16px; }
@media (max-width: 960px) {
  .grid { grid-template-columns: 1fr; }
}
</style>
