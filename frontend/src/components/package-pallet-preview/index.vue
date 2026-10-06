<!--
  原 MES 文档页 package-pallet-preview/index.vue 依赖公司 ExampleDocsShell 等内部组件。
  Demo 中改为直接复用 PackagePalletPreview 源组件（效果与生产一致）。
-->
<script setup>
import PackagePalletPreview from '../PackagePalletPreview/index.vue'
defineProps({
  palletLength: { type: [Number, String], default: 1200 },
  palletWidth: { type: [Number, String], default: 800 },
  palletHeight: { type: [Number, String], default: 120 },
  palletItems: { type: Array, default: () => [] },
  boxes: { type: Array, default: () => [] },
  selectedPalletNo: { type: [String, Number], default: '' },
})
</script>
<template>
  <PackagePalletPreview
    :pallet-length="palletLength"
    :pallet-width="palletWidth"
    :pallet-height="palletHeight"
    :pallet-items="palletItems"
    :boxes="boxes"
    :selected-pallet-no="selectedPalletNo"
    :show-labels="false"
  />
</template>
