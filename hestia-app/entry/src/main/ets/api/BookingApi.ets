import { ApiClient } from './ApiClient';
import { PageResult } from '../model/ApiTypes';
import { OptionSelection, PricePreview } from '../model/Catalog';
import {
  Availability,
  BookingCreated,
  BookingDetail,
  BookingSummary,
  Fulfillment,
  ServiceWindow
} from '../model/Booking';

/** 下单请求体（BookingCreateRequest.java，注意规格字段名是 options 不是 selectedOptions）。 */
export interface BookingCreateBody {
  serviceItemNo: string;
  appointmentDate: string;
  serviceWindowCode: string;
  addressNo: string;
  quantity: number;
  options: OptionSelection[];
}

export interface CancelBody {
  reasonCode: string;
  reasonText: string;
}

export interface RescheduleBody {
  appointmentDate: string;
  serviceWindowCode: string;
}

/** 订单域接口。 */
export class BookingApi {
  /**
   * 可约时段。区间最长 14 天，dateFrom 不得早于今天。
   * serviceAreaId 为空（<=0）时不拼该参数，否则后端可能匹配不到区域。
   */
  static availability(serviceItemNo: string, serviceAreaId: number,
                      dateFrom: string, dateTo: string): Promise<Availability[]> {
    let path: string = '/public/availability?serviceItemNo=' + encodeURIComponent(serviceItemNo) +
      '&dateFrom=' + dateFrom + '&dateTo=' + dateTo;
    if (serviceAreaId > 0) {
      path = path + '&serviceAreaId=' + serviceAreaId.toString();
    }
    return ApiClient.get<Availability[]>(path);
  }

  static serviceWindows(): Promise<ServiceWindow[]> {
    return ApiClient.get<ServiceWindow[]>('/public/service-windows');
  }

  /** 下单前价格预览（只算钱不落库）。 */
  static preview(body: BookingCreateBody): Promise<PricePreview> {
    return ApiClient.post<PricePreview>('/customer/bookings/preview', body);
  }

  /** 创建订单。必须带 Idempotency-Key，同键改内容会 409。 */
  static create(body: BookingCreateBody, idemKey: string): Promise<BookingCreated> {
    return ApiClient.post<BookingCreated>('/customer/bookings', body, idemKey);
  }

  static list(status: string, page: number, size: number): Promise<PageResult<BookingSummary>> {
    let path: string = '/customer/bookings?page=' + page.toString() + '&size=' + size.toString();
    if (status.length > 0) {
      path = path + '&status=' + encodeURIComponent(status);
    }
    return ApiClient.get<PageResult<BookingSummary>>(path);
  }

  /**
   * 按多个状态取订单并合并。
   *
   * 后端 status 只接受单值，而前端"待服务"= CONFIRMED + EN_ROUTE，
   * "服务中"= IN_SERVICE + PENDING_CONFIRM，所以要发多个请求再合并排序。
   */
  static async listByStatuses(statuses: string[], page: number,
                              size: number): Promise<BookingSummary[]> {
    if (statuses.length === 0) {
      const result: PageResult<BookingSummary> = await BookingApi.list('', page, size);
      return result.records;
    }
    const tasks: Promise<PageResult<BookingSummary>>[] = [];
    for (let i = 0; i < statuses.length; i++) {
      tasks.push(BookingApi.list(statuses[i], page, size));
    }
    const results: PageResult<BookingSummary>[] = await Promise.all(tasks);
    let merged: BookingSummary[] = [];
    for (let i = 0; i < results.length; i++) {
      merged = merged.concat(results[i].records);
    }
    merged.sort((a: BookingSummary, b: BookingSummary) => b.createdAt.localeCompare(a.createdAt));
    return merged;
  }

  static detail(orderNo: string): Promise<BookingDetail> {
    return ApiClient.get<BookingDetail>('/customer/bookings/' + encodeURIComponent(orderNo));
  }

  /** 履约进度：订单详情 6 段时间线的真实数据源。 */
  static fulfillment(orderNo: string): Promise<Fulfillment> {
    return ApiClient.get<Fulfillment>(
      '/customer/bookings/' + encodeURIComponent(orderNo) + '/fulfillment');
  }

  static cancel(orderNo: string, reasonCode: string, reasonText: string): Promise<Object> {
    const body: CancelBody = { reasonCode: reasonCode, reasonText: reasonText };
    return ApiClient.post<Object>('/customer/bookings/' + encodeURIComponent(orderNo) + '/cancel', body);
  }

  static reschedule(orderNo: string, date: string, windowCode: string): Promise<Object> {
    const body: RescheduleBody = { appointmentDate: date, serviceWindowCode: windowCode };
    return ApiClient.post<Object>(
      '/customer/bookings/' + encodeURIComponent(orderNo) + '/reschedule', body);
  }

  /** 客户确认完成：履约与订单转 COMPLETED，开放评价。 */
  static confirm(orderNo: string): Promise<Fulfillment> {
    return ApiClient.post<Fulfillment>(
      '/customer/bookings/' + encodeURIComponent(orderNo) + '/confirm');
  }
}
