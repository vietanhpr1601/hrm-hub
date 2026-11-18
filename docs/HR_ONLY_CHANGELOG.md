# Chuyển ứng dụng sang HR-only — 29/09/2026

Đã gỡ Project/Task/Kanban khỏi source frontend, backend và bản build đang chạy. Đã tiếp tục từ trạng thái đang làm; không dựng lại ứng dụng, không nâng phiên bản công nghệ, không xóa dữ liệu PostgreSQL.

## 1. Phạm vi và cách xử lý dependency

- Xóa 19 file frontend chỉ phục vụ dự án/công việc: page, component, store, service, hook quyền và constant. Bỏ menu, import, route và chuỗi dịch liên quan.
- Xóa 44 file backend chỉ phục vụ module: controller, service, repository, DTO, 7 entity, ProjectRole và ProjectUtils.
- Dashboard là điểm dùng chung: giữ nhân sự, phân bố phòng ban, công chờ duyệt, nghỉ phép và phễu ứng viên; bỏ KPI, cảnh báo, biểu đồ và hoạt động dự án. Không thêm số liệu giả; sparkline chỉ dùng dữ liệu nhân sự thực tế.
- Giữ các component dùng chung, Cloudinary cho nghiệp vụ HR, Staff/User/Role, authentication và phân quyền HR. Staff/User không có quan hệ ngược cần sửa khi bỏ module.
- Đổi tên helper trong RecruitmentDetail vốn chỉ tải tin tuyển dụng để bỏ tên Project gây nhầm lẫn; không đổi nghiệp vụ tuyển dụng.
- Sửa handler NoResourceFoundException trả HTTP 404 thay vì bị handler chung chuyển thành 500 khi truy cập API đã gỡ.
- Thay hai test luồng Project/Task bằng kiểm chứng phạm vi HR-only; giữ các test HR và bổ sung kiểm tra CRUD/trạng thái/vai trò tài khoản.

## 2. Công nghệ và cấu hình được giữ nguyên

Máy chạy Java 17.0.20.1, Spring Boot 4.0.5, Node 24.21.0, npm 11.19.0 và PostgreSQL hiện có. SHA-256 xác nhận các file sau giữ nguyên so với trước nhiệm vụ:

- `build.cmd`
- `hrm-backend/pom.xml`
- `hrm-backend/src/main/java/hrm/config/WebSecurityConfig.java`
- `hrm-backend/src/main/resources/application.yaml`
- `hrm-frontend/package-lock.json`
- `hrm-frontend/package.json`
- `hrm-frontend/src/constants/roles.js`
- `scripts/local.mjs`
- `start.cmd`
- `status.cmd`
- `stop.cmd`

Không đổi package Java, tên database, driver, framework, dependency manifest/lockfile hoặc script khởi động. Thương hiệu đang hiển thị HRM và tiêu đề Hệ thống quản lý nhân sự, nên không cần đổi tên ứng dụng hoặc database.

## 3. Route và menu frontend đã bỏ

- Nhóm menu Công việc & Dự án và hai mục Công việc, Dự án.
- `/projects`, `/projects/:id/view`, `/projects/:id/edit`, `/tasks`.
- Kanban nằm trong trang Công việc, không có route riêng trong source trước khi gỡ.
- Các liên kết dự án trên KPI/biểu đồ Dashboard và quyền cấp dự án trong useProjectPermission.
- Truy cập URL cũ dùng nhánh fallback sẵn có về trang chính theo vai trò; không còn render màn hình đã gỡ.

## 4. API đã bỏ

Toàn bộ 5 controller của module đã bị xóa, gồm 34 ánh xạ phương thức HTTP dưới các đường dẫn sau. Không tạo endpoint thay thế cho module này. Request có xác thực đến API đã gỡ nhận 404.

| Phương thức | Đường dẫn cũ |
| --- | --- |
| GET, POST | `/api/projects` |
| POST | `/api/projects/paging` |
| GET, PUT, DELETE | `/api/projects/{id}` |
| PUT | `/api/projects/{id}/finish` |
| PUT | `/api/projects/{id}/unfinish` |
| GET | `/api/projects/{id}/get-staffs` |
| GET, POST | `/api/projects/{projectId}/activities` |
| PUT, DELETE | `/api/projects/{projectId}/activities/{id}` |
| PUT | `/api/projects/{projectId}/activities/reorder` |
| GET, POST | `/api/projects/{projectId}/working-statuses` |
| PUT, DELETE | `/api/projects/{projectId}/working-statuses/{id}` |
| PUT | `/api/projects/{projectId}/working-statuses/reorder` |
| POST | `/api/tasks` |
| POST | `/api/tasks/paging` |
| POST | `/api/tasks/kanban` |
| POST | `/api/tasks/my-tasks` |
| POST | `/api/tasks/project/{projectId}/count-by-status` |
| GET, PUT, DELETE | `/api/tasks/{id}` |
| PUT | `/api/tasks/{id}/status` |
| POST | `/api/tasks/{id}/attachments` |
| POST | `/api/tasks/{id}/attachments/link` |
| GET | `/api/tasks/{taskId}/history` |
| POST | `/api/tasks/{taskId}/comments` |
| PUT, DELETE | `/api/tasks/comments/{id}` |

## 5. Entity và database

Bảy entity đã bỏ: `Project`, `ProjectStaff`, `ProjectActivity`, `ProjectWorkingStatus`, `Task`, `TaskHistory`, `TaskAttachment`.

Không có Flyway/Liquibase; giữ nguyên Hibernate ddl-auto=update và không tạo migration DROP. Các bảng `tbl_project`, `tbl_project_staff`, `tbl_project_activity`, `tbl_project_working_status`, `tbl_task`, `tbl_task_history`, `tbl_task_attachment` còn nguyên để người dùng quyết định dọn sau. Nhóm bảng legacy và tệp dùng chung cũng được giữ nguyên.

Đối chiếu PostgreSQL chỉ đọc trước/sau: 43 bảng không đổi số dòng; 60 khóa ngoại không đổi; nội dung 16 bảng nghiệp vụ HR giữ nguyên qua dấu kiểm từng dòng. Xem [rà soát database](HR_ONLY_DATABASE_REVIEW.md) để biết quan hệ và thứ tự cần xét nếu có nhiệm vụ dọn bảng riêng.

## 6. Nghiệp vụ HR còn lại

- Đăng nhập/đăng xuất, tài khoản, gán vai trò, khóa/mở khóa.
- Hồ sơ nhân viên, phòng ban, vị trí/chức vụ.
- Check-in/check-out, lịch sử/thống kê/phê duyệt chấm công.
- Yêu cầu nghỉ phép, phê duyệt/từ chối, số dư phép năm.
- Kỳ lương, khoản lương, cấu hình lương nhân viên, bảng lương, phiếu lương, chốt và trạng thái thanh toán.
- Tin tuyển dụng, ứng viên, cập nhật trạng thái và tiếp nhận thành nhân viên.
- Dashboard HR, chatbot tra cứu và tùy chọn giao diện hiện có.

Không tự mở rộng quyền Employee/Manager/HR chuyên trách hoặc bổ sung trang HR trước đây chỉ là placeholder.

## 7. Kết quả và cách chạy

Backend clean compile và verify thành công (52 test); frontend build thành công, 19 test đạt, lint 0 lỗi. Spring Boot chạy được bằng Maven và JAR. API đạt 32/32, kiểm thử Chrome đạt 37/37; không lỗi runtime/import hoặc request HTTP nội bộ thất bại. Còn cảnh báo lint, React prop/defaultProps và kích thước bundle; chi tiết/phạm vi ở [báo cáo kiểm thử](HR_ONLY_TEST_REPORT.md).

Ứng dụng đang chạy tại http://127.0.0.1:5173, backend 8080. Lần chạy tiếp theo:

```powershell
cd D:\Project\Hrm
.\start.cmd
.\status.cmd
```

Artifact đã cập nhật: `release/hrm-backend.jar` và `release/hrm-frontend.zip`.

## 8. Danh sách file đã xóa (63)

- `hrm-backend/src/main/java/hrm/controller/ProjectActivityController.java`
- `hrm-backend/src/main/java/hrm/controller/ProjectController.java`
- `hrm-backend/src/main/java/hrm/controller/ProjectWorkingStatusController.java`
- `hrm-backend/src/main/java/hrm/controller/TaskController.java`
- `hrm-backend/src/main/java/hrm/controller/TaskHistoryController.java`
- `hrm-backend/src/main/java/hrm/dto/request/ProjectActivityRequest.java`
- `hrm-backend/src/main/java/hrm/dto/request/ProjectCreateRequest.java`
- `hrm-backend/src/main/java/hrm/dto/request/ProjectStaffRequest.java`
- `hrm-backend/src/main/java/hrm/dto/request/TaskRequest.java`
- `hrm-backend/src/main/java/hrm/dto/response/ProjectActivityResponse.java`
- `hrm-backend/src/main/java/hrm/dto/response/ProjectResponse.java`
- `hrm-backend/src/main/java/hrm/dto/response/ProjectStaffDto.java`
- `hrm-backend/src/main/java/hrm/dto/response/ProjectWorkingStatusResponse.java`
- `hrm-backend/src/main/java/hrm/dto/response/TaskAttachmentResponse.java`
- `hrm-backend/src/main/java/hrm/dto/response/TaskHistoryResponse.java`
- `hrm-backend/src/main/java/hrm/dto/response/TaskResponse.java`
- `hrm-backend/src/main/java/hrm/dto/search/ProjectSearchRequest.java`
- `hrm-backend/src/main/java/hrm/dto/search/TaskSearchRequest.java`
- `hrm-backend/src/main/java/hrm/enums/ProjectRole.java`
- `hrm-backend/src/main/java/hrm/model/Project.java`
- `hrm-backend/src/main/java/hrm/model/ProjectActivity.java`
- `hrm-backend/src/main/java/hrm/model/ProjectStaff.java`
- `hrm-backend/src/main/java/hrm/model/ProjectWorkingStatus.java`
- `hrm-backend/src/main/java/hrm/model/Task.java`
- `hrm-backend/src/main/java/hrm/model/TaskAttachment.java`
- `hrm-backend/src/main/java/hrm/model/TaskHistory.java`
- `hrm-backend/src/main/java/hrm/repository/ProjectActivityRepository.java`
- `hrm-backend/src/main/java/hrm/repository/ProjectRepository.java`
- `hrm-backend/src/main/java/hrm/repository/ProjectStaffRepository.java`
- `hrm-backend/src/main/java/hrm/repository/ProjectWorkingStatusRepository.java`
- `hrm-backend/src/main/java/hrm/repository/TaskAttachmentRepository.java`
- `hrm-backend/src/main/java/hrm/repository/TaskHistoryRepository.java`
- `hrm-backend/src/main/java/hrm/repository/TaskRepository.java`
- `hrm-backend/src/main/java/hrm/service/ProjectActivityService.java`
- `hrm-backend/src/main/java/hrm/service/ProjectService.java`
- `hrm-backend/src/main/java/hrm/service/ProjectWorkingStatusService.java`
- `hrm-backend/src/main/java/hrm/service/TaskHistoryService.java`
- `hrm-backend/src/main/java/hrm/service/TaskService.java`
- `hrm-backend/src/main/java/hrm/service/impl/ProjectActivityServiceImpl.java`
- `hrm-backend/src/main/java/hrm/service/impl/ProjectServiceImpl.java`
- `hrm-backend/src/main/java/hrm/service/impl/ProjectWorkingStatusServiceImpl.java`
- `hrm-backend/src/main/java/hrm/service/impl/TaskHistoryServiceImpl.java`
- `hrm-backend/src/main/java/hrm/service/impl/TaskServiceImpl.java`
- `hrm-backend/src/main/java/hrm/utils/ProjectUtils.java`
- `hrm-frontend/src/constants/project.js`
- `hrm-frontend/src/constants/taskConstants.js`
- `hrm-frontend/src/hooks/useProjectPermission.js`
- `hrm-frontend/src/pages/Project/ProjectDetail.jsx`
- `hrm-frontend/src/pages/Project/ProjectList.jsx`
- `hrm-frontend/src/pages/Project/components/ProjectForm.jsx`
- `hrm-frontend/src/pages/Project/components/ProjectTabs/ProjectActivity.jsx`
- `hrm-frontend/src/pages/Project/components/ProjectTabs/ProjectGeneralInfo.jsx`
- `hrm-frontend/src/pages/Project/components/ProjectTabs/ProjectWorkingStatus.jsx`
- `hrm-frontend/src/pages/Task/TaskList.jsx`
- `hrm-frontend/src/pages/Task/components/KanbanBoard.jsx`
- `hrm-frontend/src/pages/Task/components/TaskCard.jsx`
- `hrm-frontend/src/pages/Task/components/TaskFormDialog.jsx`
- `hrm-frontend/src/pages/Task/components/TaskHistorySection.jsx`
- `hrm-frontend/src/pages/Task/components/TaskListFilter.jsx`
- `hrm-frontend/src/services/projectService.js`
- `hrm-frontend/src/services/taskService.js`
- `hrm-frontend/src/store/useProjectStore.js`
- `hrm-frontend/src/store/useTaskStore.js`

## 9. Danh sách file đã sửa (20)

- `README.md`
- `TEST-REPORT.md`
- `hrm-backend/src/main/java/hrm/dto/response/DashboardSummaryResponse.java`
- `hrm-backend/src/main/java/hrm/exception/GlobalExceptionHandler.java`
- `hrm-backend/src/main/java/hrm/service/impl/DashboardServiceImpl.java`
- `hrm-backend/src/test/java/hrm/BusinessWorkflowIntegrationTests.java`
- `hrm-frontend/public/locales/en/translation.json`
- `hrm-frontend/public/locales/vi/translation.json`
- `hrm-frontend/src/App.jsx`
- `hrm-frontend/src/constants/index.js`
- `hrm-frontend/src/navigationConfig.js`
- `hrm-frontend/src/pages/Dashboard/DashboardPage.jsx`
- `hrm-frontend/src/pages/Dashboard/components/KpiCards.jsx`
- `hrm-frontend/src/pages/Dashboard/components/OverviewCharts.jsx`
- `hrm-frontend/src/pages/Dashboard/components/RecentActivities.jsx`
- `hrm-frontend/src/pages/Dashboard/components/WelcomeBanner.jsx`
- `hrm-frontend/src/pages/Recruitment/RecruitmentDetail.jsx`
- `hrm-frontend/tests/auth.test.js`
- `hrm-frontend/tests/http-client.test.js`
- `scripts/smoke-api.mjs`

## 10. File bổ sung

- `docs/HR_ONLY_CHANGELOG.md`
- `docs/HR_ONLY_DATABASE_REVIEW.md`
- `docs/HR_ONLY_TEST_REPORT.md`
- `hrm-backend/src/test/java/hrm/HrOnlyScopeIntegrationTests.java`
- `scripts/smoke-ui.mjs`

Các log, snapshot kiểm tra, bản sao source và profile Chrome nằm cục bộ trong .runtime (đã được gitignore). Artifact build trong target, dist và release được tạo lại; không phải source nghiệp vụ bổ sung.
