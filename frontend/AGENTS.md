# CLAUDE.md

## Workflow

- **Package manager** - Yarn 1 (`yarn add`, `yarn <script>`); `yarn.lock` is the lockfile
- **Scaffold with Plop** - Create components and features through the generators:
  - `yarn generate component` - asks for a name and an optional feature; empty feature → `src/components/`, otherwise `src/features/<feature>/components/`
  - `yarn generate feature` - creates `src/features/<name>/` with the standard folders
- **Component shape** - A folder `<name>/` holding `index.tsx` (named PascalCase export) and `<name>.module.css`
- **SVG as component** - `import Logo from '@/assets/logo.svg?react';`
- **Done criterion** - A task is complete when `yarn lint` and `yarn build` (which runs `tsc -b`) both pass

## Project Structure

The codebase follows a feature-based architecture:

```
src/
├── app/         # Application layer (routes, providers, router)
├── components/  # Shared UI components
├── config/      # Global configurations and env variables
├── features/    # Feature-based modules (auth, discussions, comments, etc.)
├── hooks/       # Shared React hooks
├── lib/         # Preconfigured libraries (react-query, auth, etc.)
├── testing/     # Test utilities and mocks
├── types/       # Shared TypeScript types
└── utils/       # Shared utility functions
```

### Feature Structure

Each feature should be self-contained:

```
src/features/awesome-feature/
├── api/         # API calls and hooks for this feature
├── components/  # Feature-specific components
├── hooks/       # Feature-specific hooks
├── stores/      # Feature-specific state
├── types/       # Feature-specific types
└── utils/       # Feature-specific utilities
```

## Code Standards

### TypeScript

- **Strict mode enabled** - All TypeScript strict checks are enforced
- **Type-first approach** - Define types before implementation
- **Absolute imports** - Use `@/` for imports that cross folders (`@/components/button`); use `./` only for files in the same folder (`./button.module.css`)

### Code Style

- ESLint + Prettier configured for consistent formatting
- `kebab-case` for file and folder names
- `PascalCase` for React components
- `camelCase` for functions and variables

### Architecture Rules

- **No cross-feature imports** - Features should not import from each other
- **Unidirectional flow** - Code flows: shared → features → app
- **Colocation** - Keep related code as close as possible to where it's used

## Styling

- **CSS Modules** - One `*.module.css` per component, colocated and named after it (`login-form.module.css`)
- **Kebab-case classes** - Written as `.login-form` in CSS, used as `styles.loginForm` (`localsConvention: 'camelCaseOnly'` in `vite.config.ts`)
- **Native nesting syntax** - Nest with `&`; `postcss-nesting` flattens it and `autoprefixer` adds vendor prefixes (`postcss.config.js`)
- **Design tokens** - Colors, type, spacing, radius, shadow and sizes are CSS variables on `:root` in `src/tokens.css` (from the AVEscape design system); never hardcode them in modules
- **Global styles** - `src/tokens.css` and `src/index.css` only
- **No other global styles** - Everything else is scoped to its module

```css
/* login-form.module.css */
.login-form {
  display: grid;
  gap: 1rem;
  background: var(--color-surface);

  & .input {
    border: 1px solid var(--color-border);

    &:focus-visible {
      outline: 2px solid var(--color-primary);
    }
  }
}
```

```tsx
import styles from './login-form.module.css';

<form className={styles.loginForm} />;
```

## Component Guidelines

- **Composition over props** - Use children/slots instead of many props
- **Single responsibility** - Each component should have one clear purpose
- **Extract render functions** - Move complex JSX into separate components
- **Limit prop count** - Consider composition if accepting too many props

## State Management Strategy

### Component State

- `useState` for simple independent state
- `useReducer` for complex state with multiple related updates

### Application State

- Redux for global application state (modals, notifications, themes)
- Keep state as close to usage as possible
- Avoid premature globalization

### Server State

- React Query (TanStack Query) for all server state management
- MSW (Mock Service Worker) for API mocking during development
- Separate fetcher functions from hooks

### Form State

- React Hook Form for form management
- Zod for form validation schemas
- Create reusable `Form` and `Input` components

## API Layer

### Structure

Each API endpoint should have:

1. Types & validation schemas for request/response
2. Fetcher function using the configured API client
3. React Query hook for data fetching/caching

### Example Pattern

```ts
// api/get-discussions.ts
export const getDiscussions = (params: GetDiscussionsParams): Promise<Discussion[]> => {
  return api.get('/discussions', { params });
};

export const useDiscussions = (params: GetDiscussionsParams) => {
  return useQuery({
    queryKey: ['discussions', params],
    queryFn: () => getDiscussions(params),
  });
};
```

## Testing Strategy

### Testing Pyramid

1. **Integration tests** (primary focus) - Test feature workflows
2. **Unit tests** - Test shared utilities and complex logic
3. **E2E tests** - Test critical user journeys

### Tools

- **Vitest** - Test runner (Jest-compatible but faster)
- **Testing Library** - Component testing utilities
- **Playwright** - E2E testing framework
- **MSW** - API mocking for tests

### Testing Patterns

- Test behavior, not implementation details
- Use real HTTP requests with MSW instead of mocking `fetch`
- Focus on user interactions and outcomes

## Security Considerations

### Authentication

- JWT tokens stored in HttpOnly cookies (preferred) or localStorage
- React Query Auth for user state management
- Automatic token refresh handling

### Authorization

- RBAC (Role-Based Access Control) for basic permissions
- PBAC (Permission-Based Access Control) for granular control
- Client-side authorization for UX only (always validate server-side)

### XSS Prevention

- Sanitize all user inputs before rendering
- Use DOMPurify for HTML content sanitization
- Validate and escape data at boundaries

## Performance Optimization

### Code Splitting

- **Route-level splitting** - Lazy load pages/routes
- Avoid excessive splitting (balance requests vs. bundle size)

### React Optimizations

- **Children prop pattern** - Prevent unnecessary re-renders
- **State colocation** - Keep state close to where it's used
- **State initializer functions** - For expensive initial computations

### Image Optimization

- Lazy loading for images outside the viewport
- Modern formats (WebP) with fallbacks
- Responsive images using `srcset`