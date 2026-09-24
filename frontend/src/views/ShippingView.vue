<template>
  <div>
    <div class="panel">
      <div class="panel-title">
        出货与金额汇总
        <span class="hint">金额 = 数量 × 单价，状态流转 待发 → 已发 → 已结款</span>
      </div>
      <div class="summary-strip">
        <div class="summary-box"><b>{{ summary.orderTotal || 0 }}</b><span>出货单总数</span></div>
        <div class="summary-box"><b>{{ summary.quantityTotal || 0 }}</b><span>出货总量（件）</span></div>
        <div class="summary-box"><b>￥{{ summary.amountTotal || 0 }}</b><span>出货总额</span></div>
        <div class="summary-box"><b>￥{{ summary.paidAmount || 0 }}</b><span>已结款金额</span></div>
        <div v-for="item in summary.items" :key="item.status" class="summary-box">
          <b>{{ item.count }}</b><span>{{ item.statusName }} · ￥{{ item.amount }}</span>
        </div>
      </div>
      <div class="toolbar">
        <el-select v-model="statusFilter" clearable placeholder="按状态筛选" style="width: 170px" @change="load">
          <el-option label="待发" value="PENDING" />
          <el-option label="已发" value="SHIPPED" />
          <el-option label="已结款" value="PAID" />
        </el-select>
        <span class="spacer"></span>
        <el-button type="primary" @click="shipVisible = true">新增出货单</el-button>
      </div>
      <el-table :data="shipments" border stripe size="small">
        <el-table-column prop="shipmentNo" label="出货单号" width="130" />
        <el-table-column prop="customer" label="客户" min-width="140" />
        <el-table-column prop="batchNo" label="批次" width="130" />
        <el-table-column prop="packageFormName" label="包装" width="90" />
        <el-table-column prop="quantity" label="数量" width="90" />
        <el-table-column prop="unitPrice" label="单价" width="90" />
        <el-table-column prop="amount" label="金额" width="110" />
        <el-table-column prop="shipDate" label="出货日期" width="110" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="tag-inline" :style="{ background: statusColor(row.status) }">{{ row.statusName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button v-if="row.nextStatus" size="small" type="success" plain @click="nextStatus(row)">
              {{ row.nextStatusName }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="panel">
      <div class="panel-title">
        灌装单
        <span class="hint">批次 → 包装形式 → 灌装数量与损耗</span>
      </div>
      <div class="toolbar">
        <span class="spacer"></span>
        <el-button type="success" @click="bottlingVisible = true">新增灌装单</el-button>
      </div>
      <el-table :data="bottlings" border stripe size="small" max-height="360">
        <el-table-column prop="bottlingNo" label="灌装单号" width="130" />
        <el-table-column prop="batchNo" label="批次" width="130" />
        <el-table-column prop="packageFormName" label="包装形式" width="110" />
        <el-table-column prop="quantity" label="数量" width="100" />
        <el-table-column prop="bottleDate" label="灌装日期" width="120" />
        <el-table-column prop="lossL" label="损耗(L)" width="100" />
      </el-table>
    </div>

    <el-dialog v-model="shipVisible" title="新增出货单" width="520px">
      <el-form :model="shipForm" label-width="96px">
        <el-form-item label="出货单号"><el-input v-model="shipForm.shipmentNo" placeholder="如 SH-2026-13" /></el-form-item>
        <el-form-item label="客户"><el-input v-model="shipForm.customer" /></el-form-item>
        <el-form-item label="批次">
          <el-select v-model="shipForm.batchNo" filterable style="width: 100%">
            <el-option v-for="b in batches" :key="b.id" :label="b.batchNo + ' · ' + b.statusName" :value="b.batchNo" />
          </el-select>
        </el-form-item>
        <el-form-item label="包装形式">
          <el-select v-model="shipForm.packageForm" style="width: 100%">
            <el-option label="瓶" value="BOTTLE" />
            <el-option label="易拉罐" value="CAN" />
            <el-option label="桶" value="KEG" />
          </el-select>
        </el-form-item>
        <el-form-item label="数量"><el-input-number v-model="shipForm.quantity" :min="1" /></el-form-item>
        <el-form-item label="单价"><el-input-number v-model="shipForm.unitPrice" :precision="2" :min="0.01" /></el-form-item>
        <el-form-item label="金额">
          <b>￥{{ (Number(shipForm.quantity) * Number(shipForm.unitPrice)).toFixed(2) }}</b>
        </el-form-item>
        <el-form-item label="出货日期">
          <el-date-picker v-model="shipForm.shipDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shipVisible = false">取消</el-button>
        <el-button type="primary" @click="submitShipment">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="bottlingVisible" title="新增灌装单" width="480px">
      <el-form :model="bottlingForm" label-width="96px">
        <el-form-item label="灌装单号"><el-input v-model="bottlingForm.bottlingNo" placeholder="如 BT-2026-13" /></el-form-item>
        <el-form-item label="批次">
          <el-select v-model="bottlingForm.batchNo" filterable style="width: 100%">
            <el-option v-for="b in batches" :key="b.id" :label="b.batchNo" :value="b.batchNo" />
          </el-select>
        </el-form-item>
        <el-form-item label="包装形式">
          <el-select v-model="bottlingForm.packageForm" style="width: 100%">
            <el-option label="瓶" value="BOTTLE" />
            <el-option label="易拉罐" value="CAN" />
            <el-option label="桶" value="KEG" />
          </el-select>
        </el-form-item>
        <el-form-item label="数量"><el-input-number v-model="bottlingForm.quantity" :min="1" /></el-form-item>
        <el-form-item label="损耗(L)"><el-input-number v-model="bottlingForm.lossL" :precision="2" :min="0" /></el-form-item>
        <el-form-item label="灌装日期">
          <el-date-picker v-model="bottlingForm.bottleDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bottlingVisible = false">取消</el-button>
        <el-button type="primary" @click="submitBottling">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { batchApi, shippingApi } from '../api'

const shipments = ref([])
const bottlings = ref([])
const batches = ref([])
const summary = ref({})
const statusFilter = ref('')
const shipVisible = ref(false)
const bottlingVisible = ref(false)
const shipForm = ref({ shipmentNo: '', customer: '', batchNo: '', packageForm: 'BOTTLE', quantity: 100, unitPrice: 15, shipDate: '' })
const bottlingForm = ref({ bottlingNo: '', batchNo: '', packageForm: 'BOTTLE', quantity: 300, lossL: 5, bottleDate: '' })

const COLOR = { PENDING: '#9aa5ab', SHIPPED: '#7cb342', PAID: '#5b8a2a' }
function statusColor(s) {
  return COLOR[s] || '#8d9aa5'
}

async function load() {
  shipments.value = await shippingApi.shipments(statusFilter.value)
  bottlings.value = await shippingApi.bottlings()
  summary.value = await shippingApi.summary()
}

async function nextStatus(row) {
  await shippingApi.nextStatus(row.id)
  ElMessage.success('已推进到 ' + row.nextStatusName)
  await load()
}

async function submitShipment() {
  await shippingApi.createShipment(shipForm.value)
  ElMessage.success('出货单已登记')
  shipVisible.value = false
  await load()
}

async function submitBottling() {
  await shippingApi.createBottling(bottlingForm.value)
  ElMessage.success('灌装单已登记')
  bottlingVisible.value = false
  await load()
}

onMounted(async () => {
  batches.value = await batchApi.list()
  await load()
})
</script>
