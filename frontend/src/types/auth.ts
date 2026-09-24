export type Role = 'TRANSLATOR' | 'CHIEF_EDITOR' | 'PROJECT_MANAGER'

export interface User {
  id: number
  initials: string
  fullName: string
  email: string
  role: Role
}

export interface WorkspaceData {
  role: Role
  userName: string
  message: string
}

