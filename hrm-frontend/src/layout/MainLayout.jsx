import React from 'react'
import LayoutHeader from './components/LayoutHeader'
import LayoutSidebar from './components/LayoutSidebar'
import LayoutFooter from './components/LayoutFooter'
import Breadcrumb from './components/Breadcrumb'
import { Outlet } from 'react-router-dom'

const MainLayout = () => {
  return (
    <div className="flex min-h-screen">
      {/* Sidebar - cao hết màn hình, logo ở trên cùng */}
      <LayoutSidebar />

      <div className="flex flex-col flex-1 min-w-0">
        {/* Header - cố định ở trên đầu khu vực nội dung */}
        <header className="sticky top-0 z-50">
          <LayoutHeader />
        </header>

        {/* Content - Khu vực nội dung chính */}
        <main className="flex-1 px-2 sm:px-5 overflow-x-hidden flex flex-col bg-background">
          <Breadcrumb />
          <div className="flex-1 pb-8">
            <Outlet />
          </div>
        </main>

        {/* Footer - Luôn nằm ở cuối */}
        <LayoutFooter />
      </div>
    </div>
  );
};

export default MainLayout;
