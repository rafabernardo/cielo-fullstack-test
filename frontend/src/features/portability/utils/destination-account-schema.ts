import { z } from 'zod';

// Mirrors the backend rules for destinationAccount (backend/docs/API.md).
export const destinationAccountSchema = z.object({
  bankCode: z.string().regex(/^\d{3}$/, 'Banco deve ter 3 dígitos'),
  branch: z.string().regex(/^\d{4}$/, 'Agência deve ter 4 dígitos'),
  accountNumber: z
    .string()
    .regex(
      /^\d{1,12}(-[0-9Xx])?$/,
      'Conta: até 12 dígitos, com dígito verificador opcional (ex.: 123456-7)',
    ),
});

export type DestinationAccountValues = z.infer<typeof destinationAccountSchema>;
