import { expect, test, type Page } from '@playwright/test'

const correctionFeedback = 'Correct the terminology in section 2 and preserve the numbered clauses.'

function validPdf(marker: string): Buffer {
  const header = '%PDF-1.4\n'
  const catalogOffset = Buffer.byteLength(header)
  const catalog = '1 0 obj\n<</Type /Catalog /Pages 2 0 R>>\nendobj\n'
  const pagesOffset = catalogOffset + Buffer.byteLength(catalog)
  const pages = '2 0 obj\n<</Type /Pages /Kids [] /Count 0>>\nendobj\n'
  const markerOffset = pagesOffset + Buffer.byteLength(pages)
  const markerObject = `3 0 obj\n(${marker})\nendobj\n`
  const xrefOffset = markerOffset + Buffer.byteLength(markerObject)
  const offset = (value: number) => String(value).padStart(10, '0')
  const xref = `xref\n0 4\n0000000000 65535 f \n${offset(catalogOffset)} 00000 n \n${offset(pagesOffset)} 00000 n \n${offset(markerOffset)} 00000 n \n`
  const trailer = `trailer\n<</Size 4 /Root 1 0 R>>\nstartxref\n${xrefOffset}\n%%EOF\n`
  return Buffer.from(header + catalog + pages + markerObject + xref + trailer, 'ascii')
}

async function signInWithDemo(page: Page, role: 'Translator' | 'Chief Editor' | 'Project Manager') {
  const email = role === 'Translator'
    ? 'translator@easylang.local'
    : role === 'Chief Editor' ? 'editor@easylang.local' : 'manager@easylang.local'
  const workspacePath = role === 'Translator'
    ? '/translator'
    : role === 'Chief Editor' ? '/chief-editor' : '/project-manager'
  await page.goto('/login')
  await page.locator('#email').fill(email)
  await page.locator('#password').fill('Demo123!')
  await expect(page.locator('#email')).toHaveValue(email)
  await page.getByRole('button', { name: /^Sign in$/ }).click()
  await expect(page).toHaveURL(new RegExp(`${workspacePath}$`), { timeout: 30_000 })
}

async function signInAsSecondTranslator(page: Page) {
  await page.goto('/login')
  await page.locator('#email').fill('translator2@easylang.local')
  await page.locator('#password').fill('Demo123!')
  await expect(page.locator('#email')).toHaveValue('translator2@easylang.local')
  await page.getByRole('button', { name: /^Sign in$/ }).click()
  await expect(page).toHaveURL(/\/translator$/, { timeout: 30_000 })
}

async function signOut(page: Page) {
  await page.getByRole('button', { name: /^Sign out$/ }).click()
  await expect(page).toHaveURL(/\/login$/)
}

async function responseBytes(response: import('@playwright/test').APIResponse): Promise<Buffer> {
  return Buffer.from(await response.body())
}

test('isolated cross-role review loop preserves files and enforces security', async ({ page }) => {
  test.skip(process.env.E2E_ISOLATED_DB !== 'true', 'This flow requires the disposable Sprint 4 E2E database.')

  const apiBase = process.env.E2E_API_BASE_URL ?? '/api'
  const firstPdf = validPdf('cycle-one-original')
  const correctedPdf = validPdf('cycle-two-corrected')

  await signInWithDemo(page, 'Translator')
  await expect(page).toHaveURL(/\/translator$/)
  await page.getByRole('region', { name: 'Assigned activities' })
    .getByRole('link', { name: /EL-2026-002/ }).click()
  const translatorDetailUrl = page.url()

  await page.getByRole('button', { name: /Weekly remaining estimate/ }).click()
  await page.getByLabel('Remaining pages').fill('0')
  await page.getByRole('button', { name: 'Save estimate' }).click()
  await expect(page.getByText('Weekly estimate saved.')).toBeVisible()
  await page.getByRole('button', { name: 'Finish translation' }).click()
  await page.getByRole('button', { name: 'Confirm finish' }).click()
  await expect(page.getByRole('heading', { name: 'Ready for checking' })).toBeVisible()

  await page.getByLabel('Translation document').setInputFiles({
    name: 'forged.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('not a pdf'),
  })
  await page.getByRole('button', { name: 'Submit for checking' }).click()
  await expect(page.getByRole('alert')).toContainText('not a valid PDF')
  await expect(page.getByRole('heading', { name: 'Ready for checking' })).toBeVisible()

  await page.getByLabel('Translation document').setInputFiles({
    name: 'translation-cycle-1.pdf',
    mimeType: 'application/pdf',
    buffer: firstPdf,
  })
  await page.getByRole('button', { name: 'Submit for checking' }).click()
  await expect(page.getByRole('heading', { name: 'Your final version is with the Chief Editor' })).toBeVisible()
  await signOut(page)

  await signInWithDemo(page, 'Chief Editor')
  await expect(page).toHaveURL(/\/chief-editor$/)
  await expect(page.getByLabel('Open review workload')).toContainText('open version')
  await page.getByRole('button', { name: /Central Asia Legal Portal/ }).click()
  await page.getByRole('link', { name: /Supplier contract appendix/ }).click()
  await expect(page.getByRole('heading', { name: 'Supplier contract appendix' })).toBeVisible()
  const reviewId = Number(page.url().match(/\/chief-editor\/reviews\/(\d+)/)?.[1])
  expect(reviewId).toBeGreaterThan(0)

  const firstDownload = await page.request.get(`${apiBase}/v1/reviews/${reviewId}/document`)
  expect(firstDownload.status()).toBe(200)
  expect(firstDownload.headers()['content-disposition']).toContain('translation-cycle-1.pdf')
  expect(await responseBytes(firstDownload)).toEqual(firstPdf)

  const csrfStatus = await page.evaluate(async ({ apiBaseUrl, id }) => {
    const response = await fetch(`${apiBaseUrl}/v1/chief-editor/reviews/${id}/decision`, {
      body: JSON.stringify({ result: 'APPROVED', feedback: null }),
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      method: 'POST',
    })
    return response.status
  }, { apiBaseUrl: apiBase, id: reviewId })
  expect(csrfStatus).toBe(403)
  await expect(page.getByText('Open review', { exact: true })).toBeVisible()

  await page.getByLabel('Hours').fill('1')
  await page.getByLabel('Minutes').fill('45')
  await page.getByRole('button', { name: 'Save expected time' }).click()
  await expect(page.getByText('Expected time saved.')).toBeVisible()
  await page.locator('#review-feedback').fill(correctionFeedback)
  await page.getByLabel('Pages to redo if returned').fill('3')
  await page.getByRole('button', { name: 'Return for correction' }).click()
  await expect(page).toHaveURL(/\/chief-editor\?project=/)
  await expect(page.getByRole('status')).toContainText('returned for correction')
  await signOut(page)

  await signInWithDemo(page, 'Project Manager')
  const managerStatus = await page.evaluate(async ({ apiBaseUrl, id }) => {
    return (await fetch(`${apiBaseUrl}/v1/chief-editor/reviews/${id}`, { credentials: 'include' })).status
  }, { apiBaseUrl: apiBase, id: reviewId })
  expect(managerStatus).toBe(403)
  await page.goto('/chief-editor')
  await expect(page).toHaveURL(/\/project-manager$/)
  await signOut(page)

  await signInAsSecondTranslator(page)
  const foreignDownload = await page.request.get(`${apiBase}/v1/reviews/${reviewId}/document`)
  expect(foreignDownload.status()).toBe(404)
  expect(foreignDownload.headers()['content-disposition']).toBeUndefined()
  await signOut(page)

  await signInWithDemo(page, 'Translator')
  await page.goto(translatorDetailUrl)
  await expect(page.getByRole('heading', { name: 'Corrections requested' })).toBeVisible()
  await expect(page.locator('.correction-panel blockquote')).toHaveText(correctionFeedback)
  await page.getByRole('button', { name: /Notifications/ }).click()
  await expect(page.getByRole('region', { name: 'Notifications' })
    .getByRole('listitem').filter({ hasText: 'Corrections requested for EL-2026-002 (cycle 1' })
    .getByText('Correction requested', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Close notifications' }).click()
  await page.getByLabel('Corrected translation document').setInputFiles({
    name: 'translation-cycle-2.pdf',
    mimeType: 'application/pdf',
    buffer: correctedPdf,
  })
  await page.getByRole('button', { name: 'Upload corrected file' }).click()
  await expect(page.getByRole('heading', { name: 'Your final version is with the Chief Editor' })).toBeVisible()
  await signOut(page)

  await signInWithDemo(page, 'Chief Editor')
  await expect(page.getByLabel('Open review workload')).toContainText('open version')
  await page.getByRole('button', { name: /Central Asia Legal Portal/ }).click()
  await page.getByRole('link', { name: /Supplier contract appendix/ }).click()
  await expect(page.getByText('Cycle 2', { exact: true })).toBeVisible()
  const secondReviewId = Number(page.url().match(/\/chief-editor\/reviews\/(\d+)/)?.[1])
  expect(secondReviewId).toBeGreaterThan(reviewId)
  const secondDownload = await page.request.get(`${apiBase}/v1/reviews/${secondReviewId}/document`)
  expect(secondDownload.status()).toBe(200)
  expect(await responseBytes(secondDownload)).toEqual(correctedPdf)
  await page.getByRole('button', { name: 'Approve version' }).click()
  await expect(page).toHaveURL(/\/chief-editor\?project=/)
  await expect(page.getByRole('status')).toContainText('approved')
  await signOut(page)

  await signInWithDemo(page, 'Translator')
  await page.goto(translatorDetailUrl)
  await expect(page.getByRole('heading', { name: 'Activity completed' })).toBeVisible()
  await page.getByRole('link', { name: 'View activity details' }).click()
  await expect(page.getByText('Completed', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Show review history' }).click()
  await expect(page.getByText('2 cycles')).toBeVisible()
  await expect(page.getByRole('region', { name: 'Version history' })
    .getByText(correctionFeedback, { exact: true })).toBeVisible()
  await expect(page.getByText('translation-cycle-1.pdf', { exact: true })).toBeVisible()
  await expect(page.getByText('translation-cycle-2.pdf', { exact: true })).toBeVisible()
  const preservedFirstDownload = await page.request.get(`${apiBase}/v1/reviews/${reviewId}/document`)
  expect(preservedFirstDownload.status()).toBe(200)
  expect(await responseBytes(preservedFirstDownload)).toEqual(firstPdf)
})

test('weekly version, marks, rework and final completion stay separate', async ({ page }) => {
  test.skip(process.env.E2E_ISOLATED_DB !== 'true', 'This flow requires the disposable Sprint 4 E2E database.')

  const apiBase = process.env.E2E_API_BASE_URL ?? '/api'
  const weeklyPdf = validPdf('weekly-version-one')
  const weeklyCorrected = validPdf('weekly-version-two')
  const finalPdf = validPdf('final-version')

  await signInWithDemo(page, 'Translator')
  await page.getByRole('region', { name: 'Assigned activities' })
    .getByRole('link', { name: /EL-2026-001/ }).click()
  const activityUrl = page.url()
  await page.getByRole('heading', { name: "This week's version" }).waitFor()
  await page.getByLabel('Weekly translation document').setInputFiles({
    name: 'weekly-one.pdf',
    mimeType: 'application/pdf',
    buffer: weeklyPdf,
  })
  await page.getByRole('button', { name: 'Send weekly version' }).click()
  await expect(page.getByRole('heading', { name: 'Weekly version under checking' })).toBeVisible()
  await expect(page.getByText('In progress', { exact: true })).toBeVisible()
  await signOut(page)

  await signInWithDemo(page, 'Chief Editor')
  await expect(page.getByLabel('Open review workload')).toContainText('open version')
  await page.getByRole('button', { name: /Central Asia Legal Portal/ }).click()
  const queueRow = page.getByRole('link', { name: /Privacy policy translation/ })
  await expect(queueRow).toBeVisible()
  await queueRow.click()
  await expect(page.getByRole('heading', { name: 'Privacy policy translation' })).toBeVisible()
  const weeklyReviewId = Number(page.url().match(/\/chief-editor\/reviews\/(\d+)/)?.[1])
  expect(weeklyReviewId).toBeGreaterThan(0)

  const annotationStatus = await page.evaluate(async ({ apiBaseUrl, id }) => {
    const csrfResponse = await fetch(`${apiBaseUrl}/v1/auth/csrf`, { credentials: 'include' })
    const csrf = await csrfResponse.json()
    const response = await fetch(`${apiBaseUrl}/v1/chief-editor/reviews/${id}/annotations`, {
      body: JSON.stringify({
        pageNumber: 1,
        kind: 'HIGHLIGHT',
        rects: [{ x: 0.1, y: 0.2, width: 0.3, height: 0.04 }],
        quotedText: 'Weekly progress',
        comment: 'Use the approved glossary term.',
        suggestedReplacement: 'Approved term',
      }),
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
      method: 'POST',
    })
    return response.status
  }, { apiBaseUrl: apiBase, id: weeklyReviewId })
  expect(annotationStatus).toBe(200)
  await page.reload()
  await expect(page.getByText('Use the approved glossary term.')).toBeVisible()
  await expect(page.locator('.annotation-item')).toHaveCount(1)

  const annotated = await page.request.get(`${apiBase}/v1/reviews/${weeklyReviewId}/annotated-document`)
  expect(annotated.status()).toBe(200)
  expect(annotated.headers()['content-type']).toContain('application/pdf')

  await page.getByLabel('Hours').fill('0')
  await page.getByLabel('Minutes').fill('30')
  await page.getByRole('button', { name: 'Save expected time' }).click()
  await expect(page.getByText('Expected time saved.')).toBeVisible()
  await page.locator('#review-feedback').fill('Fix the two glossary terms before the final version.')
  await page.getByLabel('Pages to redo if returned').fill('2,3')
  await page.getByRole('button', { name: 'Return for correction' }).click()
  await expect(page).toHaveURL(/\/chief-editor\?project=/)
  await expect(page.getByRole('status')).toContainText('returned for correction')
  await signOut(page)

  await signInWithDemo(page, 'Translator')
  await page.goto(activityUrl)
  await expect(page.getByRole('heading', { name: 'Corrections requested' })).toBeVisible()
  await expect(page.getByText('In progress', { exact: true })).toBeVisible()
  await page.getByLabel('Pages reworked').fill('1')
  await page.getByLabel('Hours (optional)').fill('1')
  await page.getByRole('button', { name: 'Save record' }).click()
  await expect(page.getByText('Daily record saved.')).toBeVisible()
  await expect(page.locator('.rework-strip')).toContainText('1 page')
  await page.getByLabel('Corrected translation document').setInputFiles({
    name: 'weekly-two.pdf',
    mimeType: 'application/pdf',
    buffer: weeklyCorrected,
  })
  await page.getByRole('button', { name: 'Upload corrected file' }).click()
  await expect(page.getByRole('heading', { name: 'Weekly version under checking' })).toBeVisible()
  await signOut(page)

  await signInWithDemo(page, 'Chief Editor')
  await expect(page.getByLabel('Open review workload')).toContainText('open version')
  await page.getByRole('button', { name: /Central Asia Legal Portal/ }).click()
  await page.getByRole('link', { name: /Privacy policy translation/ }).click()
  const correctedWeeklyId = Number(page.url().match(/\/chief-editor\/reviews\/(\d+)/)?.[1])
  expect(correctedWeeklyId).toBeGreaterThan(weeklyReviewId)
  await page.getByRole('button', { name: 'Approve version' }).click()
  await expect(page).toHaveURL(/\/chief-editor\?project=/)
  await expect(page.getByRole('status')).toContainText('approved')
  await signOut(page)

  await signInWithDemo(page, 'Translator')
  await page.goto(activityUrl)
  await expect(page.getByText('In progress', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: /Weekly remaining estimate/ }).click()
  await page.getByLabel('Remaining pages').fill('0')
  await page.getByRole('button', { name: 'Save estimate' }).click()
  await expect(page.getByText('Weekly estimate saved.')).toBeVisible()
  await page.getByRole('button', { name: 'Finish translation' }).click()
  await page.getByRole('button', { name: 'Confirm finish' }).click()
  await expect(page.getByRole('heading', { name: 'Ready for checking' })).toBeVisible()
  await page.getByLabel('Translation document').setInputFiles({
    name: 'final.pdf',
    mimeType: 'application/pdf',
    buffer: finalPdf,
  })
  await page.getByRole('button', { name: 'Submit for checking' }).click()
  await expect(page.getByRole('heading', { name: 'Your final version is with the Chief Editor' })).toBeVisible()
  await signOut(page)

  await signInWithDemo(page, 'Chief Editor')
  await expect(page.getByLabel('Open review workload')).toContainText('open version')
  await page.getByRole('button', { name: /Central Asia Legal Portal/ }).click()
  await page.getByRole('link', { name: /Privacy policy translation/ }).click()
  await page.getByRole('button', { name: 'Approve version' }).click()
  await expect(page).toHaveURL(/\/chief-editor\?project=/)
  await expect(page.getByRole('status')).toContainText('approved')
  await signOut(page)

  await signInWithDemo(page, 'Translator')
  await page.goto(activityUrl)
  await expect(page.getByRole('heading', { name: 'Activity completed' })).toBeVisible()
  await page.getByRole('link', { name: 'View activity details' }).click()
  await expect(page.getByText('Completed', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Show review history' }).click()
  await expect(page.getByText('3 cycles')).toBeVisible()
  await expect(page.getByRole('region', { name: 'Version history' })
    .getByText('weekly-one.pdf', { exact: true })).toBeVisible()
  await expect(page.getByRole('region', { name: 'Version history' })
    .getByText('final.pdf', { exact: true })).toBeVisible()
})
