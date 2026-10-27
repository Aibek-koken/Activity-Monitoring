import { expect, test } from '@playwright/test'

async function signInAsTranslator(page: import('@playwright/test').Page) {
  await page.goto('/login')
  await page.getByRole('button', { name: /^Translator/ }).click()
  await page.getByRole('button', { name: /^Sign in$/ }).click()
  await expect(page).toHaveURL(/\/translator$/)
}

test('responsible translator estimates, finishes, and submits one review', async ({ page }, testInfo) => {
  test.skip(process.env.E2E_ISOLATED_DB !== 'true', 'This state-changing test requires a disposable E2E database.')
  test.skip(testInfo.project.name !== 'desktop', 'The write-through flow runs once against the isolated E2E database.')
  await signInAsTranslator(page)

  await page.getByRole('region', { name: 'Assigned activities' }).getByRole('link', { name: /EL-2026-002/ }).click()
  await expect(page.getByRole('heading', { name: 'Supplier contract appendix' })).toBeVisible()
  await expect(page.getByLabel('Record date')).not.toHaveValue('')
  await page.getByRole('button', { name: /Weekly remaining estimate/ }).click()
  await page.getByLabel('Remaining volume (units)').fill('12.50')
  await expect(page.getByLabel('Remaining volume (units)')).toHaveValue('12.50')
  await page.getByRole('button', { name: 'Save estimate' }).click()
  await expect(page.getByText('Weekly estimate saved.')).toBeVisible()
  await page.reload()
  await page.getByRole('button', { name: /Weekly remaining estimate/ }).click()
  await expect(page.getByLabel('Remaining volume (units)')).toHaveValue('12.50')

  await page.getByLabel('Remaining volume (units)').fill('0')
  await page.getByRole('button', { name: 'Save estimate' }).click()
  await expect(page.getByRole('button', { name: 'Finish translation' })).toBeVisible()
  await page.getByRole('button', { name: 'Finish translation' }).click()
  await expect(page.getByText('Finishing locks daily records and weekly estimates.')).toBeVisible()
  await page.getByRole('button', { name: 'Confirm finish' }).click()
  await expect(page.getByRole('heading', { name: 'Ready for checking' })).toBeVisible()
  await expect(page.getByLabel('Remaining volume (units)')).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Save record' })).toHaveCount(0)

  await page.getByRole('button', { name: 'Submit for checking' }).click()
  await expect(page.getByRole('heading', { name: 'Under checking' })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Under checking' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Submit for checking' })).toHaveCount(0)
})

test('assistant sees estimates read-only and cannot open an unassigned activity', async ({ page }) => {
  await signInAsTranslator(page)
  await page.getByRole('region', { name: 'Assigned activities' }).getByRole('link', { name: /EL-2026-003/ }).click()
  await page.getByRole('button', { name: /Weekly remaining estimate/ }).click()
  await expect(page.getByText('Only the responsible translator can update this estimate.')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Finish translation' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Submit for checking' })).toHaveCount(0)

  await page.goto('/translator/activities/4')
  await expect(page.getByRole('heading', { name: 'Activity unavailable' })).toBeVisible()
})
