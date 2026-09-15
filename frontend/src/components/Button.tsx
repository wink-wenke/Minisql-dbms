import type { ButtonHTMLAttributes } from 'react';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: 'sm' | 'md';
}

const variantClasses: Record<Variant, string> = {
  primary: 'bg-accent text-white hover:bg-accent-hover font-semibold shadow-sm',
  secondary: 'border border-border text-text-secondary hover:text-text-primary hover:border-accent/40 bg-white',
  ghost: 'text-text-secondary hover:text-text-primary hover:bg-bg-hover',
  danger: 'bg-red/10 text-red border border-red/20 hover:bg-red/15',
};

export default function Button({ variant = 'primary', size = 'md', className = '', children, ...props }: ButtonProps) {
  const sizeClass = size === 'sm' ? 'px-2.5 py-1 text-[11px]' : 'px-3.5 py-1.5 text-[12px]';
  return (
    <button
      className={`${sizeClass} rounded-lg font-mono transition-all duration-150 disabled:opacity-40 disabled:cursor-not-allowed ${variantClasses[variant]} ${className}`}
      {...props}
    >
      {children}
    </button>
  );
}
