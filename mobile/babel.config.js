module.exports = function (api) {
  api.cache(true);
  return {
    // jsxImportSource 显式钉给 babel：tsconfig 里的同名配置不被 babel-preset-expo
    // 读取时，JSX 会回落到 react/jsx-runtime，导致 className 被 RN 静默丢弃（全 app 无样式）。
    presets: [['babel-preset-expo', { jsxImportSource: 'nativewind' }]],
  };
};
