import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ModalFuncProps } from 'ant-design-vue'

const modalMock = vi.hoisted(() => ({
  confirm: vi.fn(() => ({ destroy: vi.fn(), update: vi.fn() })),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('ant-design-vue', () => ({
  Modal: modalMock,
  message: {
    error: vi.fn(),
    success: vi.fn(),
  },
}))

import { useConfirm } from '../useConfirm'

function lastProps(): ModalFuncProps {
  const calls = modalMock.confirm.mock.calls as unknown as ModalFuncProps[][]
  const last = calls[calls.length - 1]
  if (!last) {
    throw new Error('Modal.confirm 未被调用')
  }
  return last[0] as ModalFuncProps
}

describe('useConfirm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('exposes only the three converged methods', () => {
    expect(Object.keys(useConfirm()).sort()).toEqual(['confirm', 'confirmAsync', 'confirmDelete'])
  })

  it('fills defaults and always keeps the cancel button', () => {
    useConfirm().confirm(vi.fn())

    expect(lastProps()).toMatchObject({
      title: 'common.confirm',
      content: 'common.confirmOperation',
      okText: 'common.confirm',
      cancelText: 'common.cancel',
      okType: 'primary',
      okCancel: true,
    })
  })

  it('keeps an explicitly empty content instead of falling back to the default text', () => {
    useConfirm().confirm(vi.fn(), { content: '' })

    expect(lastProps().content).toBe('')
  })

  it('keeps explicitly empty ok/cancel text', () => {
    useConfirm().confirm(vi.fn(), { title: '', okText: '', cancelText: '' })

    expect(lastProps()).toMatchObject({ title: '', okText: '', cancelText: '' })
  })

  it('hides the cancel button through okCancel rather than a css class', () => {
    useConfirm().confirm(vi.fn(), { showCancel: false })

    expect(lastProps().okCancel).toBe(false)
    expect(lastProps().class).toBeUndefined()
  })

  it('runs the callback and resolves even when it throws', async () => {
    const error = new Error('boom')
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    const onOk = vi.fn(() => {
      throw error
    })

    useConfirm().confirm(onOk)
    await expect(lastProps().onOk?.()).resolves.toBeUndefined()

    expect(onOk).toHaveBeenCalledTimes(1)
    expect(consoleSpy).toHaveBeenCalledWith('confirm action failed:', error)
    consoleSpy.mockRestore()
  })

  it('resolves even when the callback returns a rejected promise', async () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

    useConfirm().confirm(() => Promise.reject(new Error('async boom')))
    await expect(lastProps().onOk?.()).resolves.toBeUndefined()

    consoleSpy.mockRestore()
  })

  it('defaults confirmDelete to the danger ok button', () => {
    useConfirm().confirmDelete(vi.fn())

    expect(lastProps()).toMatchObject({
      title: 'common.confirmDelete',
      content: 'common.confirmDeleteMessage',
      okText: 'common.delete',
      cancelText: 'common.cancel',
      okType: 'danger',
      okCancel: true,
    })
  })

  it('lets confirmDelete options override okType and pass okButtonProps through', () => {
    useConfirm().confirmDelete(vi.fn(), {
      okType: 'primary',
      okButtonProps: { loading: true, disabled: true },
      icon: 'custom-icon',
      width: 520,
    })

    expect(lastProps()).toMatchObject({
      okType: 'primary',
      okButtonProps: { loading: true, disabled: true },
      icon: 'custom-icon',
      width: 520,
    })
  })

  it('resolves confirmAsync with true when confirmed', async () => {
    const pending = useConfirm().confirmAsync({ content: 'config.modelNameInvalid' })

    lastProps().onOk?.()

    await expect(pending).resolves.toBe(true)
    expect(lastProps().content).toBe('config.modelNameInvalid')
  })

  it('resolves confirmAsync with false when cancelled', async () => {
    const pending = useConfirm().confirmAsync()

    lastProps().onCancel?.()

    await expect(pending).resolves.toBe(false)
  })
})
