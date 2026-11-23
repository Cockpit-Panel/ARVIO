"use client";

import { Check, ChevronDown } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "@/lib/i18n";
import { CALENDAR_SOURCE_LABELS, type CalendarSource } from "@/lib/calendar";

export function CalendarSourceMenu({ value, sources, counts, onChange }: {
  value: "all" | CalendarSource; sources: CalendarSource[];
  counts?: Partial<Record<"all" | CalendarSource, number>>; onChange: (value: "all" | CalendarSource) => void;
}) {
  const t = useTranslation();
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null), trigger = useRef<HTMLButtonElement>(null);
  const options = ["all", ...sources] as const;
  const label = (id: typeof value) => id === "all" ? t("All watchlists") : CALENDAR_SOURCE_LABELS[id];
  const close = () => { setOpen(false); trigger.current?.focus({ preventScroll: true }); };
  useEffect(() => {
    if (!open) return;
    root.current?.querySelector<HTMLButtonElement>(`[data-calendar-source="${value}"]`)?.focus();
    const outside = (event: MouseEvent) => { if (!root.current?.contains(event.target as Node)) setOpen(false); };
    document.addEventListener("mousedown", outside);
    return () => document.removeEventListener("mousedown", outside);
  }, [open, value]);
  return <div className="calendar-source-selector" ref={root}>
    <button className="calendar-source-trigger" ref={trigger} aria-label={t("Calendar watchlist source")}
      aria-haspopup="listbox" aria-expanded={open} onClick={() => setOpen(!open)}
      onKeyDown={event => { if (event.key === "ArrowDown" || event.key === "ArrowUp") { event.preventDefault(); event.stopPropagation(); setOpen(true); } }}>
      {label(value)}<ChevronDown size={16} />
    </button>
    {open && <div className="calendar-source-menu" role="listbox" aria-label={t("Watchlists")} onKeyDown={event => {
      const rows = Array.from(root.current?.querySelectorAll<HTMLButtonElement>('[role="option"]') ?? []);
      const index = rows.indexOf(document.activeElement as HTMLButtonElement);
      if (["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key)) {
        event.preventDefault(); event.stopPropagation();
        const next = event.key === "Home" ? 0 : event.key === "End" ? rows.length - 1 : Math.max(0, Math.min(rows.length - 1, index + (event.key === "ArrowDown" ? 1 : -1)));
        rows[next]?.focus();
      } else if (event.key === "Escape") { event.preventDefault(); event.stopPropagation(); close(); }
      else if (event.key === "Tab") setOpen(false);
    }}>
      {options.map(id => <button key={id} role="option" aria-selected={value === id} data-calendar-source={id}
        onClick={() => { onChange(id); close(); }}>
        <span className="calendar-menu-source-icon" aria-hidden="true">{id !== "all" && <img src={id === "arvio" ? "/arvio-icon-192.png" : `/logos/calendar_${id}.${id === "trakt" ? "svg" : "png"}`} alt="" />}</span>
        <span>{label(id)}</span><small>{counts?.[id]}</small><span className="calendar-menu-check">{value === id && <Check size={18} />}</span>
      </button>)}
    </div>}
  </div>;
}
