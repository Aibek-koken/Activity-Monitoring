export function parseCorrectionPages(input: string): number[] | null {
  const result = new Set<number>()
  for (const part of input.split(',')) {
    const match = /^\s*(\d{1,5})(?:\s*[-–]\s*(\d{1,5}))?\s*$/.exec(part)
    if (!match) return null
    const start = Number(match[1])
    const end = Number(match[2] ?? match[1])
    if (start < 1 || end > 10000 || end < start || end - start > 9999) return null
    for (let page = start; page <= end; page++) result.add(page)
    if (result.size > 10000) return null
  }
  return result.size ? [...result].sort((a, b) => a - b) : null
}
