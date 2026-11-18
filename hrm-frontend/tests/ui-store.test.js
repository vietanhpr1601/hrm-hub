import test from 'node:test';
import assert from 'node:assert/strict';
import useUiStore from '../src/store/uiStore.js';

const resetLoading = () => useUiStore.setState({ apiCount: 0, showLoading: false, startTime: 0 });

test('a completed request cannot hide loading while a newer request is in flight', async (context) => {
  context.mock.timers.enable({ apis: ['Date', 'setTimeout'], now: 1000 });
  resetLoading();
  useUiStore.getState().startLoading();
  const firstStop = useUiStore.getState().stopLoading();
  context.mock.timers.tick(200);
  useUiStore.getState().startLoading();
  context.mock.timers.tick(200);
  await firstStop;
  assert.equal(useUiStore.getState().apiCount, 1);
  assert.equal(useUiStore.getState().showLoading, true);

  const finalStop = useUiStore.getState().stopLoading();
  context.mock.timers.tick(200);
  await finalStop;
  assert.equal(useUiStore.getState().showLoading, false);
});

test('a stale timer cannot shorten the minimum loading duration of a newer request', async (context) => {
  context.mock.timers.enable({ apis: ['Date', 'setTimeout'], now: 1000 });
  resetLoading();
  useUiStore.getState().startLoading();
  const firstStop = useUiStore.getState().stopLoading();
  context.mock.timers.tick(200);
  useUiStore.getState().startLoading();
  context.mock.timers.tick(100);
  const secondStop = useUiStore.getState().stopLoading();
  context.mock.timers.tick(100);
  await firstStop;
  assert.equal(useUiStore.getState().apiCount, 0);
  assert.equal(useUiStore.getState().showLoading, true);

  context.mock.timers.tick(200);
  await secondStop;
  assert.equal(useUiStore.getState().showLoading, false);
});
