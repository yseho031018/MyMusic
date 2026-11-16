/** Vinyl deck drawn in the same 1000-unit space as the Android turntable. */
class TurntableView {
  constructor(deck, bars) {
    this.deck = deck;
    this.bars = bars;
    this.playing = false;
    this.hasTrack = false;
    this.progress = 0;
    this.cover = null;
    this.rotation = 0;
    this.wavePhase = 0;
    this.waveStrength = 0;
    this.needlePosition = 0;
    this.shownBass = 0;
    this.shownEnergy = 0;
    this.shownKick = 0;
    this.shownAudioAvailable = 0;
    this.barLevel = 0;
    this.barAudio = 0;
    this.barPhase = 0;
    this.lowPass = 0;
    this.previousBass = 0;
    this.accent = { r: 232, g: 165, b: 94 };
    this.accentFrom = { ...this.accent };
    this.accentTo = { ...this.accent };
    this.accentStarted = 0;
    this.lastFrame = 0;
    this.analyser = null;
    this.timeData = null;
    this.frame = this.frame.bind(this);
    requestAnimationFrame(this.frame);
  }

  setAnalyser(analyser) {
    this.analyser = analyser;
    this.timeData = analyser ? new Uint8Array(analyser.fftSize) : null;
  }

  setPlayback({ playing, progress, hasTrack }) {
    this.playing = Boolean(playing);
    this.hasTrack = Boolean(hasTrack);
    this.progress = Math.max(0, Math.min(1, progress || 0));
  }

  setCover(image) {
    this.cover = image;
  }

  setAccent(hex) {
    const next = parseHex(hex);
    if (!next) return;
    if (next.r === this.accentTo.r && next.g === this.accentTo.g && next.b === this.accentTo.b) return;
    this.accentFrom = { ...this.accent };
    this.accentTo = next;
    this.accentStarted = performance.now();
  }

  frame(now) {
    const elapsed = this.lastFrame === 0 ? 0 : Math.min(50, Math.max(0, now - this.lastFrame));
    this.lastFrame = now;
    this.advance(elapsed, now);
    this.drawDeck();
    this.drawBars();
    requestAnimationFrame(this.frame);
  }

  advance(elapsed, now) {
    const blend = Math.min(1, (now - this.accentStarted) / 1100);
    this.accent = mix(this.accentFrom, this.accentTo, Number.isFinite(blend) ? blend : 1);
    document.documentElement.style.setProperty('--accent', hex(this.accent));
    if (elapsed <= 0) return;
    const step = Math.min(1, elapsed / 320);
    this.needlePosition += ((this.playing ? 1 : 0) - this.needlePosition) * step;
    this.waveStrength += ((this.playing ? 1 : 0) - this.waveStrength) * step;
    const levels = this.sampleLevels();
    const audioStep = Math.min(1, elapsed / 90);
    const availableStep = Math.min(1, elapsed / 220);
    this.shownBass += ((this.playing ? levels.bass : 0) - this.shownBass) * audioStep;
    this.shownEnergy += ((this.playing ? levels.energy : 0) - this.shownEnergy) * audioStep;
    this.shownKick += ((this.playing ? levels.kick : 0) - this.shownKick) * audioStep;
    const available = this.playing && levels.available ? 1 : 0;
    this.shownAudioAvailable += (available - this.shownAudioAvailable) * availableStep;
    if (this.playing) {
      this.rotation = (this.rotation + elapsed * 0.2) % 360;
      this.wavePhase += elapsed * 0.0045;
    }
    const target = available ? Math.min(1, levels.bass * 0.55 + levels.energy * 0.35 + levels.kick * 0.5) : 0;
    this.barLevel += (target - this.barLevel) * availableStep;
    this.barAudio += (available - this.barAudio) * availableStep;
    if (available) this.barPhase += elapsed * 0.009;
  }

  sampleLevels() {
    if (!this.playing || !this.analyser || !this.timeData) {
      return { bass: 0, energy: 0, kick: 0, available: 0 };
    }
    this.analyser.getByteTimeDomainData(this.timeData);
    const coefficient = Math.min(1, 2 * Math.PI * 140 * 4 / 44100);
    let low = this.lowPass;
    let lowSquares = 0;
    let fullSquares = 0;
    let count = 0;
    for (let index = 0; index < this.timeData.length; index += 4) {
      const sample = (this.timeData[index] - 128) / 128;
      low += coefficient * (sample - low);
      lowSquares += low * low;
      fullSquares += sample * sample;
      count += 1;
    }
    this.lowPass = low;
    if (!count) return { bass: 0, energy: 0, kick: 0, available: 0 };
    const lowRms = Math.sqrt(lowSquares / count);
    const fullRms = Math.sqrt(fullSquares / count);
    const transientLevel = Math.max(0, lowRms - this.previousBass * 0.75);
    this.previousBass = this.previousBass * 0.45 + lowRms * 0.55;
    return {
      bass: clamp(lowRms * 7),
      energy: clamp(fullRms * 4),
      kick: clamp(transientLevel * 13),
      available: 1,
    };
  }

  drawDeck() {
    const canvas = this.deck;
    const rect = canvas.getBoundingClientRect();
    fitCanvas(canvas, rect.width, rect.height);
    const ctx = canvas.getContext('2d');
    const size = Math.min(canvas.width, canvas.height);
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.save();
    ctx.translate((canvas.width - size) / 2, (canvas.height - size) / 2);
    ctx.scale(size / 1000, size / 1000);
    this.drawPlinth(ctx);
    this.drawWave(ctx);
    this.drawRecord(ctx);
    this.drawNeedle(ctx);
    ctx.restore();
  }

  drawPlinth(ctx) {
    roundRect(ctx, 12, 12, 976, 976, 54);
    ctx.fillStyle = '#232328';
    ctx.fill();
    ctx.lineWidth = 2;
    ctx.strokeStyle = '#35353c';
    roundRect(ctx, 14, 14, 972, 972, 52);
    ctx.stroke();
    ctx.fillStyle = '#b9b4ac';
    ctx.font = '600 26px "Segoe UI", sans-serif';
    ctx.fillText('MY MUSIC', 68, 92);
    ctx.fillStyle = '#8a857e';
    ctx.font = '21px "Segoe UI", sans-serif';
    ctx.fillText('33⅓ RPM   ·   SIDE A', 68, 940);
    ctx.beginPath();
    ctx.fillStyle = this.playing ? '#e8a55e' : '#4d4d55';
    ctx.arc(920, 88, 7, 0, Math.PI * 2);
    ctx.fill();
  }

  drawWave(ctx) {
    const color = this.accent;
    ctx.beginPath();
    ctx.lineWidth = 4;
    ctx.strokeStyle = rgba(color, 55 / 255);
    ctx.arc(463, 520, 368, 0, Math.PI * 2);
    ctx.stroke();
    const intensity = Math.min(1, this.shownBass * 0.45 + this.shownEnergy * 0.35 + this.shownKick * 0.45);
    for (let i = 0; i < 90; i += 1) {
      const angle = i * Math.PI * 2 / 90;
      const rhythm = 0.45 + 0.55 * Math.abs(Math.sin(i * 0.57 + this.wavePhase) * Math.cos(i * 0.19 - this.wavePhase * 0.7));
      const length = 5 + this.waveStrength * (5 + 34 * this.shownAudioAvailable * intensity * rhythm);
      const inner = 379;
      const outer = inner + length;
      ctx.strokeStyle = rgba(color, (50 + this.waveStrength * (60 + 100 * this.shownAudioAvailable * intensity)) / 255);
      ctx.lineWidth = 4;
      ctx.lineCap = 'round';
      ctx.beginPath();
      ctx.moveTo(463 + Math.cos(angle) * inner, 520 + Math.sin(angle) * inner);
      ctx.lineTo(463 + Math.cos(angle) * outer, 520 + Math.sin(angle) * outer);
      ctx.stroke();
    }
    ctx.lineCap = 'butt';
  }

  drawRecord(ctx) {
    ctx.fillStyle = 'rgba(0, 0, 0, 0.4)';
    ctx.beginPath();
    ctx.arc(471, 537, 354, 0, Math.PI * 2);
    ctx.fill();
    ctx.save();
    ctx.translate(463, 520);
    ctx.rotate(this.rotation * Math.PI / 180);
    ctx.translate(-463, -520);
    const vinyl = ctx.createRadialGradient(378, 390, 20, 463, 520, 350);
    vinyl.addColorStop(0, '#2c2c2a');
    vinyl.addColorStop(0.38, '#121211');
    vinyl.addColorStop(0.74, '#30302d');
    vinyl.addColorStop(1, '#0f0f0e');
    ctx.fillStyle = vinyl;
    ctx.beginPath();
    ctx.arc(463, 520, 350, 0, Math.PI * 2);
    ctx.fill();
    ctx.lineWidth = 2;
    for (let radius = 199; radius < 346; radius += 12) {
      ctx.strokeStyle = radius % 3 === 0 ? '#3f3e3b' : '#2f2e2c';
      ctx.beginPath();
      ctx.arc(463, 520, radius, 0, Math.PI * 2);
      ctx.stroke();
    }
    ctx.lineWidth = 5;
    ctx.strokeStyle = '#3a3936';
    ctx.beginPath();
    ctx.arc(463, 520, 348, 0, Math.PI * 2);
    ctx.stroke();
    ctx.fillStyle = '#c98d4e';
    ctx.beginPath();
    ctx.arc(463, 520, 185, 0, Math.PI * 2);
    ctx.fill();
    if (this.cover) {
      ctx.save();
      ctx.beginPath();
      ctx.arc(463, 520, 180, 0, Math.PI * 2);
      ctx.clip();
      const scale = Math.max(360 / this.cover.width, 360 / this.cover.height);
      const width = this.cover.width * scale;
      const height = this.cover.height * scale;
      ctx.drawImage(this.cover, 463 - width / 2, 520 - height / 2, width, height);
      ctx.restore();
    }
    ctx.fillStyle = '#dedad3';
    ctx.beginPath();
    ctx.arc(463, 520, 13, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#262523';
    ctx.beginPath();
    ctx.arc(463, 520, 5, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  }

  drawNeedle(ctx) {
    ctx.fillStyle = '#111110';
    ctx.beginPath();
    ctx.arc(820, 190, 64, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#6d6a65';
    ctx.beginPath();
    ctx.arc(820, 190, 47, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#272624';
    ctx.beginPath();
    ctx.arc(820, 190, 31, 0, Math.PI * 2);
    ctx.fill();
    ctx.save();
    const angle = (-14 + this.needlePosition * (29 + this.progress * 8)) * Math.PI / 180;
    ctx.translate(820, 190);
    ctx.rotate(angle);
    ctx.translate(-820, -190);
    ctx.lineCap = 'round';
    ctx.strokeStyle = '#161615';
    ctx.lineWidth = 24;
    ctx.beginPath();
    ctx.moveTo(820, 205);
    ctx.lineTo(820, 530);
    ctx.stroke();
    ctx.strokeStyle = '#c8c4bd';
    ctx.lineWidth = 15;
    ctx.beginPath();
    ctx.moveTo(820, 205);
    ctx.lineTo(820, 530);
    ctx.stroke();
    ctx.strokeStyle = '#ecebe8';
    ctx.lineWidth = 7;
    ctx.beginPath();
    ctx.moveTo(815, 220);
    ctx.lineTo(815, 522);
    ctx.stroke();
    ctx.fillStyle = '#bdb8b0';
    roundRect(ctx, 794, 512, 52, 66, 8);
    ctx.fill();
    ctx.fillStyle = '#e8a55e';
    ctx.beginPath();
    ctx.arc(820, 589, 8, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
    ctx.fillStyle = '#dedad3';
    ctx.beginPath();
    ctx.arc(820, 190, 17, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#5f5c57';
    ctx.beginPath();
    ctx.arc(820, 190, 8, 0, Math.PI * 2);
    ctx.fill();
  }

  drawBars() {
    const canvas = this.bars;
    const rect = canvas.getBoundingClientRect();
    if (rect.width < 10 || rect.height < 10) return;
    fitCanvas(canvas, rect.width, rect.height);
    const ctx = canvas.getContext('2d');
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    const count = 32;
    const barWidth = 3.5 * (canvas.width / 320);
    const gap = Math.min(9 * (canvas.width / 320), (canvas.width - 40 - barWidth) / (count - 1));
    if (gap <= barWidth) return;
    const left = (canvas.width - ((count - 1) * gap + barWidth)) / 2;
    const baseline = canvas.height * 0.76;
    const maxHeight = canvas.height * 0.68;
    ctx.fillStyle = 'rgba(232, 165, 94, 0.8)';
    for (let i = 0; i < count; i += 1) {
      const broadWave = (Math.sin(i * 0.68 + 0.2) + 1) * 0.5;
      const smallWave = (Math.sin(i * 1.51 + 1.3) + 1) * 0.5;
      const profile = 0.25 + 0.48 * broadWave + 0.27 * smallWave;
      const motion = this.barAudio * this.barLevel * ((Math.sin(i * 0.48 + this.barPhase) + 1) * 0.5);
      const height = Math.min(maxHeight, 8 + profile * (18 + 22 * this.barLevel) + motion * 10);
      roundRect(ctx, left + i * gap, baseline - height, barWidth, height, barWidth / 2);
      ctx.fill();
    }
  }
}

function fitCanvas(canvas, cssWidth, cssHeight) {
  const dpr = window.devicePixelRatio || 1;
  const width = Math.max(1, Math.round(cssWidth * dpr));
  const height = Math.max(1, Math.round(cssHeight * dpr));
  if (canvas.width !== width || canvas.height !== height) {
    canvas.width = width;
    canvas.height = height;
  }
}

function roundRect(ctx, x, y, width, height, radius) {
  ctx.beginPath();
  ctx.roundRect(x, y, width, height, radius);
}

function parseHex(value) {
  const match = /^#?([0-9a-f]{6})$/i.exec(String(value ?? '').trim());
  if (!match) return null;
  return {
    r: Number.parseInt(match[1].slice(0, 2), 16),
    g: Number.parseInt(match[1].slice(2, 4), 16),
    b: Number.parseInt(match[1].slice(4, 6), 16),
  };
}

function mix(from, to, amount) {
  const t = Math.max(0, Math.min(1, amount));
  return {
    r: Math.round(from.r + (to.r - from.r) * t),
    g: Math.round(from.g + (to.g - from.g) * t),
    b: Math.round(from.b + (to.b - from.b) * t),
  };
}

function hex(color) {
  return `#${byte(color.r)}${byte(color.g)}${byte(color.b)}`;
}

function rgba(color, alpha) {
  return `rgba(${color.r}, ${color.g}, ${color.b}, ${alpha})`;
}

function byte(value) {
  return Math.max(0, Math.min(255, Math.round(value))).toString(16).padStart(2, '0');
}

function clamp(value) {
  return Math.max(0, Math.min(1, value));
}

window.TurntableView = TurntableView;
window.accentFromImage = function accentFromImage(image) {
  const sample = document.createElement('canvas');
  sample.width = 24;
  sample.height = 24;
  const ctx = sample.getContext('2d', { willReadFrequently: true });
  ctx.drawImage(image, 0, 0, 24, 24);
  const data = ctx.getImageData(0, 0, 24, 24).data;
  const bins = Array.from({ length: 24 }, () => ({ count: 0, sat: 0, hue: 0 }));
  for (let i = 0; i < data.length; i += 4) {
    if (data[i + 3] < 128) continue;
    const [h, s, l] = rgbToHsl(data[i], data[i + 1], data[i + 2]);
    if (l < 0.08 || l > 0.94) continue;
    const bin = Math.min(23, Math.floor(h / 15));
    bins[bin].count += 1;
    bins[bin].sat += s;
    bins[bin].hue += h;
  }
  let best = null;
  let bestScore = -1;
  for (const bin of bins) {
    if (!bin.count) continue;
    const saturation = bin.sat / bin.count;
    const score = bin.count * (0.75 + saturation * 0.55);
    if (score > bestScore) {
      bestScore = score;
      best = { h: bin.hue / bin.count, s: saturation };
    }
  }
  if (!best || best.s < 0.08) return '#E8A55E';
  return hslToHex(best.h, Math.min(0.5, 0.2 + best.s * 0.4), 0.7);
};

function rgbToHsl(r, g, b) {
  const red = r / 255;
  const green = g / 255;
  const blue = b / 255;
  const max = Math.max(red, green, blue);
  const min = Math.min(red, green, blue);
  const lightness = (max + min) / 2;
  if (max === min) return [0, 0, lightness];
  const delta = max - min;
  const saturation = lightness > 0.5 ? delta / (2 - max - min) : delta / (max + min);
  let hue;
  if (max === red) hue = (green - blue) / delta + (green < blue ? 6 : 0);
  else if (max === green) hue = (blue - red) / delta + 2;
  else hue = (red - green) / delta + 4;
  return [hue * 60, saturation, lightness];
}

function hslToHex(hue, saturation, lightness) {
  const chroma = (1 - Math.abs(2 * lightness - 1)) * saturation;
  const sector = ((hue % 360) + 360) % 360 / 60;
  const secondary = chroma * (1 - Math.abs(sector % 2 - 1));
  let red = 0;
  let green = 0;
  let blue = 0;
  if (sector < 1) { red = chroma; green = secondary; }
  else if (sector < 2) { red = secondary; green = chroma; }
  else if (sector < 3) { green = chroma; blue = secondary; }
  else if (sector < 4) { green = secondary; blue = chroma; }
  else if (sector < 5) { red = secondary; blue = chroma; }
  else { red = chroma; blue = secondary; }
  const match = lightness - chroma / 2;
  return `#${byte((red + match) * 255)}${byte((green + match) * 255)}${byte((blue + match) * 255)}`;
}
