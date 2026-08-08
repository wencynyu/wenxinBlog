/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './src/pages/**/*.{js,ts,jsx,tsx,mdx}',
    './src/components/**/*.{js,ts,jsx,tsx,mdx}',
    './src/app/**/*.{js,ts,jsx,tsx,mdx}',
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        // 主色：Semi 标志蓝 #0077FA（与 Semi 组件原生品牌蓝一致）
        primary: {
          50: '#e8f3ff',
          100: '#c7e1ff',
          200: '#97c6ff',
          300: '#6baeff',
          400: '#3d95ff',
          500: '#1b80ff',
          600: '#0077fa',
          700: '#0060d4',
          800: '#004eae',
          900: '#003c88',
          DEFAULT: '#0077fa',
        },
        // 强调「字面量」琥珀（仅用于最关键的 CTA / 精选标记）
        accent: {
          50: '#fff8eb',
          100: '#ffefc6',
          200: '#ffdd88',
          300: '#ffc649',
          400: '#f2a516',
          500: '#e08600',
          600: '#bb6400',
          700: '#944800',
          800: '#7a3a00',
          900: '#663000',
          DEFAULT: '#e08600',
        },
        // 中性语义色：引用 CSS 变量，随 body[theme-mode] 自动暗色翻转（globals.css 定义）
        ink: {
          DEFAULT: 'var(--color-ink)',
          muted: 'var(--color-ink-muted)',
          faint: 'var(--color-ink-faint)',
        },
        canvas: 'var(--color-canvas)',
        surface: 'var(--color-surface)',
        hairline: 'var(--color-hairline)',
      },
      fontFamily: {
        sans: ['var(--font-sans)', 'PingFang SC', 'Microsoft YaHei', 'system-ui', 'sans-serif'],
        serif: ['var(--font-serif)', 'Songti SC', 'STSong', 'SimSun', 'serif'],
        mono: ['var(--font-mono)', 'SF Mono', 'Menlo', 'monospace'],
      },
      borderRadius: {
        // 统一圆角：默认 10px（卡片/输入/按钮共用），大面用 16px
        DEFAULT: '0.625rem',
        xl: '0.75rem',
        '2xl': '1rem',
      },
      boxShadow: {
        card: '0 1px 3px rgba(15,23,42,0.05), 0 8px 24px rgba(15,23,42,0.07)',
        'card-hover': '0 4px 8px rgba(15,23,42,0.08), 0 20px 44px rgba(15,23,42,0.12)',
        soft: '0 1px 2px rgba(15,23,42,0.05)',
      },
    },
  },
  plugins: [],
  // Semi Design 兼容 - 禁用 preflight 避免 CSS reset 冲突
  corePlugins: {
    preflight: false,
  },
};
