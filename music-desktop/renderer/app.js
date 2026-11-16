const host = window.desktop;
const audio = document.querySelector('#audio');
const turntable = new window.TurntableView(document.querySelector('#deck'), document.querySelector('#bars'));

const ui = {
  library: document.querySelector('#library'),
  list: document.querySelector('#list'),
  empty: document.querySelector('#empty'),
  emptyTitle: document.querySelector('#emptyTitle'),
  emptyDescription: document.querySelector('#emptyDescription'),
  count: document.querySelector('#count'),
  status: document.querySelector('#status'),
  statusText: document.querySelector('#statusText'),
  search: document.querySelector('#search'),
  searchHint: document.querySelector('#searchHint'),
  clearSearch: document.querySelector('#clearSearch'),
  tabs: document.querySelector('.tabs'),
  track: document.querySelector('.track'),
  captionLines: document.querySelector('.caption-lines'),
  tabServer: document.querySelector('#tabServer'),
  tabLocal: document.querySelector('#tabLocal'),
  refresh: document.querySelector('#btnRefresh'),
  refreshLabel: document.querySelector('#refreshLabel'),
  upload: document.querySelector('#btnUpload'),
  uploadLabel: document.querySelector('#uploadLabel'),
  import: document.querySelector('#btnImport'),
  importLabel: document.querySelector('#importLabel'),
  retry: document.querySelector('#btnRetry'),
  retryLabel: document.querySelector('#retryLabel'),
  emptySettings: document.querySelector('#btnEmptySettings'),
  emptyAdd: document.querySelector('#btnEmptyAdd'),
  emptyAddLabel: document.querySelector('#emptyAddLabel'),
  dropTarget: document.querySelector('#dropTarget'),
  modeText: document.querySelector('#modeText'),
  volumeText: document.querySelector('#volumeText'),
  lyricsButton: document.querySelector('#btnLyrics'),
  title: document.querySelector('#trackTitle'),
  artist: document.querySelector('#trackArtist'),
  seek: document.querySelector('#seek'),
  timeNow: document.querySelector('#timeNow'),
  timeTotal: document.querySelector('#timeTotal'),
  play: document.querySelector('#play'),
  caption: document.querySelector('#caption'),
  linePrev: document.querySelector('#linePrev'),
  lineCurrent: document.querySelector('#lineCurrent'),
  lineNext: document.querySelector('#lineNext'),
  cc: document.querySelector('#cc'),
  shuffle: document.querySelector('#shuffle'),
  repeat: document.querySelector('#repeat'),
  volume: document.querySelector('#volume'),
  mute: document.querySelector('#mute'),
  toast: document.querySelector('#toast'),
  toastIcon: document.querySelector('#toastIcon'),
  toastText: document.querySelector('#toastText'),
  help: document.querySelector('#helpDialog'),
  settings: document.querySelector('#settingsDialog'),
  serverInput: document.querySelector('#serverInput'),
  serverError: document.querySelector('#serverError'),
  settingsSave: document.querySelector('#settingsSave'),
  settingsSaveLabel: document.querySelector('#settingsSaveLabel'),
  lyrics: document.querySelector('#lyricsDialog'),
  lyricsTitle: document.querySelector('#lyricsTitle'),
  lyricsArtist: document.querySelector('#lyricsArtist'),
  lyricsOffset: document.querySelector('#lyricsOffset'),
  lyricsBody: document.querySelector('#lyricsBody'),
  offsetControls: document.querySelector('#lyricsOffsetControls'),
  offsetReset: document.querySelector('#offsetReset'),
  confirm: document.querySelector('#confirmDialog'),
  confirmTitle: document.querySelector('#confirmTitle'),
  confirmText: document.querySelector('#confirmText'),
  confirmDetail: document.querySelector('#confirmDetail'),
  confirmOk: document.querySelector('#confirmOk'),
};

const state = {
  tab: localStorage.getItem('library-tab') === 'local' ? 'local' : 'server',
  serverUrl: 'http://127.0.0.1:8080',
  online: false,
  loading: true,
  checking: false,
  busy: false,
  adding: false,
  buffering: false,
  serverSongs: [],
  localSongs: [],
  query: '',
  queue: [],
  index: -1,
  song: null,
  lyrics: null,
  lyricsToken: 0,
  askedDuration: false,
  scrubbing: false,
  shuffle: localStorage.getItem('shuffle') === '1',
  repeat: localStorage.getItem('repeat') || 'off',
  cc: localStorage.getItem('cc') !== '0',
  volume: Number(localStorage.getItem('volume') ?? 0.8),
  muted: localStorage.getItem('muted') === '1',
  graphReady: false,
  highlightedLyric: -2,
  listKeys: '',
  listCurrent: '',
  skipEnter: false,
  captionIndex: -2,
};

const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)');

function replay(element, className) {
  if (!element) return;
  element.classList.remove(className);
  void element.offsetWidth;
  element.classList.add(className);
}

audio.volume = state.muted ? 0 : clampVolume(state.volume);
ui.volume.value = String(Math.round(clampVolume(state.volume) * 100));
paintSlider(ui.volume);

document.querySelector('#btnSettings').addEventListener('click', openSettings);
document.querySelector('#settingsCancel').addEventListener('click', () => ui.settings.close());
ui.settingsSave.addEventListener('click', saveSettings);
ui.serverInput.addEventListener('keydown', (event) => {
  if (event.key === 'Enter') saveSettings();
});
ui.serverInput.addEventListener('input', () => showServerError(''));
document.querySelector('#btnHelp').addEventListener('click', openHelp);
document.querySelector('#helpClose').addEventListener('click', () => ui.help.close());
ui.tabServer.addEventListener('click', () => switchTab('server'));
ui.tabLocal.addEventListener('click', () => switchTab('local'));
for (const tab of [ui.tabServer, ui.tabLocal]) {
  tab.addEventListener('keydown', (event) => {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
    event.preventDefault();
    event.stopPropagation();
    const next = state.tab === 'server' ? 'local' : 'server';
    switchTab(next);
    (next === 'server' ? ui.tabServer : ui.tabLocal).focus();
  });
}
ui.search.addEventListener('input', () => {
  state.query = ui.search.value;
  ui.clearSearch.hidden = !state.query;
  ui.searchHint.hidden = Boolean(state.query);
  renderLibrary();
});
ui.clearSearch.addEventListener('click', () => {
  state.query = '';
  ui.search.value = '';
  ui.clearSearch.hidden = true;
  ui.searchHint.hidden = false;
  renderLibrary();
  ui.search.focus();
});
ui.refresh.addEventListener('click', refreshMetadata);
ui.upload.addEventListener('click', () => pickAndAdd(false));
ui.import.addEventListener('click', () => pickAndAdd(true));
ui.retry.addEventListener('click', async () => {
  if (await reloadServer()) toast('서버에 다시 연결했습니다.', 'success');
});
ui.emptySettings.addEventListener('click', openSettings);
ui.emptyAdd.addEventListener('click', () => pickAndAdd(state.tab === 'local'));
ui.play.addEventListener('click', togglePlayback);
document.querySelector('#prev').addEventListener('click', previous);
document.querySelector('#next').addEventListener('click', () => advance(1));
for (const button of [ui.shuffle, ui.repeat, ui.mute, ui.cc]) {
  button.addEventListener('click', () => replay(button, 'pop'));
}
ui.shuffle.addEventListener('click', () => {
  state.shuffle = !state.shuffle;
  localStorage.setItem('shuffle', state.shuffle ? '1' : '0');
  renderPlayer();
});
ui.repeat.addEventListener('click', () => {
  state.repeat = state.repeat === 'off' ? 'all' : state.repeat === 'all' ? 'one' : 'off';
  localStorage.setItem('repeat', state.repeat);
  renderPlayer();
});
ui.cc.addEventListener('click', () => {
  state.cc = !state.cc;
  localStorage.setItem('cc', state.cc ? '1' : '0');
  renderPlayer();
});
ui.caption.addEventListener('click', openLyrics);
document.querySelector('#btnLyrics').addEventListener('click', openLyrics);
ui.seek.addEventListener('pointerdown', () => { state.scrubbing = true; });
window.addEventListener('pointerup', () => { state.scrubbing = false; });
ui.seek.addEventListener('input', () => {
  if (!Number.isFinite(audio.duration)) return;
  audio.currentTime = (Number(ui.seek.value) / 1000) * audio.duration;
  paintSlider(ui.seek);
  renderTimes();
});
ui.volume.addEventListener('input', () => {
  state.volume = Number(ui.volume.value) / 100;
  state.muted = false;
  localStorage.setItem('volume', String(state.volume));
  localStorage.setItem('muted', '0');
  audio.volume = state.volume;
  renderPlayer();
});
ui.mute.addEventListener('click', () => {
  state.muted = !state.muted;
  localStorage.setItem('muted', state.muted ? '1' : '0');
  audio.volume = state.muted ? 0 : state.volume;
  renderPlayer();
});
document.querySelector('#lyricsClose').addEventListener('click', () => ui.lyrics.close());
document.querySelector('#offsetFaster').addEventListener('click', () => changeOffset(500));
document.querySelector('#offsetSlower').addEventListener('click', () => changeOffset(-500));
document.querySelector('#offsetReset').addEventListener('click', () => changeOffset(0, true));
document.querySelector('#confirmCancel').addEventListener('click', () => closeConfirm(false));
document.querySelector('#confirmOk').addEventListener('click', () => closeConfirm(true));

audio.addEventListener('play', renderPlayer);
audio.addEventListener('pause', () => setBuffering(false));
for (const type of ['loadstart', 'waiting']) {
  audio.addEventListener(type, () => setBuffering(Boolean(state.song)));
}
for (const type of ['playing', 'canplay', 'emptied']) {
  audio.addEventListener(type, () => setBuffering(false));
}
audio.addEventListener('timeupdate', () => {
  renderTimes();
  renderCaption();
});
audio.addEventListener('durationchange', () => {
  renderTimes();
  if (state.song && !state.lyrics?.synced && !state.askedDuration && Number.isFinite(audio.duration)) {
    state.askedDuration = true;
    loadLyrics(true);
  }
});
audio.addEventListener('ended', onEnded);
audio.addEventListener('error', () => {
  setBuffering(false);
  if (!state.song) return;
  toast(state.song.source === 'server' && !state.online
    ? '서버 연결이 끊겨 재생할 수 없어요. 서버 상태를 확인한 뒤 다시 눌러 주세요.'
    : '이 곡을 재생할 수 없어요. 파일이 손상됐을 수 있으니 다른 곡을 선택해 주세요.', 'error');
});

window.addEventListener('keydown', onKeyDown);
host.onMediaKey((action) => {
  if (action === 'toggle') togglePlayback();
  if (action === 'next') advance(1);
  if (action === 'previous') previous();
});
window.addEventListener('dragover', (event) => {
  event.preventDefault();
  ui.library.classList.add('dragover');
});
window.addEventListener('dragleave', (event) => {
  if (!event.relatedTarget) ui.library.classList.remove('dragover');
});
window.addEventListener('drop', (event) => {
  event.preventDefault();
  ui.library.classList.remove('dragover');
  const paths = [...event.dataTransfer.files]
    .map((file) => host.pathForFile(file))
    .filter((filePath) => filePath && filePath.toLowerCase().endsWith('.mp3'));
  if (!paths.length) {
    toast('MP3 파일만 추가할 수 있어요. 확장자가 .mp3인지 확인해 주세요.', 'error');
    return;
  }
  if (state.tab === 'server' && !state.online) {
    toast('서버에 연결되지 않아 업로드할 수 없어요. "이 PC" 탭에 놓으면 이 컴퓨터에 저장됩니다.', 'error');
    return;
  }
  addFiles(paths, state.tab === 'local');
});

start();

async function start() {
  try {
    const settings = await host.getSettings();
    state.serverUrl = settings.serverUrl;
  } catch {
    // The default local server is still usable when settings cannot be read.
  }
  await Promise.all([reloadServer(), reloadLocal()]);
  state.loading = false;
  renderLibrary();
  renderPlayer();
}

async function reloadServer() {
  state.checking = true;
  renderLibrary();
  try {
    state.serverSongs = (await host.serverSongs()).map(mapServer);
    state.online = true;
  } catch {
    state.serverSongs = [];
    state.online = false;
  } finally {
    state.checking = false;
  }
  renderLibrary();
  return state.online;
}

async function reloadLocal() {
  state.localSongs = (await host.localSongs()).map(mapLocal);
  renderLibrary();
}

function mapServer(song) {
  return {
    id: String(song.id),
    title: song.title || '제목 없음',
    artist: song.artist || 'Unknown Artist',
    album: song.album || 'Unknown Album',
    source: 'server',
    streamUrl: `mymusic://player/server/stream/${song.id}`,
    coverUrl: `mymusic://player/server/cover/${song.id}`,
  };
}

function mapLocal(song) {
  return {
    id: song.id,
    title: song.title || '제목 없음',
    artist: song.artist || 'Unknown Artist',
    album: song.album || 'Unknown Album',
    source: 'local',
    streamUrl: `mymusic://player/local/audio/${song.id}`,
    coverUrl: song.hasCover ? `mymusic://player/local/cover/${song.id}` : null,
  };
}

function currentSongs() {
  return state.tab === 'local' ? state.localSongs : state.serverSongs;
}

function filteredSongs() {
  const query = state.query.trim().toLowerCase();
  const songs = currentSongs();
  if (!query) return songs;
  return songs.filter((song) => [song.title, song.artist, song.album].some((value) =>
    String(value).toLowerCase().includes(query)));
}

function switchTab(tab) {
  state.tab = tab;
  localStorage.setItem('library-tab', tab);
  if (tab === 'server') reloadServer();
  renderLibrary();
}

function renderLibrary() {
  const songs = filteredSongs();
  const total = currentSongs().length;
  ui.tabs.classList.toggle('local', state.tab === 'local');
  ui.tabServer.classList.toggle('active', state.tab === 'server');
  ui.tabLocal.classList.toggle('active', state.tab === 'local');
  ui.tabServer.setAttribute('aria-selected', String(state.tab === 'server'));
  ui.tabLocal.setAttribute('aria-selected', String(state.tab === 'local'));
  const serverTab = state.tab === 'server';
  ui.refresh.hidden = !serverTab;
  ui.upload.hidden = !serverTab;
  ui.import.hidden = serverTab;

  ui.refresh.disabled = state.busy || !state.online;
  ui.refresh.classList.toggle('busy', state.busy);
  ui.refreshLabel.textContent = state.busy ? '정보 찾는 중…' : '앨범 정보 채우기';
  ui.refresh.title = state.online
    ? '빠진 아티스트·앨범 이름과 앨범 커버를 온라인에서 찾아 채웁니다'
    : '서버에 연결되면 사용할 수 있어요';
  ui.upload.disabled = state.adding || !state.online;
  ui.upload.classList.toggle('busy', state.adding && serverTab);
  ui.uploadLabel.textContent = state.adding && serverTab ? '업로드 중…' : 'MP3 업로드';
  ui.upload.title = state.online
    ? 'MP3를 서버에 올려 휴대폰에서도 들을 수 있게 합니다'
    : '서버에 연결되면 업로드할 수 있어요';
  ui.import.disabled = state.adding;
  ui.import.classList.toggle('busy', state.adding && !serverTab);
  ui.importLabel.textContent = state.adding && !serverTab ? '가져오는 중…' : 'MP3 가져오기';
  ui.dropTarget.textContent = serverTab ? '서버에 업로드되어 휴대폰에서도 들을 수 있어요' : '이 PC 보관함에 복사됩니다';

  ui.count.textContent = state.query.trim()
    ? `검색 결과 ${songs.length}곡 / 전체 ${total}곡`
    : `전체 ${total}곡`;
  const status = !serverTab ? 'local' : state.checking ? 'checking' : state.online ? 'online' : 'offline';
  ui.status.className = status === 'local' ? 'online' : status;
  ui.statusText.textContent = {
    local: '이 PC에 저장된 음악',
    checking: '서버 확인 중…',
    online: '서버 연결됨',
    offline: '서버에 연결할 수 없음',
  }[status];

  const showEmpty = songs.length === 0;
  ui.list.hidden = showEmpty;
  ui.empty.hidden = !showEmpty;
  if (showEmpty) {
    const copy = emptyCopy();
    ui.empty.className = `empty${copy.kind ? ` ${copy.kind}` : ''}`;
    ui.emptyTitle.textContent = copy.title;
    ui.emptyDescription.textContent = copy.description;
    ui.retry.hidden = copy.kind !== 'offline';
    ui.retry.disabled = state.checking;
    ui.retryLabel.textContent = state.checking ? '연결 중…' : '다시 연결';
    ui.emptySettings.hidden = copy.kind !== 'offline';
    ui.emptyAdd.hidden = !copy.addLabel;
    ui.emptyAdd.disabled = state.adding;
    ui.emptyAddLabel.textContent = copy.addLabel ?? '';
  }

  const keys = `${state.tab}|${songs.map(songKey).join(',')}`;
  const currentKey = state.song ? songKey(state.song) : '';
  if (keys === state.listKeys && currentKey === state.listCurrent) return;
  const entering = keys !== state.listKeys && !state.skipEnter;
  const picked = currentKey !== state.listCurrent;
  state.listKeys = keys;
  state.listCurrent = currentKey;
  state.skipEnter = false;
  const rows = songs.map(songRow);
  rows.forEach((row, index) => {
    if (entering) {
      row.classList.add('enter');
      row.style.setProperty('--i', String(Math.min(index, 14)));
    } else if (picked && row.classList.contains('current')) {
      row.classList.add('just-played');
    }
  });
  ui.list.replaceChildren(...rows);
}

function songKey(song) {
  return `${song.source}:${song.id}`;
}

async function collapseRow(song) {
  const row = ui.list.querySelector(`[data-key="${CSS.escape(songKey(song))}"]`);
  if (!row || reduceMotion.matches) return;
  row.classList.add('leaving');
  row.style.overflow = 'hidden';
  await row.animate([
    { opacity: 1, height: `${row.offsetHeight}px`, transform: 'none' },
    { opacity: 0, height: '0px', transform: 'translateX(-24px)' },
  ], { duration: 320, easing: 'cubic-bezier(0.22, 1, 0.36, 1)', fill: 'forwards' }).finished;
}

function emptyCopy() {
  if (state.loading) return { kind: 'loading', title: '곡 목록을 불러오는 중', description: '' };
  if (state.query.trim()) {
    return {
      title: `'${state.query.trim()}' 검색 결과가 없어요`,
      description: '제목, 아티스트, 앨범 이름으로 찾을 수 있어요.\n철자를 확인하거나 다른 탭에서 찾아보세요.',
    };
  }
  if (state.tab === 'local') {
    return {
      title: '이 PC에 저장된 음악이 없어요',
      description: '가져온 MP3는 이 컴퓨터에만 저장되어\n서버가 꺼져 있어도 들을 수 있어요.',
      addLabel: 'MP3 가져오기',
    };
  }
  if (!state.online) {
    return {
      kind: 'offline',
      title: '서버에 연결할 수 없어요',
      description: `음악 서버가 실행 중인지 확인해 주세요.\n현재 주소: ${state.serverUrl}`,
    };
  }
  return {
    title: '서버에 아직 음악이 없어요',
    description: 'MP3를 업로드하면 이 목록과 휴대폰 앱에 함께 표시돼요.\n파일을 이곳으로 끌어다 놓아도 됩니다.',
    addLabel: 'MP3 업로드',
  };
}

function songRow(song) {
  const current = state.song?.id === song.id && state.song?.source === song.source;
  const row = document.createElement('div');
  row.className = `song${current ? ' current' : ''}${current && audio.paused ? ' paused' : ''}`;
  row.dataset.key = songKey(song);
  row.setAttribute('role', 'listitem');

  const main = document.createElement('button');
  main.className = 'song-main';
  main.type = 'button';
  main.setAttribute('aria-label', `${song.title}, ${subtitle(song)}${current ? (audio.paused ? ', 일시정지됨' : ', 재생 중') : ''}`);
  main.addEventListener('click', () => playSong(song));

  const cover = document.createElement('span');
  cover.className = 'cover';
  if (song.coverUrl) {
    const image = document.createElement('img');
    image.alt = '';
    image.src = song.coverUrl;
    image.addEventListener('error', () => image.remove());
    cover.append(image);
  }
  const copy = document.createElement('span');
  copy.className = 'song-copy';
  const title = document.createElement('strong');
  title.textContent = song.title;
  const meta = document.createElement('span');
  meta.textContent = subtitle(song);
  const mark = document.createElement('span');
  mark.className = 'playing-mark';
  mark.setAttribute('aria-hidden', 'true');
  mark.innerHTML = '<span class="eq"><i></i><i></i><i></i></span><span class="mark-text"></span>';
  mark.lastChild.textContent = audio.paused ? '일시정지' : '재생 중';
  copy.append(title, meta);
  main.append(cover, copy, mark);

  const remove = document.createElement('button');
  remove.className = 'delete';
  remove.type = 'button';
  remove.title = song.source === 'local' ? '이 PC에서 삭제' : '서버에서 삭제';
  remove.setAttribute('aria-label', `${song.title} ${remove.title}`);
  remove.innerHTML = '<svg><use href="#i-trash"/></svg>';
  remove.addEventListener('click', (event) => deleteSong(song, event));
  row.append(main, remove);
  return row;
}

function subtitle(song) {
  const artist = displayName(song.artist, '알 수 없는 아티스트');
  const album = displayName(song.album, '');
  return album ? `${artist} · ${album}` : artist;
}

function displayName(value, fallback) {
  if (!value || value === 'Unknown Artist' || value === 'Unknown Album') return fallback;
  return value;
}

function playSong(song) {
  state.queue = filteredSongs();
  const index = state.queue.findIndex((item) => item.id === song.id && item.source === song.source);
  if (index < 0) return;
  if (state.song?.id === song.id && state.song?.source === song.source) {
    togglePlayback();
    return;
  }
  playAt(index);
}

function playAt(index) {
  const song = state.queue[index];
  if (!song) return;
  state.index = index;
  state.song = song;
  state.lyrics = null;
  state.askedDuration = false;
  state.highlightedLyric = -2;
  ensureAudioGraph();
  audio.src = song.streamUrl;
  const pending = audio.play();
  if (pending) pending.catch(() => {});
  setCover(song.coverUrl);
  replay(ui.track, 'swap');
  state.captionIndex = -2;
  loadLyrics(false);
  renderLibrary();
  renderPlayer();
  document.title = `${song.title} — My Music`;
}

function togglePlayback() {
  replay(ui.play, 'pulse');
  if (!state.song) {
    const songs = filteredSongs();
    if (!songs.length) {
      toast('재생할 곡이 없어요. MP3를 먼저 추가해 주세요.');
      return;
    }
    state.queue = songs;
    playAt(0);
    return;
  }
  if (audio.paused) {
    ensureAudioGraph();
    audio.play().catch(() => {});
  } else {
    audio.pause();
  }
}

function previous() {
  if (!state.song) return;
  replay(document.querySelector('#prev'), 'pop');
  if (audio.currentTime > 3) {
    audio.currentTime = 0;
    return;
  }
  advance(-1);
}

function advance(delta) {
  if (!state.queue.length || state.index < 0) return;
  if (delta > 0) replay(document.querySelector('#next'), 'pop');
  if (delta > 0 && state.shuffle && state.queue.length > 1) {
    let index = state.index;
    while (index === state.index) index = Math.floor(Math.random() * state.queue.length);
    playAt(index);
    return;
  }
  let index = state.index + delta;
  if (index < 0) index = state.queue.length - 1;
  else if (index >= state.queue.length) index = 0;
  playAt(index);
}

function onEnded() {
  if (!state.song) return;
  if (state.repeat === 'one') {
    audio.currentTime = 0;
    audio.play();
    return;
  }
  if (state.shuffle && state.queue.length > 1) {
    advance(1);
    return;
  }
  if (state.index + 1 < state.queue.length) playAt(state.index + 1);
  else if (state.repeat === 'all' && state.queue.length) playAt(0);
  else renderPlayer();
}

function ensureAudioGraph() {
  if (state.graphReady) {
    state.audioContext?.resume();
    return;
  }
  const Context = window.AudioContext || window.webkitAudioContext;
  if (!Context) return;
  state.audioContext = new Context();
  const source = state.audioContext.createMediaElementSource(audio);
  const analyser = state.audioContext.createAnalyser();
  analyser.fftSize = 2048;
  source.connect(analyser);
  analyser.connect(state.audioContext.destination);
  turntable.setAnalyser(analyser);
  state.graphReady = true;
  state.audioContext.resume();
}

function setCover(url) {
  if (!url) {
    turntable.setCover(null);
    turntable.setAccent('#E8A55E');
    return;
  }
  const image = new Image();
  image.onload = () => {
    if (state.song?.coverUrl !== url) return;
    turntable.setCover(image);
    try {
      turntable.setAccent(window.accentFromImage(image));
    } catch {
      turntable.setAccent('#E8A55E');
    }
  };
  image.onerror = () => {
    if (state.song?.coverUrl === url) {
      turntable.setCover(null);
      turntable.setAccent('#E8A55E');
    }
  };
  image.src = url;
}

async function loadLyrics(useDuration) {
  const song = state.song;
  if (!song) return;
  const token = ++state.lyricsToken;
  try {
    const lyrics = await host.loadLyrics({
      source: song.source,
      id: song.id,
      title: song.title,
      artist: song.artist,
      durationMs: useDuration && Number.isFinite(audio.duration) ? Math.round(audio.duration * 1000) : 0,
    });
    if (token !== state.lyricsToken || state.song !== song) return;
    state.lyrics = lyrics;
    state.highlightedLyric = -2;
    renderCaption();
    if (ui.lyrics.open) renderLyricsDialog();
  } catch (error) {
    if (token !== state.lyricsToken || state.song !== song) return;
    if (!state.lyrics) toast(`가사를 불러오지 못했어요. ${cleanError(error.message)}`, 'error');
  }
}

function renderPlayer() {
  const song = state.song;
  const playing = Boolean(song) && !audio.paused && !audio.ended;
  ui.title.textContent = song ? song.title : '재생 중인 음악 없음';
  ui.artist.textContent = song ? subtitle(song) : '목록에서 음악을 재생해 주세요';
  const loading = Boolean(song) && state.buffering && !audio.paused;
  ui.play.classList.toggle('paused', playing);
  ui.play.classList.toggle('loading', loading);
  ui.play.setAttribute('aria-label', loading ? '불러오는 중' : playing ? '일시정지' : '재생');
  ui.play.title = loading ? '불러오는 중…' : playing ? '일시정지 (Space)' : '재생 (Space)';
  ui.seek.disabled = !song;
  ui.shuffle.setAttribute('aria-pressed', String(state.shuffle));
  ui.repeat.setAttribute('aria-pressed', String(state.repeat !== 'off'));
  ui.repeat.classList.toggle('one', state.repeat === 'one');
  ui.shuffle.title = state.shuffle ? '셔플 켜짐 — 눌러서 끄기' : '셔플 꺼짐 — 눌러서 켜기';
  ui.repeat.title = {
    off: '반복 꺼짐 — 눌러서 전체 반복',
    all: '전체 반복 — 눌러서 한 곡 반복',
    one: '한 곡 반복 — 눌러서 반복 끄기',
  }[state.repeat];
  ui.repeat.setAttribute('aria-label', state.repeat === 'one' ? '한 곡 반복' : state.repeat === 'all' ? '전체 반복' : '반복 꺼짐');
  ui.modeText.textContent = [
    state.shuffle ? '셔플' : '',
    state.repeat === 'all' ? '전체 반복' : state.repeat === 'one' ? '한 곡 반복' : '',
  ].filter(Boolean).join(' · ');
  ui.cc.setAttribute('aria-pressed', String(state.cc));
  ui.cc.setAttribute('aria-label', state.cc ? '싱크 자막 켜짐' : '싱크 자막 꺼짐');
  const silent = state.muted || state.volume === 0;
  ui.mute.classList.toggle('muted', silent);
  ui.mute.setAttribute('aria-label', silent ? '음소거 해제' : '음소거');
  ui.mute.title = silent ? '음소거 해제 (M)' : '음소거 (M)';
  ui.volumeText.textContent = silent ? '0' : String(Math.round(state.volume * 100));
  paintSlider(ui.volume);
  renderTimes();
  renderCaption();
  turntable.setPlayback({
    playing,
    hasTrack: Boolean(song),
    progress: audio.duration ? audio.currentTime / audio.duration : 0,
  });
  const current = ui.list.querySelector('.song.current');
  current?.classList.toggle('paused', !playing);
  const markText = current?.querySelector('.mark-text');
  if (markText) markText.textContent = loading ? '불러오는 중' : playing ? '재생 중' : '일시정지';
  if (ui.modeText.dataset.last !== ui.modeText.textContent) {
    ui.modeText.dataset.last = ui.modeText.textContent;
    replay(ui.modeText, 'changed');
  }
}

function setBuffering(value) {
  state.buffering = value;
  renderPlayer();
}

function renderTimes() {
  ui.timeNow.textContent = formatTime(audio.currentTime);
  ui.timeTotal.textContent = formatTime(audio.duration);
  if (!state.scrubbing && Number.isFinite(audio.duration) && audio.duration > 0) {
    ui.seek.value = String(Math.round((audio.currentTime / audio.duration) * 1000));
  } else if (!state.song) {
    ui.seek.value = '0';
  }
  paintSlider(ui.seek);
  const playing = Boolean(state.song) && !audio.paused && !audio.ended;
  turntable.setPlayback({
    playing,
    hasTrack: Boolean(state.song),
    progress: audio.duration ? audio.currentTime / audio.duration : 0,
  });
}

function renderCaption() {
  const synced = Boolean(state.cc && state.lyrics?.synced && state.song);
  ui.caption.classList.toggle('lines-mode', synced);
  ui.caption.classList.toggle('bars-mode', !synced);
  if (!synced) return;
  const position = (audio.currentTime || 0) * 1000 + lyricOffset();
  const lines = state.lyrics.lines;
  const index = lineAt(lines, position);
  ui.linePrev.textContent = index > 0 ? lines[index - 1].text : '';
  ui.lineCurrent.textContent = index >= 0 ? lines[index].text : '';
  ui.lineNext.textContent = lines[index + 1]?.text ?? '';
  if (index !== state.captionIndex) {
    if (state.captionIndex !== -2 && index > state.captionIndex) replay(ui.captionLines, 'advance');
    state.captionIndex = index;
  }
  if (ui.lyrics.open) highlightLyrics(index);
}

function openLyrics() {
  if (!state.song) {
    toast('곡을 재생하면 가사를 볼 수 있어요.');
    return;
  }
  if (ui.lyrics.open) return;
  renderLyricsDialog();
  ui.lyrics.showModal();
}

function renderLyricsDialog() {
  const song = state.song;
  ui.lyricsTitle.textContent = song ? song.title : '';
  ui.lyricsArtist.textContent = song ? displayName(song.artist, '알 수 없는 아티스트') : '';
  renderOffset();
  const synced = Boolean(state.lyrics?.synced);
  ui.offsetControls.hidden = !synced;
  ui.lyricsBody.replaceChildren();
  if (!state.lyrics || (!state.lyrics.synced && !state.lyrics.plain?.trim())) {
    const empty = document.createElement('p');
    empty.className = 'lyrics-empty';
    empty.textContent = state.lyrics
      ? '이 곡의 가사를 찾지 못했어요.\n곡 파일에 가사가 없고, 온라인 가사 서비스에도 등록되지 않은 곡입니다.'
      : '가사를 불러오는 중…';
    ui.lyricsBody.append(empty);
    return;
  }
  const note = document.createElement('p');
  note.className = 'lyrics-note';
  note.textContent = synced
    ? '가사 줄을 누르면 그 부분부터 재생됩니다.'
    : '시간 정보가 없는 가사라 재생 위치를 따라가지 않습니다.';
  ui.lyricsBody.append(note);
  if (!synced) {
    const plain = document.createElement('p');
    plain.className = 'plain-lyrics';
    plain.textContent = state.lyrics.plain;
    ui.lyricsBody.append(plain);
    return;
  }
  state.lyrics.lines.forEach((line, index) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'lyric-line';
    button.dataset.index = String(index);
    button.textContent = line.text || '…';
    button.addEventListener('click', () => {
      audio.currentTime = Math.max(0, (line.timeMs - lyricOffset()) / 1000);
      if (audio.paused) audio.play();
    });
    ui.lyricsBody.append(button);
  });
  state.highlightedLyric = -2;
  highlightLyrics(lineAt(state.lyrics.lines, (audio.currentTime || 0) * 1000 + lyricOffset()));
}

function highlightLyrics(index) {
  const buttons = ui.lyricsBody.querySelectorAll('.lyric-line');
  buttons.forEach((button, buttonIndex) => button.classList.toggle('current', buttonIndex === index));
  if (index !== state.highlightedLyric && index >= 0) {
    state.highlightedLyric = index;
    buttons[index]?.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }
}

function lyricOffset() {
  if (!state.song) return 0;
  return Number(localStorage.getItem(offsetKey(state.song))) || 0;
}

function offsetKey(song) {
  return `lyrics_sync_offset_${song.source}:${song.id}`;
}

function changeOffset(delta, reset = false) {
  if (!state.song) return;
  const next = reset ? 0 : Math.max(-10000, Math.min(10000, lyricOffset() + delta));
  localStorage.setItem(offsetKey(state.song), String(next));
  renderOffset();
  replay(ui.lyricsOffset, 'bump');
  renderCaption();
}

function renderOffset() {
  const ms = lyricOffset();
  ui.lyricsOffset.textContent = offsetLabel(ms);
  ui.lyricsOffset.classList.toggle('changed', ms !== 0);
  ui.offsetReset.hidden = ms === 0;
}

function offsetLabel(ms) {
  if (!ms) return '기본 타이밍';
  return `${Math.abs(ms) / 1000}초 ${ms > 0 ? '빠르게' : '늦게'} 맞춤`;
}

function paintSlider(slider) {
  const min = Number(slider.min) || 0;
  const max = Number(slider.max) || 100;
  const ratio = max > min ? (Number(slider.value) - min) / (max - min) : 0;
  slider.style.setProperty('--fill', `${Math.max(0, Math.min(1, ratio)) * 100}%`);
}

function lineAt(lines, positionMs) {
  let index = -1;
  for (let i = 0; i < lines.length; i += 1) {
    if (lines[i].timeMs > positionMs) break;
    index = i;
  }
  return index;
}

function openSettings() {
  ui.serverInput.value = state.serverUrl;
  showServerError('');
  ui.settings.showModal();
  ui.serverInput.select();
}

function showServerError(message) {
  ui.serverError.textContent = message;
  ui.serverError.hidden = !message;
  ui.serverInput.setAttribute('aria-invalid', String(Boolean(message)));
}

async function saveSettings() {
  if (ui.settingsSave.disabled) return;
  ui.settingsSave.disabled = true;
  ui.settingsSave.classList.add('busy');
  ui.settingsSaveLabel.textContent = '연결 확인 중…';
  showServerError('');
  try {
    const saved = await host.saveSettings({ serverUrl: ui.serverInput.value });
    state.serverUrl = saved.serverUrl;
    ui.serverInput.value = saved.serverUrl;
    if (await reloadServer()) {
      ui.settings.close();
      toast('서버에 연결했습니다.', 'success');
    } else {
      showServerError('주소는 저장했지만 서버가 응답하지 않아요. 서버가 실행 중인지, 주소와 포트 번호(예: 8080)가 맞는지 확인해 주세요.');
    }
  } catch (error) {
    showServerError(cleanError(error.message) || '서버 주소를 저장하지 못했습니다.');
    ui.serverInput.focus();
  } finally {
    ui.settingsSave.disabled = false;
    ui.settingsSave.classList.remove('busy');
    ui.settingsSaveLabel.textContent = '저장하고 연결';
  }
}

function openHelp() {
  if (!ui.help.open) ui.help.showModal();
}

async function refreshMetadata() {
  if (state.busy) return;
  state.busy = true;
  renderLibrary();
  toast('앨범 정보를 찾는 중입니다. 곡이 많으면 몇 분 걸릴 수 있어요.', 'progress');
  try {
    state.serverSongs = (await host.serverRefresh()).map(mapServer);
    state.online = true;
    toast('앨범 정보와 커버를 채웠습니다.', 'success');
  } catch (error) {
    toast(`앨범 정보를 채우지 못했어요. ${cleanError(error.message)}`, 'error');
  } finally {
    state.busy = false;
    renderLibrary();
  }
}

async function pickAndAdd(local) {
  if (state.adding) return;
  const paths = await host.pickMp3();
  if (paths?.length) await addFiles(paths, local);
}

async function addFiles(paths, local) {
  if (state.adding) {
    toast('이전 파일을 추가하는 중이에요. 끝난 뒤 다시 시도해 주세요.');
    return;
  }
  const target = local ? '이 PC로 가져오는' : '서버에 업로드하는';
  state.adding = true;
  renderLibrary();
  toast(`${paths.length}곡을 ${target} 중…`, 'progress');
  try {
    const result = local ? await host.importLocal(paths) : await host.serverUpload(paths);
    if (local) state.localSongs = result.songs.map(mapLocal);
    else await reloadServer();
    const failed = result.errors?.length ?? 0;
    const done = Math.max(0, Math.min(paths.length, 100) - failed);
    if (!failed) toast(local ? `${done}곡을 이 PC로 가져왔습니다.` : `${done}곡을 업로드했습니다. 휴대폰에서도 들을 수 있어요.`, 'success');
    else if (!done) toast(`추가하지 못했어요. ${cleanError(result.errors[0])}`, 'error');
    else toast(`${done}곡 완료, ${failed}곡 실패 — ${cleanError(result.errors[0])}`, 'error');
  } catch (error) {
    toast(`파일을 추가하지 못했어요. ${cleanError(error.message)}`, 'error');
  } finally {
    state.adding = false;
    renderLibrary();
  }
}

async function deleteSong(song, event) {
  event.stopPropagation();
  const local = song.source === 'local';
  const accepted = await confirmAction({
    title: local ? '이 PC에서 삭제할까요?' : '서버에서 삭제할까요?',
    song,
    detail: local
      ? '이 PC 보관함에 복사된 파일이 지워집니다. 처음 가져온 원본 MP3는 그대로 남아요.'
      : '서버에서 지우면 휴대폰 앱에서도 사라지며 되돌릴 수 없습니다.',
    ok: local ? '이 PC에서 삭제' : '서버에서 삭제',
  });
  if (!accepted) return;
  try {
    if (song.source === 'local') {
      const remaining = (await host.removeLocal(song.id)).map(mapLocal);
      await collapseRow(song);
      state.skipEnter = true;
      state.localSongs = remaining;
    } else {
      await host.serverDelete(song.id);
      await collapseRow(song);
      state.skipEnter = true;
      await reloadServer();
    }
    if (state.song?.id === song.id && state.song?.source === song.source) {
      audio.pause();
      audio.removeAttribute('src');
      state.song = null;
      state.queue = [];
      state.index = -1;
      turntable.setCover(null);
      document.title = 'My Music';
      renderPlayer();
    } else {
      state.queue = state.queue.filter((item) => !(item.id === song.id && item.source === song.source));
      state.index = state.queue.findIndex((item) => item.id === state.song?.id && item.source === state.song?.source);
    }
    renderLibrary();
    toast(`'${song.title}'을(를) 삭제했습니다.`, 'success');
  } catch (error) {
    toast(`삭제하지 못했어요. ${cleanError(error.message)}`, 'error');
  }
}

function confirmAction({ title, song, detail, ok }) {
  ui.confirmTitle.textContent = title;
  const name = document.createElement('strong');
  name.textContent = song.title;
  ui.confirmText.replaceChildren(name, ` · ${displayName(song.artist, '알 수 없는 아티스트')}`);
  ui.confirmDetail.textContent = detail;
  ui.confirmOk.textContent = ok;
  ui.confirm.returnValue = '';
  ui.confirm.showModal();
  return new Promise((resolve) => {
    ui.confirm.addEventListener('close', () => resolve(ui.confirm.returnValue === 'ok'), { once: true });
  });
}

function closeConfirm(accepted) {
  ui.confirm.returnValue = accepted ? 'ok' : 'cancel';
  ui.confirm.close();
}

function onKeyDown(event) {
  const active = document.activeElement;
  const tag = active?.tagName;
  if (tag === 'INPUT' && active.type === 'range' && event.code === 'Space') {
    event.preventDefault();
    togglePlayback();
    return;
  }
  if (tag === 'INPUT' || tag === 'TEXTAREA') {
    if (event.key === 'Escape' && !active.closest('dialog')) active.blur();
    return;
  }
  if (document.querySelector('dialog[open]:not(#lyricsDialog)')) return;
  if (ui.lyrics.open && !['Space', 'ArrowLeft', 'ArrowRight'].includes(event.code)) return;
  if ((event.code === 'Space' || event.key === 'Enter') && tag === 'BUTTON') return;
  const key = event.key.toLowerCase();
  if (event.code === 'Space') {
    event.preventDefault();
    togglePlayback();
  } else if (event.ctrlKey && event.key === 'ArrowRight') {
    event.preventDefault();
    advance(1);
  } else if (event.ctrlKey && event.key === 'ArrowLeft') {
    event.preventDefault();
    previous();
  } else if (event.key === 'ArrowRight') {
    audio.currentTime = Math.min(audio.duration || 0, (audio.currentTime || 0) + 5);
  } else if (event.key === 'ArrowLeft') {
    audio.currentTime = Math.max(0, (audio.currentTime || 0) - 5);
  } else if (event.key === '?' || event.key === 'F1') {
    event.preventDefault();
    openHelp();
  } else if (!event.ctrlKey && !event.altKey && key === 'm') {
    ui.mute.click();
  } else if (!event.ctrlKey && !event.altKey && key === 'l') {
    openLyrics();
  } else if (event.key === 'ArrowUp') {
    event.preventDefault();
    ui.volume.value = String(Math.min(100, Number(ui.volume.value) + 5));
    ui.volume.dispatchEvent(new Event('input'));
  } else if (event.key === 'ArrowDown') {
    event.preventDefault();
    ui.volume.value = String(Math.max(0, Number(ui.volume.value) - 5));
    ui.volume.dispatchEvent(new Event('input'));
  } else if (event.ctrlKey && event.key.toLowerCase() === 'f') {
    event.preventDefault();
    ui.search.focus();
  }
}

function formatTime(seconds) {
  if (!Number.isFinite(seconds) || seconds < 0) return '0:00';
  const total = Math.floor(seconds);
  const minutes = Math.floor(total / 60);
  const remain = total % 60;
  if (minutes >= 60) {
    return `${Math.floor(minutes / 60)}:${String(minutes % 60).padStart(2, '0')}:${String(remain).padStart(2, '0')}`;
  }
  return `${minutes}:${String(remain).padStart(2, '0')}`;
}

function clampVolume(value) {
  return Math.max(0, Math.min(1, Number.isFinite(value) ? value : 0.8));
}

function cleanError(message) {
  const text = String(message ?? '').trim();
  const marker = text.lastIndexOf('Error: ');
  return marker >= 0 ? text.slice(marker + 'Error: '.length) : text;
}

const toastIcons = { info: '#i-info', success: '#i-check', error: '#i-alert', progress: '#i-spinner' };
let toastTimer = 0;
function toast(message, type = 'info') {
  ui.toastText.textContent = cleanError(message);
  ui.toastIcon.setAttribute('href', toastIcons[type] ?? toastIcons.info);
  const wasShown = ui.toast.classList.contains('show');
  ui.toast.className = `toast show ${type}`;
  if (wasShown) replay(ui.toast, 'bump');
  ui.toast.setAttribute('role', type === 'error' ? 'alert' : 'status');
  window.clearTimeout(toastTimer);
  if (type === 'progress') return;
  toastTimer = window.setTimeout(() => ui.toast.classList.remove('show'), type === 'error' ? 6000 : 3200);
}
