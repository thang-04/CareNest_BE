// Bộ sinh ngẫu nhiên có seed cố định: chạy lại generator luôn ra cùng một bộ dữ liệu.
export function createRng(seed) {
  let state = seed >>> 0;
  const next = () => {
    // mulberry32
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  const int = (min, max) => min + Math.floor(next() * (max - min + 1));
  const pick = (items) => items[Math.floor(next() * items.length)];
  // items: [[value, weight], ...]
  const weighted = (items) => {
    const total = items.reduce((sum, [, w]) => sum + w, 0);
    let roll = next() * total;
    for (const [value, w] of items) {
      roll -= w;
      if (roll < 0) return value;
    }
    return items[items.length - 1][0];
  };
  const chance = (p) => next() < p;
  const digits = (n) => Array.from({ length: n }, () => int(0, 9)).join('');
  return { next, int, pick, weighted, chance, digits };
}
