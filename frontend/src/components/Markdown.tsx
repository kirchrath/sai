import { Fragment, useMemo, type ReactNode } from 'react';
import { parseMarkdown, type Block, type Inline } from '../lib/markdown';

/** Rendert Markdown mit Bulma-Typografie (Klasse "content"). */
export default function Markdown({ text }: { text: string }) {
  const blocks = useMemo(() => parseMarkdown(text), [text]);
  return <div className="content markdown">{blocks.map(renderBlock)}</div>;
}

function renderBlock(block: Block, key: number): ReactNode {
  switch (block.type) {
    case 'heading': {
      const Tag = `h${block.level}` as const;
      return <Tag key={key}>{renderInlines(block.children)}</Tag>;
    }
    case 'paragraph':
      return <p key={key}>{renderInlines(block.children)}</p>;
    case 'code':
      return (
        <pre key={key} data-lang={block.lang || undefined}>
          <code>{block.text}</code>
        </pre>
      );
    case 'quote':
      return <blockquote key={key}>{block.children.map(renderBlock)}</blockquote>;
    case 'hr':
      return <hr key={key} />;
    case 'list': {
      const items = block.items.map((item, i) => (
        <li key={i}>
          {item.checked !== null && (
            <input type="checkbox" className="mr-2" checked={item.checked} disabled readOnly />
          )}
          {renderListItem(item.children)}
        </li>
      ));
      return block.ordered ? (
        <ol key={key} start={block.start}>
          {items}
        </ol>
      ) : (
        <ul key={key}>{items}</ul>
      );
    }
    case 'table':
      return (
        <div key={key} className="table-container">
          <table className="table is-narrow is-bordered">
            <thead>
              <tr>
                {block.header.map((cell, c) => (
                  <th key={c} style={{ textAlign: block.align[c] ?? undefined }}>
                    {renderInlines(cell)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {block.rows.map((row, r) => (
                <tr key={r}>
                  {row.map((cell, c) => (
                    <td key={c} style={{ textAlign: block.align[c] ?? undefined }}>
                      {renderInlines(cell)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      );
  }
}

/** Ein einzelner Absatz in einem Listenpunkt wird ohne <p> gerendert (kompakte Listen). */
function renderListItem(children: Block[]): ReactNode {
  if (children.length >= 1 && children[0].type === 'paragraph') {
    return (
      <>
        {renderInlines(children[0].children)}
        {children.slice(1).map((block, i) => renderBlock(block, i + 1))}
      </>
    );
  }
  return children.map(renderBlock);
}

function renderInlines(inlines: Inline[]): ReactNode {
  return inlines.map((inline, key) => {
    switch (inline.type) {
      case 'text':
        return <Fragment key={key}>{inline.text}</Fragment>;
      case 'br':
        return <br key={key} />;
      case 'code':
        return <code key={key}>{inline.text}</code>;
      case 'strong':
        return <strong key={key}>{renderInlines(inline.children)}</strong>;
      case 'em':
        return <em key={key}>{renderInlines(inline.children)}</em>;
      case 'del':
        return <del key={key}>{renderInlines(inline.children)}</del>;
      case 'link':
        return (
          <a key={key} href={inline.href} target="_blank" rel="noopener noreferrer nofollow">
            {renderInlines(inline.children)}
          </a>
        );
    }
  });
}
