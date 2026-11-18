/**
 * Cách nhóm menu trên sidebar (giao diện dạng nhóm có tiêu đề).
 * Chỉ sắp xếp lại các trang đã có trong navigationConfig: đường dẫn, quyền truy cập
 * và route vẫn lấy từ navigationConfig, ở đây chỉ quy định nhóm, tên hiển thị và icon.
 */
export const sidebarGroups = [
  {
    title: "Cá nhân & Tổng quan",
    items: [
      { path: "/dashboard", label: "Dashboard", icon: "assessment" },
      { path: "/announcement/announcement", label: "Thông báo", icon: "campaign" },
    ],
  },
  {
    title: "Tổ chức & Nhân sự",
    items: [
      { path: "/staff/all", label: "Hồ sơ nhân viên", icon: "people" },
      { path: "/recruitments", label: "Tin tuyển dụng", icon: "vertical_split" },
      { path: "/department", label: "Phòng ban", icon: "account_tree" },
      { path: "/category/staff/position", label: "Quản lý vị trí", icon: "account_tree" },
    ],
  },
  {
    title: "Chấm công & Nghỉ phép",
    items: [
      { path: "/time-sheet-detail", label: "Chi tiết chấm công", icon: "access_time" },
      { path: "/check-inout-result", label: "Phê duyệt chấm công", icon: "access_time" },
      { path: "/time-sheet-summary", label: "Thống kê công", icon: "assessment" },
      { path: "/my-leave", label: "Yêu cầu nghỉ phép", icon: "event" },
      { path: "/leave-requests", label: "Phê duyệt nghỉ phép", icon: "event" },
      { path: "/leave-balance", label: "Số dư phép năm", icon: "event" },
    ],
  },
  {
    title: "Lương thưởng",
    items: [
      { path: "/salary/payrolls", label: "Bảng lương", icon: "attach_money" },
      { path: "/salary/salary-item", label: "Khoản lương", icon: "attach_money" },
      { path: "/salary/salary-period", label: "Kỳ lương", icon: "event" },
      { path: "/salary/salary-staff-payslip", label: "Phiếu lương cá nhân", icon: "receipt" },
    ],
  },
  {
    title: "Công việc & Dự án",
    items: [
      { path: "/tasks", label: "Công việc", icon: "work" },
      { path: "/projects", label: "Dự án", icon: "work" },
      { path: "/tasks/board", label: "Bảng Kanban", icon: "work" },
    ],
  },
  {
    title: "Quản trị",
    items: [
      { path: "/administration/accounts", label: "Tài khoản", icon: "person" },
      { path: "/administration/roles", label: "Vai trò", icon: "security" },
      { path: "/administration/permissions", label: "Phân quyền", icon: "settings" },
    ],
  },
];

const flattenLeaves = (items) => items.flatMap((item) => (item.children ? flattenLeaves(item.children) : [item]));

/**
 * Ghép các mục người dùng được phép xem (đã lọc theo quyền) vào nhóm.
 * Mục nào chưa được khai báo nhóm vẫn hiện ở nhóm "Khác" để không bị mất trang.
 */
export const buildSidebarGroups = (allowedNavigations) => {
  const leaves = flattenLeaves(allowedNavigations).filter((item) => item.path);
  const used = new Set();
  const groups = sidebarGroups.map((group) => ({
    title: group.title,
    items: group.items.flatMap((entry) => {
      const nav = leaves.find((item) => item.path === entry.path);
      if (!nav) return [];
      used.add(nav.path);
      return [{ ...nav, label: entry.label, icon: entry.icon }];
    }),
  }));
  const others = leaves.filter((item) => !used.has(item.path)).map((item) => ({ ...item, label: item.name, icon: item.icon || "list_alt" }));
  if (others.length) groups.push({ title: "Khác", items: others });
  return groups.filter((group) => group.items.length);
};

/** Nhóm + tên hiển thị của trang hiện tại (cho breadcrumb). */
export const findSidebarEntry = (pathname) => {
  for (const group of sidebarGroups) {
    const item = group.items.find((entry) => entry.path === pathname);
    if (item) return { group: group.title, label: item.label };
  }
  return null;
};
