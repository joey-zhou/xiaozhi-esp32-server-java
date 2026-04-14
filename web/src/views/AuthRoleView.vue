<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import * as Icons from '@ant-design/icons-vue'
import { queryAuthRoles, getAuthRolePermissionConfig, updateAuthRolePermissions } from '@/services/authRole'
import type { AuthRole, AuthRolePermissionConfig, PermissionTreeNode } from '@/types/authRole'

type PermissionTableItem = PermissionTreeNode & {
  children?: PermissionTableItem[]
  iconComponent?: unknown
}

const { t } = useI18n()
const antIcons = Icons as Record<string, unknown>

const authRoles = ref<AuthRole[]>([])
const permissionConfig = ref<AuthRolePermissionConfig | null>(null)
const selectedAuthRoleId = ref<number>()
const checkedPermissionIds = ref<number[]>([])
const expandedKeys = ref<number[]>([])

const roleLoading = ref(false)
const configLoading = ref(false)
const saveLoading = ref(false)

const roleKeyword = ref('')

const filteredAuthRoles = computed(() => {
  const keyword = roleKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return authRoles.value
  }
  return authRoles.value.filter((item) => {
    return (
      item.authRoleName.toLowerCase().includes(keyword) ||
      item.roleKey.toLowerCase().includes(keyword)
    )
  })
})

const checkedPermissionIdSet = computed(() => new Set(checkedPermissionIds.value))

const selectedAuthRole = computed(() => {
  if (permissionConfig.value?.authRoleId === selectedAuthRoleId.value) {
    return permissionConfig.value
  }
  return authRoles.value.find((item) => item.authRoleId === selectedAuthRoleId.value) ?? null
})

const permissionTableData = computed<PermissionTableItem[]>(() => {
  return toPermissionTableData(permissionConfig.value?.permissionTree ?? [])
})

const expandablePermissionIds = computed(() => collectExpandableIds(permissionConfig.value?.permissionTree ?? []))

const permissionColumns = computed<TableColumnsType<PermissionTableItem>>(() => [
  {
    title: t('authRole.permissionName'),
    key: 'name',
    width: 280,
  },
  {
    title: t('authRole.permissionKeyLabel'),
    dataIndex: 'permissionKey',
    key: 'permissionKey',
    width: 240,
    ellipsis: true,
  },
  {
    title: t('authRole.permissionType'),
    dataIndex: 'permissionType',
    key: 'permissionType',
    width: 120,
    align: 'center',
  },
  {
    title: t('authRole.routePath'),
    dataIndex: 'path',
    key: 'path',
    width: 220,
    ellipsis: true,
  },
  {
    title: t('authRole.componentPath'),
    dataIndex: 'component',
    key: 'component',
    width: 240,
    ellipsis: true,
  },
  {
    title: t('authRole.iconLabel'),
    dataIndex: 'icon',
    key: 'icon',
    width: 180,
    ellipsis: true,
  },
  {
    title: t('common.sort'),
    dataIndex: 'sort',
    key: 'sort',
    width: 90,
    align: 'center',
  },
  {
    title: t('authRole.visibleLabel'),
    dataIndex: 'visible',
    key: 'visible',
    width: 100,
    align: 'center',
  },
  {
    title: t('common.status'),
    dataIndex: 'status',
    key: 'status',
    width: 100,
    align: 'center',
  },
])

const permissionRowSelection = computed(() => ({
  selectedRowKeys: checkedPermissionIds.value,
  preserveSelectedRowKeys: true,
  checkStrictly: false,
  columnWidth: 52,
  onChange: (keys: Array<string | number>) => {
    checkedPermissionIds.value = Array.from(new Set(keys.map(Number))).sort((a, b) => a - b)
  },
}))

watch(
  () => permissionConfig.value?.authRoleId,
  () => {
    expandedKeys.value = []
  },
  { immediate: true }
)

onMounted(() => {
  loadAuthRoles()
})

async function loadAuthRoles() {
  roleLoading.value = true
  try {
    const res = await queryAuthRoles({ pageNo: 1, pageSize: 200 })
    if (res.code !== 200 || !res.data?.list) {
      message.error(res.message || t('authRole.loadRolesFailed'))
      return
    }

    authRoles.value = res.data.list
    if (!authRoles.value.length) {
      selectedAuthRoleId.value = undefined
      permissionConfig.value = null
      checkedPermissionIds.value = []
      return
    }

    const nextAuthRoleId = authRoles.value.some((item) => item.authRoleId === selectedAuthRoleId.value)
      ? selectedAuthRoleId.value
      : authRoles.value[0]!.authRoleId

    if (nextAuthRoleId) {
      await selectAuthRole(nextAuthRoleId)
    }
  } catch (error) {
    console.error('加载权限角色失败:', error)
    message.error(t('authRole.loadRolesFailed'))
  } finally {
    roleLoading.value = false
  }
}

async function selectAuthRole(authRoleId: number) {
  if (selectedAuthRoleId.value === authRoleId && permissionConfig.value?.authRoleId === authRoleId) {
    return
  }

  selectedAuthRoleId.value = authRoleId
  permissionConfig.value = null
  checkedPermissionIds.value = []
  configLoading.value = true
  try {
    const res = await getAuthRolePermissionConfig(authRoleId)
    if (res.code !== 200 || !res.data) {
      message.error(res.message || t('authRole.loadConfigFailed'))
      return
    }

    permissionConfig.value = res.data
    checkedPermissionIds.value = Array.from(new Set(res.data.checkedPermissionIds ?? [])).sort((a, b) => a - b)
  } catch (error) {
    console.error('加载权限配置失败:', error)
    message.error(t('authRole.loadConfigFailed'))
  } finally {
    configLoading.value = false
  }
}

async function handleSavePermissions() {
  if (!selectedAuthRoleId.value || !permissionConfig.value) {
    return
  }

  saveLoading.value = true
  try {
    const permissionIds = Array.from(new Set(checkedPermissionIds.value)).sort((a, b) => a - b)
    const res = await updateAuthRolePermissions(selectedAuthRoleId.value, permissionIds)
    if (res.code !== 200 || !res.data) {
      message.error(res.message || t('authRole.saveFailed'))
      return
    }

    permissionConfig.value = res.data
    checkedPermissionIds.value = Array.from(new Set(res.data.checkedPermissionIds ?? [])).sort((a, b) => a - b)
    message.success(t('authRole.saveSuccess'))
  } catch (error) {
    console.error('保存权限配置失败:', error)
    message.error(t('authRole.saveFailed'))
  } finally {
    saveLoading.value = false
  }
}

function expandAll() {
  expandedKeys.value = expandablePermissionIds.value
}

function collapseAll() {
  expandedKeys.value = []
}

function getPermissionRowClass(record: PermissionTableItem) {
  return checkedPermissionIdSet.value.has(record.permissionId) ? 'permission-row--checked' : ''
}

function collectExpandableIds(nodes: PermissionTreeNode[]): number[] {
  return nodes.flatMap((node) => {
    if (!node.children?.length) {
      return []
    }
    return [node.permissionId, ...collectExpandableIds(node.children)]
  })
}

function toPermissionTableData(nodes: PermissionTreeNode[]): PermissionTableItem[] {
  return nodes.map((node) => {
    const children = toPermissionTableData(node.children ?? [])
    const normalizedIconName = node.icon
      ? node.icon
          .split('-')
          .filter(Boolean)
          .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
          .join('') + 'Outlined'
      : ''

    return {
      ...node,
      children: children.length ? children : undefined,
      iconComponent: node.icon ? antIcons[node.icon] ?? antIcons[normalizedIconName] ?? null : null,
    }
  })
}
</script>

<template>
  <div class="auth-role-view">
    <div class="auth-role-layout">
      <div class="auth-role-sidebar">
        <a-card :title="t('authRole.roleList')" :loading="roleLoading" :bordered="false" class="page-card">
          <template #extra>
            <a-input
              v-model:value="roleKeyword"
              :placeholder="t('authRole.roleSearchPlaceholder')"
              allow-clear
              class="role-search"
            />
          </template>

          <a-empty v-if="!filteredAuthRoles.length" :description="t('authRole.emptyRoleList')" />

          <a-list v-else :data-source="filteredAuthRoles" :split="false" class="auth-role-list">
            <template #renderItem="{ item }">
              <a-list-item>
                <button
                  type="button"
                  class="auth-role-item"
                  :class="{ active: item.authRoleId === selectedAuthRoleId }"
                  @click="selectAuthRole(item.authRoleId)"
                >
                  <div class="auth-role-item__header">
                    <span class="auth-role-item__name">{{ item.authRoleName }}</span>
                    <a-tag :color="item.status === '1' ? 'success' : 'default'">
                      {{ item.status === '1' ? t('common.enable') : t('common.disable') }}
                    </a-tag>
                  </div>
                  <div class="auth-role-item__meta">
                    <span>{{ item.roleKey }}</span>
                  </div>
                  <div class="auth-role-item__desc">
                    {{ item.description || t('authRole.noDescription') }}
                  </div>
                </button>
              </a-list-item>
            </template>
          </a-list>
        </a-card>
      </div>

      <div class="auth-role-content">
        <a-card :bordered="false" class="page-card">
          <template #title>
            <div class="permission-panel-title">
              <span>{{ t('authRole.permissionWorkbench') }}</span>
              <a-tag v-if="selectedAuthRole" color="blue">{{ selectedAuthRole.authRoleName }}</a-tag>
            </div>
          </template>
          <template #extra>
            <a-space>
              <a-button :disabled="!permissionTableData.length" @click="expandAll">
                {{ t('authRole.expandAll') }}
              </a-button>
              <a-button :disabled="!permissionTableData.length" @click="collapseAll">
                {{ t('authRole.collapseAll') }}
              </a-button>
              <a-button
                v-permission="'system:auth-role:assign'"
                type="primary"
                :loading="saveLoading"
                :disabled="!selectedAuthRoleId"
                @click="handleSavePermissions"
              >
                {{ t('authRole.savePermissions') }}
              </a-button>
            </a-space>
          </template>

          <a-empty v-if="!selectedAuthRole" :description="t('authRole.selectRole')" />

          <template v-else>
            <a-spin :spinning="configLoading">
              <a-empty
                v-if="!permissionTableData.length"
                :description="t('authRole.emptyPermissionTree')"
                class="permission-empty"
              />
              <div v-else class="permission-table-shell">
                <a-table
                  v-model:expandedRowKeys="expandedKeys"
                  :columns="permissionColumns"
                  :data-source="permissionTableData"
                  :pagination="false"
                  :row-selection="permissionRowSelection"
                  :row-class-name="getPermissionRowClass"
                  :scroll="{ x: 1520 }"
                  children-column-name="children"
                  row-key="permissionId"
                  size="middle"
                  class="permission-table"
                >
                  <template #bodyCell="{ column, record }">
                    <template v-if="column.key === 'name'">
                      <div class="permission-name-cell">
                        <div class="permission-name-cell__icon">
                          <component :is="record.iconComponent" v-if="record.iconComponent" />
                          <span v-else>{{ record.icon ? record.icon.slice(0, 2).toUpperCase() : '#' }}</span>
                        </div>
                        <div class="permission-name-cell__content">
                          <div class="permission-name-cell__title">{{ record.name }}</div>
                          <div class="permission-name-cell__sub">
                            <span>#{{ record.permissionId }}</span>
                            <span v-if="record.children?.length">
                              {{ t('authRole.childPermissions') }} {{ record.children.length }}
                            </span>
                          </div>
                        </div>
                      </div>
                    </template>

                    <template v-else-if="column.key === 'permissionKey'">
                      <code class="permission-code">{{ record.permissionKey }}</code>
                    </template>

                    <template v-else-if="column.key === 'permissionType'">
                      <a-tag :color="record.permissionType === 'menu' ? 'blue' : record.permissionType === 'button' ? 'gold' : 'cyan'">
                        {{
                          record.permissionType === 'menu'
                            ? t('authRole.menuType')
                            : record.permissionType === 'button'
                              ? t('authRole.buttonType')
                              : t('authRole.apiType')
                        }}
                      </a-tag>
                    </template>

                    <template v-else-if="column.key === 'path'">
                      <span v-if="record.path" class="permission-ellipsis">{{ record.path }}</span>
                      <span v-else>-</span>
                    </template>

                    <template v-else-if="column.key === 'component'">
                      <span v-if="record.component" class="permission-ellipsis">{{ record.component }}</span>
                      <span v-else>-</span>
                    </template>

                    <template v-else-if="column.key === 'icon'">
                      <a-tag v-if="record.icon" class="permission-icon-tag">
                        {{ record.icon }}
                      </a-tag>
                      <span v-else>-</span>
                    </template>

                    <template v-else-if="column.key === 'sort'">
                      {{ record.sort ?? 0 }}
                    </template>

                    <template v-else-if="column.key === 'visible'">
                      <a-tag :color="record.visible === '1' ? 'green' : 'default'">
                        {{ record.visible === '1' ? t('common.yes') : t('common.no') }}
                      </a-tag>
                    </template>

                    <template v-else-if="column.key === 'status'">
                      <a-tag :color="record.status === '1' ? 'success' : 'default'">
                        {{ record.status === '1' ? t('common.enable') : t('common.disable') }}
                      </a-tag>
                    </template>
                  </template>
                </a-table>
              </div>
            </a-spin>
          </template>
        </a-card>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.auth-role-view {
  padding: 24px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.auth-role-layout {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 16px;
  flex: 1;
  min-height: 0;
}

.auth-role-sidebar {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.auth-role-content {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.page-card {
  display: flex;
  flex-direction: column;
  height: 100%;

  :deep(.ant-card-body) {
    flex: 1;
    min-height: 0;
    overflow: auto;
  }
}

.role-search {
  width: 220px;
}

.auth-role-list {
  margin-top: 8px;

  :deep(.ant-list-item) {
    padding: 0 0 12px 0;
  }
}

.auth-role-item {
  width: 100%;
  border: 1px solid var(--ant-color-border-secondary);
  border-radius: 10px;
  background: var(--ant-color-bg-container);
  padding: 14px 16px;
  text-align: left;
  cursor: pointer;
  transition: all 0.2s ease;
}

.auth-role-item:hover {
  border-color: var(--ant-color-primary-border);
  background: var(--ant-color-fill-quaternary);
}

.auth-role-item.active {
  border-color: var(--ant-color-primary);
  box-shadow: 0 0 0 2px var(--ant-color-primary-bg);
  background: var(--ant-color-primary-bg-hover);
}

.auth-role-item__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.auth-role-item__name {
  font-size: 15px;
  font-weight: 600;
  color: var(--ant-color-text);
}

.auth-role-item__meta {
  margin-top: 6px;
  color: var(--ant-color-text-secondary);
  font-size: 13px;
}

.auth-role-item__desc {
  margin-top: 8px;
  color: var(--ant-color-text-tertiary);
  font-size: 13px;
  line-height: 1.5;
}

.auth-role-content {
  min-width: 0;
}

.permission-panel-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.permission-table-shell {
  overflow: hidden;
  border: 1px solid var(--ant-color-border-secondary);
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(248, 250, 252, 0.98));
}

.permission-empty {
  padding: 40px 0;
}

.permission-name-cell {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.permission-name-cell__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--ant-color-primary-bg);
  color: var(--ant-color-primary);
  font-size: 16px;
  flex: none;
}

.permission-name-cell__content {
  min-width: 0;
}

.permission-name-cell__title {
  color: var(--ant-color-text);
  font-weight: 600;
  line-height: 1.4;
}

.permission-name-cell__sub {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  margin-top: 4px;
  color: var(--ant-color-text-secondary);
  font-size: 12px;
}

.permission-code {
  display: inline-block;
  padding: 3px 8px;
  border-radius: 8px;
  background: var(--ant-color-fill-quaternary);
  color: var(--ant-color-text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.permission-ellipsis {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.permission-icon-tag {
  margin-inline-end: 0;
}

:deep(.permission-table .ant-table-container) {
  border-radius: 18px;
}

:deep(.permission-table .ant-table-thead > tr > th) {
  background: #f7f9fc;
  color: var(--ant-color-text-secondary);
  font-weight: 600;
}

:deep(.permission-table .ant-table-tbody > tr > td) {
  transition: background 0.2s ease;
}

:deep(.permission-table .ant-table-tbody > tr.permission-row--checked > td) {
  background: var(--ant-color-primary-bg);
}

:deep(.permission-table .ant-table-tbody > tr:hover > td) {
  background: rgba(22, 119, 255, 0.04);
}

:deep(.permission-table .ant-table-selection-column) {
  vertical-align: top;
  padding-top: 15px;
}

:deep(.permission-table .ant-table-row-expand-icon) {
  margin-top: 10px;
}

@media (max-width: 992px) {
  .auth-role-layout {
    grid-template-columns: 1fr;
  }

  .role-search {
    width: 180px;
  }
}

@media (max-width: 768px) {
  .role-search {
    width: 140px;
  }
}
</style>
