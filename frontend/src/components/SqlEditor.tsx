import { useRef, useState, useCallback, useEffect, type KeyboardEvent as ReactKeyboardEvent } from 'react';

interface SqlEditorProps {
  value: string;
  onChange: (value: string) => void;
  onRun?: () => void;
  placeholder?: string;
}

const SQL_KEYWORDS = new Set([
  'SELECT', 'FROM', 'WHERE', 'INSERT', 'INTO', 'VALUES', 'UPDATE', 'SET',
  'DELETE', 'CREATE', 'DROP', 'TABLE', 'ALTER', 'ADD', 'COLUMN',
  'AND', 'OR', 'NOT', 'IN', 'BETWEEN', 'LIKE', 'IS', 'NULL', 'AS',
  'ORDER', 'BY', 'GROUP', 'HAVING', 'LIMIT', 'DISTINCT', 'ALL',
  'JOIN', 'LEFT', 'RIGHT', 'INNER', 'OUTER', 'ON', 'CROSS',
  'COUNT', 'SUM', 'AVG', 'MIN', 'MAX',
  'PRIMARY', 'KEY', 'FOREIGN', 'REFERENCES', 'UNIQUE', 'DEFAULT',
  'INT', 'INTEGER', 'VARCHAR', 'CHAR', 'TEXT', 'FLOAT', 'DOUBLE',
  'BOOLEAN', 'DATE', 'TIME', 'TIMESTAMP', 'REAL',
  'IF', 'EXISTS', 'CASCADE', 'CONSTRAINT',
  'UNION', 'EXCEPT', 'INTERSECT',
  'ASC', 'DESC', 'CASE', 'WHEN', 'THEN', 'ELSE', 'END',
  'TRUE', 'FALSE',
]);

function highlightSql(sql: string): { __html: string } {
  const escaped = sql
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');

  const highlighted = escaped.replace(
    /('(?:[^'\\]|\\.)*')|(--)|(\/\*[\s\S]*?\*\/)|(\b\d+(?:\.\d+)?\b)|(\b[A-Za-z_]\w*\b)/g,
    (match, string, lineComment, blockComment, number, word) => {
      if (string) return `<span style="color:#3F704B">${match}</span>`;
      if (lineComment || blockComment) return `<span style="color:#8A8678">${match}</span>`;
      if (number) return `<span style="color:#A36316">${match}</span>`;
      if (word && SQL_KEYWORDS.has(word.toUpperCase())) return `<span style="color:#2A6DB5;font-weight:500">${match}</span>`;
      return match;
    }
  );

  return { __html: highlighted };
}

export default function SqlEditor({ value, onChange, onRun, placeholder }: SqlEditorProps) {
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const highlightRef = useRef<HTMLDivElement>(null);
  const lineNumbersRef = useRef<HTMLDivElement>(null);
  const [currentLine, setCurrentLine] = useState(1);

  const lines = value.split('\n');
  const lineCount = Math.max(lines.length, 1);

  const handleKeyDown = useCallback((e: ReactKeyboardEvent<HTMLTextAreaElement>) => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
      e.preventDefault();
      onRun?.();
      return;
    }
    if (e.key === 'Tab') {
      e.preventDefault();
      const ta = e.currentTarget;
      const start = ta.selectionStart;
      const end = ta.selectionEnd;
      const newValue = value.substring(0, start) + '    ' + value.substring(end);
      onChange(newValue);
      requestAnimationFrame(() => {
        ta.selectionStart = ta.selectionEnd = start + 4;
      });
    }
  }, [value, onChange, onRun]);

  const handleInput = useCallback(() => {
    const ta = textareaRef.current;
    if (!ta) return;
    onChange(ta.value);
  }, [onChange]);

  const handleScroll = useCallback(() => {
    const ta = textareaRef.current;
    if (!ta) return;
    if (highlightRef.current) {
      highlightRef.current.scrollTop = ta.scrollTop;
      highlightRef.current.scrollLeft = ta.scrollLeft;
    }
    if (lineNumbersRef.current) {
      lineNumbersRef.current.scrollTop = ta.scrollTop;
    }
  }, []);

  const handleSelect = useCallback(() => {
    const ta = textareaRef.current;
    if (!ta) return;
    const pos = ta.selectionStart;
    const line = value.substring(0, pos).split('\n').length;
    setCurrentLine(line);
  }, [value]);

  useEffect(() => {
    handleScroll();
  }, [value, handleScroll]);

  const highlighted = highlightSql(value);

  return (
    <div className="relative flex border border-border rounded-lg overflow-hidden bg-bg-input font-mono text-[14px] leading-[1.7]">
      {/* Line numbers */}
      <div
        ref={lineNumbersRef}
        className="flex-shrink-0 w-10 bg-bg-elevated/60 border-r border-border select-none overflow-hidden text-right py-3 pr-1.5"
        aria-hidden="true"
      >
        {Array.from({ length: lineCount }, (_, i) => (
          <div
            key={i + 1}
            className={`px-0.5 text-[11px] leading-[1.7] ${
              i + 1 === currentLine ? 'text-accent font-semibold' : 'text-text-muted/40'
            }`}
          >
            {i + 1}
          </div>
        ))}
      </div>

      {/* Editor area */}
      <div className="relative flex-1 min-w-0">
        {/* Syntax highlight layer (behind textarea) */}
        <div
          ref={highlightRef}
          className="absolute inset-0 py-3 px-3 pointer-events-none overflow-hidden whitespace-pre-wrap break-words"
          aria-hidden="true"
        >
          <div dangerouslySetInnerHTML={highlighted} />
        </div>

        {/* Transparent textarea (on top) */}
        <textarea
          ref={textareaRef}
          value={value}
          onChange={handleInput}
          onKeyDown={handleKeyDown}
          onScroll={handleScroll}
          onSelect={handleSelect}
          onClick={handleSelect}
          placeholder={placeholder || 'Enter SQL query...'}
          spellCheck={false}
          className="relative w-full min-h-[140px] bg-transparent text-transparent caret-accent py-3 px-3 outline-none resize-none font-mono text-[14px] leading-[1.7] placeholder:text-text-muted/40 z-10"
          style={{ WebkitTextFillColor: 'transparent' }}
        />
      </div>
    </div>
  );
}
