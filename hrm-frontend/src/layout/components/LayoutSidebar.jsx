import React, { useEffect, useMemo, useState, memo } from "react";
import { NavLink, useLocation } from "react-router-dom";
import HomeIcon from "@mui/icons-material/Home";
import PeopleIcon from "@mui/icons-material/People";
import AccessTimeIcon from "@mui/icons-material/AccessTime";
import ChatIcon from "@mui/icons-material/Chat";
import AttachMoneyIcon from "@mui/icons-material/AttachMoney";
import SecurityIcon from "@mui/icons-material/Security";
import PersonIcon from "@mui/icons-material/Person";
import WorkIcon from "@mui/icons-material/Work";
import AssessmentIcon from "@mui/icons-material/Assessment";
import SettingsIcon from "@mui/icons-material/Settings";
import AccountTreeIcon from "@mui/icons-material/AccountTree";
import VerticalSplitIcon from "@mui/icons-material/VerticalSplit";
import GavelIcon from "@mui/icons-material/Gavel";
import WalletIcon from "@mui/icons-material/AccountBalanceWallet";
import EditNoteIcon from "@mui/icons-material/EditNote";
import TokenIcon from "@mui/icons-material/Token";
import BookmarkIcon from "@mui/icons-material/Bookmark";
import ListAltIcon from "@mui/icons-material/ListAlt";
import EventIcon from "@mui/icons-material/Event";
import CampaignIcon from "@mui/icons-material/Campaign";
import ReceiptIcon from "@mui/icons-material/ReceiptLong";
import SearchIcon from "@mui/icons-material/Search";
import IconButton from "@mui/material/IconButton";
import CloseIcon from "@mui/icons-material/Close";
import Tooltip from "@mui/material/Tooltip";
import { navigations } from "@/navigationConfig";
import useAuthStore from "@/store/useAuthStore";
import useSidebarStore from "@/store/sidebarStore";
import { filterNavigations } from "../../utils/permissions.js";
import { buildSidebarGroups } from "../sidebarGroups";
import { useLanguage } from "@/i18n/LanguageProvider";

const icons = {
  home: HomeIcon,
  people: PeopleIcon,
  access_time: AccessTimeIcon,
  attach_money: AttachMoneyIcon,
  security: SecurityIcon,
  person: PersonIcon,
  work: WorkIcon,
  chat: ChatIcon,
  assessment: AssessmentIcon,
  settings: SettingsIcon,
  account_tree: AccountTreeIcon,
  vertical_split: VerticalSplitIcon,
  gavel: GavelIcon,
  wallet: WalletIcon,
  edit_document: EditNoteIcon,
  token: TokenIcon,
  bookmark: BookmarkIcon,
  list_alt: ListAltIcon,
  event: EventIcon,
  campaign: CampaignIcon,
  receipt: ReceiptIcon,
};

const IconMapper = memo(({ iconName, ...props }) => {
  const IconComponent = icons[iconName] || ListAltIcon;
  return <IconComponent {...props} />;
});

export const SidebarLogo = ({ compact = false }) => (
  <NavLink
    to="/dashboard"
    className="no-underline font-black italic text-primary leading-none tracking-wide select-none"
    style={{ fontSize: compact ? 18 : 30, textShadow: "1px 1px 0 hsl(var(--primary) / 0.25)" }}
  >
    HRM
  </NavLink>
);

const itemClass = (isActive, collapsed) =>
  `flex items-center ${collapsed ? "justify-center px-0" : "gap-3 px-3"} mx-2 mb-[2px] py-[7px] rounded-md no-underline text-[13px] transition-colors ${
    isActive
      ? "bg-sidebar-accent text-sidebar-accent-foreground font-semibold"
      : "text-sidebar-foreground hover:bg-sidebar-accent/50 hover:text-sidebar-accent-foreground"
  }`;

const LayoutSidebar = () => {
  const location = useLocation();
  const user = useAuthStore((state) => state.user);
  const { isCollapsed, isMobileOpen, setMobileOpen } = useSidebarStore();
  const { t } = useLanguage();
  const [query, setQuery] = useState("");

  const groups = useMemo(() => buildSidebarGroups(filterNavigations(navigations, user)), [user]);

  // Tự động đóng menu trên điện thoại khi chuyển trang
  useEffect(() => {
    setMobileOpen(false);
  }, [location.pathname, setMobileOpen]);

  const collapsed = isMobileOpen ? false : isCollapsed;
  const keyword = query.trim().toLocaleLowerCase();
  const visibleGroups = keyword
    ? groups
        .map((group) => ({
          ...group,
          items: group.items.filter((item) =>
            `${item.label} ${t(item.label)}`.toLocaleLowerCase().includes(keyword)),
        }))
        .filter((group) => group.items.length)
    : groups;

  return (
    <>
      {isMobileOpen && (
        <div className="fixed inset-0 z-[55] bg-black/40 md:hidden" onClick={() => setMobileOpen(false)} />
      )}

      <div
        className={`transition-[width,min-width] duration-300 flex-shrink-0 w-0
          ${collapsed ? "md:w-[56px] md:min-w-[56px]" : "md:w-[220px] md:min-w-[220px]"}`}
      >
        <aside
          className={`fixed md:sticky top-0 z-[60] md:z-40 h-screen bg-sidebar border-r border-sidebar-border select-none
            transition-[width,transform] duration-300 flex flex-col
            ${isMobileOpen ? "translate-x-0" : "-translate-x-full md:translate-x-0"}
            w-[240px] ${collapsed ? "md:w-[56px]" : "md:w-[220px]"}`}
        >
          {/* Logo */}
          <div className="h-[48px] flex-shrink-0 flex items-center justify-center relative border-b border-sidebar-border">
            <SidebarLogo compact={collapsed} />
            <IconButton size="small" className="!absolute right-2 md:!hidden" onClick={() => setMobileOpen(false)}>
              <CloseIcon fontSize="small" sx={{ color: "hsl(var(--foreground))" }} />
            </IconButton>
          </div>

          {/* Tìm menu */}
          {!collapsed && (
            <div className="px-3 pt-3 pb-2 flex-shrink-0">
              <label className="flex items-center gap-1.5 h-[34px] px-2 rounded-md border border-sidebar-border bg-background focus-within:border-primary">
                <SearchIcon sx={{ fontSize: 18 }} className="text-muted-foreground" />
                <input
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  placeholder={t("Tìm menu...")}
                  aria-label={t("Tìm menu...")}
                  className="flex-1 min-w-0 bg-transparent outline-none border-0 text-[13px] text-foreground placeholder:text-muted-foreground"
                />
              </label>
            </div>
          )}

          {/* Các nhóm menu */}
          <nav className="flex-1 overflow-y-auto no-scrollbar pb-4">
            {visibleGroups.map((group) => (
              <div key={group.title} className={collapsed ? "pt-2 border-b border-sidebar-border/60 pb-1" : "pt-2"}>
                {!collapsed && (
                  <div className="px-4 pb-1 pt-1 text-[11px] font-bold uppercase tracking-wide text-muted-foreground">
                    {t(group.title)}
                  </div>
                )}
                {group.items.map((item) => {
                  const label = t(item.label);
                  const link = (
                    <NavLink
                      key={item.path}
                      to={item.path}
                      className={({ isActive }) => itemClass(isActive, collapsed)}
                    >
                      <IconMapper iconName={item.icon} style={{ fontSize: 18 }} />
                      {!collapsed && <span className="flex-1 truncate">{label}</span>}
                    </NavLink>
                  );
                  return collapsed ? (
                    <Tooltip key={item.path} title={label} placement="right" arrow>
                      <div>{link}</div>
                    </Tooltip>
                  ) : link;
                })}
              </div>
            ))}
            {!visibleGroups.length && (
              <div className="px-4 py-2 text-[13px] text-muted-foreground">{t("Không tìm thấy menu")}</div>
            )}
          </nav>
        </aside>
      </div>
    </>
  );
};

export default LayoutSidebar;
