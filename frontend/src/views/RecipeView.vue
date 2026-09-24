<template>
  <div>
    <div class="panel">
      <div class="panel-title">
        配方与原料用量
        <span class="hint">环形图为各类原料投放占比；用量明细是「投放阶段 → 用量」的 Map</span>
      </div>
      <div class="toolbar">
        <span class="spacer"></span>
        <el-button type="primary" @click="createVisible = true">新增配方</el-button>
      </div>

      <div class="card-wall">
        <div v-for="r in recipes" :key="r.id" class="recipe-card">
          <div class="recipe-card-head">
            <div class="ring" :style="{ background: ringGradient(r) }">
              <div class="ring-label">{{ r.itemCount }}<br />阶段</div>
            </div>
            <div style="flex: 1">
              <div style="font-size: 15px; font-weight: 700">{{ r.name }}</div>
              <div style="font-size: 12px; color: #6b7760">
                {{ r.code }} · {{ r.styleName }}
              </div>
              <div style="font-size: 12px; margin-top: 4px">
                OG {{ r.targetOg }} · {{ r.targetAbv }}% · {{ r.targetIbu }} IBU · {{ r.targetEbc }} EBC
              </div>
            </div>
          </div>
          <div class="legend">
            <span v-for="s in r.composition" :key="s.ingredientType">
              <i :style="{ background: s.color }"></i>{{ s.ingredientTypeName }} {{ s.percent }}%
            </span>
          </div>
          <div style="margin-top: 10px; display: flex; gap: 8px; align-items: center">
            <el-button size="small" type="primary" plain @click="openEdit(r)">编辑用量（Map）</el-button>
            <span style="font-size: 12px; color: #6b7760">{{ r.processNote }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Map 编辑：按投放阶段（Map 的 key）增删改用量 -->
    <el-drawer v-model="drawer" :title="current ? current.name + ' · 原料用量' : '用量'" size="620px">
      <el-table v-if="current" :data="current.items" size="small" border>
        <el-table-column prop="stageName" label="投放阶段（key）" width="140">
          <template #default="{ row }">
            <el-tag size="small" type="success">{{ row.stage }}</el-tag>
            <div style="font-size: 11px; color: #6b7760">{{ row.stageName }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="ingredientName" label="原料" min-width="120" />
        <el-table-column prop="ingredientTypeName" label="类别" width="80" />
        <el-table-column label="用量" width="110">
          <template #default="{ row }">{{ row.quantity }} {{ row.unit }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <el-button size="small" plain @click="fillForm(row)">修改</el-button>
            <el-button size="small" type="danger" plain @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-divider content-position="left">新增 / 修改某个 key 的用量</el-divider>
      <el-form :model="itemForm" label-width="96px">
        <el-form-item label="投放阶段">
          <el-select v-model="itemForm.stage" filterable allow-create style="width: 100%">
            <el-option v-for="s in stages" :key="s.key" :label="s.name + '（' + s.key + '）'" :value="s.key" />
          </el-select>
        </el-form-item>
        <el-form-item label="原料">
          <el-select v-model="itemForm.ingredientCode" filterable style="width: 100%">
            <el-option v-for="i in ingredients" :key="i.code" :label="i.name + ' · ' + i.code" :value="i.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="用量">
          <el-input-number v-model="itemForm.quantity" :precision="3" :min="0.001" :step="0.5" style="width: 60%" />
          <el-input v-model="itemForm.unit" placeholder="单位" style="width: 35%; margin-left: 5%" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="itemForm.note" /></el-form-item>
        <el-form-item>
          <el-button type="primary" @click="submitItem">保存（无此 key 则新增）</el-button>
          <el-button @click="resetForm">清空</el-button>
        </el-form-item>
      </el-form>
    </el-drawer>

    <el-dialog v-model="createVisible" title="新增配方" width="520px">
      <el-form :model="createForm" label-width="96px">
        <el-form-item label="配方编码"><el-input v-model="createForm.code" placeholder="如 R-1011" /></el-form-item>
        <el-form-item label="配方名"><el-input v-model="createForm.name" /></el-form-item>
        <el-form-item label="风格">
          <el-select v-model="createForm.style" style="width: 100%">
            <el-option label="IPA" value="IPA" />
            <el-option label="世涛" value="STOUT" />
            <el-option label="皮尔森" value="PILSNER" />
            <el-option label="小麦" value="WHEAT" />
            <el-option label="酸啤" value="SOUR" />
            <el-option label="淡色艾尔" value="PALE_ALE" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标原浓"><el-input-number v-model="createForm.targetOg" :precision="3" :step="0.001" /></el-form-item>
        <el-form-item label="目标酒精度"><el-input-number v-model="createForm.targetAbv" :precision="2" :step="0.1" /></el-form-item>
        <el-form-item label="目标苦度"><el-input-number v-model="createForm.targetIbu" :precision="1" :step="1" /></el-form-item>
        <el-form-item label="目标色度"><el-input-number v-model="createForm.targetEbc" :precision="1" :step="1" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ingredientApi, recipeApi } from '../api'

const recipes = ref([])
const stages = ref([])
const ingredients = ref([])
const drawer = ref(false)
const current = ref(null)
const createVisible = ref(false)
const createForm = ref({ code: '', name: '', style: 'IPA', targetOg: 1.05, targetAbv: 5, targetIbu: 30, targetEbc: 10 })
const itemForm = ref({ stage: 'MASH_IN', ingredientCode: '', quantity: 1, unit: 'kg', note: '' })

/** CSS conic-gradient 环形图：把各原料类型占比拼成一段一段的角度 */
function ringGradient(recipe) {
  const slices = recipe.composition || []
  if (!slices.length) return '#e3e8d9'
  let acc = 0
  const parts = slices.map((s) => {
    const from = acc
    acc += Number(s.percent)
    return `${s.color} ${from}% ${acc}%`
  })
  return `conic-gradient(${parts.join(', ')})`
}

async function load() {
  recipes.value = await recipeApi.list()
  stages.value = await recipeApi.stages()
  ingredients.value = await ingredientApi.list()
}

async function openEdit(recipe) {
  current.value = await recipeApi.get(recipe.id)
  resetForm()
  drawer.value = true
}

function resetForm() {
  itemForm.value = { stage: 'MASH_IN', ingredientCode: '', quantity: 1, unit: 'kg', note: '' }
}

function fillForm(row) {
  itemForm.value = {
    stage: row.stage,
    ingredientCode: row.ingredientCode,
    quantity: row.quantity,
    unit: row.unit,
    note: row.note
  }
}

async function submitItem() {
  if (!itemForm.value.ingredientCode) {
    ElMessage.warning('请选择原料')
    return
  }
  const body = {
    ingredientCode: itemForm.value.ingredientCode,
    quantity: itemForm.value.quantity,
    unit: itemForm.value.unit,
    note: itemForm.value.note
  }
  await recipeApi.putItem(current.value.id, itemForm.value.stage, body)
  ElMessage.success('用量已保存')
  current.value = await recipeApi.get(current.value.id)
  await load()
}

async function remove(row) {
  await recipeApi.removeItem(current.value.id, row.stage)
  ElMessage.success('该阶段用量已删除')
  current.value = await recipeApi.get(current.value.id)
  await load()
}

async function submitCreate() {
  await recipeApi.create(createForm.value)
  ElMessage.success('配方已新建')
  createVisible.value = false
  await load()
}

onMounted(load)
</script>
