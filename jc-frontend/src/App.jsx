import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import AdminLayout from "./admin/AdminLayout";
import AdminRouteGuard from "./admin/AdminRouteGuard";
import AdminDashboardPage from "./pages/admin/AdminDashboardPage";
import AdminReportsPage from "./pages/admin/AdminReportsPage";
import AdminReportDetailPage from "./pages/admin/AdminReportDetailPage";
import AdminPostsPage from "./pages/admin/AdminPostsPage";
import AdminPostDetailPage from "./pages/admin/AdminPostDetailPage";
import AdminUsersPage from "./pages/admin/AdminUsersPage";
import AdminUserDetailPage from "./pages/admin/AdminUserDetailPage";
import AdminNotFoundPage from "./pages/admin/AdminNotFoundPage";
import AdminLoginPage from "./pages/AdminLoginPage";

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/admin" replace />} />
        <Route path="/login" element={<AdminLoginPage />} />
        <Route
          path="/admin"
          element={(
            <AdminRouteGuard>
              <AdminLayout />
            </AdminRouteGuard>
          )}
        >
          <Route index element={<AdminDashboardPage />} />
          <Route path="reports" element={<AdminReportsPage />} />
          <Route path="reports/:reportId" element={<AdminReportDetailPage />} />
          <Route path="posts" element={<AdminPostsPage />} />
          <Route path="posts/:postId" element={<AdminPostDetailPage />} />
          <Route path="users" element={<AdminUsersPage />} />
          <Route path="users/:userId" element={<AdminUserDetailPage />} />
          <Route path="*" element={<AdminNotFoundPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/admin" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
