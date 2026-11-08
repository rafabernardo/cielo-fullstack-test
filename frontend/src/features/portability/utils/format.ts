const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const dateTime = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });

export const formatCurrency = (amount: number) => currency.format(amount);

// Splits the string instead of using Date, which would shift the day in timezones behind UTC.
export const formatDate = (isoDate: string) => isoDate.split('-').reverse().join('/');

export const formatDateTime = (instant: string) => dateTime.format(new Date(instant));
