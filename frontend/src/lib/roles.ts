import type { Role } from '../types/auth'

export const roleLabels: Record<Role, string> = {
  TRANSLATOR: 'Translator',
  CHIEF_EDITOR: 'Chief Editor',
  PROJECT_MANAGER: 'Project Manager',
}

export const rolePaths: Record<Role, string> = {
  TRANSLATOR: '/translator',
  CHIEF_EDITOR: '/chief-editor',
  PROJECT_MANAGER: '/project-manager',
}

export function rolePath(role: Role): string {
  return rolePaths[role]
}

