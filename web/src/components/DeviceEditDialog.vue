<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Device, Role } from '@/types/device'

const { t } = useI18n()

interface Props {
  visible: boolean
  current: Device | null
  roleItems: Role[]
  clearMemoryLoading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  clearMemoryLoading: false,
})

const emit = defineEmits<{
  close: []
  submit: [device: Device]
  clearMemory: [device: Device]
}>()

const formData = ref<Device>({
  deviceId: '',
  deviceName: '',
  roleId: undefined,
  state: '0',
})

function handleClose() {
  emit('close')
}

function handleOk() {
  emit('submit', formData.value)
}

function handleClearMemory() {
  emit('clearMemory', formData.value)
}

watch(
  () => props.visible,
  (visible) => {
    if (visible && props.current) {
      formData.value = { ...props.current }
    }
  },
)
</script>

<template>
  <a-modal
    :open="visible"
    :title="t('device.deviceDetails')"
    width="650px"
    @ok="handleOk"
    @cancel="handleClose"
  >
    <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
      <a-form-item :label="t('device.deviceName')">
        <a-input v-model:value="formData.deviceName" class="center-input" />
      </a-form-item>

      <a-form-item :label="t('device.bindRole')">
        <a-select v-model:value="formData.roleId" class="center-select">
          <a-select-option
            v-for="role in roleItems"
            :key="role.roleId"
            :value="role.roleId"
          >
            {{ role.roleName }}
          </a-select-option>
        </a-select>
      </a-form-item>

    </a-form>

    <template #footer>
      <a-popconfirm
        v-permission="'system:device:memory'"
        :title="t('device.confirmClearMemory')"
        :ok-text="t('common.confirm')"
        :cancel-text="t('common.cancel')"
        @confirm="handleClearMemory"
      >
        <a-button key="clear" type="primary" danger :loading="clearMemoryLoading">
          {{ t('device.clearMemory') }}
        </a-button>
      </a-popconfirm>
      <a-button key="back" @click="handleClose">{{ t('common.cancel') }}</a-button>
      <a-button v-permission="'system:device:update'" key="submit" type="primary" @click="handleOk">{{ t('common.confirm') }}</a-button>
    </template>
  </a-modal>
</template>

<style scoped lang="scss">
:deep(.center-input) {
  text-align: center;
}

:deep(.center-select .ant-select-selection-item) {
  text-align: center;
}
</style>
