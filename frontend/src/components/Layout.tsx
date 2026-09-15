import { NavLink, Outlet } from 'react-router-dom';
import Topbar from './Topbar';

interface NavItem {
  route: string;
  label: string;
}

interface NavGroup {
  label: string;
  items: NavItem[];
}

const navGroups: NavGroup[] = [
  {
    label: '工作区',
    items: [
      { route: '/', label: '仪表盘' },
      { route: '/console', label: 'SQL 控制台' },
      { route: '/pipeline', label: '管道' },
    ],
  },
  {
    label: '数据库',
    items: [
      { route: '/storage', label: '存储' },
    ],
  },
  {
    label: '工具',
    items: [
      { route: '/tests', label: '测试' },
      { route: '/history', label: '历史' },
      { route: '/logs', label: '日志' },
    ],
  },
];

export default function Layout() {
  return (
    <div className="flex flex-col h-full">
      {/* Top navigation bar — 48px */}
      <header className="h-12 flex-shrink-0 border-b border-border bg-white/80 backdrop-blur-[12px] sticky top-0 z-50">
        <div className="h-full flex items-center justify-between px-5">
          {/* Left: Logo */}
          <div className="flex items-center gap-2.5 flex-shrink-0">
            <div className="w-7 h-7 rounded-lg bg-accent/12 flex items-center justify-center">
              <span className="text-accent font-mono text-xs font-bold">S</span>
            </div>
            <div className="leading-tight">
              <div className="text-[14px] font-bold text-text-primary tracking-tight">MiniSQL</div>
              <div className="text-[10px] text-text-muted font-mono leading-none">Database Manager</div>
            </div>
          </div>

          {/* Center: Navigation */}
          <nav className="flex items-center justify-center gap-1.5 mx-6">
            {navGroups.map((group, gi) => (
              <div key={group.label} className="flex items-center gap-1">
                {gi > 0 && (
                  <span className="mx-1.5 text-border select-none text-xs">|</span>
                )}
                {group.items.map((item) => (
                  <NavLink
                    key={item.route}
                    to={item.route}
                    end={item.route === '/'}
                    className={({ isActive }) =>
                      `nav-item ${isActive ? 'nav-item--active' : ''}`
                    }
                  >
                    {item.label}
                  </NavLink>
                ))}
              </div>
            ))}
          </nav>

          {/* Right: Status + Version */}
          <Topbar />
        </div>
      </header>

      {/* Content area — fills remaining viewport */}
      <main className="flex-1 overflow-auto">
        <div className="px-6 py-5 h-full">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
