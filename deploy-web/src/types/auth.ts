export interface UserProfile {
  id: string
  username: string
  displayName: string
  phone?: string
  email?: string
  avatar?: string
  authorities?: string[]
}

export interface PasswordForm {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}
