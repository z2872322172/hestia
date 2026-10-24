/** 金额与日期格式化。所有页面统一走这里，避免各页写法不一致。 */
export class Format {
  /** 后端 LocalDateTime 形如 2026-10-24T14:30:00，无时区。手动解析避免 Date 解析歧义。 */
  static parseDateTime(iso: string): Date | null {
    if (iso === undefined || iso === null || iso.length < 10) {
      return null;
    }
    const m: RegExpExecArray | null =
      new RegExp('^(\\d{4})-(\\d{2})-(\\d{2})[T ]?(\\d{2})?:?(\\d{2})?').exec(iso);
    if (m === null) {
      return null;
    }
    const year: number = Number.parseInt(m[1], 10);
    const month: number = Number.parseInt(m[2], 10) - 1;
    const day: number = Number.parseInt(m[3], 10);
    const hour: number = m[4] === undefined ? 0 : Number.parseInt(m[4], 10);
    const minute: number = m[5] === undefined ? 0 : Number.parseInt(m[5], 10);
    return new Date(year, month, day, hour, minute, 0);
  }

  private static pad(n: number): string {
    return n < 10 ? '0' + n.toString() : n.toString();
  }

  /** 2026-10-24T14:30:00 → 10-24 14:30 */
  static dateTime(iso: string): string {
    const d: Date | null = Format.parseDateTime(iso);
    if (d === null) {
      return '';
    }
    return Format.pad(d.getMonth() + 1) + '-' + Format.pad(d.getDate()) +
      ' ' + Format.pad(d.getHours()) + ':' + Format.pad(d.getMinutes());
  }

  /** 2026-10-24 → 10-24 */
  static monthDay(dateStr: string): string {
    if (dateStr.length < 10) {
      return dateStr;
    }
    return dateStr.substring(5, 10);
  }

  /** 2026-10-24 → 24 */
  static dayOfMonth(dateStr: string): string {
    if (dateStr.length < 10) {
      return dateStr;
    }
    return dateStr.substring(8, 10);
  }

  /** 2026-10-24 → 周六 */
  static weekday(dateStr: string): string {
    const d: Date | null = Format.parseDateTime(dateStr);
    if (d === null) {
      return '';
    }
    const names: string[] = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
    return names[d.getDay()];
  }

  /** 14:30:00 → 14:30 */
  static clock(timeStr: string): string {
    if (timeStr.length >= 5) {
      return timeStr.substring(0, 5);
    }
    return timeStr;
  }

  /** 金额：整数不显示小数，小数保留两位 */
  static money(amount: number): string {
    if (amount === undefined || amount === null || Number.isNaN(amount)) {
      return '0';
    }
    if (Math.abs(amount - Math.round(amount)) < 0.005) {
      return Math.round(amount).toString();
    }
    return amount.toFixed(2);
  }

  /** 秒 → 14:32 */
  static countdown(totalSeconds: number): string {
    if (totalSeconds <= 0) {
      return '00:00';
    }
    const minutes: number = Math.floor(totalSeconds / 60);
    const seconds: number = totalSeconds % 60;
    return Format.pad(minutes) + ':' + Format.pad(seconds);
  }

  /** 今天起第 offset 天的 yyyy-MM-dd */
  static dateAfter(offset: number): string {
    const d: Date = new Date();
    d.setDate(d.getDate() + offset);
    return d.getFullYear().toString() + '-' + Format.pad(d.getMonth() + 1) + '-' + Format.pad(d.getDate());
  }

  /** 未来 days 天的日期数组 */
  static dateRange(days: number): string[] {
    const result: string[] = [];
    for (let i = 0; i < days; i++) {
      result.push(Format.dateAfter(i));
    }
    return result;
  }

  /** 手机号脱敏 138****8000（后端已脱敏时原样返回） */
  static maskPhone(phone: string): string {
    if (phone.length !== 11) {
      return phone;
    }
    return phone.substring(0, 3) + '****' + phone.substring(7);
  }
}
