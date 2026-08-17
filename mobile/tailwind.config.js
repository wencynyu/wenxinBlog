/** @type {import('tailwindcss').Config} */
module.exports = {
  presets: [require('nativewind/preset')],
  content: ['./app/**/*.{js,ts,jsx,tsx}', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        // 主色：Semi 标志蓝 #0077FA（与 web 端 tailwind.config.js 色板一致）
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
        // 中性语义色：引用 global.css 的 CSS 变量，随系统深色模式翻转
        ink: {
          DEFAULT: 'var(--color-ink)',
          muted: 'var(--color-ink-muted)',
          faint: 'var(--color-ink-faint)',
        },
        canvas: 'var(--color-canvas)',
        surface: 'var(--color-surface)',
        hairline: 'var(--color-hairline)',
      },
      borderRadius: {
        // 与 web 端统一：卡片/输入/按钮 10px，大面 16px
        DEFAULT: '10',
        xl: '12',
        '2xl': '16',
      },
    },
  },
  plugins: [],
};
