import type { ReactNode } from 'react'

/** Ícones de traço fino desenhados para o projeto (sem biblioteca externa). Sempre decorativos: o texto ao lado dá o sentido. */
const PATHS = {
  home: (
    <>
      <path d="M3.5 10.5 12 3.5l8.5 7" />
      <path d="M5.5 9v11h13V9" />
      <path d="M10 20v-5.5h4V20" />
    </>
  ),
  orders: (
    <>
      <rect x="5.5" y="4.5" width="13" height="16" rx="2" />
      <path d="M9 3.5h6v3H9z" />
      <path d="M9 11h6M9 14.5h6M9 18h3.5" />
    </>
  ),
  production: (
    <>
      <circle cx="12" cy="13.5" r="7.5" />
      <path d="M12 9.5v4l2.5 2.5M10 2.5h4M12 2.5V6" />
    </>
  ),
  calendar: (
    <>
      <rect x="3.5" y="5" width="17" height="15.5" rx="2.5" />
      <path d="M3.5 10h17M8 3v4M16 3v4" />
    </>
  ),
  finance: (
    <>
      <path d="M4.5 8V7a2.5 2.5 0 0 1 2.5-2.5h10V8" />
      <rect x="3.5" y="8" width="17" height="12" rx="2.5" />
      <path d="M16 14h.01" />
    </>
  ),
  cake: (
    <>
      <path d="M3.5 20.5h17" />
      <path d="M5 20.5V14a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v6.5" />
      <path d="M5 16.2c1.2.9 2.3.9 3.5 0s2.3-.9 3.5 0 2.3.9 3.5 0 2.3-.9 3.5 0" />
      <path d="M12 12V8.5" />
      <path d="M12 6c.9-.8.9-1.7 0-2.5-.9.8-.9 1.7 0 2.5z" />
    </>
  ),
  settings: (
    <>
      <path d="M4 6.5h9M17.5 6.5H20M4 12h3M11.5 12H20M4 17.5h11M19.5 17.5h.5" />
      <circle cx="15.2" cy="6.5" r="2.2" />
      <circle cx="9.2" cy="12" r="2.2" />
      <circle cx="17.2" cy="17.5" r="2.2" />
    </>
  ),
  bell: (
    <>
      <path d="M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 2h-15z" />
      <path d="M10 20.5a2 2 0 0 0 4 0" />
    </>
  ),
  logout: (
    <>
      <path d="M14.5 4H18a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3.5" />
      <path d="M10 8l-4 4 4 4M6 12h10" />
    </>
  ),
  menu: <path d="M4 7h16M4 12h16M4 17h10" />,
  close: <path d="M6 6l12 12M18 6 6 18" />,
  check: <path d="M5 12.5l4.5 4.5L19 7.5" />,
  arrowRight: <path d="M5 12h14M13 6l6 6-6 6" />,
  arrowLeft: <path d="M19 12H5M11 6l-6 6 6 6" />,
  clock: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7.5V12l3 2" />
    </>
  ),
  alert: (
    <>
      <path d="M12 4 21 19.5H3z" />
      <path d="M12 10v4M12 17h.01" />
    </>
  ),
  info: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 11v5M12 8h.01" />
    </>
  ),
  refresh: (
    <>
      <path d="M19.5 11A7.5 7.5 0 0 0 6 6.6L4.5 8" />
      <path d="M4.5 4v4h4" />
      <path d="M4.5 13A7.5 7.5 0 0 0 18 17.4l1.5-1.4" />
      <path d="M19.5 20v-4h-4" />
    </>
  ),
  message: <path d="M20 11.5a8 8 0 0 1-11.8 7L4 20l1.5-4.1A8 8 0 1 1 20 11.5z" />,
  instagram: (
    <>
      <rect x="4" y="4" width="16" height="16" rx="5" />
      <circle cx="12" cy="12" r="3.5" />
      <path d="M16.8 7.2h.01" />
    </>
  ),
  mapPin: (
    <>
      <path d="M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 0 1 13 0c0 5.4-6.5 11-6.5 11z" />
      <circle cx="12" cy="10" r="2.3" />
    </>
  ),
  sparkle: <path d="M12 3.5l1.7 5L18.5 10l-4.8 1.6L12 16.5l-1.7-4.9L5.5 10l4.8-1.5z" />,
  image: (
    <>
      <rect x="3.5" y="4.5" width="17" height="15" rx="2.5" />
      <circle cx="9" cy="10" r="1.7" />
      <path d="M20.5 16l-5-5-9 8.5" />
    </>
  ),
  copy: (
    <>
      <rect x="8.5" y="8.5" width="11.5" height="11.5" rx="2" />
      <path d="M15.5 8.5V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v7.5a2 2 0 0 0 2 2h2.5" />
    </>
  ),
  search: (
    <>
      <circle cx="11" cy="11" r="6.5" />
      <path d="M16 16l4 4" />
    </>
  ),
  bag: (
    <>
      <path d="M5 8.5h14l-1.1 11.5H6.1z" />
      <path d="M9 8.5V7a3 3 0 0 1 6 0v1.5" />
    </>
  ),
  receipt: (
    <>
      <path d="M6 3.5h12v17l-3-2-3 2-3-2-3 2z" />
      <path d="M9 8h6M9 11.5h6M9 15h3.5" />
    </>
  ),
  chevronDown: <path d="M6 9l6 6 6-6" />,
} satisfies Record<string, ReactNode>

export type IconName = keyof typeof PATHS

export function Icon({ name, size = 20, className }: { name: IconName; size?: number; className?: string }) {
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {PATHS[name]}
    </svg>
  )
}
