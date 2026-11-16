import assert from 'node:assert/strict';
import test from 'node:test';
import { chooseLrclibResult, lineAt, parseLrcText } from '../lyrics.js';

test('a positive LRC offset advances the lyric', () => {
  const lyrics = parseLrcText('[offset:500]\n[00:10.00] hello\n');
  assert.equal(lyrics.lines[0].timeMs, 9500);
  assert.equal(lineAt(lyrics.lines, 9500), 0);
  assert.equal(lineAt(lyrics.lines, 9499), -1);
});

test('one lyric line can carry several timestamps', () => {
  const lyrics = parseLrcText('[00:01.00][00:05.50] again\n');
  assert.deepEqual(lyrics.lines.map((line) => line.timeMs), [1000, 5500]);
  assert.equal(lyrics.plain, 'again');
});

test('metadata lines stay out of the lyric text', () => {
  const lyrics = parseLrcText('[ti:Song]\n[00:01.00] line\nplain tail\n');
  assert.equal(lyrics.plain, 'line\nplain tail');
});

test('search results reject a different duration and ambiguous titles', () => {
  const records = [
    { trackName: 'Song', artistName: 'A', duration: 200, syncedLyrics: '[00:01.00] a' },
    { trackName: 'Song', artistName: 'B', duration: 80, syncedLyrics: '[00:01.00] b' },
  ];
  assert.equal(chooseLrclibResult(records, { title: 'Song', artist: '', durationMs: 0 }), null);
  assert.equal(
    chooseLrclibResult(records, { title: 'Song', artist: 'A', durationMs: 190_000 })?.artistName,
    'A',
  );
  assert.equal(chooseLrclibResult(records, { title: 'Song', artist: 'A', durationMs: 20_000 }), null);
});
