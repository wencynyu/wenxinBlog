'use client';

import { useMemo, useState, useEffect } from 'react';
import { marked } from 'marked';
import hljs from 'highlight.js/lib/core';
import 'highlight.js/styles/github.css';

// 只注册常用语言，避免把 highlight.js 全部 ~190 种语言打进 bundle。
import javascript from 'highlight.js/lib/languages/javascript';
import typescript from 'highlight.js/lib/languages/typescript';
import python from 'highlight.js/lib/languages/python';
import go from 'highlight.js/lib/languages/go';
import java from 'highlight.js/lib/languages/java';
import json from 'highlight.js/lib/languages/json';
import bash from 'highlight.js/lib/languages/bash';
import sql from 'highlight.js/lib/languages/sql';
import yaml from 'highlight.js/lib/languages/yaml';
import xml from 'highlight.js/lib/languages/xml';
import css from 'highlight.js/lib/languages/css';
import markdown from 'highlight.js/lib/languages/markdown';
import shell from 'highlight.js/lib/languages/shell';

(
  [
    ['javascript', javascript],
    ['typescript', typescript],
    ['python', python],
    ['go', go],
    ['java', java],
    ['json', json],
    ['bash', bash],
    ['sql', sql],
    ['yaml', yaml],
    ['xml', xml],
    ['css', css],
    ['markdown', markdown],
    ['shell', shell],
  ] as const
).forEach(([name, lang]) => hljs.registerLanguage(name, lang));

function escapeHtml(s: string) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

// Custom renderer for code blocks with highlight.js
// marked v12 调用签名是位置参数 (code, infostring, escaped)，不是 token 对象
const renderer = new marked.Renderer();
renderer.code = (code: string, lang: string | undefined) => {
  let highlighted: string;
  if (lang && hljs.getLanguage(lang)) {
    try {
      highlighted = hljs.highlight(code, { language: lang }).value;
    } catch {
      highlighted = escapeHtml(code);
    }
  } else {
    highlighted = escapeHtml(code);
  }
  return `<pre><code class="hljs language-${lang || 'plain'}">${highlighted}</code></pre>`;
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
    let cancelled = false;
    import('dompurify')
      .then((mod) => {
        if (!cancelled) setDOMPurify(() => mod.default || mod);
      })
      .catch(() => {
        // DOMPurify 加载失败：保持空占位（fail-safe），绝不输出未净化 HTML。
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // DOMPurify 就绪前返回空占位；保证任何时刻 __html 都经过净化，首帧不含未净化 HTML。
  const html = useMemo(() => {
    if (!content || !DOMPurify) return '';
    return DOMPurify.sanitize(marked.parse(content) as string);
  }, [content, DOMPurify]);

  return <div className="markdown-body" dangerouslySetInnerHTML={{ __html: html }} />;
}
