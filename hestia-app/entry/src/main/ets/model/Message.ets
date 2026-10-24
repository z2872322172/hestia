/** 站内消息（NotificationView.java）。readAt 为空串表示未读。 */
export interface Notification {
  notificationNo: string;
  title: string;
  content: string;
  businessType: string;
  businessId: string;
  priority: string;
  readAt: string;
  createdAt: string;
}

/** 消息筛选分组。后端没有 businessType 参数，这里只能前端过滤。 */
export interface MessageFilter {
  label: string;
  /** 空串表示不过滤 */
  businessType: string;
}

export const MESSAGE_FILTERS: MessageFilter[] = [
  { label: '全部', businessType: '' },
  { label: '订单通知', businessType: 'BOOKING' },
  { label: '系统消息', businessType: 'SYSTEM' }
];
