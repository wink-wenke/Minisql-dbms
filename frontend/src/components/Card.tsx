import type { ReactNode } from 'react';

interface CardProps {
  title?: string;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
}

export default function Card({ title, action, children, className = '' }: CardProps) {
  return (
    <div className={`rounded-xl bg-white border border-border overflow-hidden ${className}`}>
      {(title || action) && (
        <div className="flex items-center justify-between px-4 py-2.5 border-b border-border-subtle">
          {title && <span className="text-[12px] font-semibold text-text-primary">{title}</span>}
          {action}
        </div>
      )}
      <div className="p-4">{children}</div>
    </div>
  );
}
