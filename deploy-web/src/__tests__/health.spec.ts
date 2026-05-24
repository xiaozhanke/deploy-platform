import { describe, expect, it } from 'vitest'

describe('vitest 基础冒烟', () => {
  it('运行环境为 jsdom 并能访问 window', () => {
    expect(typeof window).toBe('object')
    expect(typeof document).toBe('object')
  })

  it('支持 @ 别名解析（package.json 标识）', async () => {
    const pkg = (await import('../../package.json')) as { default: { name: string } }
    expect(pkg.default.name).toBe('deploy-web')
  })
})
