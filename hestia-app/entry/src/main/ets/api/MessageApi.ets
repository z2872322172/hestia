import { ApiClient } from './ApiClient';
import { PageResult } from '../model/ApiTypes';
import { Notification } from '../model/Message';

/** 后端 unread-count 返回 { "unread": n }，不是裸数字。 */
export interface UnreadCountVo {
  unread: number;
}

/** 后端 read-all 返回 { "marked": n }。 */
export interface MarkedCountVo {
  marked: number;
}

/** 消息域接口（/api/v1/notifications）。 */
export class MessageApi {
  static list(onlyUnread: boolean, page: number, size: number): Promise<PageResult<Notification>> {
    const path: string = '/notifications?onlyUnread=' + (onlyUnread ? 'true' : 'false') +
      '&page=' + page.toString() + '&size=' + size.toString();
    return ApiClient.get<PageResult<Notification>>(path);
  }

  static async unreadCount(): Promise<number> {
    const vo: UnreadCountVo = await ApiClient.get<UnreadCountVo>('/notifications/unread-count');
    return vo.unread;
  }

  static markRead(notificationNo: string): Promise<Object> {
    return ApiClient.put<Object>(
      '/notifications/' + encodeURIComponent(notificationNo) + '/read');
  }

  static readAll(): Promise<Object> {
    return ApiClient.put<Object>('/notifications/read-all');
  }
}
