export const currentFormula = item => ['FIXED', 'BY_STANDARD_DAYS'].includes(item?.calculationType);

export const buildSalaryRows = (definitions, configured) => {
  const selected = new Map(configured.map(row => [row.salaryItem?.id, row]));
  const available = new Map(definitions.filter(item => !item.isDeleted).map(item => [item.id, item]));
  // Retain old entries visibly so opening/saving the table cannot silently drop them.
  configured.forEach(row => { if (row.salaryItem && !available.has(row.salaryItem.id)) available.set(row.salaryItem.id, row.salaryItem); });
  return [...available.values()].sort((a, b) => a.name.localeCompare(b.name, 'vi')).map(item => ({
    salaryItem: item, enabled: selected.has(item.id), amount: selected.has(item.id) ? String(selected.get(item.id).amount ?? '') : '',
  }));
};

/** Khoản có số tiền lớn hơn 0 là khoản đang áp dụng; để trống hoặc 0 nghĩa là không áp dụng. */
export const hasSalaryAmount = row => String(row.amount ?? '').trim() !== '' && Number(row.amount) > 0;

export const salaryRowsPayload = rows => rows.filter(row => String(row.amount ?? '').trim() !== '').map(row => {
  const amount = Number(row.amount);
  if (!Number.isFinite(amount) || amount < 0) throw new Error(`Nhập số tiền không âm cho khoản ${row.salaryItem.name}.`);
  return { row, amount };
}).filter(({ amount }) => amount > 0).map(({ row, amount }) => {
  if (!currentFormula(row.salaryItem)) throw new Error(`Khoản ${row.salaryItem.name} dùng cách tính cũ. Hãy cập nhật danh mục hoặc xóa số tiền của khoản này.`);
  return { salaryItem: { id: row.salaryItem.id }, amount };
});
