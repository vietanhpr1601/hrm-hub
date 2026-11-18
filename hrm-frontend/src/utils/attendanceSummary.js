const finite = (value) => typeof value === 'number' && Number.isFinite(value);
const validTime = (value) => typeof value === 'string' && Number.isFinite(Date.parse(value));

export function summarizeAttendance(timesheet = {}, logs = []) {
  const details = timesheet.details || [];
  const byTime = (a, b) => Date.parse(a) - Date.parse(b);
  const ins = [...details.map(item => item.checkInTime), ...logs.filter(item => item.recordType === 'CHECK_IN').map(item => item.recordTime)].filter(validTime).sort(byTime);
  const outs = [...details.map(item => item.checkOutTime), ...logs.filter(item => item.recordType === 'CHECK_OUT').map(item => item.recordTime)].filter(validTime).sort(byTime);
  const sumMinutes = (key) => details.length ? details.reduce((sum, item) => sum + (finite(item[key]) ? item[key] : 0), 0) : null;
  return {
    checkIn: ins[0] || null,
    checkOut: outs.at(-1) || null,
    standardHours: finite(timesheet.standardHours) ? timesheet.standardHours : null,
    overtimeHours: finite(timesheet.overtimeHours) ? timesheet.overtimeHours : null,
    totalHours: finite(timesheet.standardHours) && finite(timesheet.overtimeHours) ? timesheet.standardHours + timesheet.overtimeHours : null,
    lateMinutes: sumMinutes('lateMinutes'),
    earlyMinutes: sumMinutes('earlyMinutes'),
  };
}

export function workplaceDistanceLabel(log = {}) {
  if (finite(log.distanceMeters) && log.distanceMeters >= 0) {
    return log.distanceMeters < 1000 ? `${Math.round(log.distanceMeters)} m` : `${(log.distanceMeters / 1000).toFixed(2)} km`;
  }
  return log.workplaceName ? 'Chưa xác định' : 'Chưa cấu hình';
}
