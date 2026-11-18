export const batchPreviewRows = (page, statusField, pendingStatus) => {
  if (!Array.isArray(page?.content)) throw new Error('Không lấy được danh sách để duyệt.');
  if (page.totalElements > 500) throw new Error('Có hơn 500 bản ghi chờ duyệt. Hãy thu hẹp bộ lọc.');
  if (page.totalElements !== page.content.length) throw new Error('Danh sách chưa đầy đủ. Hãy tải lại hoặc thu hẹp bộ lọc.');
  if (page.content.some(row => !row.id || row[statusField] !== pendingStatus)
      || new Set(page.content.map(row => row.id)).size !== page.content.length)
    throw new Error('Danh sách không đúng trạng thái chờ duyệt. Hãy tải lại.');
  return page.content;
};
