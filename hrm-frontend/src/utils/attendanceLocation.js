export const LOCATION_MAX_AGE_MS = 120_000;
export const LOCATION_FUTURE_TOLERANCE_MS = 30_000;
export const LOCATION_OPTIONS = Object.freeze({ enableHighAccuracy: true, maximumAge: 0 });

const locationError = (code, message) => Object.assign(new Error(message), { code });
export const isLocationCancelled = (error) => error?.code === 'LOCATION_CANCELLED';

export function validateLocationPolicy(policy) {
  if (!Number.isFinite(policy?.maxAccuracyMeters) || policy.maxAccuracyMeters <= 0
    || !Number.isFinite(policy?.targetAccuracyMeters) || policy.targetAccuracyMeters <= 0
    || policy.targetAccuracyMeters > policy.maxAccuracyMeters
    || !Number.isInteger(policy?.acquisitionTimeoutMs) || policy.acquisitionTimeoutMs < 1_000
    || policy.acquisitionTimeoutMs > 60_000) {
    throw locationError('LOCATION_POLICY', 'Không đọc được cấu hình độ chính xác vị trí. Hãy lấy lại vị trí hoặc liên hệ quản trị viên.');
  }
  return { maxAccuracyMeters: policy.maxAccuracyMeters, targetAccuracyMeters: policy.targetAccuracyMeters, acquisitionTimeoutMs: policy.acquisitionTimeoutMs };
}

// Round upward for display so a result just outside the limit never looks acceptable.
export const formatLocationAccuracy = accuracy => String(Math.ceil(accuracy * 10) / 10);

export const attendanceSessionKey = user => JSON.stringify(user ? {
  id: user.id, username: user.username, staffId: user.staffId, active: user.active, isDeleted: user.isDeleted,
  permissions: Array.isArray(user.permissions) ? [...new Set(user.permissions)].sort() : user.permissions,
  roles: Array.isArray(user.roles)
    ? [...new Set(user.roles.filter(role => role && role.isDeleted !== true).map(role => `${role.id || ''}:${role.name || ''}`))].sort()
    : [...new Set(user.role || [])].sort(),
} : null);

export function hasLocationCoordinates(location) {
  return Number.isFinite(location?.latitude) && location.latitude >= -90 && location.latitude <= 90
    && Number.isFinite(location?.longitude) && location.longitude >= -180 && location.longitude <= 180;
}

export function validateAttendanceLocation(location, now = Date.now()) {
  if (!hasLocationCoordinates(location)) throw locationError('LOCATION_INVALID', 'Tọa độ không hợp lệ. Hãy lấy lại vị trí.');
  if (!Number.isFinite(location.locationAccuracy) || location.locationAccuracy < 0) {
    throw locationError('LOCATION_INVALID', 'Không xác định được độ chính xác vị trí. Hãy thử lại.');
  }
  const capturedAt = typeof location.locationCapturedAt === 'string' ? Date.parse(location.locationCapturedAt) : NaN;
  if (!Number.isFinite(capturedAt) || now - capturedAt > LOCATION_MAX_AGE_MS || capturedAt - now > LOCATION_FUTURE_TOLERANCE_MS) {
    throw locationError('LOCATION_STALE', 'Vị trí đã cũ hoặc thời gian thiết bị chưa đúng. Kiểm tra giờ tự động và lấy lại vị trí.');
  }
  return location;
}

export function getLocationMapUrl(location) {
  return hasLocationCoordinates(location)
    ? `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${location.latitude},${location.longitude}`)}`
    : null;
}

export function requestFreshLocation({
  policy,
  geolocation = globalThis.navigator?.geolocation,
  secureContext = globalThis.isSecureContext,
  now = Date.now,
  signal,
  onSample,
} = {}) {
  return new Promise((resolve, reject) => {
    let config;
    try { config = validateLocationPolicy(policy); } catch (error) { reject(error); return; }
    if (secureContext === false) {
      reject(locationError('LOCATION_INSECURE', 'Định vị cần kết nối HTTPS hoặc localhost. Hãy mở hệ thống bằng địa chỉ phù hợp.'));
      return;
    }
    if (typeof geolocation?.watchPosition !== 'function' || typeof geolocation?.clearWatch !== 'function') {
      reject(locationError('LOCATION_UNSUPPORTED', 'Trình duyệt không hỗ trợ định vị. Hãy dùng trình duyệt có hỗ trợ và bật dịch vụ vị trí.'));
      return;
    }
    let settled = false;
    let watchId;
    let timer;
    let samples = [];
    let best;
    let newestTimestamp = -Infinity;
    let lastError;
    const clearWatch = () => {
      if (watchId !== undefined) {
        geolocation.clearWatch(watchId);
        watchId = undefined;
      }
    };
    const finish = (callback, value) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      clearWatch();
      signal?.removeEventListener('abort', cancel);
      callback(value);
    };
    const cancel = () => finish(reject, locationError('LOCATION_CANCELLED', 'Đã hủy lấy vị trí.'));
    if (signal?.aborted) { cancel(); return; }
    signal?.addEventListener('abort', cancel, { once: true });
    const currentBest = () => {
      samples = samples.filter(sample => {
        try { validateAttendanceLocation(sample, now()); return true; } catch { return false; }
      });
      return samples.reduce((chosen, sample) => !chosen || sample.locationAccuracy < chosen.locationAccuracy
        || (sample.locationAccuracy === chosen.locationAccuracy && sample.locationCapturedAt > chosen.locationCapturedAt)
        ? sample : chosen, null);
    };
    timer = setTimeout(() => {
      best = currentBest();
      if (best && best.locationAccuracy <= config.maxAccuracyMeters) { finish(resolve, best); return; }
      if (best) {
        finish(reject, locationError('LOCATION_INACCURATE', `Sai số vị trí tốt nhất là ±${formatLocationAccuracy(best.locationAccuracy)} m, vượt mức cho phép ${formatLocationAccuracy(config.maxAccuracyMeters)} m. Bật vị trí chính xác, di chuyển đến nơi thoáng hơn và lấy lại vị trí.`));
        return;
      }
      finish(reject, lastError || locationError('LOCATION_UNAVAILABLE', 'Lấy vị trí quá thời gian. Bật dịch vụ vị trí, kiểm tra kết nối mạng và lấy lại vị trí.'));
    }, config.acquisitionTimeoutMs);
    try {
      watchId = geolocation.watchPosition((position) => {
        if (settled) return;
        try {
          if (!Number.isFinite(position?.timestamp)) throw locationError('LOCATION_INVALID', 'Thiếu thời điểm lấy vị trí. Hãy thử lại.');
          const sample = validateAttendanceLocation({
            latitude: position.coords?.latitude,
            longitude: position.coords?.longitude,
            locationAccuracy: position.coords?.accuracy,
            locationCapturedAt: new Date(position.timestamp).toISOString(),
          }, now());
          // A delayed callback must not replace a measurement from a later fix.
          if (position.timestamp < newestTimestamp) return;
          newestTimestamp = position.timestamp;
          samples.push(sample);
          const updatedBest = currentBest();
          if (updatedBest !== best) {
            best = updatedBest;
            onSample?.(best);
          }
          if (settled) return;
          if (best && best.locationAccuracy <= config.targetAccuracyMeters) finish(resolve, best);
        } catch (error) { lastError = error; }
      }, (error) => {
        if (settled) return;
        const message = error?.code === 1
          ? 'Quyền vị trí đang bị chặn. Cho phép Vị trí trong cài đặt trang web và bật dịch vụ vị trí trên thiết bị, rồi thử lại.'
          : error?.code === 3
            ? 'Lấy vị trí quá thời gian. Kiểm tra dịch vụ vị trí, kết nối mạng và thử lại.'
            : 'Không xác định được vị trí. Bật dịch vụ vị trí trên thiết bị và thử lại ở nơi có tín hiệu tốt hơn.';
        lastError = locationError('LOCATION_UNAVAILABLE', message);
        // A poor first fix or a transient provider timeout can improve before our deadline.
        if (error?.code === 1) finish(reject, lastError);
      }, { ...LOCATION_OPTIONS, timeout: config.acquisitionTimeoutMs });
      // Test doubles and some providers may invoke the callback before returning watch ID 0.
      if (settled) clearWatch();
    } catch {
      finish(reject, locationError('LOCATION_UNAVAILABLE', 'Không thể truy cập vị trí. Kiểm tra quyền vị trí của trình duyệt rồi thử lại.'));
    }
  });
}

function requestPolicy(loadPolicy, signal) {
  return new Promise((resolve, reject) => {
    const cancel = () => reject(locationError('LOCATION_CANCELLED', 'Đã hủy lấy vị trí.'));
    if (signal.aborted) { cancel(); return; }
    signal.addEventListener('abort', cancel, { once: true });
    Promise.resolve().then(() => {
      if (signal.aborted) throw locationError('LOCATION_CANCELLED', 'Đã hủy lấy vị trí.');
      return loadPolicy({ signal });
    }).then(validateLocationPolicy).then(resolve, error => {
      reject(isLocationCancelled(error) || signal.aborted
        ? locationError('LOCATION_CANCELLED', 'Đã hủy lấy vị trí.')
        : locationError('LOCATION_POLICY', 'Không tải được cấu hình độ chính xác vị trí. Kiểm tra kết nối và nhấn Lấy lại vị trí.'));
    }).finally(() => signal.removeEventListener('abort', cancel));
  });
}

/** Cancels obsolete callbacks when a dialog closes or a newer request replaces one. */
export function createLocationRequestManager() {
  let controller;
  return {
    async request({ loadPolicy, onPolicy, ...options }) {
      controller?.abort();
      controller = new AbortController();
      const { signal } = controller;
      const policy = await requestPolicy(loadPolicy, signal);
      if (signal.aborted) throw locationError('LOCATION_CANCELLED', 'Đã hủy lấy vị trí.');
      onPolicy?.(policy);
      return requestFreshLocation({ ...options, policy, signal });
    },
    cancel() { controller?.abort(); controller = undefined; },
  };
}
