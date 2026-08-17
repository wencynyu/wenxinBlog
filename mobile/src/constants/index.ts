// 全局常量（避免 magic string 散落各处）
export const TOKEN_KEY = 'auth_token';

// zustand persist 的 AsyncStorage 存储 key
export const AUTH_STORAGE_KEY = 'auth-storage';

// 网关 API 地址：构建期由 EXPO_PUBLIC_* 环境变量内联（.env.example 有说明）
export const API_BASE_URL = process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:8080';

// 列表分页默认每页条数
export const DEFAULT_PAGE_SIZE = 10;
