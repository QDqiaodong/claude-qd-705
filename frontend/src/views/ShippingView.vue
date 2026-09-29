<template>
  <div>
    <div class="panel">
      <div class="panel-title">
        出货与金额汇总
        <span class="hint">
          出货必须基于已灌装记录且放行仍有效；撤销放行/复检/驳回会冻结待发单，重新放行后恢复
        </span>
      </div>
      <div class="summary-strip">
        <div class="summary-box"><b>{{ summary.orderTotal || 0 }}</b><span>出货单总数</span></div>
        <div class="summary-box"><b>{{ summary.quantityTotal || 0 }}</b><span>出货总量（件）</span></div>
        <div class="summary-box"><b>￥{{ summary.amountTotal || 0 }}</b><span>出货总额</span></div>
        <div class="summary-box"><b>￥{{ summary.paidAmount || 0 }}</b><span>已结款金额</span></div>
        <div class="summary-box" :class="{ 'is-alert-box': (summary.frozenCount || 0) > 0 }">
          <b>{{ summary.frozenCount || 0 }}</b><span>质检冻结中</span>
        </div>
        <div v-for="item in summary.items" :key="item.status" class="summary-box">
          <b>{{ item.count }}</b><span>{{ item.statusName }} · ￥{{ item.amount }}</span>
        </div>
      </div>
      <div class="toolbar">
        <el-select v-model="statusFilter" clearable placeholder="按单据状态筛选" style="width: 170px" @change="load">
          <el-option label="待发" value="PENDING" />
          <el-option label="已发" value="SHIPPED" />
          <el-option label="已结款" value="PAID" />
        </el-select>
        <span class="spacer"></span>
        <el-button type="primary" @click="openShip">新增出货单</el-button>
      </div>
      <el-table :data="shipments" border stripe size="small">
        <el-table-column prop="shipmentNo" label="出货单号" width="120" />
        <el-table-column prop="customer" label="客户" min-width="130" />
        <el-table-column prop="batchNo" label="批次" width="120" />
        <el-table-column prop="packageFormName" label="包装" width="80" />
        <el-table-column prop="quantity" label="数量" width="70" />
        <el-table-column prop="unitPrice" label="单价" width="80" />
        <el-table-column prop="amount" label="金额" width="100" />
        <el-table-column prop="shipDate" label="出货日期" width="105" />
        <el-table-column label="质检放行" width="92">
          <template #default="{ row }">
            <span class="tag-inline" :style="{ background: row.qcStateColor || '#9aa5ab' }">
              {{ row.qcStateName || '待质检' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="单据状态" min-width="170">
          <template #default="{ row }">
            <span class="tag-inline" :style="{ background: shipStatusColor(row.status) }">{{ row.statusName }}</span>
            <el-tooltip v-if="row.frozen" :content="row.frozenReason || '质检冻结中'" placement="top">
              <span class="tag-inline tag-frozen">已冻结</span>
            </el-tooltip>
            <div v-if="row.frozen" class="freeze-reason">{{ row.frozenReason }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-tooltip v-if="row.frozen" content="质检冻结中，重新放行后才能发货" placement="top">
              <span><el-button size="small" type="success" plain disabled>发货</el-button></span>
            </el-tooltip>
            <el-button
              v-else-if="row.nextStatus"
              size="small"
              type="success"
              plain
              @click="nextStatus(row)"
            >
              {{ row.nextStatusName }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="panel">
      <div class="panel-title">
        灌装单
        <span class="hint">仅「熟成/已灌装 + 测量齐全 + 最新一轮放行」的批次可新建；放行轮次随单留痕</span>
      </div>
      <div class="toolbar">
        <span class="spacer"></span>
        <el-button type="success" @click="openBottling">新增灌装单</el-button>
      </div>
      <el-table :data="bottlings" border stripe size="small" max-height="380">
        <el-table-column prop="bottlingNo" label="灌装单号" width="120" />
        <el-table-column prop="batchNo" label="批次" width="120" />
        <el-table-column prop="packageFormName" label="包装形式" width="90" />
        <el-table-column prop="quantity" label="数量" width="80" />
        <el-table-column prop="bottleDate" label="灌装日期" width="105" />
        <el-table-column prop="lossL" label="损耗(L)" width="85" />
        <el-table-column label="放行依据 / 当前质检" min-width="220">
          <template #default="{ row }">
            <div class="qc-cell">
              <span class="line">
                放行轮次：
                <b v-if="row.qcRoundId">#{{ row.qcRoundId }}</b>
                <span v-else class="warn-text">无（历史数据）</span>
              </span>
              <span class="line">
                该批次当前：
                <span class="tag-inline" :style="{ background: row.qcStateColor || '#9aa5ab' }">
                  {{ row.qcStateName || '待质检' }}
                </span>
                <span v-if="row.qcState && row.qcState !== 'PASSED'" class="warn-text">
                  （历史灌装单保留，新灌装/出货已被阻止）
                </span>
              </span>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增出货单 -->
    <el-dialog v-model="shipVisible" title="新增出货单" width="560px">
      <el-alert
        v-if="selectedShipBatch && shipBlockReason"
        :title="shipBlockReason"
        type="error"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
      />
      <el-form :model="shipForm" label-width="96px">
        <el-form-item label="出货单号"><el-input v-model="shipForm.shipmentNo" placeholder="如 SH-2026-13" /></el-form-item>
        <el-form-item label="客户"><el-input v-model="shipForm.customer" /></el-form-item>
        <el-form-item label="批次">
          <el-select v-model="shipForm.batchNo" filterable style="width: 100%" @change="onShipBatchChange">
            <el-option
              v-for="b in batches"
              :key="b.id"
              :disabled="!!b.qcBlockReason || b.status === 'SCRAPPED'"
              :label="`${b.batchNo} · ${b.statusName} · ${b.qcStateName}${b.qcBlockReason ? '（' + b.qcBlockReason + '）' : ''}`"
              :value="b.batchNo"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="包装形式">
          <el-select v-model="shipForm.packageForm" style="width: 100%">
            <el-option label="瓶 BOTTLE" value="BOTTLE" />
            <el-option label="易拉罐 CAN" value="CAN" />
            <el-option label="桶 KEG" value="KEG" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button size="small" text @click="showBatchGate = !showBatchGate">
            {{ showBatchGate ? '收起候选批次质检状态' : '查看全部批次质检状态' }}
          </el-button>
        </el-form-item>
        <el-form-item v-if="showBatchGate" label=" ">
          <div class="gate-list">
            <div v-for="b in batches" :key="b.id" class="gate-row" :class="{ blocked: !!b.qcBlockReason }">
              <b>{{ b.batchNo }}</b> {{ b.statusName }}
              <span class="tag-inline" :style="{ background: b.qcStateColor }">{{ b.qcStateName }}</span>
              <span v-if="b.qcBlockReason" class="warn-text">{{ b.qcBlockReason }}</span>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="数量"><el-input-number v-model="shipForm.quantity" :min="1" /></el-form-item>
        <el-form-item label="单价"><el-input-number v-model="shipForm.unitPrice" :precision="2" :min="0.01" /></el-form-item>
        <el-form-item label="金额">
          <b>￥{{ (Number(shipForm.quantity) * Number(shipForm.unitPrice || 0)).toFixed(2) }}</b>
        </el-form-item>
        <el-form-item label="出货日期">
          <el-date-picker v-model="shipForm.shipDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shipVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!!shipBlockReason" @click="submitShipment">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新增灌装单 -->
    <el-dialog v-model="bottlingVisible" title="新增灌装单" width="520px">
      <el-alert
        v-if="selectedBottlingBatch && bottlingBlockReason"
        :title="bottlingBlockReason"
        type="error"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
      />
      <el-form :model="bottlingForm" label-width="96px">
        <el-form-item label="灌装单号"><el-input v-model="bottlingForm.bottlingNo" placeholder="如 BT-2026-13" /></el-form-item>
        <el-form-item label="批次">
          <el-select v-model="bottlingForm.batchNo" filterable style="width: 100%">
            <el-option
              v-for="b in batches"
              :key="b.id"
              :disabled="!!b.qcBlockReason || b.status === 'SCRAPPED'"
              :label="`${b.batchNo} · ${b.statusName} · ${b.qcStateName}${b.qcBlockReason ? '（' + b.qcBlockReason + '）' : ''}`"
              :value="b.batchNo"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="包装形式">
          <el-select v-model="bottlingForm.packageForm" style="width: 100%">
            <el-option label="瓶 BOTTLE" value="BOTTLE" />
            <el-option label="易拉罐 CAN" value="CAN" />
            <el-option label="桶 KEG" value="KEG" />
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
        <el-button type="primary" :disabled="!!bottlingBlockReason" @click="submitBottling">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { batchApi, shippingApi } from '../api'

const shipments = ref([])
const bottlings = ref([])
const batches = ref([])
const summary = ref({})
const statusFilter = ref('')
const shipVisible = ref(false)
const bottlingVisible = ref(false)
const showBatchGate = ref(false)
const shipForm = ref(blankShip())
const bottlingForm = ref(blankBottling())

function blankShip() {
  return { shipmentNo: '', customer: '', batchNo: '', packageForm: 'BOTTLE', quantity: 100, unitPrice: 15, shipDate: '' }
}
function blankBottling() {
  return { bottlingNo: '', batchNo: '', packageForm: 'BOTTLE', quantity: 300, lossL: 5, bottleDate: '' }
}

const SHIP_COLOR = { PENDING: '#9aa5ab', SHIPPED: '#7cb342', PAID: '#5b8a2a' }
function shipStatusColor(s) {
  return SHIP_COLOR[s] || '#8d99a5'
}

const selectedShipBatch = computed(() => batches.value.find((b) => b.batchNo === shipForm.value.batchNo))
const selectedBottlingBatch = computed(() => batches.value.find((b) => b.batchNo === bottlingForm.value.batchNo))

// 前端只是把后端口径提前展示给操作员；保存时后端仍会强制裁决一次
const shipBlockReason = computed(() => {
  const b = selectedShipBatch.value
  if (!b) return ''
  if (b.status === 'SCRAPPED') return `批次 ${b.batchNo} 已报废，不能新建出货单`
  if (b.qcBlockReason) return `批次 ${b.batchNo}：${b.qcBlockReason}`
  return ''
})
const bottlingBlockReason = computed(() => {
  const b = selectedBottlingBatch.value
  if (!b) return ''
  if (b.status === 'SCRAPPED') return `批次 ${b.batchNo} 已报废，不能新建灌装单`
  if (b.qcBlockReason) return `批次 ${b.batchNo}：${b.qcBlockReason}`
  return ''
})

function onShipBatchChange() {
  // 换批次时包装形式保留，是否有对应灌装记录由后端最终校验
}

async function load() {
  const [sh, bt, sm] = await Promise.all([
    shippingApi.shipments(statusFilter.value),
    shippingApi.bottlings(),
    shippingApi.summary()
  ])
  shipments.value = sh
  bottlings.value = bt
  summary.value = sm
}

async function nextStatus(row) {
  await shippingApi.nextStatus(row.id)
  ElMessage.success('已推进到 ' + row.nextStatusName)
  await load()
}

function openShip() {
  shipForm.value = blankShip()
  shipVisible.value = true
}
function openBottling() {
  bottlingForm.value = blankBottling()
  bottlingVisible.value = true
}

async function submitShipment() {
  await shippingApi.createShipment(shipForm.value)
  ElMessage.success('出货单已登记')
  shipVisible.value = false
  await load()
}

async function submitBottling() {
  await shippingApi.createBottling(bottlingForm.value)
  ElMessage.success('灌装单已登记（放行已生效）')
  bottlingVisible.value = false
  await load()
}

onMounted(async () => {
  // 批次列表自带当前质检态与后端裁决的 qcBlockReason，下拉里直接禁用不可用批次
  batches.value = await batchApi.list()
  await load()
})
</script>

<style scoped>
.tag-frozen {
  background: #7a4e9e !important;
  margin-left: 6px;
  cursor: help;
}
.freeze-reason {
  margin-top: 3px;
  font-size: 12px;
  color: #7a4e9e;
  line-height: 1.3;
}
.qc-cell .line {
  display: block;
  font-size: 12px;
  line-height: 1.7;
}
.warn-text {
  color: var(--bw-warn, #ef6c00);
  font-size: 12px;
}
.is-alert-box b {
  color: #7a4e9e;
}
.gate-list {
  max-height: 180px;
  overflow: auto;
  width: 100%;
  border: 1px solid var(--bw-border, #dfe6d3);
  border-radius: 6px;
  padding: 6px 8px;
}
.gate-row {
  font-size: 12px;
  line-height: 2;
  display: flex;
  align-items: center;
  gap: 8px;
}
.gate-row.blocked {
  color: var(--bw-warn, #ef6c00);
}
</style>
