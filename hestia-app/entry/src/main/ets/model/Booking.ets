/** 订单摘要（BookingSummaryView.java）。 */
export interface BookingSummary {
  orderNo: string;
  serviceName: string;
  orderStatus: string;
  paymentStatus: string;
  appointmentDate: string;
  appointmentStart: string;
  payableAmount: number;
  createdAt: string;
}

/** 订单明细（BookingDetailView.java）。字段与后端 record 一一对应。 */
export interface BookingDetail {
  orderNo: string;
  customerId: number;
  customerNo: string;
  orderStatus: string;
  paymentStatus: string;
  assignmentStatus: string;
  fulfillmentStatus: string;
  afterSaleStatus: string;
  reviewStatus: string;
  serviceItemNo: string;
  serviceName: string;
  serviceItemVersion: number;
  serviceAreaId: number;
  serviceAreaName: string;
  appointmentDate: string;
  serviceWindowName: string;
  appointmentStart: string;
  appointmentEnd: string;
  currentWorkerId: number;
  workerSlotId: number;
  serviceAmount: number;
  addonAmount: number;
  materialAmount: number;
  travelFee: number;
  discountAmount: number;
  payableAmount: number;
  paidAmount: number;
  refundedAmount: number;
  currency: string;
  contactName: string;
  contactPhone: string;
  detailAddress: string;
  cancelReasonCode: string;
  cancelReasonText: string;
  paymentDeadline: string;
  source: string;
  items: BookingItem[];
  createdAt: string;
  paidAt: string;
  canceledAt: string;
}

export interface BookingItem {
  itemType: string;
  itemCode: string;
  itemName: string;
  unitPrice: number;
  quantity: number;
  amount: number;
}

/** 下单结果（BookingCreatedView.java）。 */
export interface BookingCreated {
  orderNo: string;
  orderStatus: string;
  payableAmount: number;
  paymentDeadline: string;
}

/** 可约时段（AvailabilityView.java）。 */
export interface Availability {
  serviceDate: string;
  windows: WindowAvailability[];
}

export interface WindowAvailability {
  windowCode: string;
  windowName: string;
  startTime: string;
  endTime: string;
  available: boolean;
}

/** 标准时间窗（ServiceWindowView.java）。 */
export interface ServiceWindow {
  windowId: number;
  windowCode: string;
  windowName: string;
  startTime: string;
  endTime: string;
  sortOrder: number;
  status: string;
  version: number;
}

/**
 * 履约进度（FulfillmentView.java）。
 *
 * 订单详情 6 段进度时间线的真实数据源，来自
 * GET /api/v1/customer/bookings/{orderNo}/fulfillment。
 * 这里只声明 App 用到的字段，JSON 里的多余字段（checklist/evidences/exceptions）会被忽略。
 */
export interface Fulfillment {
  id: number;
  recordNo: string;
  bookingId: number;
  workerId: number;
  status: string;
  departedAt: string;
  checkInAt: string;
  startedAt: string;
  finishedAt: string;
  completionRequestedAt: string;
  confirmationDeadline: string;
  customerConfirmedAt: string;
  serviceResult: string;
}

/** 时间线单步。 */
export interface TimelineStep {
  title: string;
  time: string;
  /** done 已完成 / now 进行中 / todo 未开始 */
  state: string;
}
