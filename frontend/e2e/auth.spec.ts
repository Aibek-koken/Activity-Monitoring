import { expect, test } from '@playwright/test'

const accounts = [
  { role: 'Translator', path: '/translator', heading: 'Welcome, Maya' },
  { role: 'Chief Editor', path: '/chief-editor', heading: 'Welcome, Elias' },
  { role: 'Project Manager', path: '/project-manager', heading: 'Welcome, Oliver' },
] as const

test.beforeEach(async ({ page }) => {
  const pageErrors: string[] = []
  page.on('pageerror', (error) => {
    pageErrors.push(error.message)
  })
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible()
  expect(pageErrors).toEqual([])
})

for (const account of accounts) {
  test(`${account.role} signs in and reaches only the correct workspace`, async ({ page }, testInfo) => {
    await page.getByRole('button', { name: new RegExp(`^${account.role}`) }).click()
    await page.getByRole('button', { name: /^Sign in$/ }).click()

    await expect(page).toHaveURL(new RegExp(`${account.path}$`))
    await expect(page.getByRole('heading', { name: new RegExp(account.heading) })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Your workspace' })).toBeVisible()
    await expect(page.locator('.profile-details').getByText(account.role, { exact: true })).toBeVisible()
    if (account.role === 'Project Manager') {
      await page.screenshot({ path: testInfo.outputPath('project-manager-workspace.png'), fullPage: true })
    }
  })
}

test('invalid credentials keep the form and show a clear error', async ({ page }) => {
  await page.getByLabel('Email').fill('translator@easylang.local')
  await page.getByLabel('Password', { exact: true }).fill('incorrect')
  await page.getByRole('button', { name: /^Sign in$/ }).click()

  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('alert')).toContainText('Email or password is incorrect')
  await expect(page.getByLabel('Email')).toHaveValue('translator@easylang.local')
})

test('a signed-in user is redirected away from another role route', async ({ page }) => {
  await page.getByRole('button', { name: /^Translator/ }).click()
  await page.getByRole('button', { name: /^Sign in$/ }).click()
  await expect(page).toHaveURL(/\/translator$/)

  await page.goto('/project-manager')
  await expect(page).toHaveURL(/\/translator$/)
})

test('sign out clears the session and protects the previous workspace', async ({ page }) => {
  await page.getByRole('button', { name: /^Chief Editor/ }).click()
  await page.getByRole('button', { name: /^Sign in$/ }).click()
  await expect(page).toHaveURL(/\/chief-editor$/)

  await page.getByRole('button', { name: /^Sign out$/ }).click()
  await expect(page).toHaveURL(/\/login$/)
  await page.goto('/chief-editor')
  await expect(page).toHaveURL(/\/login$/)
})

test('login page has no error overlay and remains usable at the active viewport', async ({ page }, testInfo) => {
  await expect(page.locator('.vite-error-overlay')).toHaveCount(0)
  await expect(page.locator('body')).not.toHaveText('')
  await page.screenshot({ path: testInfo.outputPath('login-page.png'), fullPage: true })
})
