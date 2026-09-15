import { useState, useCallback } from 'react';

interface ResultTableProps {
  columns: string[];
  rows: (string | number)[][];
}

export default function ResultTable({ columns, rows }: ResultTableProps) {
  const [copied, setCopied] = useState(false);

  const copyToClipboard = useCallback(() => {
    const header = columns.join('\t');
    const body = rows.map((r) => r.join('\t')).join('\n');
    navigator.clipboard.writeText(`${header}\n${body}`).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  }, [columns, rows]);

  const exportCsv = useCallback(() => {
    const header = columns.map((c) => `"${c}"`).join(',');
    const body = rows.map((r) => r.map((v) => `"${String(v).replace(/"/g, '""')}"`).join(',')).join('\n');
    const blob = new Blob([`${header}\n${body}`], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'query_result.csv';
    a.click();
    URL.revokeObjectURL(url);
  }, [columns, rows]);

  if (columns.length === 0) return null;

  return (
    <div className="border border-border rounded-xl overflow-hidden">
      {/* Toolbar */}
      <div className="flex items-center justify-between px-3 py-2 bg-bg-elevated/40 border-b border-border-subtle">
        <span className="text-[12px] font-mono text-text-muted">
          {rows.length} row{rows.length !== 1 ? 's' : ''}
        </span>
        <div className="flex items-center gap-1">
          <button
            onClick={copyToClipboard}
            className="text-[11px] font-mono text-text-muted hover:text-text-primary transition-colors px-2 py-0.5 rounded-md hover:bg-bg-hover"
          >
            {copied ? 'Copied' : 'Copy'}
          </button>
          <button
            onClick={exportCsv}
            className="text-[11px] font-mono text-text-muted hover:text-text-primary transition-colors px-2 py-0.5 rounded-md hover:bg-bg-hover"
          >
            Export CSV
          </button>
        </div>
      </div>

      {/* Table */}
      <div className="overflow-x-auto">
        <table className="w-full text-[13px] font-mono">
          <thead>
            <tr className="border-b border-border">
              {columns.map((col) => (
                <th
                  key={col}
                  className="px-3 py-2.5 text-left text-[11px] font-semibold text-text-muted uppercase tracking-wider whitespace-nowrap bg-bg-elevated/30"
                >
                  {col}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row, i) => (
              <tr
                key={i}
                className={`border-b border-border-subtle last:border-0 transition-colors ${
                  i % 2 === 0 ? 'bg-white' : 'bg-bg-elevated/20'
                } hover:bg-bg-hover/60`}
              >
                {row.map((cell, j) => (
                  <td key={j} className="px-3 py-2 text-text-primary whitespace-nowrap">
                    {cell === null ? (
                      <span className="text-text-muted italic">NULL</span>
                    ) : (
                      String(cell)
                    )}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
