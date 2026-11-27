import {
  snapshot,
  formatHMS,
  holidayLabelCN,
  holidayLabelEN,
  workStatusCN,
} from "./clock.js";

const els = {
  time: document.getElementById("time"),
  period: document.getElementById("period"),
  date: document.getElementById("date"),
  weekday: document.getElementById("weekday"),
  holiday: document.getElementById("holiday"),
  china: document.getElementById("china"),
  status: document.getElementById("status"),
  countdownLabel: document.getElementById("countdown-label"),
  hours: document.getElementById("hours"),
  minutes: document.getElementById("minutes"),
  seconds: document.getElementById("seconds"),
  countdownCn: document.getElementById("countdown-cn"),
  countdownEn: document.getElementById("countdown-en"),
  upcoming: document.getElementById("upcoming"),
};

function pad2(n) {
  return String(n).padStart(2, "0");
}

function render(now = new Date()) {
  const s = snapshot(now);
  const hms = formatHMS(s.remainingSeconds);
  const status = workStatusCN(s);
  const holiday = holidayLabelCN(s);

  els.time.textContent = s.timeWithSeconds;
  els.period.textContent = `${s.periodCN} ${s.periodEN} · ${s.zone}`;
  els.date.textContent = s.dateCN;
  els.weekday.textContent = `${s.weekdayCN} · ${s.weekdayEN.toUpperCase()}`;
  els.holiday.textContent = holiday;
  els.holiday.classList.toggle("is-today", Boolean(s.holidayCN));
  els.china.textContent = `比中国慢 ${Math.abs(s.chinaOffsetHours)} 小时 · ${s.zone === "CDT" ? "夏令时" : "标准时"}`;
  els.status.textContent = status;
  els.status.classList.toggle("off", s.phase !== "working");
  els.countdownLabel.textContent = s.isWorking ? "距下班" : "距上班";
  els.hours.textContent = String(hms.hours);
  els.minutes.textContent = pad2(hms.minutes);
  els.seconds.textContent = pad2(hms.seconds);
  els.countdownCn.textContent = s.workCountdownCN;
  els.countdownEn.textContent = s.workCountdownEN;
  els.upcoming.textContent = `下个假日：${s.upcomingHolidayCN}（${s.daysUntilHoliday} 天后）`;

  document.title = `${s.time} ${s.periodEN} · ${els.countdownLabel.textContent} ${hms.text}`;
  const live = document.getElementById("live");
  if (live) {
    live.textContent = `达拉斯 ${s.periodCN} ${s.timeWithSeconds} ${s.periodEN} ${s.zone}，${s.dateCN} ${s.weekdayCN}，${holiday}，${status}，${s.workCountdownCN}`;
  }
}

function loop() {
  render();
  const delay = 1000 - (Date.now() % 1000) + 20;
  setTimeout(loop, delay);
}

const about = document.getElementById("about");
document.getElementById("open-about").addEventListener("click", () => about.showModal());
document.getElementById("close-about").addEventListener("click", () => about.close());

document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "visible") render();
});

if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("./sw.js").catch(() => {});
}

loop();
