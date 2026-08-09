<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  AppstoreOutlined,
  ArrowUpOutlined,
  CheckOutlined,
  FileImageOutlined,
  PaperClipOutlined,
  PlusOutlined,
  RightOutlined,
  RobotOutlined,
  StopOutlined,
} from '@ant-design/icons-vue'

const props = defineProps<{
  modelValue: string
  disabled?: boolean
  sending?: boolean
  roleName?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'send'): void
  (e: 'stop'): void
}>()

const { t } = useI18n()
const editor = ref<HTMLDivElement>()
const plusWrap = ref<HTMLDivElement>()
const menuOpen = ref(false)
const skillsOpen = ref(false)
const modelHovered = ref(false)

const skills = ['Deep Research', 'Code Review', 'Web Search', 'Summarize']
const hasText = computed(() => props.modelValue.trim().length > 0)
const sendActive = computed(() => hasText.value && !props.disabled && !props.sending)

watch(
  () => props.modelValue,
  (value) => {
    if (editor.value && editor.value.textContent !== value) {
      editor.value.textContent = value
    }
  }
)

function onEditorInput() {
  emit('update:modelValue', editor.value?.textContent || '')
}

function onEditorKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    if (sendActive.value) emit('send')
  }
}

function focus() {
  nextTick(() => editor.value?.focus())
}

function closeMenu() {
  menuOpen.value = false
  skillsOpen.value = false
  modelHovered.value = false
}

function onDocumentPointerDown(event: PointerEvent) {
  if (menuOpen.value && !plusWrap.value?.contains(event.target as Node)) closeMenu()
}

function onDocumentKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') closeMenu()
}

onMounted(() => {
  if (editor.value) editor.value.textContent = props.modelValue
  document.addEventListener('pointerdown', onDocumentPointerDown)
  document.addEventListener('keydown', onDocumentKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocumentPointerDown)
  document.removeEventListener('keydown', onDocumentKeydown)
})

defineExpose({ focus })
</script>

<template>
  <div class="wrap">
    <div class="frame" :class="{ 'is-disabled': disabled }">
      <div class="editor-wrap">
        <div
          ref="editor"
          class="field"
          :contenteditable="disabled ? 'false' : 'true'"
          role="textbox"
          aria-multiline="true"
          :aria-label="disabled ? t('chat.connectFirst') : t('chat.inputPlaceholder')"
          :data-empty="!hasText || undefined"
          :data-placeholder="disabled ? t('chat.connectFirst') : t('chat.inputPlaceholder')"
          @input="onEditorInput"
          @keydown="onEditorKeydown"
        ></div>
      </div>

      <div class="row">
        <div ref="plusWrap" class="plus-wrap">
          <button
            type="button"
            class="icon-btn plus"
            :data-open="menuOpen || undefined"
            :aria-label="t('chat.composer.addMenu')"
            :aria-expanded="menuOpen"
            :disabled="disabled"
            @click="menuOpen = !menuOpen"
          >
            <span class="plus-icon"><PlusOutlined /></span>
          </button>

          <div v-if="menuOpen" class="menu" role="menu">
            <button
              type="button"
              role="menuitem"
              class="menu-item"
              aria-disabled="true"
              :title="t('chat.composer.comingSoon')"
              @click.prevent
            >
              <span class="menu-icon"><FileImageOutlined /></span>
              <span class="menu-name">{{ t('chat.composer.addImage') }}</span>
            </button>
            <button
              type="button"
              role="menuitem"
              class="menu-item"
              aria-disabled="true"
              :title="t('chat.composer.comingSoon')"
              @click.prevent
            >
              <span class="menu-icon"><PaperClipOutlined /></span>
              <span class="menu-name">{{ t('chat.composer.attachFile') }}</span>
            </button>

            <div class="menu-divider"></div>

            <div class="menu-sub" @mouseenter="skillsOpen = true" @mouseleave="skillsOpen = false">
              <button
                type="button"
                role="menuitem"
                class="menu-item"
                aria-haspopup="menu"
                :aria-expanded="skillsOpen"
                @click="skillsOpen = true"
              >
                <span class="menu-icon"><AppstoreOutlined /></span>
                <span class="menu-name">{{ t('chat.composer.skills') }}</span>
                <span class="menu-chevron"><RightOutlined /></span>
              </button>
              <div v-if="skillsOpen" class="menu-flyout" role="menu">
                <button
                  v-for="skill in skills"
                  :key="skill"
                  type="button"
                  role="menuitem"
                  class="menu-item"
                  aria-disabled="true"
                  :title="t('chat.composer.comingSoon')"
                  @click.prevent
                >
                  <span class="menu-name">{{ skill }}</span>
                </button>
              </div>
            </div>

            <div class="menu-divider"></div>
            <div class="menu-label">{{ t('chat.composer.model') }}</div>
            <div
              class="menu-sub"
              @mouseenter="modelHovered = true"
              @mouseleave="modelHovered = false"
            >
              <button
                type="button"
                role="menuitemradio"
                aria-checked="true"
                class="menu-item"
                @click.prevent
              >
                <span class="menu-brand"><RobotOutlined /></span>
                <span class="menu-name">{{ roleName || t('chat.composer.followRole') }}</span>
                <span class="menu-check"><CheckOutlined /></span>
              </button>
              <div v-if="modelHovered" class="menu-popover" role="tooltip">
                <div class="popover-title">{{ roleName || t('chat.defaultAssistant') }}</div>
                <p class="popover-desc">{{ t('chat.composer.followRole') }}</p>
                <div class="popover-meta">{{ t('chat.composer.comingSoon') }}</div>
              </div>
            </div>
          </div>
        </div>

        <div class="right">
          <button
            v-if="sending"
            type="button"
            class="icon-btn send send-active"
            :aria-label="t('chat.composer.stop')"
            @click="emit('stop')"
          >
            <StopOutlined />
          </button>
          <button
            v-else
            type="button"
            class="icon-btn send"
            :class="{ 'send-active': sendActive }"
            :aria-label="t('chat.composer.send')"
            :disabled="!sendActive"
            @click="emit('send')"
          >
            <ArrowUpOutlined />
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.wrap {
  width: 100%;
  max-width: 420px;
  font-family: 'Inter Variable', 'Inter', sans-serif;
}

.frame {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 8px 10px 10px;
  background: #ffffff;
  border: 0.5px solid transparent;
  border-radius: 12px;
  box-shadow:
    0 0 0 0.5px rgba(0, 0, 0, 0.08),
    0 1px 2px rgba(0, 0, 0, 0.05),
    0 2px 4px rgba(0, 0, 0, 0.02);
}

.frame.is-disabled {
  background: #fafafa;
}

.editor-wrap {
  position: relative;
}

.field {
  position: relative;
  width: 100%;
  margin: 0;
  outline: 0;
  background: transparent;
  color: #1a1a1a;
  font: inherit;
  font-size: 12px;
  line-height: 18px;
  letter-spacing: -0.12px;
  min-height: 18px;
  max-height: 160px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
}

.field ::selection,
.field::selection {
  background: Highlight;
  color: HighlightText;
}

.field[data-empty]::before {
  content: attr(data-placeholder);
  position: absolute;
  top: 0;
  left: 0;
  color: #1a1a1a;
  opacity: 0.5;
  pointer-events: none;
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.plus-wrap {
  position: relative;
  display: flex;
}

.right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.icon-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  flex: none;
  padding: 0;
  border: 0;
  background: transparent;
  color: #1a1a1a;
  cursor: pointer;
}

.icon-btn::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 999px;
  background: rgba(26, 26, 26, 0.06);
  transition:
    background 150ms cubic-bezier(0.22, 1, 0.36, 1),
    transform 150ms cubic-bezier(0.22, 1, 0.36, 1);
}

.icon-btn:hover::before {
  background: rgba(26, 26, 26, 0.1);
}

.icon-btn:active::before {
  transform: scale(0.98);
}

.icon-btn :deep(svg) {
  position: relative;
  width: 14px;
  height: 14px;
}

.plus-icon {
  position: relative;
  display: inline-flex;
  transition: transform 200ms cubic-bezier(0.35, 1.55, 0.65, 1);
}

.plus[data-open]::before {
  background: rgba(26, 26, 26, 0.12);
}

.plus[data-open] .plus-icon {
  transform: rotate(45deg);
}

.send {
  color: #a1a1a1;
}

.send:disabled {
  cursor: default;
}

.send:disabled:active::before {
  transform: none;
}

.send-active {
  color: #ffffff;
}

.send-active::before {
  background: #0b0d12;
}

.send-active:hover::before {
  background: #2a2f3a;
}

.menu {
  position: absolute;
  bottom: calc(100% + 4px);
  left: 0;
  z-index: 20;
  width: 180px;
  padding: 3px;
  background: #ffffff;
  border: 0.5px solid #e6e8ec;
  border-radius: 10px;
  box-shadow:
    0 1px 2px rgba(0, 0, 0, 0.02),
    0 1px 1px rgba(0, 0, 0, 0.04);
  transform-origin: bottom left;
  animation: pi-menu-in 200ms cubic-bezier(0.22, 1, 0.36, 1) both;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  height: 26px;
  padding: 0 7px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: #1a1a1a;
  font-size: 11px;
  font-weight: 425;
  line-height: 12px;
  text-align: left;
  cursor: pointer;
}

.menu-item:hover {
  background: rgba(26, 26, 26, 0.06);
}

.menu-item:active {
  background: rgba(26, 26, 26, 0.09);
}

.wrap :deep(svg) {
  stroke-width: 1.5px;
}

.menu-icon,
.menu-check,
.menu-chevron,
.menu-brand {
  display: inline-flex;
  flex: none;
}

.menu-icon,
.menu-chevron {
  color: #a1a1a1;
}

.menu-check,
.menu-brand {
  color: #1a1a1a;
}

.menu-icon :deep(svg),
.menu-chevron :deep(svg),
.menu-check :deep(svg) {
  width: 14px;
  height: 14px;
}

.menu-brand :deep(svg) {
  width: 12px;
  height: 12px;
}

.menu-name {
  flex: 1 1 auto;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.menu-sub {
  position: relative;
}

.menu-flyout {
  position: absolute;
  top: -3px;
  left: calc(100% + 6px);
  width: 168px;
  padding: 3px;
  background: #ffffff;
  border: 0.5px solid #e6e8ec;
  border-radius: 10px;
  box-shadow:
    0 1px 2px rgba(0, 0, 0, 0.02),
    0 1px 1px rgba(0, 0, 0, 0.04);
}

.menu-flyout::before {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: -7px;
  width: 7px;
}

.menu-popover {
  position: absolute;
  top: -3px;
  left: calc(100% + 6px);
  z-index: 30;
  width: 200px;
  padding: 10px 12px;
  background: #ffffff;
  border: 0.5px solid #e6e8ec;
  border-radius: 10px;
  box-shadow:
    0 1px 2px rgba(0, 0, 0, 0.02),
    0 1px 1px rgba(0, 0, 0, 0.04);
  pointer-events: none;
}

.popover-title {
  font-size: 12px;
  font-weight: 500;
  line-height: 16px;
  color: #1a1a1a;
}

.popover-desc {
  margin: 2px 0 0;
  font-size: 11px;
  line-height: 15px;
  color: #a1a1a1;
}

.popover-meta {
  margin-top: 8px;
  font-size: 11px;
  line-height: 14px;
  color: #a1a1a1;
}

.menu-divider {
  height: 0.5px;
  margin: 4px -3px;
  background: #e6e8ec;
}

.menu-label {
  padding: 3px 7px;
  font-size: 11px;
  font-weight: 425;
  color: #a1a1a1;
}

@keyframes pi-menu-in {
  from {
    opacity: 0;
    transform: translateY(6px) scale(0.98);
    filter: blur(2px);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
    filter: blur(0);
  }
}

@media (max-width: 520px) {
  .menu-flyout {
    top: auto;
    bottom: calc(100% + 4px);
    left: 0;
  }

  .menu-popover {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .icon-btn::before,
  .menu-item,
  .plus-icon {
    transition: none;
  }
  .menu,
  .menu-flyout,
  .menu-popover {
    animation: none;
  }
}
</style>
