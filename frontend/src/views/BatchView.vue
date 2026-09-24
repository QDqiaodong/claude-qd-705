<template>
  <div>
    <div class="panel">
      <div class="panel-title">
        啤酒批次
        <span class="hint">计划 → 糖化 → 煮沸 → 发酵 → 熟成 → 已灌装 / 报废</span>
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
        <el-select v-model="statusFilter" clearable placeholder="按状态筛选" style="width: 180px" @change="load">
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
        <el-table-column prop="batchNo" label="批号" width="130" />
        <el-table-column prop="recipeName" label="配方" min-width="150" />
        <el-table-column prop="fermenterNo" label="占用罐" width="90" />
        <el-table-column prop="brewer" label="酿酒师" width="90" />
        <el-table-column label="工艺节点" min-width="220">
          <template #default="{ row }">
            <span style="font-size: 12px">
              糖化 {{ fmt(row.mashStart) }} | 煮沸终 {{ fmt(row.boilEnd) }} | 发酵启 {{ fmt(row.fermentStart) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="expectedMature" label="预计成熟" width="110" />
        <el-table-column prop="volumeL" label="产量(L)" width="90" />
        <el-table-column label="ABV" width="70"><template #default="{ row }">{{ row.abv ?? '—' }}</template></el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="tag-inline" :style="{ background: statusColor(row.status) }">{{ row.statusName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190">
          <template #default="{ row }">
            <el-button v-if="row.nextStatus" size="small" type="success" plain @click="advance(row)">
              推进到{{ row.nextStatusName }}
            </el-button>
            <el-button v-if="!isEnd(row)" size="small" type="danger" plain @click="scrap(row)">报废</el-button>
            <el-button size="small" plain @click="openReading(row)">测温</el-button>
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

    <el-dialog v-model="readingVisible" :title="`逐日测温 / 测糖 · ${currentRow.batchNo || ''}`" width="520px">
      <el-table :data="currentRow.curve || []" size="small" border max-height="240">
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
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { batchApi, recipeApi } from '../api'

const rows = ref([])
const recipes = ref([])
const summary = ref({})
const statusFilter = ref('')
const createVisible = ref(false)
const readingVisible = ref(false)
const currentRow = ref({})
const createForm = ref({ batchNo: '', recipeId: null, brewer: '', brewDate: '', volumeL: 1000 })
const readingForm = ref({ dayKey: 'D1', temp: 19, gravity: 12 })

const COLOR = {
  PLAN: '#9aa5ab',
  MASH: '#f0d58a',
  BOIL: '#ef9b52',
  FERMENT: '#8bc34a',
  CONDITION: '#d99a2b',
  PACKAGED: '#5b8a2a',
  SCRAPPED: '#c0392b'
}

function statusColor(status) {
  return COLOR[status] || '#8d9aa5'
}

function fmt(v) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(5, 16)
}

function isEnd(row) {
  return row.status === 'PACKAGED' || row.status === 'SCRAPPED'
}

async function load() {
  rows.value = await batchApi.list(statusFilter.value)
  summary.value = await batchApi.summary()
}

async function advance(row) {
  await batchApi.advance(row.id, row.nextStatus)
  ElMessage.success('批次已推进到 ' + row.nextStatusName)
  await load()
}

async function scrap(row) {
  await batchApi.scrap(row.id)
  ElMessage.success('批次已报废')
  await load()
}

function openReading(row) {
  currentRow.value = row
  readingVisible.value = true
}

async function submitReading() {
  await batchApi.putReading(currentRow.value.id, readingForm.value)
  await refreshCurrent()
  ElMessage.success('测量数据已登记')
}

async function removeReading(dayKey) {
  await batchApi.removeReading(currentRow.value.id, dayKey)
  await refreshCurrent()
  ElMessage.success('已删除')
}

async function refreshCurrent() {
  const fresh = await batchApi.get(currentRow.value.id)
  currentRow.value = fresh
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
