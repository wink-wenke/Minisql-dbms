type Variant = 'ok' | 'error' | 'pass' | 'fail' | 'warn' | 'info';

interface BadgeProps {
  variant: Variant;
  children: React.ReactNode;
}

const variantClasses: Record<Variant, string> = {
  ok: 'bg-green-muted text-green',
  pass: 'bg-green-muted text-green',
  error: 'bg-red-muted text-red',
  fail: 'bg-red-muted text-red',
  warn: 'bg-yellow-muted text-yellow',
  info: 'bg-blue-muted text-blue',
};

export default function Badge({ variant, children }: BadgeProps) {
  return (
    <span className={`inline-flex items-center px-1.5 py-0.5 text-[10px] font-mono font-semibold uppercase rounded-md ${variantClasses[variant]}`}>
      {children}
    </span>
  );
}
