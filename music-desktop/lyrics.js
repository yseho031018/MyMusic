/** LRC parsing and LRCLIB selection shared by the desktop player. */

const TIMESTAMP = /\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]/y;
const OFFSET = /^\[offset:\s*([+-]?\d+)\s*\]$/i;
const LRC_METADATA = /^\[(ar|al|ti|by|offset|length):.*\]$/i;

export function parseLrcText(text, source = 'lrc') {
  const lines = [];
  const plain = [];
  let offsetMs = 0;
  const normalized = String(text ?? '').replace(/\r\n/g, '\n').replace(/\r/g, '\n');
  for (const raw of normalized.split('\n')) {
    const trimmed = raw.trim();
    const offset = OFFSET.exec(trimmed);
    if (offset) {
      const parsed = Number(offset[1]);
      offsetMs = Number.isFinite(parsed) ? Math.max(-600_000, Math.min(600_000, parsed)) : 0;
      continue;
    }
    TIMESTAMP.lastIndex = 0;
    const times = [];
    let end = 0;
    let match = TIMESTAMP.exec(raw);
    while (match && match.index === end) {
      const minutes = Number(match[1]);
      const seconds = Number(match[2]);
      const fraction = match[3];
      const millis = fraction == null ? 0 : Number(`${fraction}000`.slice(0, 3));
      times.push((minutes * 60 + seconds) * 1000 + millis);
      end = TIMESTAMP.lastIndex;
      match = TIMESTAMP.exec(raw);
    }
    const words = raw.slice(end).trim();
    if (times.length) {
      for (const time of times) lines.push({ timeMs: time, text: words });
      if (words) plain.push(words);
    } else if (!LRC_METADATA.test(trimmed)) {
      plain.push(raw);
    }
  }
  const adjusted = lines
    .map((line) => ({ timeMs: Math.max(0, line.timeMs - offsetMs), text: line.text }))
    .sort((a, b) => a.timeMs - b.timeMs);
  return {
    synced: adjusted.length > 0,
    lines: adjusted,
    plain: plain.join('\n').trim(),
    source,
  };
}

export function lineAt(lines, positionMs) {
  let index = -1;
  for (let i = 0; i < lines.length; i += 1) {
    if (lines[i].timeMs > positionMs) break;
    index = i;
  }
  return index;
}

export function lyricsFromCommon(common, format) {
  const tags = Array.isArray(common?.lyrics)
    ? common.lyrics
    : common?.lyrics
      ? [common.lyrics]
      : [];
  for (const tag of tags) {
    if (!tag?.syncText?.length) continue;
    const lines = tag.syncText
      .filter((line) => line?.text && String(line.text).trim())
      .map((line) => ({ timeMs: stampToMs(tag, line.timestamp, format), text: String(line.text).trim() }))
      .sort((a, b) => a.timeMs - b.timeMs);
    if (lines.length) {
      return {
        synced: true,
        lines,
        plain: lines.map((line) => line.text).join('\n'),
        source: 'embedded',
      };
    }
  }
  for (const tag of tags) {
    const text = tag?.text?.trim();
    if (!text) continue;
    if (/\[\d{1,3}:\d{2}/.test(text)) {
      const parsed = parseLrcText(text, 'embedded');
      if (parsed.synced || parsed.plain) return parsed;
    }
    return {
      synced: false,
      lines: text.split(/\n/).map((line) => ({ timeMs: null, text: line })),
      plain: text,
      source: 'embedded',
    };
  }
  return null;
}

export function fromLrclibRecord(record) {
  if (!record || record.instrumental) return emptyLyrics('online');
  if (record.syncedLyrics) {
    const parsed = parseLrcText(record.syncedLyrics, 'online');
    if (parsed.synced) return parsed;
  }
  if (record.plainLyrics && String(record.plainLyrics).trim()) {
    const plain = String(record.plainLyrics).trim();
    return {
      synced: false,
      lines: plain.split(/\n/).map((text) => ({ timeMs: null, text })),
      plain,
      source: 'online',
    };
  }
  return emptyLyrics('online');
}

export function chooseLrclibResult(records, { title, artist, durationMs }) {
  const list = Array.isArray(records) ? records : records?.results;
  if (!Array.isArray(list)) return null;
  const wantedTitle = String(title ?? '').trim();
  const wantedArtist = String(artist ?? '').trim();
  const artistKnown = wantedArtist && wantedArtist.toLowerCase() !== 'unknown artist';
  let best = null;
  let bestDifference = Number.POSITIVE_INFINITY;
  let plausible = 0;
  for (const candidate of list) {
    if (!candidate || wantedTitle.toLowerCase() !== String(candidate.trackName ?? '').trim().toLowerCase()) continue;
    if (artistKnown && wantedArtist.toLowerCase() !== String(candidate.artistName ?? '').trim().toLowerCase()) continue;
    const difference = durationMs > 0
      ? Math.abs(Math.round(durationMs / 1000) - Number(candidate.duration || 0))
      : 0;
    if (durationMs > 0 && difference > 10) continue;
    plausible += 1;
    if (difference < bestDifference) {
      bestDifference = difference;
      best = candidate;
    }
  }
  if (!artistKnown && durationMs <= 0 && plausible > 1) return null;
  return best;
}

export function emptyLyrics(source = 'none') {
  return { synced: false, lines: [], plain: '', source };
}

function stampToMs(tag, value, format) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric < 0) return 0;
  // ID3 SYLT can count MPEG frames instead of milliseconds.
  if (tag?.timeStampFormat === 1) {
    const sampleRate = Number(format?.sampleRate) > 0 ? Number(format.sampleRate) : 44100;
    return Math.round(numeric * (1152 / sampleRate) * 1000);
  }
  return Math.round(numeric);
}

export function hasLyrics(lyrics) {
  return Boolean(lyrics && (lyrics.synced || String(lyrics.plain ?? '').trim()));
}
