import { describe, expect, it } from 'vitest'
import { rolePath } from './roles'

describe('rolePath', () => {
  it('maps every backend role to its dedicated route', () => {
    expect(rolePath('TRANSLATOR')).toBe('/translator')
    expect(rolePath('CHIEF_EDITOR')).toBe('/chief-editor')
    expect(rolePath('PROJECT_MANAGER')).toBe('/project-manager')
  })
})

