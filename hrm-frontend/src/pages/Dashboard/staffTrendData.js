const count = value => Number.isInteger(value) && value >= 0 ? value : null;

export function prepareStaffTrend(staffTrend) {
    if (!Array.isArray(staffTrend)) return [];
    return staffTrend.filter(item => item && typeof item.month === 'string').map(item => ({
        period: item.period,
        month: item.month,
        newHires: count(item.newHires),
        resignations: count(item.resignations),
    })).sort((left, right) => String(left.period || '').localeCompare(String(right.period || '')));
}

export function staffTrendMissingDateNote(metadata) {
    const parts = [];
    if (metadata?.unknownStartDates > 0) parts.push(`${metadata.unknownStartDates} hồ sơ thiếu ngày vào làm`);
    if (metadata?.unknownResignationDates > 0) parts.push(`${metadata.unknownResignationDates} hồ sơ nghỉ việc thiếu ngày nghỉ`);
    return parts.length ? `Chưa thể xếp theo tháng: ${parts.join('; ')}.` : '';
}
