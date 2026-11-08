import { useId, type ComponentProps } from 'react';
import styles from './text-field.module.css';

type TextFieldProps = ComponentProps<'input'> & {
  label: string;
  error?: string;
};

// Labelled input with its validation message; spread register() from React Hook Form into it.
export const TextField = ({ label, error, ...inputProps }: TextFieldProps) => {
  const id = useId();
  const errorId = `${id}-error`;

  return (
    <div className={styles.textField}>
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        aria-invalid={error !== undefined}
        aria-describedby={error ? errorId : undefined}
        {...inputProps}
      />
      {error && (
        <p id={errorId} className={styles.error}>
          {error}
        </p>
      )}
    </div>
  );
};
