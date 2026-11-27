import { snapshot } from "../clock.js";

let assertions = 0;
let failed = 0;

function equal(actual, expected, label) {
  assertions += 1;
  if (actual !== expected) {
    failed += 1;
    console.error(`FAIL: ${label}. Expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
  }
}

function snap(utc) {
  return snapshot(new Date(utc));
}

// UTC fixtures make host locale and host time zone irrelevant.
const beforeMidnight = snap("2026-11-15T05:59:00Z");
equal(beforeMidnight.time, "11:59", "Dallas time before midnight");
equal(beforeMidnight.periodCN, "下午", "Chinese PM");
equal(beforeMidnight.periodEN, "PM", "English PM");
equal(beforeMidnight.dateCN, "2026年11月14日", "Dallas date, not Shanghai date");
equal(beforeMidnight.dateEN, "NOV 14, 2026", "Stable English date");
equal(beforeMidnight.weekdayCN, "星期六", "Chinese Saturday");
equal(beforeMidnight.weekdayEN, "Saturday", "English Saturday");
equal(beforeMidnight.isWeekend, true, "Saturday is weekend");
equal(beforeMidnight.zone, "CST", "Dallas winter zone");
equal(beforeMidnight.chinaOffsetHours, -14, "Winter China offset");
equal(beforeMidnight.upcomingHolidayEN, "Thanksgiving Day", "Next holiday from Nov 14");
equal(beforeMidnight.daysUntilHoliday, 12, "Countdown uses Dallas dates");

const midnight = snap("2026-11-15T06:00:00Z");
equal(midnight.time, "12:00", "12-hour midnight");
equal(midnight.periodCN, "上午", "Chinese midnight AM");
equal(midnight.periodEN, "AM", "English midnight AM");
equal(midnight.dateCN, "2026年11月15日", "Dallas midnight advances date");
equal(midnight.weekdayEN, "Sunday", "Dallas midnight advances weekday");
equal(midnight.isWeekend, true, "Sunday is weekend");
equal(midnight.daysUntilHoliday, 11, "Countdown advances at Dallas midnight");
const noon = snap("2026-11-15T18:00:00Z");
equal(noon.time, "12:00", "12-hour noon");
equal(noon.periodCN, "下午", "Chinese noon PM");
equal(noon.periodEN, "PM", "English noon PM");
equal(snap("2026-11-16T06:00:00Z").isWeekend, false, "Monday is weekday");

const beforeSpring = snap("2026-03-08T07:59:00Z");
const afterSpring = snap("2026-03-08T08:00:00Z");
equal(beforeSpring.time, "1:59", "Before spring DST jump");
equal(beforeSpring.zone, "CST", "Before spring zone");
equal(beforeSpring.chinaOffsetHours, -14, "Before spring offset");
equal(afterSpring.time, "3:00", "Spring skips 2 AM");
equal(afterSpring.zone, "CDT", "After spring zone");
equal(afterSpring.chinaOffsetHours, -13, "After spring offset");
equal(afterSpring.periodEN, "AM", "Spring AM");
equal(afterSpring.daysUntilHoliday, beforeSpring.daysUntilHoliday, "DST does not change calendar countdown");
equal(snap("2026-03-07T18:00:00Z").daysUntilHoliday, 79, "Countdown spans a 23-hour day");

const beforeFall = snap("2026-11-01T06:59:00Z");
const afterFall = snap("2026-11-01T07:00:00Z");
equal(beforeFall.time, "1:59", "Before fall DST rollback");
equal(beforeFall.zone, "CDT", "Before fall zone");
equal(beforeFall.chinaOffsetHours, -13, "Before fall offset");
equal(afterFall.time, "1:00", "Fall repeats 1 AM");
equal(afterFall.zone, "CST", "After fall zone");
equal(afterFall.chinaOffsetHours, -14, "After fall offset");
equal(afterFall.daysUntilHoliday, beforeFall.daysUntilHoliday, "Repeated hour keeps countdown");

const holidays2026 = [
  ["2026-01-01", "New Year's Day"],
  ["2026-01-19", "Birthday of Martin Luther King, Jr."],
  ["2026-02-16", "Washington's Birthday"],
  ["2026-05-25", "Memorial Day"],
  ["2026-06-19", "Juneteenth National Independence Day"],
  ["2026-07-03", "Independence Day (observed)"],
  ["2026-09-07", "Labor Day"],
  ["2026-10-12", "Columbus Day"],
  ["2026-11-11", "Veterans Day"],
  ["2026-11-26", "Thanksgiving Day"],
  ["2026-12-25", "Christmas Day"],
];
for (const [date, name] of holidays2026) {
  equal(snap(date + "T18:00:00Z").holidayEN, name, `2026 OPM holiday ${date}`);
}
equal(snap("2026-11-26T05:59:00Z").holidayEN, null, "Holiday waits for Dallas midnight");
const thanksgiving = snap("2026-11-26T06:00:00Z");
equal(thanksgiving.holidayCN, "感恩节", "Thanksgiving starts at Dallas midnight");
equal(thanksgiving.weekdayEN, "Thursday", "Thanksgiving weekday");
equal(thanksgiving.upcomingHolidayEN, "Christmas Day", "Upcoming excludes today's holiday");
equal(thanksgiving.daysUntilHoliday, 29, "Days from Thanksgiving to Christmas");
equal(snap("2026-11-27T18:00:00Z").holidayEN, null, "Black Friday is not federal");

const july3 = snap("2026-07-03T18:00:00Z");
const july4 = snap("2026-07-04T18:00:00Z");
equal(july3.holidayCN, "独立日（调休）", "Observed marker in Chinese");
equal(july3.isWeekend, false, "Observed Friday is a weekday");
equal(july3.upcomingHolidayEN, "Independence Day", "Actual date follows observed date");
equal(july3.daysUntilHoliday, 1, "One day from observed to actual");
equal(july4.holidayEN, "Independence Day", "Actual Saturday is recognized");
equal(july4.holidayCN, "独立日", "Actual date has no observed marker");
equal(july4.isWeekend, true, "Actual Saturday remains weekend");
equal(snap("2026-07-05T18:00:00Z").holidayEN, null, "Adjacent Sunday is not a holiday");
equal(snap("2026-07-02T18:00:00Z").upcomingHolidayEN, "Independence Day (observed)", "Upcoming uses observed date first");

equal(snap("2021-06-18T18:00:00Z").holidayEN, "Juneteenth National Independence Day (observed)", "Juneteenth Saturday observed Friday");
equal(snap("2021-06-19T18:00:00Z").holidayEN, "Juneteenth National Independence Day", "Juneteenth actual date");
equal(snap("2020-06-19T18:00:00Z").holidayEN, null, "Juneteenth was not yet a federal holiday");
equal(snap("2022-06-19T18:00:00Z").holidayEN, "Juneteenth National Independence Day", "Juneteenth Sunday actual date");
equal(snap("2022-06-20T18:00:00Z").holidayEN, "Juneteenth National Independence Day (observed)", "Sunday observed on Monday");

const crossYear = snap("2021-12-31T18:00:00Z");
equal(crossYear.holidayEN, "New Year's Day (observed)", "Next year's New Year observed in prior year");
equal(crossYear.holidayCN, "元旦（调休）", "Cross-year Chinese observed marker");
equal(crossYear.upcomingHolidayEN, "New Year's Day", "Next actual holiday crosses year");
equal(crossYear.daysUntilHoliday, 1, "Cross-year countdown");
equal(snap("2021-12-30T18:00:00Z").upcomingHolidayEN, "New Year's Day (observed)", "Upcoming finds next year's prior-year observance");
equal(snap("2022-01-01T18:00:00Z").holidayEN, "New Year's Day", "Actual New Year is recognized");
equal(snap("2021-01-20T18:00:00Z").holidayEN, null, "DC Inauguration Day is excluded");
equal(snap("2026-03-02T18:00:00Z").holidayEN, null, "Texas Independence Day is excluded");

const sundayEvening = snap("2026-11-22T23:45:00Z");
equal(sundayEvening.isWorking, false, "Sunday is outside the shift");
equal(sundayEvening.workMinutesRemaining, 885, "Sunday 17:45 to Monday 08:30 is 14h45m");
equal(sundayEvening.workCountdownCN, "距上班 14小时45分", "Chinese start countdown");
equal(sundayEvening.workCountdownEN, "Work starts in 14h 45m", "English start countdown");
equal(snap("2026-11-23T14:29:00Z").workMinutesRemaining, 1, "One minute before work");
const shiftStart = snap("2026-11-23T14:30:00Z");
equal(shiftStart.isWorking, true, "08:30 switches to working");
equal(shiftStart.workMinutesRemaining, 540, "Full shift is nine elapsed hours");
equal(shiftStart.workCountdownCN, "距下班 9小时00分", "Chinese end countdown");
equal(shiftStart.workCountdownEN, "Work ends in 9h 00m", "English end countdown");
equal(snap("2026-11-23T23:29:30Z").workMinutesRemaining, 1, "Partial minute rounds up");
const shiftEnd = snap("2026-11-23T23:30:00Z");
equal(shiftEnd.isWorking, false, "17:30 switches to next start");
equal(shiftEnd.workMinutesRemaining, 900, "Overnight break is 15 hours");
equal(snap("2026-11-27T23:30:00Z").workMinutesRemaining, 3780, "Friday close to Monday start is 63 hours");
equal(snap("2026-03-07T23:30:00Z").workMinutesRemaining, 2280, "Spring weekend countdown uses 38 elapsed hours");
equal(snap("2026-10-31T22:30:00Z").workMinutesRemaining, 2400, "Fall weekend countdown uses 40 elapsed hours");
equal(snap("2026-11-26T14:30:00Z").isWorking, true, "Federal holiday alone does not define company leave");

if (failed) {
  console.error(`FAILED ${failed} of ${assertions} assertions`);
  process.exit(1);
}
console.log(`PASS: ${assertions} ClockCore assertions`);
