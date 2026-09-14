import { NavLink, Outlet } from 'react-router-dom';

const navItems = [
  { to: '/', label: '架构总览', icon: '◇' },
  { to: '/playground', label: 'SQL 演练场', icon: '▶' },
  { to: '/pipeline', label: '查询流水线', icon: '→' },
  { to: '/storage', label: '存储', icon: '▦' },
  { to: '/tests', label: '测试', icon: '✓' },
  { to: '/fuzz', label: '模糊测试', icon: '⚡' },
  { to: '/engine', label: '引擎通道', icon: '⚙' },
];

export default function Layout() {
  return (
    <div className="flex h-full">
      {/* Sidebar */}
      <aside className="w-56 flex-shrink-0 border-r border-border bg-bg-surface flex flex-col">
        {/* Logo */}
        <div className="px-4 py-4 border-b border-border">
          <div className="flex items-center gap-2">
            <div className="w-7 h-7 rounded bg-accent flex items-center justify-center text-bg-primary font-bold text-sm">
              S
            </div>
            <div>
              <div className="text-sm font-semibold text-text-primary">MiniSQL</div>
              <div className="text-xs text-text-secondary">DBMS v1.0</div>
            </div>
          </div>
        </div>

        {/* Navigation */}
        <nav className="flex-1 py-2">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                `flex items-center gap-3 px-4 py-2 mx-2 rounded text-sm transition-colors ${
                  isActive
                    ? 'bg-accent/10 text-accent border-l-2 border-accent'
                    : 'text-text-secondary hover:text-text-primary hover:bg-bg-elevated'
                }`
              }
            >
              <span className="font-mono text-xs w-4 text-center">{item.icon}</span>
              {item.label}
            </NavLink>
          ))}
        </nav>

        {/* Footer */}
        <div className="px-4 py-3 border-t border-border">
          <div className="text-xs text-text-muted font-mono">
            SimpleDB 3.4
          </div>
        </div>
      </aside>

      {/* Main Content */}
      <main className="flex-1 flex flex-col min-w-0 overflow-hidden">
        <Outlet />
      </main>
    </div>
  );
}
