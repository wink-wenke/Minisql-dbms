import { Link } from 'react-router-dom';

export default function NotFound() {
  return (
    <div className="flex flex-col items-center justify-center h-full text-center">
      <span className="text-5xl font-bold font-mono text-text-muted/30 mb-4">404</span>
      <h1 className="text-lg font-semibold text-text-primary mb-2">页面未找到</h1>
      <p className="text-[13px] text-text-secondary mb-6">您访问的页面不存在。</p>
      <Link
        to="/"
        className="px-4 py-2 bg-accent text-white rounded-lg text-[13px] font-semibold hover:bg-accent-hover transition-colors shadow-sm"
      >
        返回仪表盘
      </Link>
    </div>
  );
}
