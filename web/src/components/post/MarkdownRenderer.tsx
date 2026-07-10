'use client';

import { useMemo, useState, useEffect } from 'react';
import { marked } from 'marked';
import hljs from 'highlight.js';

// Custom renderer for code blocks with highlight.js
const renderer = new marked.Renderer();
renderer.code = (token: any) => {
  const code = token.text || '';
  const lang = token.lang || '';
  let highlighted = code;
  if (lang && hljs.getLanguage(lang)) {
    try {
      highlighted = hljs.highlight(code, { language: lang }).value;
    } catch {
      highlighted = hljs.highlightAuto(code).value;
    }
  } else {
    highlighted = hljs.highlightAuto(code).value;
  }
  return `<pre><code class="hljs language-${lang}">${highlighted}</code></pre>`;
};

marked.setOptions({
  renderer,
  breaks: true,
  gfm: true,
});

interface MarkdownRendererProps {
  content: string;
}

export default function MarkdownRenderer({ content }: MarkdownRendererProps) {
  const [DOMPurify, setDOMPurify] = useState<any>(null);

  useEffect(() => {
    import('dompurify').then((mod) => {
      setDOMPurify(() => mod.default || mod);
    });
  }, []);

  const html = useMemo(() => {
    if (!content) return '';

    const rawHtml = marked.parse(content) as string;
    if (DOMPurify) {
      return DOMPurify.sanitize(rawHtml);
    }
    return rawHtml;
  }, [content, DOMPurify]);

  return (
    <div
      className="markdown-body"
      dangerouslySetInnerHTML={{ __html: html }}
    />
  );
}
