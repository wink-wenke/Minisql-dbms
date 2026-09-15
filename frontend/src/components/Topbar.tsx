export default function Topbar() {
  return (
    <div className="flex items-center gap-3 text-[12px] font-mono text-text-muted flex-shrink-0">
      <div className="flex items-center gap-1.5 px-2 py-0.5 rounded-full bg-green/10">
        <span className="w-1.5 h-1.5 rounded-full bg-green animate-pulse" />
        <span className="text-green font-medium">Connected</span>
      </div>
      <div className="flex items-center gap-0.5 text-text-muted/70">
        <span>localhost</span>
        <span className="text-text-muted/40">:</span>
        <span className="font-semibold text-text-muted/80">8080</span>
      </div>
      <span className="px-1.5 py-0.5 rounded bg-bg-hover text-[11px]">v1.0.0</span>
    </div>
  );
}
