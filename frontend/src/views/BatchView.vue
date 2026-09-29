<template>
  <div>
    <div class="panel">
      <div class="panel-title">
        啤酒批次
        <span class="hint">计划 → 糖化 → 煮沸 → 发酵 → 熟成 → 已灌装 / 报废；熟成后须经质检放行才能灌装出货</span>
      </div>

      <div class="summary-strip">
        <div class="summary-box"><b>{{ summary.batchTotal || 0 }}</b><span>批次总数</span></div>
        <div class="summary-box"><b>{{ summary.volumeTotal || 0 }} L</b><span>累计产量</span></div>
        <div class="summary-box"><b>{{ summary.packagedVolume || 0 }} L</b><span>已灌装产量</span></div>
        <div v-for="item in summary.items" :key="item.status" class="summary-box">
          <b>{{ item.count }}</b><span>{{ item.statusName }}（{{ item.totalVolume }} L）</span>
        </div>
      </div>

      <div class="toolbar">
        <el-select v-model="statusFilter" clearable placeholder="按批次状态筛选" style="width: 180px" @change="load">
          <el-option label="计划" value="PLAN" />
          <el-option label="糖化" value="MASH" />
          <el-option label="煮沸" value="BOIL" />
          <el-option label="发酵" value="FERMENT" />
          <el-option label="熟成" value="CONDITION" />
          <el-option label="已灌装" value="PACKAGED" />
          <el-option label="报废" value="SCRAPPED" />
        </el-select>
        <span class="spacer"></span>
        <el-button type="primary" @click="createVisible = true">新增批次</el-button>
      </div>

      <el-table :data="rows" border stripe size="small">
        <el-table-column prop="batchNo" label="批号" width="120" />
        <el-table-column prop="recipeName" label="配方" min-width="140" />
        <el-table-column prop="fermenterNo" label="占用罐" width="80" />
        <el-table-column prop="brewer" label="酿酒师" width="80" />
        <el-table-column label="工艺节点" min-width="200">
          <template #default="{ row }">
            <span style="font-size: 12px">
              糖化 {{ fmt(row.mashStart) }} | 煮沸终 {{ fmt(row.boilEnd) }} | 发酵启 {{ fmt(row.fermentStart) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="volumeL" label="产量(L)" width="80" />
        <el-table-column label="批次状态" width="80">
          <template #default="{ row }">
            <span class="tag-inline" :style="{ background: statusColor(row.status) }">{{ row.statusName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="质检放行" min-width="200">
          <template #default="{ row }">
            <div class="qc-cell">
              <span class="tag-inline" :style="{ background: row.qcStateColor }">{{ row.qcStateName }}</span>
              <span v-if="row.latestQcRoundNo" class="qc-cell-sub">
                第{{ row.latestQcRoundNo }}轮
                <template v-if="row.latestQcOperator"> · {{ row.latestQcOperator }} · {{ fmt(row.latestQcAt) }}</template>
              </span>
              <span v-if="row.qcBlockReason && row.status !== 'SCRAPPED'" class="qc-cell-reason">{{ row.qcBlockReason }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain @click="openQc(row)">质检</el-button>
            <el-button v-if="row.nextStatus" size="small" type="success" plain @click="advance(row)">
              推进{{ row.nextStatusName }}
            </el-button>
            <el-button v-if="!isEnd(row)" size="small" type="danger" plain @click="scrap(row)">报废</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="createVisible" title="新增批次" width="520px">
      <el-form :model="createForm" label-width="96px">
        <el-form-item label="批号"><el-input v-model="createForm.batchNo" placeholder="如 BC-2026-114" /></el-form-item>
        <el-form-item label="配方">
          <el-select v-model="createForm.recipeId" style="width: 100%">
            <el-option v-for="r in recipes" :key="r.id" :label="r.name + ' · ' + r.styleName" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="酿酒师"><el-input v-model="createForm.brewer" /></el-form-item>
        <el-form-item label="酿造日期"><el-date-picker v-model="createForm.brewDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="预计产量(L)"><el-input-number v-model="createForm.volumeL" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">保存</el-button>
      </template>
    </el-dialog>

    <!-- ================= 批次质检放行 ================= -->
    <el-dialog v-model="qcVisible" :title="`质检放行 · ${qc.batchNo || ''} · ${qc.recipeName || ''}`" width="860px" top="6vh">
      <div class="qc-head">
        <div>
          批次状态：
          <span class="tag-inline" :style="{ background: statusColor(qc.status) }">{{ qc.statusName }}</span>
          <span style="margin-left: 12px">
            当前质检：
            <span class="tag-inline" :style="{ background: qc.qcStateColor }">{{ qc.qcStateName }}</span>
            <span class="qc-version">共 {{ qc.qcRounds?.length || 0 }} 轮 · 版本 v{{ qc.qcVersion }}</span>
          </span>
        </div>
        <div>
          <el-button size="small" @click="openReading(qc)">逐日测温/测糖</el-button>
          <el-button size="small" @click="reloadQc">刷新</el-button>
        </div>
      </div>

      <el-alert
        v-if="qc.qcBlockReason"
        :closable="false"
        class="qc-block"
        type="warning"
        show-icon
        :title="`下游受限：${qc.qcBlockReason}`"
        description="该结论是灌装单、出货单与推进「已灌装」的真实后端约束；驳回/复检中/撤销放行时入口会被直接阻止。"
      />

      <!-- 当前轮次操作区 -->
      <div class="panel-inner">
        <div class="panel-inner-title">
          {{ isOpen ? `第 ${latestRound.roundNo} 轮 · 送检中（待提交结论）` : '发起新一轮质检' }}
        </div>

        <template v-if="isOpen">
          <el-descriptions :column="3" size="small" border>
            <el-descriptions-item label="送检人">{{ latestRound.inspector }}</el-descriptions-item>
            <el-descriptions-item label="送检时间">{{ fmtFull(latestRound.openedAt) }}</el-descriptions-item>
            <el-descriptions-item label="送检时批次状态">{{ latestRound.batchStatusName }}</el-descriptions-item>
            <el-descriptions-item label="逐日温度/糖度摘要" :span="3">
              <span class="snap-text">{{ summarize(latestRound.readingsSummary) }}</span>
            </el-descriptions-item>
          </el-descriptions>

          <el-form label-width="92px" class="qc-form" :model="submitForm">
            <el-form-item label="检验酒精度">
              <el-input-number v-model="submitForm.qcAbv" :precision="2" :step="0.1" :min="0" />
              <span class="form-hint">%vol（放行必填）</span>
            </el-form-item>
            <el-form-item label="感官结论">
              <el-input v-model="submitForm.sensory" type="textarea" :rows="2" placeholder="色 / 香 / 味 / 澄清度（放行必填）" />
            </el-form-item>
            <el-form-item label="驳回原因">
              <el-input v-model="submitForm.reason" type="textarea" :rows="2" placeholder="仅驳回时必填；放行时可留空" />
            </el-form-item>
            <el-form-item label="操作人">
              <el-input v-model="submitForm.operator" placeholder="提交结论的质检员" style="width: 220px" />
            </el-form-item>
            <el-form-item>
              <el-button type="success" :loading="acting" @click="submitVerdict('PASS')">提交放行</el-button>
              <el-button type="danger" plain :loading="acting" @click="submitVerdict('REJECT')">驳回</el-button>
            </el-form-item>
          </el-form>
        </template>

        <template v-else>
          <el-form :inline="true" class="qc-form">
            <el-form-item :label="qc.qcRounds?.length ? '复检说明' : '送检人'">
              <el-input v-model="openForm.inspector" :placeholder="qc.qcRounds?.length ? '发起复检的操作人' : '送检操作人'" style="width: 200px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="acting" @click="openRound">
                {{ qc.qcRounds?.length ? `发起复检（第 ${(qc.qcRounds?.length || 0) + 1} 轮）` : '发起质检（送检）' }}
              </el-button>
              <el-button v-if="qc.qcState === 'PASSED'" type="warning" plain :loading="acting" @click="revokeVisible = true">
                撤销放行
              </el-button>
            </el-form-item>
          </el-form>
          <p v-if="qc.qcRounds?.length" class="form-hint">
            复检会新开一轮并立即挂起原放行结论（该批次未发出货同步冻结）；已提交的历史轮次不会被覆盖或删除。
          </p>
        </template>
      </div>

      <!-- 历史轮次（只读、可追溯） -->
      <div class="panel-inner">
        <div class="panel-inner-title">历史轮次（只增不改，按时间顺序）</div>
        <el-timeline v-if="qc.qcRounds?.length">
          <el-timeline-item
            v-for="r in [...qc.qcRounds].reverse()"
            :key="r.id"
            :type="timelineType(r.result)"
            :hollow="!r.latest"
            :timestamp="`第 ${r.roundNo} 轮 · ${roundTime(r)}`"
          >
            <div class="round-card" :class="{ 'is-latest': r.latest }">
              <div class="round-head">
                <span class="tag-inline" :style="{ background: resultColor(r.result) }">{{ r.resultName }}</span>
                <span v-if="r.latest" class="tag-inline" style="background: #4b8a25">当前有效</span>
                <span class="round-meta">送检 {{ r.inspector }} · 送检时状态 {{ r.batchStatusName }}</span>
              </div>
              <div v-if="r.qcAbv != null" class="round-line">酒精度：{{ r.qcAbv }} %vol</div>
              <div v-if="r.sensory" class="round-line">感官：{{ r.sensory }}</div>
              <div v-if="r.readingsSummary" class="round-line snap-text">读数摘要：{{ summarize(r.readingsSummary) }}</div>
              <div v-if="r.submittedBy" class="round-line">结论人：{{ r.submittedBy }}</div>
              <div v-if="r.reason" class="round-line round-reason">原因：{{ r.reason }}</div>
              <div v-if="r.revokedBy" class="round-line round-reason">
                撤销人：{{ r.revokedBy }} · {{ fmtFull(r.revokedAt) }}（放行结论保留为「已撤销」，未删除）
              </div>
            </div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="尚未发起过质检" :image-size="60" />
      </div>
    </el-dialog>

    <!-- 撤销放行（必须填原因） -->
    <el-dialog v-model="revokeVisible" title="撤销放行" width="520px" append-to-body>
      <el-alert type="warning" :closable="false" show-icon
        title="撤销后该批次所有【待发】出货单将立即冻结，灌装/出货入口被阻止；重新放行后才能恢复。已发/已结款单据不受影响。"
        style="margin-bottom: 12px" />
      <el-form label-width="80px">
        <el-form-item label="撤销原因" required>
          <el-input v-model="revokeForm.reason" type="textarea" :rows="3" placeholder="必须填写撤销原因，将随该轮永久留痕" />
        </el-form-item>
        <el-form-item label="操作人" required>
          <el-input v-model="revokeForm.operator" style="width: 220px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="revokeVisible = false">取消</el-button>
        <el-button type="warning" :loading="acting" @click="doRevoke">确认撤销并冻结</el-button>
      </template>
    </el-dialog>

    <!-- 逐日测温 / 测糖 -->
    <el-dialog v-model="readingVisible" :title="`逐日测温 / 测糖 · ${qc.batchNo || currentRow.batchNo || ''}`" width="520px" append-to-body>
      <el-table :data="readingTarget.curve || []" size="small" border max-height="240">
        <el-table-column prop="dayKey" label="第 N 天" width="100" />
        <el-table-column prop="temp" label="温度 ℃" />
        <el-table-column prop="gravity" label="糖度 °P" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button size="small" type="danger" plain @click="removeReading(row.dayKey)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-divider />
      <el-form :inline="true" :model="readingForm">
        <el-form-item label="天数"><el-input v-model="readingForm.dayKey" placeholder="D7" style="width: 90px" /></el-form-item>
        <el-form-item label="温度"><el-input-number v-model="readingForm.temp" :precision="1" :step="0.5" style="width: 120px" /></el-form-item>
        <el-form-item label="糖度"><el-input-number v-model="readingForm.gravity" :precision="1" :step="0.5" style="width: 120px" /></el-form-item>
        <el-form-item><el-button type="primary" @click="submitReading">登记</el-button></el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { batchApi, qcApi, recipeApi } from '../api'

const rows = ref([])
const recipes = ref([])
const summary = ref({})
const statusFilter = ref('')
const createVisible = ref(false)
const readingVisible = ref(false)
const qcVisible = ref(false)
const revokeVisible = ref(false)
const acting = ref(false)
const qc = ref({})
const currentRow = ref({})
const readingTarget = ref({})
const createForm = ref({ batchNo: '', recipeId: null, brewer: '', brewDate: '', volumeL: 1000 })
const readingForm = ref({ dayKey: 'D1', temp: 19, gravity: 12 })
const openForm = ref({ inspector: '' })
const submitForm = ref({ qcAbv: null, sensory: '', reason: '', operator: '' })
const revokeForm = ref({ reason: '', operator: '' })

const COLOR = {
  PLAN: '#9aa5ab',
  MASH: '#f0d58a',
  BOIL: '#ef9b52',
  FERMENT: '#8bc34a',
  CONDITION: '#d99a2b',
  PACKAGED: '#5b8a2a',
  SCRAPPED: '#c0392b'
}
const RESULT_COLOR = { OPEN: '#ef9b52', PASSED: '#4b8a25', REJECTED: '#c0392b', REVOKED: '#7a4e9e' }

function statusColor(status) {
  return COLOR[status] || '#8d99a6'
}
function resultColor(r) {
  return RESULT_COLOR[r] || '#8d99a6'
}
function timelineType(result) {
  return { PASSED: 'success', REJECTED: 'danger', REVOKED: 'warning', OPEN: 'primary' }[result] || 'info'
}

function fmt(v) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(5, 16)
}
function fmtFull(v) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 16)
}
function roundTime(r) {
  if (r.revokedAt) return `${fmtFull(r.revokedAt)} 撤销`
  if (r.submittedAt) return `${fmtFull(r.submittedAt)} 结论`
  return `${fmtFull(r.openedAt)} 送检`
}
function summarize(json) {
  if (!json) return '（无测量快照）'
  try {
    const o = JSON.parse(json)
    const parts = []
    if (o.tempDays != null) parts.push(`温度 ${o.tempDays} 天`)
    if (o.gravityDays != null) parts.push(`糖度 ${o.gravityDays} 天`)
    if (o.tempFirst != null) parts.push(`温度 ${o.tempFirst}→${o.tempLast}℃`)
    if (o.gravityFirst != null) parts.push(`糖度 ${o.gravityFirst}→${o.gravityLast}°P`)
    return parts.join('；')
  } catch (e) {
    return json
  }
}

const latestRound = computed(() => {
  const list = qc.value.qcRounds || []
  return list.find((r) => r.latest) || list[list.length - 1] || {}
})
const isOpen = computed(() => latestRound.value.result === 'OPEN')

function isEnd(row) {
  return row.status === 'PACKAGED' || row.status === 'SCRAPPED'
}

async function load() {
  rows.value = await batchApi.list(statusFilter.value)
  summary.value = await batchApi.summary()
}

async function advance(row) {
  try {
    await batchApi.advance(row.id, row.nextStatus)
    ElMessage.success('批次已推进到 ' + row.nextStatusName)
    await load()
  } catch (e) {
    // 后端会给出明确的质检拦截原因（拦截器已弹错），这里不刷新覆盖
  }
}

async function scrap(row) {
  try {
    await ElMessageBox.confirm(`确认报废批次 ${row.batchNo}？已有灌装单或质检进行中时会被阻止。`, '批次报废', {
      type: 'warning'
    })
    await batchApi.scrap(row.id)
    ElMessage.success('批次已报废')
    await load()
  } catch (e) {
    // 取消或业务拦截均无需额外处理
  }
}

// ---------------- 质检 ----------------
async function openQc(row) {
  currentRow.value = row
  qcVisible.value = true
  await reloadQc()
}

async function reloadQc() {
  qc.value = await qcApi.detail(currentRow.value.id)
  const r = latestRound.value
  submitForm.value = {
    qcAbv: r.qcAbv ?? null,
    sensory: r.sensory || '',
    reason: '',
    operator: ''
  }
  openForm.value = { inspector: qc.value.brewer || '' }
  revokeForm.value = { reason: '', operator: qc.value.brewer || '' }
}

async function openRound() {
  if (!openForm.value.inspector?.trim()) {
    ElMessage.error('请填写送检/复检操作人')
    return
  }
  acting.value = true
  try {
    await qcApi.open(qc.value.id, {
      inspector: openForm.value.inspector.trim(),
      expectedVersion: qc.value.qcVersion
    })
    ElMessage.success(qc.value.qcRounds?.length ? '复检轮次已发起，原放行挂起，待发出货已冻结' : '质检已发起（送检）')
    await reloadQc()
    await load()
  } finally {
    acting.value = false
  }
}

async function submitVerdict(verdict) {
  const f = submitForm.value
  if (!f.operator?.trim()) {
    ElMessage.error('请填写操作人')
    return
  }
  if (verdict === 'PASS' && (!f.qcAbv || f.qcAbv <= 0)) {
    ElMessage.error('放行必须填写检验酒精度')
    return
  }
  if (verdict === 'PASS' && !f.sensory?.trim()) {
    ElMessage.error('放行必须填写感官结论')
    return
  }
  if (verdict === 'REJECT' && !f.reason?.trim()) {
    ElMessage.error('驳回必须填写原因')
    return
  }
  acting.value = true
  try {
    await qcApi.submit(qc.value.id, {
      verdict,
      operator: f.operator.trim(),
      qcAbv: f.qcAbv,
      sensory: f.sensory,
      reason: f.reason,
      roundId: latestRound.value.id,
      expectedVersion: qc.value.qcVersion
    })
    ElMessage.success(verdict === 'PASS' ? '已放行，冻结的待发出货已恢复' : '已驳回，待发出货已冻结')
    await reloadQc()
    await load()
  } finally {
    acting.value = false
  }
}

async function doRevoke() {
  if (!revokeForm.value.reason?.trim()) {
    ElMessage.error('撤销放行必须填写原因')
    return
  }
  if (!revokeForm.value.operator?.trim()) {
    ElMessage.error('请填写操作人')
    return
  }
  acting.value = true
  try {
    const r = await qcApi.revoke(qc.value.id, {
      reason: revokeForm.value.reason.trim(),
      operator: revokeForm.value.operator.trim(),
      roundId: latestRound.value.id,
      expectedVersion: qc.value.qcVersion
    })
    revokeVisible.value = false
    ElMessage.success(r.reason || '放行已撤销，待发出货已冻结')
    await reloadQc()
    await load()
  } finally {
    acting.value = false
  }
}

// ---------------- 逐日读数 ----------------
function openReading(row) {
  readingTarget.value = row
  readingVisible.value = true
}

async function submitReading() {
  await batchApi.putReading(readingTarget.value.id || currentRow.value.id, readingForm.value)
  ElMessage.success('测量数据已登记')
  const fresh = await batchApi.get(readingTarget.value.id || currentRow.value.id)
  readingTarget.value = fresh
  if (qcVisible.value) await reloadQc()
  await load()
}

async function removeReading(dayKey) {
  const id = readingTarget.value.id || currentRow.value.id
  await batchApi.removeReading(id, dayKey)
  ElMessage.success('已删除')
  readingTarget.value = await batchApi.get(id)
  await load()
}

async function submitCreate() {
  await batchApi.create(createForm.value)
  ElMessage.success('批次已建档')
  createVisible.value = false
  await load()
}

onMounted(async () => {
  recipes.value = await recipeApi.list()
  await load()
})
</script>

<style scoped>
.qc-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.qc-cell-sub {
  font-size: 12px;
  color: var(--bw-text-sub);
}
.qc-cell-reason {
  font-size: 12px;
  color: var(--bw-warn);
  line-height: 1.3;
}
.qc-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.qc-version {
  margin-left: 10px;
  font-size: 12px;
  color: var(--bw-text-sub);
}
.qc-block {
  margin-bottom: 12px;
}
.panel-inner {
  border: 1px solid var(--bw-border);
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 12px;
  background: #fbfdf8;
}
.panel-inner-title {
  font-weight: 700;
  margin-bottom: 10px;
}
.qc-form {
  margin-top: 10px;
}
.form-hint {
  font-size: 12px;
  color: var(--bw-text-sub);
  margin-left: 8px;
}
.snap-text {
  font-size: 12px;
  color: var(--bw-text-sub);
}
.round-card {
  padding: 8px 10px;
  border-radius: 6px;
  background: #fff;
  border: 1px solid var(--bw-border);
}
.round-card.is-latest {
  border-color: var(--bw-primary);
  box-shadow: 0 0 0 1px var(--bw-primary-soft);
}
.round-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.round-meta {
  font-size: 12px;
  color: var(--bw-text-sub);
}
.round-line {
  font-size: 13px;
  line-height: 1.6;
}
.round-reason {
  color: var(--bw-warn);
}
</style>
