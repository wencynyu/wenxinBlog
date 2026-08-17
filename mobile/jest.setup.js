// 原生模块在 Node 测试环境中不可用，用内存实现模拟
jest.mock('expo-secure-store', () => {
  const store = new Map();
  return {
    setItemAsync: jest.fn(async (key, value) => {
      store.set(key, value);
    }),
    getItemAsync: jest.fn(async (key) => store.get(key) ?? null),
    deleteItemAsync: jest.fn(async (key) => {
      store.delete(key);
    }),
    __store: store,
  };
});

jest.mock('@react-native-async-storage/async-storage', () => {
  const store = new Map();
  return {
    setItem: jest.fn(async (key, value) => {
      store.set(key, value);
    }),
    getItem: jest.fn(async (key) => store.get(key) ?? null),
    removeItem: jest.fn(async (key) => {
      store.delete(key);
    }),
    __store: store,
  };
});
