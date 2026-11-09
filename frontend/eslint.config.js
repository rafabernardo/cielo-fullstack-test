import fs from 'node:fs';
import js from '@eslint/js';
import globals from 'globals';
import checkFile from 'eslint-plugin-check-file';
import importPlugin from 'eslint-plugin-import';
import react from 'eslint-plugin-react';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import testingLibrary from 'eslint-plugin-testing-library';
import jestDom from 'eslint-plugin-jest-dom';
import prettierRecommended from 'eslint-plugin-prettier/recommended';
import tseslint from 'typescript-eslint';
import { defineConfig, globalIgnores } from 'eslint/config';

const featuresDir = 'src/features';
const features = fs.existsSync(featuresDir)
  ? fs.readdirSync(featuresDir, { withFileTypes: true }).filter((d) => d.isDirectory())
  : [];
const appLayer = ['./src/app', './src/app.tsx'];
const shared = ['components', 'config', 'hooks', 'lib', 'testing', 'types', 'utils'];

// Architecture rules from AGENTS.md: no cross-feature imports, shared -> features -> app.
const architectureZones = [
  // a feature may not import from any other feature
  ...features.map(({ name }) => ({
    target: `./${featuresDir}/${name}`,
    from: `./${featuresDir}`,
    except: [`./${name}`],
  })),
  // features may not import from app
  { target: `./${featuresDir}`, from: appLayer },
  // shared code may not import from features or app
  ...shared.map((dir) => ({
    target: `./src/${dir}`,
    from: [`./${featuresDir}`, ...appLayer],
  })),
];

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      importPlugin.flatConfigs.errors,
      importPlugin.flatConfigs.warnings,
      importPlugin.flatConfigs.typescript,
      tseslint.configs.recommended,
      react.configs.flat.recommended,
      react.configs.flat['jsx-runtime'],
      reactHooks.configs.flat.recommended,
      jsxA11y.flatConfigs.recommended,
      testingLibrary.configs['flat/react'],
      jestDom.configs['flat/recommended'],
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      globals: globals.browser,
    },
    settings: {
      react: { version: '19' },
      'import/resolver': { typescript: true, node: true },
    },
  },
  {
    files: ['src/**/*'],
    plugins: { 'check-file': checkFile },
    rules: {
      'check-file/filename-naming-convention': [
        'error',
        { '**/*.{ts,tsx}': 'KEBAB_CASE' },
        // ignore middle extensions to support names like smoke.spec.ts
        { ignoreMiddleExtensions: true },
      ],
      'check-file/folder-naming-convention': [
        'error',
        // all folders within src (except __tests__) must be kebab-case
        { 'src/**': 'KEBAB_CASE' },
        { ignoreWords: ['__tests__'] },
      ],
    },
  },
  {
    files: ['src/**/*.{ts,tsx}'],
    rules: { 'import/no-restricted-paths': ['error', { zones: architectureZones }] },
  },
  // Must stay last: disables conflicting rules and runs Prettier as a rule.
  prettierRecommended,
]);
