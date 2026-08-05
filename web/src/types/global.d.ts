// Next 内置类型只声明了 *.module.css（见 node_modules/next/types/global.d.ts），
// 全局 CSS 导入（如 import '@/styles/globals.css'）在 IDE 的 TS server 下会报
// "Cannot find module"。此处补一个 ambient 声明，仅用于类型检查，不影响运行时
// （CSS 的实际加载由 Next 编译器处理）。
declare module '*.css';
