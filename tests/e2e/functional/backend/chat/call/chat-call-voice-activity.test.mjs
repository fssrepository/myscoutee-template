import assert from 'node:assert/strict';
import { test } from 'node:test';
import { ChatCallVoiceActivity } from '../../../../../../frontend/src/app/shared/core/base/services/chat/chat-call-voice-activity.ts';

const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
async function until(predicate) {
  for (let attempt = 0; attempt < 20; attempt++) { if (predicate()) return; await pause(50); }
  assert.ok(predicate(), 'Audio activity did not reach the expected state');
}
class AudioFixture {
  state = 'running'; sampleRate = 48000; amplitude = 0; sources = []; closed = false;
  constructor() { AudioFixture.current = this; }
  createMediaStreamSource() {
    const source = { connections: [], disconnected: false, connect: target => source.connections.push(target), disconnect: () => { source.disconnected = true; } };
    this.sources.push(source); return source;
  }
  createAnalyser() {
    const analyser = { fftSize: 512, get frequencyBinCount() { return this.fftSize / 2; }, disconnected: false,
      getFloatTimeDomainData: samples => samples.forEach((_, index) => { samples[index] = this.amplitude * Math.sin(index * Math.PI / 8); }),
      getByteFrequencyData: bins => bins.forEach((_, index) => { bins[index] = this.amplitude ? Math.max(0, 240 - index * 5) : 0; }),
      disconnect: () => { analyser.disconnected = true; } };
    return analyser;
  }
  resume() { return Promise.resolve(); }
  close() { this.closed = true; return Promise.resolve(); }
}

test('real PCM levels produce a changing FFT spectrum, hold brief speech gaps, and suppress muted sources', async () => {
  globalThis.AudioContext = AudioFixture;
  let active = new Set(), levels = {}, enabled = true;
  const detector = new ChatCallVoiceActivity(value => { active = value; }, value => { levels = value; });
  const context = AudioFixture.current;
  try {
    detector.watch('alice', { getAudioTracks: () => [{}] }, () => enabled);
    await pause(150); assert.equal(active.size, 0); assert.deepEqual(levels, {});
    context.amplitude = 0.15;
    await until(() => active.has('alice'));
    assert.equal(levels.alice.length, 48); assert.ok(new Set(levels.alice).size > 1);
    assert.equal(context.sources[0].connections.length, 1, 'Analyser must not create another playback route');
    context.amplitude = 0;
    await pause(150); assert.ok(active.has('alice'), 'Brief gap must not flicker the speaking frame');
    await until(() => !active.has('alice')); assert.deepEqual(levels, {});
    context.amplitude = 0.15; await until(() => active.has('alice'));
    enabled = false; await until(() => !active.has('alice')); assert.deepEqual(levels, {});
    enabled = true; await until(() => active.has('alice'));
    detector.remove('alice'); assert.equal(active.size, 0); assert.deepEqual(levels, {});
    assert.equal(context.sources[0].disconnected, true);
  } finally { detector.destroy(); delete globalThis.AudioContext; }
  assert.equal(context.closed, true);
});

test('an unavailable browser analyser leaves the call usable and cleanup clears indicators', () => {
  delete globalThis.AudioContext;
  let active = null, levels = null;
  const detector = new ChatCallVoiceActivity(value => { active = value; }, value => { levels = value; });
  detector.watch('alice', { getAudioTracks: () => [{}] }, () => true); detector.resume(); detector.destroy();
  assert.equal(active.size, 0); assert.deepEqual(levels, {});
});
