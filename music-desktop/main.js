import { app, BrowserWindow, dialog, globalShortcut, ipcMain, Menu, nativeTheme, net, protocol } from 'electron';
import { createHash } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { parseBuffer, parseFile } from 'music-metadata';
import {
  chooseLrclibResult,
  emptyLyrics,
  fromLrclibRecord,
  hasLyrics,
  lyricsFromCommon,
} from './lyrics.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DEFAULT_SERVER = 'http://127.0.0.1:8080';
const USER_AGENT = 'MyMusicDesktop/1.0 (personal music player)';

protocol.registerSchemesAsPrivileged([
  {
    scheme: 'mymusic',
    privileges: {
      standard: true,
      secure: true,
      supportFetchAPI: true,
      stream: true,
      corsEnabled: true,
    },
  },
]);

let mainWindow = null;
const rendererFiles = new Map([
  ['/index.html', 'text/html; charset=utf-8'],
  ['/styles.css', 'text/css; charset=utf-8'],
  ['/app.js', 'text/javascript; charset=utf-8'],
  ['/turntable.js', 'text/javascript; charset=utf-8'],
]);

function normalizeServer(value) {
  let raw = String(value ?? '').trim();
  if (!raw) throw new Error('서버 주소를 입력해 주세요.');
  if (!/^https?:\/\//i.test(raw)) raw = `http://${raw}`;
  let url;
  try {
    url = new URL(raw);
  } catch {
    throw new Error('서버 주소 형식이 올바르지 않습니다.');
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    throw new Error('http 주소만 사용할 수 있습니다.');
  }
  return url.origin;
}

function settingsFile() {
  return path.join(app.getPath('userData'), 'settings.json');
}

function readSettings() {
  try {
    const raw = JSON.parse(fs.readFileSync(settingsFile(), 'utf8'));
    return {
      serverUrl: normalizeServer(raw.serverUrl || DEFAULT_SERVER),
      bounds: raw.bounds ?? null,
    };
  } catch {
    return { serverUrl: DEFAULT_SERVER, bounds: null };
  }
}

function writeSettings(settings) {
  fs.mkdirSync(path.dirname(settingsFile()), { recursive: true });
  const current = readSettings();
  const next = {
    serverUrl: settings.serverUrl ? normalizeServer(settings.serverUrl) : current.serverUrl,
    bounds: settings.bounds === undefined ? current.bounds : settings.bounds,
  };
  fs.writeFileSync(settingsFile(), JSON.stringify(next, null, 2));
  return next;
}

function libraryRoot() {
  return path.join(app.getPath('userData'), 'library');
}

function libraryFile() {
  return path.join(libraryRoot(), 'library.json');
}

function readLibrary() {
  try {
    const raw = JSON.parse(fs.readFileSync(libraryFile(), 'utf8'));
    return {
      nextId: Number(raw.nextId) || 1,
      songs: Array.isArray(raw.songs) ? raw.songs : [],
    };
  } catch {
    return { nextId: 1, songs: [] };
  }
}

function writeLibrary(library) {
  fs.mkdirSync(libraryRoot(), { recursive: true });
  const destination = libraryFile();
  const temporary = `${destination}.tmp`;
  fs.writeFileSync(temporary, JSON.stringify(library, null, 2));
  fs.renameSync(temporary, destination);
}

function isInside(parent, child) {
  const relative = path.relative(parent, child);
  return relative !== '' && !relative.startsWith('..') && !path.isAbsolute(relative);
}

function storedFile(folder, name) {
  if (!name || path.basename(name) !== name) return null;
  const full = path.resolve(folder, name);
  return isInside(path.resolve(folder), full) && fs.existsSync(full) ? full : null;
}

function publicSong(song) {
  return {
    id: song.id,
    title: song.title,
    artist: song.artist,
    album: song.album,
    hasCover: Boolean(song.cover),
  };
}

function assertMp3(filePath) {
  if (typeof filePath !== 'string' || !filePath.trim()) {
    throw new Error('파일 경로가 올바르지 않습니다.');
  }
  const resolved = path.resolve(filePath);
  if (!fs.existsSync(resolved) || !fs.statSync(resolved).isFile()) {
    throw new Error('파일을 찾을 수 없습니다.');
  }
  if (path.extname(resolved).toLowerCase() !== '.mp3') {
    throw new Error('MP3 파일만 사용할 수 있습니다.');
  }
  return resolved;
}

async function importLocalFiles(filePaths) {
  const library = readLibrary();
  const errors = [];
  fs.mkdirSync(path.join(libraryRoot(), 'files'), { recursive: true });
  fs.mkdirSync(path.join(libraryRoot(), 'covers'), { recursive: true });
  for (const candidate of filePaths.slice(0, 100)) {
    try {
      const source = assertMp3(candidate);
      const id = `l${library.nextId}`;
      library.nextId += 1;
      const fileName = `${id}.mp3`;
      fs.copyFileSync(source, path.join(libraryRoot(), 'files', fileName));
      const song = {
        id,
        title: path.basename(source, path.extname(source)),
        artist: 'Unknown Artist',
        album: 'Unknown Album',
        file: fileName,
        cover: null,
        lyrics: null,
      };
      try {
        const metadata = await parseFile(source, { duration: false });
        song.title = metadata.common.title?.trim() || song.title;
        song.artist = metadata.common.artist?.trim() || song.artist;
        song.album = metadata.common.album?.trim() || song.album;
        const picture = metadata.common.picture?.[0];
        if (picture?.data?.length) {
          const mime = picture.format || 'image/jpeg';
          const extension = mime.includes('png') ? 'png' : 'jpg';
          const coverName = `${id}.${extension}`;
          fs.writeFileSync(path.join(libraryRoot(), 'covers', coverName), Buffer.from(picture.data));
          song.cover = coverName;
        }
        song.lyrics = lyricsFromCommon(metadata.common, metadata.format);
      } catch {
        // A file without readable tags still belongs in the local library.
      }
      library.songs.push(song);
    } catch (error) {
      errors.push(error.message || '가져오기에 실패했습니다.');
    }
  }
  writeLibrary(library);
  return { songs: library.songs.map(publicSong), errors };
}

function removeLocal(id) {
  const library = readLibrary();
  const song = library.songs.find((item) => item.id === id);
  if (!song) return library.songs.map(publicSong);
  for (const [folder, name] of [['files', song.file], ['covers', song.cover]]) {
    const full = name ? storedFile(path.join(libraryRoot(), folder), name) : null;
    if (full) fs.rmSync(full, { force: true });
  }
  library.songs = library.songs.filter((item) => item.id !== id);
  writeLibrary(library);
  return library.songs.map(publicSong);
}

async function serverRequest(pathname, options = {}) {
  const serverUrl = readSettings().serverUrl;
  const response = await fetch(`${serverUrl}${pathname}`, {
    ...options,
    signal: options.signal ?? AbortSignal.timeout(options.timeoutMs ?? 15000),
  });
  return response;
}

async function readError(response) {
  try {
    const text = (await response.text()).trim();
    return text || `서버 응답 오류: ${response.status}`;
  } catch {
    return `서버 응답 오류: ${response.status}`;
  }
}

async function uploadToServer(filePaths) {
  const added = [];
  const errors = [];
  for (const candidate of filePaths.slice(0, 100)) {
    try {
      const source = assertMp3(candidate);
      const bytes = fs.readFileSync(source);
      const form = new FormData();
      form.append('file', new Blob([bytes], { type: 'audio/mpeg' }), path.basename(source));
      const response = await serverRequest('/api/songs/upload', {
        method: 'POST',
        body: form,
        timeoutMs: 120000,
      });
      if (!response.ok) throw new Error(await readError(response));
      added.push(await response.json());
    } catch (error) {
      errors.push(error.message || '업로드에 실패했습니다.');
    }
  }
  return { added, errors };
}

function lyricsCacheFile(title, artist) {
  const name = createHash('sha1').update(`${title}\0${artist}`).digest('hex');
  return path.join(app.getPath('userData'), 'lyrics-cache', `${name}.json`);
}

async function embeddedLyricsForServer(id, title) {
  const url = `${readSettings().serverUrl}/api/songs/${id}/stream`;
  const head = await fetch(url, {
    headers: { Range: 'bytes=0-9' },
    signal: AbortSignal.timeout(8000),
  });
  if (!head.ok && head.status !== 206) return null;
  const headerLength = Number(head.headers.get('content-length') || 0);
  if (head.status === 200 && headerLength > 6 * 1024 * 1024) {
    await head.body?.cancel();
    return null;
  }
  const header = Buffer.from(await head.arrayBuffer());
  if (header.length < 10 || header.toString('latin1', 0, 3) !== 'ID3') return null;
  const tagSize = ((header[6] & 127) << 21)
    | ((header[7] & 127) << 14)
    | ((header[8] & 127) << 7)
    | (header[9] & 127);
  const total = Math.min(tagSize + 10, 4 * 1024 * 1024);
  const body = await fetch(url, {
    headers: { Range: `bytes=0-${total - 1}` },
    signal: AbortSignal.timeout(15000),
  });
  if (!body.ok && body.status !== 206) return null;
  const receivedLength = Number(body.headers.get('content-length') || 0);
  if (body.status === 200 && receivedLength > 6 * 1024 * 1024) {
    await body.body?.cancel();
    return null;
  }
  const buffer = Buffer.from(await body.arrayBuffer());
  const metadata = await parseBuffer(buffer, { mimeType: 'audio/mpeg', path: `${title}.mp3` });
  return lyricsFromCommon(metadata.common, metadata.format);
}

async function fetchLrclib(title, artist, durationMs) {
  const headers = { 'User-Agent': USER_AGENT, Accept: 'application/json' };
  const exact = new URLSearchParams({ track_name: title });
  const artistKnown = artist && artist.toLowerCase() !== 'unknown artist';
  if (artistKnown) exact.set('artist_name', artist);
  if (durationMs >= 1000 && durationMs <= 3_600_000) exact.set('duration', String(Math.round(durationMs / 1000)));
  if (artistKnown) {
    const response = await fetch(`https://lrclib.net/api/get?${exact}`, {
      headers,
      signal: AbortSignal.timeout(8000),
    });
    if (response.status === 429) throw new Error('온라인 가사 서비스 요청 제한 중');
    if (response.ok) {
      const lyrics = fromLrclibRecord(await response.json());
      if (hasLyrics(lyrics)) return lyrics;
    }
  }
  const search = new URLSearchParams({ track_name: title });
  if (artistKnown) search.set('artist_name', artist);
  const response = await fetch(`https://lrclib.net/api/search?${search}`, {
    headers,
    signal: AbortSignal.timeout(8000),
  });
  if (response.status === 429) throw new Error('온라인 가사 서비스 요청 제한 중');
  if (!response.ok) return null;
  const chosen = chooseLrclibResult(await response.json(), { title, artist, durationMs });
  return chosen ? fromLrclibRecord(chosen) : null;
}

async function loadLyrics(query) {
  const title = String(query?.title ?? '').trim();
  const artist = String(query?.artist ?? '').trim();
  const durationMs = Number(query?.durationMs) || 0;
  if (!title) return emptyLyrics();

  let embedded = null;
  if (query?.source === 'local' && query.id) {
    const song = readLibrary().songs.find((item) => item.id === query.id);
    if (song?.lyrics?.synced) return song.lyrics;
    embedded = hasLyrics(song?.lyrics) ? song.lyrics : null;
  } else if (query?.source === 'server' && /^\d+$/.test(String(query.id))) {
    try {
      embedded = await embeddedLyricsForServer(query.id, title);
    } catch {
      embedded = null;
    }
  }
  if (embedded?.synced || (query?.embeddedOnly && hasLyrics(embedded))) return embedded;
  if (query?.embeddedOnly) return embedded ?? emptyLyrics();

  const cache = lyricsCacheFile(title, artist);
  if (fs.existsSync(cache)) {
    try {
      const cached = JSON.parse(fs.readFileSync(cache, 'utf8'));
      const cachedDuration = Number(cached.duration) || 0;
      const requested = durationMs > 0 ? Math.round(durationMs / 1000) : 0;
      if (!(requested && cachedDuration && Math.abs(requested - cachedDuration) > 10)) {
        const saved = fromLrclibRecord(cached);
        if (saved.synced || (!embedded?.plain && hasLyrics(saved))) return saved;
      }
    } catch {
      // A broken cache is replaced by the next successful lookup.
    }
  }

  try {
    const online = await fetchLrclib(title, artist, durationMs);
    if (online && hasLyrics(online)) {
      fs.mkdirSync(path.dirname(cache), { recursive: true });
      fs.writeFileSync(cache, JSON.stringify({
        duration: durationMs > 0 ? Math.round(durationMs / 1000) : 0,
        syncedLyrics: online.synced ? online.lines.map((line) => {
          const total = line.timeMs;
          const minutes = Math.floor(total / 60000);
          const seconds = Math.floor((total % 60000) / 1000);
          const millis = String(total % 1000).padStart(3, '0');
          return `[${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}.${millis}]${line.text}`;
        }).join('\n') : '',
        plainLyrics: online.plain,
        trackName: title,
        artistName: artist,
      }));
      if (online.synced || !hasLyrics(embedded)) return online;
    }
  } catch (error) {
    if (!hasLyrics(embedded)) throw error;
  }
  return embedded ?? emptyLyrics();
}

function rangeHeaders(request) {
  const headers = {};
  const range = request.headers.get('range');
  if (range) headers.Range = range;
  return headers;
}

function fileResponse(filePath, request) {
  return net.fetch(pathToFileURL(filePath).toString(), { headers: rangeHeaders(request) });
}

async function handlePlayerRequest(request) {
  const url = new URL(request.url);
  if (url.hostname !== 'player') return new Response('not found', { status: 404 });

  const rendererType = rendererFiles.get(url.pathname);
  if (rendererType) {
    const filePath = path.join(__dirname, 'renderer', url.pathname.slice(1));
    return new Response(new Uint8Array(fs.readFileSync(filePath)), {
      headers: { 'Content-Type': rendererType, 'Cache-Control': 'no-cache' },
    });
  }

  const serverStream = url.pathname.match(/^\/server\/stream\/(\d+)$/);
  const serverCover = url.pathname.match(/^\/server\/cover\/(\d+)$/);
  if (serverStream || serverCover) {
    const id = (serverStream || serverCover)[1];
    const kind = serverStream ? 'stream' : 'cover';
    try {
      return await net.fetch(`${readSettings().serverUrl}/api/songs/${id}/${kind}`, {
        headers: rangeHeaders(request),
      });
    } catch {
      return new Response('server unavailable', { status: 502 });
    }
  }

  const localAudio = url.pathname.match(/^\/local\/audio\/([A-Za-z0-9_-]+)$/);
  if (localAudio) {
    const song = readLibrary().songs.find((item) => item.id === localAudio[1]);
    const filePath = song ? storedFile(path.join(libraryRoot(), 'files'), song.file) : null;
    if (!filePath) return new Response('not found', { status: 404 });
    return fileResponse(filePath, request);
  }

  const localCover = url.pathname.match(/^\/local\/cover\/([A-Za-z0-9_-]+)$/);
  if (localCover) {
    const song = readLibrary().songs.find((item) => item.id === localCover[1]);
    const filePath = song?.cover ? storedFile(path.join(libraryRoot(), 'covers'), song.cover) : null;
    if (!filePath) return new Response('not found', { status: 404 });
    return fileResponse(filePath, request);
  }

  return new Response('not found', { status: 404 });
}

function createWindow() {
  const settings = readSettings();
  const bounds = settings.bounds ?? { width: 1180, height: 780 };
  mainWindow = new BrowserWindow({
    ...bounds,
    minWidth: 980,
    minHeight: 680,
    show: false,
    backgroundColor: '#111113',
    autoHideMenuBar: true,
    titleBarStyle: 'hidden',
    titleBarOverlay: { color: '#1B1B1F', symbolColor: '#B9B4AC', height: 36 },
    title: 'My Music',
    icon: path.join(__dirname, 'assets', 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });
  Menu.setApplicationMenu(null);
  mainWindow.once('ready-to-show', () => mainWindow.show());
  mainWindow.on('close', () => {
    if (mainWindow.isDestroyed()) return;
    writeSettings({ bounds: mainWindow.getBounds() });
  });
  mainWindow.webContents.on('before-input-event', (_event, input) => {
    if (!app.isPackaged && input.control && input.shift && input.key.toLowerCase() === 'i') {
      mainWindow.webContents.toggleDevTools();
    }
  });
  mainWindow.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  mainWindow.loadURL('mymusic://player/index.html');
}

function registerMediaKeys() {
  const bindings = [
    ['MediaPlayPause', 'toggle'],
    ['MediaNextTrack', 'next'],
    ['MediaPreviousTrack', 'previous'],
  ];
  for (const [accelerator, action] of bindings) {
    try {
      globalShortcut.register(accelerator, () => {
        if (mainWindow && !mainWindow.isDestroyed()) mainWindow.webContents.send('media-key', action);
      });
    } catch {
      // Another app may already own the media key.
    }
  }
}

app.setAppUserModelId('com.mymusic.desktop');
if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (!mainWindow) return;
    if (mainWindow.isMinimized()) mainWindow.restore();
    mainWindow.focus();
  });

  app.whenReady().then(() => {
    nativeTheme.themeSource = 'dark';
    protocol.handle('mymusic', handlePlayerRequest);
    registerIpc();
    createWindow();
    registerMediaKeys();
  });

  app.on('window-all-closed', () => app.quit());
  app.on('will-quit', () => globalShortcut.unregisterAll());
}

function registerIpc() {
  ipcMain.handle('settings:get', () => readSettings());
  ipcMain.handle('settings:set', (_event, settings) => writeSettings(settings ?? {}));
  ipcMain.handle('server:songs', async () => {
    const response = await serverRequest('/api/songs', { timeoutMs: 5000 });
    if (!response.ok) throw new Error(await readError(response));
    return response.json();
  });
  ipcMain.handle('server:refresh', async () => {
    const response = await serverRequest('/api/songs/refresh', { method: 'POST', timeoutMs: 600000 });
    if (!response.ok) throw new Error(await readError(response));
    return response.json();
  });
  ipcMain.handle('server:upload', (_event, paths) => uploadToServer(Array.isArray(paths) ? paths : []));
  ipcMain.handle('server:delete', async (_event, id) => {
    if (!/^\d+$/.test(String(id))) throw new Error('삭제할 곡을 찾지 못했습니다.');
    const response = await serverRequest(`/api/songs/${id}`, { method: 'DELETE' });
    if (response.status !== 204 && response.status !== 200) throw new Error(await readError(response));
    return true;
  });
  ipcMain.handle('dialog:pickMp3', async () => {
    const result = await dialog.showOpenDialog(mainWindow, {
      title: 'MP3 선택',
      filters: [{ name: 'MP3', extensions: ['mp3'] }],
      properties: ['openFile', 'multiSelections'],
    });
    return result.canceled ? [] : result.filePaths;
  });
  ipcMain.handle('library:list', () => readLibrary().songs.map(publicSong));
  ipcMain.handle('library:import', (_event, paths) => importLocalFiles(Array.isArray(paths) ? paths : []));
  ipcMain.handle('library:remove', (_event, id) => removeLocal(String(id ?? '')));
  ipcMain.handle('lyrics:load', (_event, query) => loadLyrics(query ?? {}));
}
