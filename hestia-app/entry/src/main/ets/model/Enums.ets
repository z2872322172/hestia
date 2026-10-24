import { Palette } from '../common/Theme';

/** 订单主状态（后端 BookingStatus，共 7 个）。 */
export class BookingStatus {
  static readonly PENDING_PAYMENT: string = 'PENDING_PAYMENT';
  static readonly CONFIRMED: string = 'CONFIRMED';
  static readonly EN_ROUTE: string = 'EN_ROUTE';
  static readonly IN_SERVICE: string = 'IN_SERVICE';
  static readonly PENDING_CONFIRM: string = 'PENDING_CONFIRM';
  static readonly COMPLETED: string = 'COMPLETED';
  static readonly CANCELED: string = 'CANCELED';
}

/** 状态标签的文案与配色。 */
export interface StatusStyle {
  text: string;
  color: string;
  bg: string;
}

export function orderStatusStyle(status: string): StatusStyle {
  if (status === BookingStatus.PENDING_PAYMENT) {
    const s: StatusStyle = { text: '待支付', color: Palette.warnText, bg: Palette.warnBg };
    return s;
  }
  if (status === BookingStatus.CONFIRMED) {
    const s: StatusStyle = { text: '待服务', color: Palette.warnText, bg: Palette.warnBg };
    return s;
  }
  if (status === BookingStatus.EN_ROUTE) {
    const s: StatusStyle = { text: '服务人员已出发', color: Palette.brandDeep, bg: Palette.brandXLight };
    return s;
  }
  if (status === BookingStatus.IN_SERVICE) {
    const s: StatusStyle = { text: '服务中', color: Palette.brandDeep, bg: Palette.brandXLight };
    return s;
  }
  if (status === BookingStatus.PENDING_CONFIRM) {
    const s: StatusStyle = { text: '待你确认', color: Palette.brandDeep, bg: Palette.brandXLight };
    return s;
  }
  if (status === BookingStatus.COMPLETED) {
    const s: StatusStyle = { text: '已完成', color: Palette.ink3, bg: Palette.grayBg };
    return s;
  }
  if (status === BookingStatus.CANCELED) {
    const s: StatusStyle = { text: '已取消', color: Palette.ink3, bg: Palette.grayBg };
    return s;
  }
  const s: StatusStyle = { text: status, color: Palette.ink3, bg: Palette.grayBg };
  return s;
}

/** 订单详情的状态头文案（比标签更长一点，带一句引导）。 */
export function orderStatusHeadline(status: string): string {
  if (status === BookingStatus.PENDING_PAYMENT) {
    return '请尽快完成支付';
  }
  if (status === BookingStatus.CONFIRMED) {
    return '服务人员将按预约时间上门';
  }
  if (status === BookingStatus.EN_ROUTE) {
    return '服务人员正在前往您的地址';
  }
  if (status === BookingStatus.IN_SERVICE) {
    return '服务进行中，请留意验收';
  }
  if (status === BookingStatus.PENDING_CONFIRM) {
    return '服务已完成，请确认验收';
  }
  if (status === BookingStatus.COMPLETED) {
    return '感谢您的信任，期待再次为您服务';
  }
  if (status === BookingStatus.CANCELED) {
    return '订单已取消';
  }
  return '';
}

/** 订单 Tab 的前端聚合分组。后端 status 只接受单值，待服务/服务中需合并两个状态。 */
export interface OrderTabGroup {
  label: string;
  /** 需要请求的状态列表，空数组表示"全部"（不传 status） */
  statuses: string[];
}

export const ORDER_TAB_GROUPS: OrderTabGroup[] = [
  { label: '全部', statuses: [] },
  { label: '待支付', statuses: [BookingStatus.PENDING_PAYMENT] },
  { label: '待服务', statuses: [BookingStatus.CONFIRMED, BookingStatus.EN_ROUTE] },
  { label: '服务中', statuses: [BookingStatus.IN_SERVICE, BookingStatus.PENDING_CONFIRM] },
  { label: '已完成', statuses: [BookingStatus.COMPLETED] }
];

/** 地址片区匹配状态。 */
export function areaMatchStyle(status: string): StatusStyle {
  if (status === 'MATCHED') {
    const s: StatusStyle = { text: '✓ 在服务范围内', color: Palette.okText, bg: Palette.okBg };
    return s;
  }
  if (status === 'OUT_OF_RANGE') {
    const s: StatusStyle = { text: '暂不在服务范围内', color: Palette.danger, bg: '#FDECEC' };
    return s;
  }
  const s: StatusStyle = { text: '', color: Palette.ink3, bg: Palette.grayBg };
  return s;
}
