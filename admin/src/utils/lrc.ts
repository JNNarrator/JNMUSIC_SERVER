export interface LyricLine {
  time: number // 秒
  text: string
}

/**
 * 解析 LRC 格式歌词文本，返回按时间排序的歌词行。
 * 支持 [mm:ss.xx] 和 [mm:ss] 格式，以及元数据头。
 */
export function parseLrc(raw: string): LyricLine[] {
  if (!raw) return []
  const lines: LyricLine[] = []
  for (const line of raw.split('\n')) {
    const m = line.match(/^\[(\d{1,3}):(\d{2})(?:[.:](\d{2,3}))?\]\s*(.*)/)
    if (m) {
      const min = parseInt(m[1], 10)
      const sec = parseInt(m[2], 10)
      const ms = m[3] ? parseInt(m[3].padEnd(3, '0'), 10) : 0
      lines.push({ time: min * 60 + sec + ms / 1000, text: m[4].trim() })
    }
  }
  return lines.sort((a, b) => a.time - b.time)
}

/**
 * 二分查找当前应高亮的歌词行 index。
 * 返回 -1 表示尚未到第一行歌词。
 */
export function findCurrentLine(lines: LyricLine[], currentTime: number): number {
  if (!lines.length || currentTime < lines[0].time) return -1
  let lo = 0
  let hi = lines.length - 1
  while (lo <= hi) {
    const mid = (lo + hi) >>> 1
    if (lines[mid].time <= currentTime) {
      lo = mid + 1
    } else {
      hi = mid - 1
    }
  }
  return Math.max(0, lo - 1)
}

// -- 歌词缓存（内存级，同一首歌只请求一次） --
const lyricsCache = new Map<string, string>()

/**
 * 带缓存的歌词获取。命中缓存直接返回，未命中则请求 API 并存入缓存。
 * 返回 { raw, error }。
 */
export async function fetchLyricsCached(trackId: string): Promise<{ raw: string; error: string }> {
  if (!trackId) return { raw: '', error: '' }

  // 命中缓存
  if (lyricsCache.has(trackId)) {
    return { raw: lyricsCache.get(trackId)!, error: '' }
  }

  try {
    const res = await fetch(`/music/api/v1/tracks/${trackId}/lyrics`)
    const payload = await res.json()
    if (payload.success) {
      const raw = payload.data || ''
      lyricsCache.set(trackId, raw)
      return { raw, error: '' }
    }
    // 即使无歌词也缓存空串，避免重复请求
    lyricsCache.set(trackId, '')
    return { raw: '', error: payload.error?.message || '暂无歌词' }
  } catch {
    return { raw: '', error: '获取歌词失败' }
  }
}
