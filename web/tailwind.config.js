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
        // 主色「关键字」靛蓝（替换原 Ant-Blue sky）
        primary: {
          50: '#eef0ff',
          100: '#e0e3ff',
          200: '#c7ccfe',
          300: '#a5acfc',
          400: '#8189f8',
          500: '#6366f1',
          600: '#4f46e5',
          700: '#3930d8',
          800: '#3730a3',
          900: '#312e81',
          DEFAULT: '#4f46e5',
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
        // 中性「墨」色阶
        ink: {
          DEFAULT: '#1e2026',
          muted: '#5b6470',
          faint: '#8a94a6',
        },
        canvas: '#f7f8fa',
        surface: '#ffffff',
        hairline: '#e6e8ec',
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
