import '@testing-library/jest-dom';

// Node 22+ ships a native global `localStorage` that shadows jsdom's and is
// degraded without `--localstorage-file`. Replace it with a fully-featured
// in-memory implementation so the API client's token helpers work in tests.
const store = new Map<string, string>();

const localStorageMock: Storage = {
  getItem: (key: string) => store.get(key) ?? null,
  setItem: (key: string, value: string) => {
    store.set(key, String(value));
  },
  removeItem: (key: string) => {
    store.delete(key);
  },
  clear: () => {
    store.clear();
  },
  key: (index: number) => Array.from(store.keys())[index] ?? null,
  get length() {
    return store.size;
  },
};

Object.defineProperty(globalThis, 'localStorage', {
  value: localStorageMock,
  writable: true,
  configurable: true,
});
