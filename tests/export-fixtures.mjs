/**
 * Export clock.js snapshots so the Android port can be checked against the same instants.
 *
 *   node tests/export-fixtures.mjs          # write android test fixtures
 *   node tests/export-fixtures.mjs --check  # fail if the committed fixtures are stale
 */
import { readFileSync, writeFileSync } from "node:fs";
import {
  snapshot,
  formatHMS,
  holidayLabelCN,
  holidayLabelEN,
  workStatusCN,
  holidaysInYear,
} from "../clock.js";

const outPath = new URL("../android/app/src/test/resources/clock-fixtures.json", import.meta.url);

function pad(n) {
  return String(n).padStart(2, "0");
}

function stamp(ms) {
  return new Date(ms).toISOString().replace(/\.000Z$/, "Z");
}

const instants = new Set();

const testSrc = readFileSync(new URL("./clock-core.test.mjs", import.meta.url), "utf8");
for (const date of testSrc.match(/20\d\d-\d\d-\d\d/g) || []) {
  instants.add(`${date}T18:00:00Z`);
}
for (const utc of testSrc.match(/20\d\d-\d\d-\d\dT[0-9:.]+Z/g) || []) {
  instants.add(utc);
}

for (let t = Date.parse("2026-01-01T18:00:00Z"); t <= Date.parse("2026-12-31T18:00:00Z"); t += 86400000) {
  instants.add(stamp(t));
}

function addRange(start, end, step) {
  for (let t = Date.parse(start); t <= Date.parse(end); t += step) instants.add(stamp(t));
}

addRange("2026-11-23T12:00:00Z", "2026-11-24T02:00:00Z", 60_000);
addRange("2026-03-08T05:30:00Z", "2026-03-08T10:30:00Z", 60_000);
addRange("2026-11-01T04:30:00Z", "2026-11-01T09:30:00Z", 60_000);
addRange("2026-03-07T22:00:00Z", "2026-03-09T16:00:00Z", 30 * 60_000);
addRange("2026-10-31T20:00:00Z", "2026-11-02T16:00:00Z", 30 * 60_000);

for (let year = 2018; year <= 2035; year++) {
  for (const [month, day] of [
    [1, 1], [1, 2], [1, 19], [1, 20],
    [3, 2],
    [6, 18], [6, 19], [6, 20], [6, 21],
    [7, 2], [7, 3], [7, 4], [7, 5],
    [11, 26], [11, 27],
    [12, 24], [12, 25], [12, 30], [12, 31],
  ]) {
    instants.add(`${year}-${pad(month)}-${pad(day)}T18:00:00Z`);
  }
}

for (const utc of [
  "2026-11-23T23:29:30.001Z",
  "2026-11-23T23:29:59.999Z",
  "2026-03-08T07:59:59.999Z",
  "2026-03-08T08:00:00.001Z",
  "2026-11-01T06:59:59.999Z",
  "2026-11-01T07:00:00.001Z",
]) {
  instants.add(utc);
}

function record(utc) {
  const s = snapshot(new Date(utc));
  const hms = formatHMS(s.remainingSeconds);
  return {
    utc,
    time: s.time,
    timeWithSeconds: s.timeWithSeconds,
    periodCN: s.periodCN,
    periodEN: s.periodEN,
    dateCN: s.dateCN,
    dateEN: s.dateEN,
    weekdayCN: s.weekdayCN,
    weekdayEN: s.weekdayEN,
    zone: s.zone,
    chinaOffsetHours: s.chinaOffsetHours,
    holidayCN: s.holidayCN,
    holidayEN: s.holidayEN,
    isWeekend: s.isWeekend,
    upcomingHolidayCN: s.upcomingHolidayCN,
    upcomingHolidayEN: s.upcomingHolidayEN,
    daysUntilHoliday: s.daysUntilHoliday,
    isWorking: s.isWorking,
    isWeekday: s.isWeekday,
    phase: s.phase,
    workMinutesRemaining: s.workMinutesRemaining,
    remainingSeconds: s.remainingSeconds,
    workCountdownCN: s.workCountdownCN,
    workCountdownEN: s.workCountdownEN,
    holidayLabelCN: holidayLabelCN(s),
    holidayLabelEN: holidayLabelEN(s),
    workStatusCN: workStatusCN(s),
    hmsHours: hms.hours,
    hmsMinutes: hms.minutes,
    hmsSeconds: hms.seconds,
    hmsText: hms.text,
  };
}

const cases = [...instants].sort().map(record);
const holidayYears = [];
for (let year = 1985; year <= 2036; year++) {
  holidayYears.push({
    year,
    days: holidaysInYear(year).map((h) => ({
      year: h.year,
      month: h.month,
      day: h.day,
      nameCN: h.nameCN,
      nameEN: h.nameEN,
    })),
  });
}

const payload = { cases, holidayYears };
const text = `${JSON.stringify(payload)}\n`;

if (process.argv.includes("--check")) {
  const current = readFileSync(outPath, "utf8");
  if (current !== text) {
    console.error("clock-fixtures.json is stale. Run: node tests/export-fixtures.mjs");
    process.exit(1);
  }
  console.log(`PASS: fixtures match clock.js (${cases.length} instants, ${holidayYears.length} years)`);
} else {
  writeFileSync(outPath, text);
  console.log(`Wrote ${cases.length} instants to ${outPath.pathname}`);
}
