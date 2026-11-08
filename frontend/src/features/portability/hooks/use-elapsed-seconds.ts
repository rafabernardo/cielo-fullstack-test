import { useEffect, useState } from 'react';

const secondsSince = (instant: string) =>
  Math.max(0, Math.floor((Date.now() - Date.parse(instant)) / 1000));

// Seconds elapsed since `instant`, updated every second.
export const useElapsedSeconds = (instant: string) => {
  const [seconds, setSeconds] = useState(() => secondsSince(instant));

  useEffect(() => {
    const timer = setInterval(() => setSeconds(secondsSince(instant)), 1000);
    return () => clearInterval(timer);
  }, [instant]);

  return seconds;
};
