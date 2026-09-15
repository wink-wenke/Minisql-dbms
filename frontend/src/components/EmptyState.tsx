interface EmptyStateProps {
  icon?: string;
  title: string;
  description?: string;
}

export default function EmptyState({ icon = '○', title, description }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center py-16 text-center">
      <span className="text-3xl text-text-muted/30 mb-3">{icon}</span>
      <span className="text-sm text-text-secondary font-medium">{title}</span>
      {description && <span className="text-[13px] text-text-muted mt-1.5 max-w-xs">{description}</span>}
    </div>
  );
}
