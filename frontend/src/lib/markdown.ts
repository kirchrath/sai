// Kleiner Markdown-Parser ohne Abhaengigkeiten. Erzeugt einen AST, der in Markdown.tsx ausschliesslich
// ueber React-Elemente gerendert wird (kein innerHTML) und damit XSS-sicher ist.
// Unterstuetzt: Ueberschriften, Absaetze, Listen (verschachtelt, nummeriert, Checkboxen), Zitate,
// Code-Bloecke, Tabellen, Trennlinien sowie inline Fett, Kursiv, Durchgestrichen, Code und Links.

export type Inline =
  | { type: 'text'; text: string }
  | { type: 'strong' | 'em' | 'del'; children: Inline[] }
  | { type: 'code'; text: string }
  | { type: 'link'; href: string; children: Inline[] }
  | { type: 'br' };

export interface ListItem {
  checked: boolean | null;
  children: Block[];
}

export type Block =
  | { type: 'heading'; level: 1 | 2 | 3 | 4 | 5 | 6; children: Inline[] }
  | { type: 'paragraph'; children: Inline[] }
  | { type: 'code'; lang: string; text: string }
  | { type: 'quote'; children: Block[] }
  | { type: 'list'; ordered: boolean; start: number; items: ListItem[] }
  | { type: 'table'; align: ('left' | 'center' | 'right' | null)[]; header: Inline[][]; rows: Inline[][][] }
  | { type: 'hr' };

const FENCE = /^ {0,3}(`{3,}|~{3,})\s*([\w+-]*)\s*$/;
const HEADING = /^ {0,3}(#{1,6})\s+(.*?)\s*#*\s*$/;
const HR = /^ {0,3}([-*_])(\s*\1){2,}\s*$/;
const QUOTE = /^ {0,3}>\s?(.*)$/;
const LIST_ITEM = /^(\s*)([-*+]|\d{1,9}[.)])\s+(.*)$/;
const TABLE_SEPARATOR = /^\s*\|?\s*:?-{1,}:?\s*(\|\s*:?-{1,}:?\s*)*\|?\s*$/;

export function parseMarkdown(source: string): Block[] {
  return parseBlocks(source.replace(/\r\n?/g, '\n').replace(/\t/g, '    ').split('\n'));
}

function parseBlocks(lines: string[]): Block[] {
  const blocks: Block[] = [];
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];

    if (line.trim() === '') {
      i++;
      continue;
    }

    const fence = FENCE.exec(line);
    if (fence) {
      const code: string[] = [];
      i++;
      while (i < lines.length && !lines[i].trim().startsWith(fence[1])) {
        code.push(lines[i]);
        i++;
      }
      i++; // schliessender Zaun (oder Ende)
      blocks.push({ type: 'code', lang: fence[2], text: code.join('\n') });
      continue;
    }

    const heading = HEADING.exec(line);
    if (heading) {
      blocks.push({
        type: 'heading',
        level: heading[1].length as 1 | 2 | 3 | 4 | 5 | 6,
        children: parseInline(heading[2]),
      });
      i++;
      continue;
    }

    if (HR.test(line)) {
      blocks.push({ type: 'hr' });
      i++;
      continue;
    }

    if (QUOTE.test(line)) {
      const quoted: string[] = [];
      while (i < lines.length && QUOTE.test(lines[i])) {
        quoted.push(QUOTE.exec(lines[i])![1]);
        i++;
      }
      blocks.push({ type: 'quote', children: parseBlocks(quoted) });
      continue;
    }

    if (LIST_ITEM.test(line)) {
      i = parseList(lines, i, blocks);
      continue;
    }

    if (line.includes('|') && i + 1 < lines.length && TABLE_SEPARATOR.test(lines[i + 1]) && lines[i + 1].includes('-')) {
      i = parseTable(lines, i, blocks);
      continue;
    }

    // Absatz: bis Leerzeile oder Beginn eines anderen Blocks
    const paragraph: string[] = [];
    while (
      i < lines.length &&
      lines[i].trim() !== '' &&
      !FENCE.test(lines[i]) &&
      !HEADING.test(lines[i]) &&
      !HR.test(lines[i]) &&
      !QUOTE.test(lines[i]) &&
      !(paragraph.length > 0 && LIST_ITEM.test(lines[i]))
    ) {
      paragraph.push(lines[i]);
      i++;
    }
    blocks.push({ type: 'paragraph', children: parseParagraph(paragraph) });
  }
  return blocks;
}

function parseParagraph(lines: string[]): Inline[] {
  const result: Inline[] = [];
  lines.forEach((line, index) => {
    // Zwei Leerzeichen oder Backslash am Zeilenende erzwingen einen Umbruch, sonst wird mit Leerzeichen verbunden
    const hardBreak = / {2,}$/.test(line) || line.endsWith('\\');
    const text = line.replace(/\\$/, '').trim();
    result.push(...parseInline(text));
    if (index < lines.length - 1) {
      result.push(hardBreak ? { type: 'br' } : { type: 'text', text: ' ' });
    }
  });
  return result;
}

function indentOf(line: string): number {
  return line.length - line.trimStart().length;
}

function parseList(lines: string[], start: number, blocks: Block[]): number {
  const first = LIST_ITEM.exec(lines[start])!;
  const baseIndent = first[1].length;
  const ordered = /\d/.test(first[2]);
  const items: ListItem[] = [];
  let i = start;

  while (i < lines.length) {
    const match = LIST_ITEM.exec(lines[i]);
    if (!match || match[1].length !== baseIndent || /\d/.test(match[2]) !== ordered) {
      break;
    }
    const content: string[] = [match[3]];
    const contentIndent = baseIndent + match[2].length + 1;
    i++;
    // Folgezeilen: eingerueckt (verschachtelt) oder direkte Fortsetzung; Leerzeilen nur innerhalb
    while (i < lines.length) {
      const next = lines[i];
      if (next.trim() === '') {
        const following = lines[i + 1];
        if (following !== undefined && following.trim() !== '' && indentOf(following) >= contentIndent) {
          content.push('');
          i++;
          continue;
        }
        break;
      }
      if (indentOf(next) >= contentIndent) {
        content.push(next.slice(Math.min(contentIndent, indentOf(next))));
        i++;
        continue;
      }
      if (LIST_ITEM.test(next) || indentOf(next) <= baseIndent && blockStart(next)) {
        break;
      }
      content.push(next.trim());
      i++;
    }

    let checked: boolean | null = null;
    const task = /^\[([ xX])\]\s+(.*)$/.exec(content[0]);
    if (task) {
      checked = task[1] !== ' ';
      content[0] = task[2];
    }
    items.push({ checked, children: parseBlocks(content) });

    // Leerzeile zwischen zwei Punkten derselben Liste ueberspringen
    if (i < lines.length && lines[i].trim() === '' && i + 1 < lines.length) {
      const after = LIST_ITEM.exec(lines[i + 1]);
      if (after && after[1].length === baseIndent && /\d/.test(after[2]) === ordered) {
        i++;
      }
    }
  }

  blocks.push({ type: 'list', ordered, start: ordered ? parseInt(first[2], 10) : 1, items });
  return i;
}

function blockStart(line: string): boolean {
  return FENCE.test(line) || HEADING.test(line) || HR.test(line) || QUOTE.test(line);
}

function splitRow(line: string): string[] {
  let row = line.trim();
  if (row.startsWith('|')) row = row.slice(1);
  if (row.endsWith('|') && !row.endsWith('\\|')) row = row.slice(0, -1);
  const cells: string[] = [];
  let current = '';
  for (let k = 0; k < row.length; k++) {
    if (row[k] === '\\' && row[k + 1] === '|') {
      current += '|';
      k++;
    } else if (row[k] === '|') {
      cells.push(current.trim());
      current = '';
    } else {
      current += row[k];
    }
  }
  cells.push(current.trim());
  return cells;
}

function parseTable(lines: string[], start: number, blocks: Block[]): number {
  const header = splitRow(lines[start]);
  const align = splitRow(lines[start + 1]).map((cell) => {
    const left = cell.startsWith(':');
    const right = cell.endsWith(':');
    return left && right ? 'center' : right ? 'right' : left ? 'left' : null;
  });
  const rows: Inline[][][] = [];
  let i = start + 2;
  while (i < lines.length && lines[i].includes('|') && lines[i].trim() !== '') {
    const cells = splitRow(lines[i]);
    rows.push(header.map((_, c) => parseInline(cells[c] ?? '')));
    i++;
  }
  blocks.push({
    type: 'table',
    align: header.map((_, c) => align[c] ?? null),
    header: header.map((cell) => parseInline(cell)),
    rows,
  });
  return i;
}

// ---- Inline ----

const SAFE_URL = /^(https?:\/\/|mailto:|\/|#)/i;

export function isSafeUrl(url: string): boolean {
  return SAFE_URL.test(url.trim());
}

interface InlineRule {
  pattern: RegExp;
  build: (match: RegExpExecArray) => Inline;
}

const INLINE_RULES: InlineRule[] = [
  { pattern: /\\([\\`*_{}[\]()#+\-.!|~>])/, build: (m) => ({ type: 'text', text: m[1] }) },
  { pattern: /`([^`]+)`/, build: (m) => ({ type: 'code', text: m[1] }) },
  {
    pattern: /\[([^\]]+)\]\(([^)\s]+)(?:\s+"[^"]*")?\)/,
    build: (m) =>
      isSafeUrl(m[2])
        ? { type: 'link', href: m[2], children: parseInline(m[1]) }
        : { type: 'text', text: m[1] },
  },
  { pattern: /<(https?:\/\/[^>\s]+)>/, build: (m) => ({ type: 'link', href: m[1], children: [{ type: 'text', text: m[1] }] }) },
  { pattern: /\*\*(?=\S)([\s\S]*?\S)\*\*/, build: (m) => ({ type: 'strong', children: parseInline(m[1]) }) },
  { pattern: /__(?=\S)([\s\S]*?\S)__/, build: (m) => ({ type: 'strong', children: parseInline(m[1]) }) },
  { pattern: /~~(?=\S)([\s\S]*?\S)~~/, build: (m) => ({ type: 'del', children: parseInline(m[1]) }) },
  { pattern: /\*(?=\S)([\s\S]*?\S)\*/, build: (m) => ({ type: 'em', children: parseInline(m[1]) }) },
  { pattern: /(?<![\w])_(?=\S)([\s\S]*?\S)_(?![\w])/, build: (m) => ({ type: 'em', children: parseInline(m[1]) }) },
];

export function parseInline(text: string): Inline[] {
  const result: Inline[] = [];
  let rest = text;

  while (rest.length > 0) {
    // Fruehesten Treffer suchen; bei Gleichstand gewinnt die erste Regel (Code vor Hervorhebung)
    let best: { index: number; match: RegExpExecArray; rule: InlineRule } | null = null;
    for (const rule of INLINE_RULES) {
      const match = rule.pattern.exec(rest);
      if (match && (best === null || match.index < best.index)) {
        best = { index: match.index, match, rule };
      }
    }
    if (!best) {
      pushText(result, rest);
      break;
    }
    if (best.index > 0) {
      pushText(result, rest.slice(0, best.index));
    }
    const built = best.rule.build(best.match);
    if (built.type === 'text') {
      pushText(result, built.text);
    } else {
      result.push(built);
    }
    rest = rest.slice(best.index + best.match[0].length);
  }
  return result;
}

function pushText(result: Inline[], text: string) {
  const last = result[result.length - 1];
  if (last && last.type === 'text') {
    last.text += text;
  } else {
    result.push({ type: 'text', text });
  }
}
