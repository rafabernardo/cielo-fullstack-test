import { z } from 'zod';

const processEnv = () => {
  const schema = z.object({
    // Base of the backend API, e.g. `/api`. Relative goes through the dev proxy / nginx: the backend has no CORS.
    VITE_API_URL: z.string().min(1),
  });

  const parsed = schema.safeParse(import.meta.env);

  if (!parsed.success) {
    throw new Error(`Invalid environment variables:\n${z.prettifyError(parsed.error)}`);
  }

  return parsed.data;
};

export const envs = processEnv();
