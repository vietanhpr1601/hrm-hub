import { useEffect } from "react";
import { Routes, Route, Navigate, Link, useNavigate } from "react-router-dom";
import 'nprogress/nprogress.css';
import { Toaster } from 'sonner';

import MainLayout from "./layout/MainLayout";
import AuthGuard from "./components/auth/AuthGuard";
import { navigations } from "./navigationConfig";
import StaffList from "./pages/Staff/StaffList";
import UserList from "./pages/User/UserList";
import StaffDetailPage from "./pages/Staff/StaffDetailPage";
import RecruitmentList from "./pages/Recruitment/RecruitmentList";
import RecruitmentDetail from "./pages/Recruitment/RecruitmentDetail";
import TimekeepingCalendar from "./pages/Timekeeping/TimekeepingCalendar";
import TimekeepingSummary from "./pages/Timekeeping/TimekeepingSummary";
import TimesheetApprovalList from "./pages/Timesheet/TimesheetApprovalList";
import useUiStore from "./store/uiStore";
import Loading from "./components/ui/Loading";
import GlobalLoadingHandler from "./components/common/GlobalLoadingHandler";
import AiChatbotWidget from "./components/ui/AiChatbotWidget";
import LoginPage from "./pages/auth/LoginPage";
import { setNavigate } from "./navigation";
import { ROLES } from "./constants/roles";
import SalaryItemPage from "./pages/Salary/SalaryItemPage";
import SalaryPeriodPage from "./pages/Salary/SalaryPeriodPage";
import PayrollListPage from "./pages/Salary/PayrollListPage";
import PayrollDetailPage from "./pages/Salary/PayrollDetailPage";
import StaffPayslipPage from "./pages/Salary/StaffPayslipPage";
import LeaveRequestList from "./pages/Leave/LeaveRequestList";
import MyLeaveRequests from "./pages/Leave/MyLeaveRequests";
import LeaveBalance from "./pages/Leave/LeaveBalance";
import DepartmentPage from "./pages/Department/DepartmentPage";
import PositionPage from "./pages/Position/PositionPage";
import DashboardPage from "./pages/Dashboard/DashboardPage";
import useAuthStore from "./store/useAuthStore";
import { getHomePath } from "./utils/auth.js";
import PermissionManagementPage from './pages/User/PermissionManagementPage';
import RoleManagementPage from './pages/User/RoleManagementPage';
import NotificationPage from './pages/Notification/NotificationPage';
import PermissionGuard from './components/auth/PermissionGuard';
import MyProfilePage from './pages/Profile/MyProfilePage';
import ProjectList from './pages/Project/ProjectList';
import ProjectWorkspace from './pages/Project/ProjectWorkspace';
import MyTasks from './pages/Task/MyTasks';
import TaskList from './pages/Task/TaskList';

const HomeRedirect = () => {
  const user = useAuthStore((state) => state.user);
  return <Navigate to={getHomePath(user)} replace />;
};

const AccessDenied = () => {
  const user = useAuthStore((state) => state.user);
  const homePath = getHomePath(user);
  return (
    <div className="p-8 text-center">
      <h1 className="text-2xl font-semibold">Bạn không có quyền truy cập trang này</h1>
      <p className="mt-3">Vui lòng liên hệ quản trị viên nếu cần được cấp quyền.</p>
      {homePath !== '/access-denied' && <Link className="inline-block mt-4 text-primary underline" to={homePath}>Về trang chính</Link>}
    </div>
  );
};

// Placeholder component for pages
const PagePlaceholder = ({ title }) => (
  <div className="bg-white p-6 rounded-lg shadow-sm border border-gray-100 min-h-[400px] flex flex-col items-center justify-center text-center">
    <div className="mb-4 p-4 rounded-full bg-primary/5 text-primary">
      <h2 className="text-2xl font-semibold uppercase tracking-wider">{title}</h2>
    </div>
    <div className="w-20 h-1 bg-secondary rounded-full mb-6"></div>
    <p className="text-gray-400 italic">Trang này đang được phát triển trong hệ thống HRM mới...</p>
    <p className="text-xs text-gray-300 mt-4">Path: {window.location.pathname}</p>
  </div>
);

// Real LoginPage is imported above

function App() {
  const navigate = useNavigate();
  const accessToken = useAuthStore((state) => state.accessToken);
  const refreshCurrentUser = useAuthStore((state) => state.getCurrentUser);

  useEffect(() => {
    if (!accessToken) return;
    let pending = false;
    const refresh = async () => {
      if (pending || document.visibilityState === 'hidden') return;
      pending = true;
      try { await refreshCurrentUser(); } finally { pending = false; }
    };
    refresh();
    window.addEventListener('focus', refresh);
    document.addEventListener('visibilitychange', refresh);
    return () => {
      window.removeEventListener('focus', refresh);
      document.removeEventListener('visibilitychange', refresh);
    };
  }, [accessToken, refreshCurrentUser]);
  
  useEffect(() => {
    setNavigate(navigate);
  }, [navigate]);

  const showLoading = useUiStore(state => state.showLoading);

  // Hàm làm phẳng danh sách menu để tạo Route tự động
  const flattenNavigations = (items) => {
    let flat = [];
    items.forEach(item => {
      if (item.path && !item.external) {
        flat.push(item);
      }
      if (item.children) {
        flat = [...flat, ...flattenNavigations(item.children)];
      }
    });
    return flat;
  };

  const allRoutes = flattenNavigations(navigations);

  // Map các component đặc biệt
  const componentMap = {
    "/dashboard": <DashboardPage />,
    "/announcement/announcement": <NotificationPage />,
    "/staff/all": <StaffList />,

    "/administration/accounts": <UserList />,
    "/administration/roles": <RoleManagementPage />,
    "/administration/permissions": <PermissionManagementPage />,
    "/recruitments": <RecruitmentList />,
    "/time-sheet-detail": <TimekeepingCalendar />,
    "/check-inout-result": <TimesheetApprovalList />,
    "/time-sheet-summary": <TimekeepingSummary />,
    "/salary/salary-item": <SalaryItemPage />,
    "/salary/salary-period": <SalaryPeriodPage />,
    "/salary/payrolls": <PayrollListPage />,
    "/salary/salary-staff-payslip": <StaffPayslipPage />,
    "/leave-requests": <LeaveRequestList />,
    "/my-leave": <MyLeaveRequests />,
    "/leave-balance": <LeaveBalance />,
    "/department": <DepartmentPage />,
    "/category/staff/position": <PositionPage />,
    "/projects": <ProjectList />,
    "/tasks": <MyTasks />,
    "/tasks/board": <TaskList />,
  };

  return (
    <>
      <Toaster position="bottom-right" richColors closeButton />

      {/* Người quan sát điều khiển thanh NProgress */}
      <GlobalLoadingHandler />
      
      {/* Vòng xoay trung tâm điều khiển bởi showLoading */}
      {showLoading && <Loading fixed />}
      
      {/* Widget Trợ lý AI */}
      <PermissionGuard permissions="AI_CHAT"><AiChatbotWidget /></PermissionGuard>

      <Routes>
        {/* Route công khai (Public) */}
        <Route path="/login" element={<LoginPage />} />

        {/* Routes được bảo vệ (Protected) */}
        <Route element={<AuthGuard><MainLayout /></AuthGuard>}>
          <Route path="/" element={<HomeRedirect />} />
          <Route path="/access-denied" element={<AccessDenied />} />
          <Route path="/profile" element={<MyProfilePage />} />
          
          {/* Tự động tạo Route từ cấu hình Menu */}
          {allRoutes.map((route, index) => (
            <Route 
              key={index} 
              path={route.path} 
              element={
                <AuthGuard roles={route.auth} permissions={route.permissions}>
                  {componentMap[route.path] || <PagePlaceholder title={route.name} />}
                </AuthGuard>
              } 
            />
          ))}

          {/* Route chi tiết nhân viên (Staff Detail/Edit) */}
          <Route 
            path="/staff/:id" 
            element={
              <AuthGuard permissions="STAFF_VIEW" roles={[ROLES.ADMIN, ROLES.HR_MANAGER, ROLES.HR_EMPLOYEE]}>
                <StaffDetailPage />
              </AuthGuard>
            } 
          />

          {/* Route chi tiết tin tuyển dụng - chế độ xem */}
          <Route 
            path="/recruitments/:id/view" 
            element={
              <AuthGuard permissions="RECRUITMENT_VIEW" roles={[ROLES.ADMIN, ROLES.HR_MANAGER, ROLES.HR_RECRUITMENT]}>
                <RecruitmentDetail />
              </AuthGuard>
            } 
          />

          {/* Route chi tiết tin tuyển dụng - chế độ sửa */}
          <Route 
            path="/recruitments/:id/edit" 
            element={
              <AuthGuard permissions="RECRUITMENT_MANAGE" roles={[ROLES.ADMIN, ROLES.HR_MANAGER, ROLES.HR_RECRUITMENT]}>
                <RecruitmentDetail />
              </AuthGuard>
            } 
          />

          {/* Route chi tiết dự án - chế độ xem / sửa */}
          <Route path="/projects/:id/view" element={<AuthGuard roles={Object.values(ROLES)}><ProjectWorkspace /></AuthGuard>} />
          <Route path="/projects/:id/edit" element={<AuthGuard roles={Object.values(ROLES)}><ProjectWorkspace /></AuthGuard>} />

          {/* Route chi tiết bảng lương */}
          <Route 
            path="/salary/payrolls/:id" 
            element={
              <AuthGuard permissions="PAYROLL_VIEW" roles={[ROLES.ADMIN, ROLES.HR_COMPENSATION_BENEFIT]}>
                <PayrollDetailPage />
              </AuthGuard>
            } 
          />

          {/* Fallback */}
          <Route path="*" element={<HomeRedirect />} />
        </Route>
      </Routes>
    </>
  );
}

export default App;
