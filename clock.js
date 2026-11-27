/**
 * Dallas Clock core — ported from the Mac app ClockCore.swift (v2.2.0).
 * Timezone: America/Chicago. Work hours: Mon–Fri 08:30–17:30 Dallas time.
 * Federal holidays are displayed, but do not by themselves pause the work countdown.
 */
export const DALLAS_TZ = "America/Chicago";
export const SHANGHAI_TZ = "Asia/Shanghai";

const MONTHS_EN = ["JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"];
const WEEKDAYS_EN = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
const WEEKDAYS_CN = ["星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"];

const PARTS_OPTS = {
  timeZone: DALLAS_TZ,
  hourCycle: "h23",
  year: "numeric",
  month: "numeric",
  day: "numeric",
  hour: "numeric",
  minute: "numeric",
  second: "numeric",
  weekday: "short",
};

const dallasPartsFmt = new Intl.DateTimeFormat("en-US", PARTS_OPTS);
const shanghaiPartsFmt = new Intl.DateTimeFormat("en-US", { ...PARTS_OPTS, timeZone: SHANGHAI_TZ });
const dallasNameFmt = new Intl.DateTimeFormat("en-US", {
  timeZone: DALLAS_TZ,
  timeZoneName: "short",
  hour: "numeric",
});

function partsOf(date, fmt = dallasPartsFmt) {
  const out = { year: 0, month: 0, day: 0, hour: 0, minute: 0, second: 0, weekdayText: "" };
  for (const p of fmt.formatToParts(date)) {
    if (p.type === "year") out.year = Number(p.value);
    else if (p.type === "month") out.month = Number(p.value);
    else if (p.type === "day") out.day = Number(p.value);
    else if (p.type === "hour") out.hour = Number(p.value);
    else if (p.type === "minute") out.minute = Number(p.value);
    else if (p.type === "second") out.second = Number(p.value);
    else if (p.type === "weekday") out.weekdayText = p.value;
  }
  return out;
}

/** Sunday = 1 … Saturday = 7, matching Foundation Calendar. */
function weekdayIndex(year, month, day) {
  // Use noon UTC as a stable civil-date weekday; Gregorian weekday is timezone-independent for a Y-M-D.
  // But we need the weekday of that Dallas calendar date, which is the same as the civil weekday.
  return new Date(Date.UTC(year, month - 1, day, 12, 0, 0)).getUTCDay() + 1;
}

function pad2(n) {
  return String(n).padStart(2, "0");
}

/**
 * Offset of `timeZone` from UTC at `date`, in milliseconds
 * (e.g. CST = -6h = -21600000).
 */
export function zoneOffsetMs(date, timeZone) {
  const fmt = timeZone === DALLAS_TZ ? dallasPartsFmt : timeZone === SHANGHAI_TZ ? shanghaiPartsFmt : new Intl.DateTimeFormat("en-US", { ...PARTS_OPTS, timeZone });
  const p = partsOf(date, fmt);
  const asUtc = Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second);
  return asUtc - date.getTime();
}

/** Instant corresponding to a wall-clock time in America/Chicago. */
export function dallasLocalDate(year, month, day, hour = 0, minute = 0, second = 0) {
  const utcGuess = Date.UTC(year, month - 1, day, hour, minute, second);
  let date = new Date(utcGuess + 6 * 3600 * 1000); // CST first guess (behind UTC)
  for (let i = 0; i < 4; i++) {
    const off = zoneOffsetMs(date, DALLAS_TZ);
    date = new Date(utcGuess - off);
  }
  return date;
}

function startOfDallasDay(date) {
  const p = partsOf(date);
  return dallasLocalDate(p.year, p.month, p.day, 0, 0, 0);
}

function addDays(year, month, day, delta) {
  const dt = new Date(Date.UTC(year, month - 1, day + delta));
  return { year: dt.getUTCFullYear(), month: dt.getUTCMonth() + 1, day: dt.getUTCDate() };
}

function daysInMonth(year, month) {
  return new Date(Date.UTC(year, month, 0)).getUTCDate();
}

function calendarDaysBetween(a, b) {
  const au = Date.UTC(a.year, a.month - 1, a.day);
  const bu = Date.UTC(b.year, b.month - 1, b.day);
  return Math.round((bu - au) / 86400000);
}

function nthWeekday(target, ordinal, month, year) {
  const firstWeekday = weekdayIndex(year, month, 1);
  const day = 1 + ((target - firstWeekday + 7) % 7) + (ordinal - 1) * 7;
  return { year, month, day };
}

function lastMonday(month, year) {
  const lastDay = daysInMonth(year, month);
  const lastWeekday = weekdayIndex(year, month, lastDay);
  const day = lastDay - ((lastWeekday - 2 + 7) % 7);
  return { year, month, day };
}

function civilKey(year, month, day) {
  return `${year}-${pad2(month)}-${pad2(day)}`;
}

/**
 * Recurring nationwide federal holidays, using OPM Monday–Friday observance.
 * No Texas-only, commercial, DC inauguration, or one-off executive holidays.
 */
export function holidaysInYear(year) {
  const date = (month, day) => ({ year, month, day });
  const actual = [
    { ...date(1, 1), nameCN: "元旦", nameEN: "New Year's Day" },
    { ...nthWeekday(2, 3, 2, year), nameCN: "华盛顿诞辰日", nameEN: "Washington's Birthday" },
    { ...lastMonday(5, year), nameCN: "阵亡将士纪念日", nameEN: "Memorial Day" },
    { ...date(7, 4), nameCN: "独立日", nameEN: "Independence Day" },
    { ...nthWeekday(2, 1, 9, year), nameCN: "劳动节", nameEN: "Labor Day" },
    { ...nthWeekday(2, 2, 10, year), nameCN: "哥伦布日", nameEN: "Columbus Day" },
    { ...date(11, 11), nameCN: "退伍军人节", nameEN: "Veterans Day" },
    { ...nthWeekday(5, 4, 11, year), nameCN: "感恩节", nameEN: "Thanksgiving Day" },
    { ...date(12, 25), nameCN: "圣诞节", nameEN: "Christmas Day" },
  ];
  if (year >= 1986) {
    actual.push({ ...nthWeekday(2, 3, 1, year), nameCN: "马丁·路德·金纪念日", nameEN: "Birthday of Martin Luther King, Jr." });
  }
  if (year >= 2021) {
    actual.push({ ...date(6, 19), nameCN: "六月节", nameEN: "Juneteenth National Independence Day" });
  }

  const observed = [];
  for (const holiday of actual) {
    const wd = weekdayIndex(holiday.year, holiday.month, holiday.day);
    let shift = 0;
    if (wd === 7) shift = -1; // Saturday -> preceding Friday
    else if (wd === 1) shift = 1; // Sunday -> following Monday
    else continue;
    const shifted = addDays(holiday.year, holiday.month, holiday.day, shift);
    observed.push({
      ...shifted,
      nameCN: holiday.nameCN + "（调休）",
      nameEN: holiday.nameEN + " (observed)",
    });
  }
  return actual.concat(observed);
}

function allHolidays(year) {
  const list = [];
  for (let y = year - 1; y <= year + 1; y++) list.push(...holidaysInYear(y));
  list.sort((a, b) => {
    if (a.year !== b.year) return a.year - b.year;
    if (a.month !== b.month) return a.month - b.month;
    return a.day - b.day;
  });
  return list;
}

function isDaylightSaving(date) {
  const name = dallasNameFmt.formatToParts(date).find((p) => p.type === "timeZoneName")?.value || "";
  return name === "CDT";
}

function zoneAbbreviation(date) {
  return isDaylightSaving(date) ? "CDT" : "CST";
}

function padCountdownMinutes(n) {
  return String(n).padStart(2, "0");
}

export function workCountdownCN(isWorking, workMinutesRemaining) {
  return `距${isWorking ? "下班" : "上班"} ${Math.floor(workMinutesRemaining / 60)}小时${padCountdownMinutes(workMinutesRemaining % 60)}分`;
}

export function workCountdownEN(isWorking, workMinutesRemaining) {
  return `Work ${isWorking ? "ends" : "starts"} in ${Math.floor(workMinutesRemaining / 60)}h ${padCountdownMinutes(workMinutesRemaining % 60)}m`;
}

export function snapshot(now = new Date()) {
  const parts = partsOf(now);
  const year = parts.year;
  const month = parts.month;
  const day = parts.day;
  const hour = parts.hour;
  const minute = parts.minute;
  const second = parts.second;
  const weekday = weekdayIndex(year, month, day);
  const today = { year, month, day };

  const holidays = allHolidays(year);
  const todayKey = civilKey(year, month, day);
  const current = holidays.find((h) => civilKey(h.year, h.month, h.day) === todayKey) || null;
  const upcoming = holidays.find((h) => {
    if (h.year > year) return true;
    if (h.year < year) return false;
    if (h.month > month) return true;
    if (h.month < month) return false;
    return h.day > day;
  });
  const daysUntilHoliday = upcoming ? calendarDaysBetween(today, upcoming) : 0;

  const start = dallasLocalDate(year, month, day, 8, 30, 0);
  const end = dallasLocalDate(year, month, day, 17, 30, 0);
  const isWeekday = weekday >= 2 && weekday <= 6;
  const isWorking = isWeekday && now >= start && now < end;

  let workTarget;
  if (isWorking) {
    workTarget = end;
  } else if (isWeekday && now < start) {
    workTarget = start;
  } else {
    let next = addDays(year, month, day, 1);
    while (!(weekdayIndex(next.year, next.month, next.day) >= 2 && weekdayIndex(next.year, next.month, next.day) <= 6)) {
      next = addDays(next.year, next.month, next.day, 1);
    }
    workTarget = dallasLocalDate(next.year, next.month, next.day, 8, 30, 0);
  }

  const remainingMs = workTarget.getTime() - now.getTime();
  const workMinutesRemaining = Math.ceil(remainingMs / 60000);
  const remainingSeconds = Math.max(0, Math.ceil(remainingMs / 1000));

  const dallasOff = zoneOffsetMs(now, DALLAS_TZ);
  const shanghaiOff = zoneOffsetMs(now, SHANGHAI_TZ);
  const chinaOffsetHours = Math.round((dallasOff - shanghaiOff) / 3600000);

  const hour12 = hour % 12 === 0 ? 12 : hour % 12;
  const zone = zoneAbbreviation(now);
  const isWeekend = weekday === 1 || weekday === 7;
  const phase = isWorking ? "working" : isWeekend ? "weekend" : isWeekday && now < start ? "before" : "after";

  return {
    time: `${hour12}:${pad2(minute)}`,
    timeWithSeconds: `${hour12}:${pad2(minute)}:${pad2(second)}`,
    periodCN: hour < 12 ? "上午" : "下午",
    periodEN: hour < 12 ? "AM" : "PM",
    dateCN: `${year}年${month}月${day}日`,
    dateEN: `${MONTHS_EN[month - 1]} ${day}, ${year}`,
    weekdayCN: WEEKDAYS_CN[weekday - 1],
    weekdayEN: WEEKDAYS_EN[weekday - 1],
    zone,
    chinaOffsetHours,
    holidayCN: current ? current.nameCN : null,
    holidayEN: current ? current.nameEN : null,
    isWeekend,
    upcomingHolidayCN: upcoming ? upcoming.nameCN : "",
    upcomingHolidayEN: upcoming ? upcoming.nameEN : "",
    daysUntilHoliday,
    isWorking,
    isWeekday,
    phase,
    workMinutesRemaining,
    remainingSeconds,
    workCountdownCN: workCountdownCN(isWorking, workMinutesRemaining),
    workCountdownEN: workCountdownEN(isWorking, workMinutesRemaining),
  };
}

export function formatHMS(totalSeconds) {
  const s = Math.max(0, totalSeconds);
  const hours = Math.floor(s / 3600);
  const minutes = Math.floor((s % 3600) / 60);
  const seconds = s % 60;
  return { hours, minutes, seconds, text: `${hours}:${pad2(minutes)}:${pad2(seconds)}` };
}

export function holidayLabelCN(s) {
  if (s.holidayCN) return s.holidayCN;
  return s.isWeekend ? "周末 · 非联邦假日" : "非联邦假日";
}

export function holidayLabelEN(s) {
  if (s.holidayEN) return s.holidayEN;
  return s.isWeekend ? "Weekend · No federal holiday" : "No federal holiday";
}

export function workStatusCN(s) {
  if (s.phase === "working") return "正在上班";
  if (s.phase === "before") return "即将上班";
  if (s.phase === "after") return "已下班";
  if (s.phase === "weekend") return "周末";
  return "休息中";
}
