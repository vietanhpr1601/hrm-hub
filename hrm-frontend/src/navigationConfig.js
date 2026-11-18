import { ROLES } from "./constants/roles.js";

const {
  ADMIN,
  HR_MANAGER,
  HR_EMPLOYEE,
  HR_RECRUITMENT,
  HR_COMPENSATION_BENEFIT,
  HR_TIMEKEEPING_MANAGER,
} = ROLES;

const ALL_ROLES = Object.values(ROLES);

const navigationItems = [
  {
    name: "Bảng điều khiển",
    path: "/dashboard",
    icon: "assessment",
    auth: [ADMIN]
  },
  {
    name: "Trang chủ",
    icon: "home",
    auth: ALL_ROLES,
    children: [
      // { name: "Đánh giá", path: "/profile-page/available-evaluations", auth: [ADMIN, HR_MANAGER, HR_EMPLOYEE] },
      // { name: "Email", path: "/email", auth: [ADMIN] },
      // { name: "Diễn đàn", path: "/forum/forum-group", auth: [ADMIN] },
      { name: "Thông báo", path: "/announcement/announcement" },
    ]
  },
  {
    name: "Cơ cấu tổ chức",
    icon: "account_tree",
    auth: [ADMIN, HR_MANAGER],
    children: [
      // { name: "Sơ đồ tổ chức", path: "/organization/diagram", auth: [ADMIN, HR_MANAGER, HR_EMPLOYEE] },
      // { name: "Cây tổ chức", path: "/organization/tree", auth: [ADMIN, HR_MANAGER, HR_EMPLOYEE] },
      { name: "Phòng ban", path: "/department", auth: [ADMIN] },
      { name: "Quản lý vị trí", path: "/category/staff/position", auth: [ADMIN] },
      // { name: "Nhóm ngạch", path: "/organization/group-position-title", auth: [ADMIN, HR_MANAGER] },
      // { name: "Ngạch lương", path: "/organization/rank-title", auth: [ADMIN, HR_MANAGER] },
      // { name: "Chức danh", path: "/organization/position-title", auth: [ADMIN, HR_MANAGER] },
    ],
  },
  {
    name: "Tuyển dụng",
    icon: "vertical_split",
    auth: [ADMIN, HR_MANAGER, HR_RECRUITMENT],
    children: [
      { name: "Tin tuyển dụng", path: "/recruitments", auth: [ADMIN, HR_MANAGER, HR_RECRUITMENT] },
    ],
  },
  {
    name: "Nhân viên",
    icon: "people",
    auth: [ADMIN, HR_MANAGER, HR_EMPLOYEE],
    children: [
      { name: "Hồ sơ nhân viên", path: "/staff/all", auth: [ADMIN, HR_MANAGER, HR_EMPLOYEE] },
      // { name: "Phiếu đánh giá", path: "/staff-evaluation-ticket", auth: [ADMIN, HR_MANAGER, HR_EMPLOYEE] },
      // { name: "Chứng chỉ nhân viên", path: "/staff-certificate", auth: [ADMIN, HR_MANAGER] },
      // {
      //   name: "Danh mục nhân sự",
      //   auth: [ADMIN, HR_MANAGER],
      //   children: [
      //     { name: "Loại nhân viên", path: "/category/staff/staff-type" },
      //     { name: "Khen thưởng/Kỷ luật", path: "/category/staff/reward" },
      //     { name: "Lý do nghỉ việc", path: "/category/staff/leaving-job-reason" },
      //     { name: "Loại hợp đồng", path: "/category/staff/contract-type" },
      //     { name: "Quan hệ gia đình", path: "/category/familyRelationship" },
      //   ]
      // }
    ],
  },
  {
    name: "Chấm công",
    icon: "access_time",
    auth: [ADMIN, HR_MANAGER, HR_TIMEKEEPING_MANAGER, HR_EMPLOYEE],
    children: [
      { name: "Chi tiết chấm công", path: "/time-sheet-detail", auth: [ADMIN, HR_MANAGER, HR_EMPLOYEE] },
      // { name: "Ca làm việc", path: "/category/shift-work", auth: [ADMIN, HR_MANAGER, HR_TIMEKEEPING_MANAGER] },
      // { name: "Phân công lịch trực", path: "/staff-work-schedule", auth: [ADMIN, HR_MANAGER] },
      // { name: "Bảng phân ca", path: "/work-schedule-calendar", auth: [ADMIN, HR_MANAGER, HR_EMPLOYEE] },
      // { name: "Thiết bị chấm công", path: "/timekeeping-device", auth: [ADMIN, HR_MANAGER] },
      // { name: "Yêu cầu làm thêm", path: "/category/overtime-request", auth: [ADMIN, HR_MANAGER, HR_EMPLOYEE] },
      // { name: "Xác nhận lịch trực", path: "/category/confirm-staff-work-schedule", auth: [ADMIN, HR_MANAGER] },
      { name: "Phê duyệt chấm công", path: "/check-inout-result", auth: [ADMIN, HR_MANAGER] },
      { name: "Thống kê công", path: "/time-sheet-summary", auth: [ADMIN, HR_MANAGER, HR_TIMEKEEPING_MANAGER] },
    ],
  },
  {
    name: "Nghỉ phép",
    icon: "event",
    auth: ALL_ROLES,
    children: [
      { name: "Yêu cầu nghỉ phép", path: "/my-leave", auth: ALL_ROLES },
      { name: "Phê duyệt nghỉ phép", path: "/leave-requests", auth: [ADMIN, HR_MANAGER] },
      { name: "Số dư phép năm", path: "/leave-balance", auth: [ADMIN, HR_MANAGER] },
    ],
  },
  // {
  //   name: "KPI",
  //   icon: "assessment",
  //   auth: [ADMIN, HR_MANAGER, HR_COMPENSATION_BENEFIT, HR_EMPLOYEE],
  //   children: [
  //     { name: "Thành phần KPI", path: "/hr-kpi/hr-kpi-item", auth: [ADMIN, HR_MANAGER] },
  //     { name: "Mẫu KPI", path: "/hr-kpi/hr-kpi-template", auth: [ADMIN, HR_MANAGER] },
  //     { name: "Kỳ đánh giá KPI", path: "/hr-kpi/hr-kpi-evaluation-period", auth: [ADMIN, HR_MANAGER] },
  //     { name: "Đăng ký chỉ tiêu KPI", path: "/hr-kpi/hr-kpi-target-register", auth: [ADMIN, HR_EMPLOYEE] },
  //     { name: "Kết quả KPI", path: "/hr-kpi/hr-kpi-result", auth: [ADMIN, HR_EMPLOYEE] },
  //   ]
  // },
  {
    name: "Lương thưởng",
    icon: "attach_money",
    auth: [ADMIN, HR_MANAGER, HR_COMPENSATION_BENEFIT, HR_EMPLOYEE],
    children: [
      { name: "Khoản lương", path: "/salary/salary-item", auth: [ADMIN, HR_MANAGER, HR_COMPENSATION_BENEFIT] },
      { name: "Kỳ lương", path: "/salary/salary-period", auth: [ADMIN, HR_MANAGER, HR_COMPENSATION_BENEFIT] },
      { name: "Bảng lương", path: "/salary/payrolls", auth: [ADMIN, HR_COMPENSATION_BENEFIT] },
      { name: "Phiếu lương cá nhân", path: "/salary/salary-staff-payslip", auth: [HR_EMPLOYEE] },
    ],
  },
  {
    name: "Công việc & Dự án",
    icon: "work",
    auth: ALL_ROLES,
    children: [
      { name: "Dự án", path: "/projects", auth: ALL_ROLES },
      { name: "Công việc của tôi", path: "/tasks", auth: ALL_ROLES },
      { name: "Bảng Kanban", path: "/tasks/board", auth: ALL_ROLES },
    ],
  },
  // {
  //   name: "Bảo hiểm",
  //   icon: "security",
  //   auth: [ADMIN, HR_MANAGER, HR_COMPENSATION_BENEFIT],
  //   children: [
  //     { name: "BHXH nhân viên", path: "/insurance/staff-social-insurance", auth: [ADMIN, HR_MANAGER, HR_COMPENSATION_BENEFIT] },
  //     // { name: "Gói bảo hiểm", path: "/insurance-package", auth: [ADMIN, HR_MANAGER] },
  //   ],
  // },
  // {
  //   name: "Pháp chế",
  //   icon: "gavel",
  //   auth: [ADMIN, HR_MANAGER],
  //   children: [
  //     { name: "Sổ quản lý lao động", path: "/staff-labour-management-book", auth: [ADMIN, HR_MANAGER] },
  //   ]
  // },
  // {
  //   name: "Quy trình (Flowable)",
  //   icon: "account_tree",
  //   auth: [ADMIN, HR_MANAGER],
  //   children: [
  //     { name: "Quy trình mẫu", path: "/profile-page/process-definitions" },
  //     { name: "Công việc quy trình", path: "/profile-page/flowable-my-work" },
  //     { name: "Danh mục quy trình", path: "/profile-page/process-categories" },
  //   ]
  // },
  // {
  //   name: "Quản lý văn bản",
  //   icon: "edit_document",
  //   auth: [ADMIN, HR_MANAGER],
  //   children: [
  //     { name: "Văn bản đi", path: "/document/outgoing-document" },
  //     { name: "Văn bản đến", path: "/document/incoming-document" },
  //     { name: "Sổ văn bản", path: "/document/document-book" },
  //     { name: "Loại văn bản", path: "/document/document-type" },
  //   ]
  // },
  // {
  //   name: "Ngân sách",
  //   icon: "wallet",
  //   auth: [ADMIN],
  //   children: [
  //     { name: "Quản lý ngân sách", path: "/budget/budget" },
  //     { name: "Danh mục ngân sách", path: "/budget/budget-category" },
  //     { name: "Chứng từ", path: "/budget/voucher" },
  //     { name: "Báo cáo ngân sách", path: "/budget/report" },
  //   ]
  // },
  // {
  //   name: "Báo cáo",
  //   icon: "assessment",
  //   auth: [ADMIN, HR_MANAGER, HR_RECRUITMENT],
  //   children: [
  //     { name: "BC Định biên nhân sự", path: "/report/hr-resource-plan-report", auth: [ADMIN, HR_MANAGER] },
  //     { name: "BC Yêu cầu tuyển dụng", path: "/report/recruitment-request-report", auth: [ADMIN, HR_RECRUITMENT] },
  //     { name: "BC Tiếp nhận ứng viên", path: "/export-candidate-report", auth: [ADMIN, HR_RECRUITMENT] },
  //     { name: "BC Sử dụng lao động", path: "/report/staff-labour-util-report", auth: [ADMIN] },
  //   ],
  // },
  {
    name: "Quản trị",
    icon: "settings",
    auth: [ADMIN],
    children: [
      { name: "Tài khoản", path: "/administration/accounts", auth: [ADMIN] },
      { name: "Vai trò", path: "/administration/roles", auth: [ADMIN] },
      { name: "Phân quyền", path: "/administration/permissions", auth: [ADMIN] },
      // { name: "Đơn vị hành chính", path: "/category/administrative-unit", auth: [ADMIN] },
      // { name: "Cấu hình hệ thống", path: "/category/system-config", auth: [ADMIN] },
    ],
  },
  // { name: "Chat nội bộ", path: "https://realtimechat.duyhanh.site", icon: "chat", external: true, auth: ALL_ROLES },
];

const routePermissions = {
  '/dashboard': 'DASHBOARD_VIEW',
  '/announcement/announcement': 'NOTIFICATION_VIEW',
  '/department': 'DEPARTMENT_VIEW', '/category/staff/position': 'POSITION_VIEW',
  '/staff/all': 'STAFF_VIEW', '/recruitments': 'RECRUITMENT_VIEW',
  '/time-sheet-detail': 'ATTENDANCE_SELF', '/check-inout-result': ['ATTENDANCE_VIEW', 'ATTENDANCE_APPROVE'],
  '/time-sheet-summary': 'ATTENDANCE_VIEW', '/my-leave': 'LEAVE_SELF',
  '/leave-requests': ['LEAVE_VIEW', 'LEAVE_APPROVE'], '/leave-balance': 'LEAVE_VIEW',
  '/salary/salary-item': 'SALARY_CONFIG_VIEW', '/salary/salary-period': 'PERIOD_VIEW',
  '/salary/payrolls': 'PAYROLL_VIEW', '/salary/salary-staff-payslip': 'PAYROLL_SELF',
};
const withPermissions = (items) => items.map((item) => ({
  ...item,
  ...(routePermissions[item.path] ? { permissions: routePermissions[item.path] } : {}),
  ...(item.children ? { children: withPermissions(item.children) } : {}),
}));
export const navigations = withPermissions(navigationItems);

export const getBreadcrumbByPath = (pathname) => {
  if (pathname === "/" || pathname === "/dashboard") {
    return [{ name: "Trang chủ", path: "/dashboard" }];
  }

  const findPathInMenu = (items, currentPath) => {
    for (const item of items) {
      // Trường hợp khớp chính xác path
      if (item.path && item.path === currentPath) {
        return [{ name: item.name, path: item.path }];
      }
      
      // Trường hợp có con, tìm kiếm đệ quy
      if (item.children && item.children.length > 0) {
        const found = findPathInMenu(item.children, currentPath);
        if (found) {
          // Nếu tìm thấy trong con, trả về [Cha, ...Con]
          return [{ name: item.name, path: item.path || null }, ...found];
        }
      }
    }
    return null;
  };

  const result = findPathInMenu(navigations, pathname);
  if (result) {
    return result;
  }

  return [];
};
