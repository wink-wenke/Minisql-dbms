import { NavLink, Outlet } from 'react-router-dom';

const navItems = [
  { to: '/', label: 'Architecture', icon: '◇' },
  { to: '/playground', label: 'Playground', icon: '▶' },
  { to: '/pipeline', label: 'Pipeline', icon: '→' },
  { to: '/storage', label: 'Storage', icon: '▦' },
  { to: '/tests', label: 'Tests', icon: '✓' },
  { to: '/fuzz', label: 'Fuzz', icon: '⚡' },
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
