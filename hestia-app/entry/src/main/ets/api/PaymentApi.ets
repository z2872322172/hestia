import { ApiClient } from './ApiClient';

/** 支付请求体（CustomerPaymentController.PayRequest）。 */
export interface PayBody {
  channel: string;
}

/** 支付创建结果（PaymentCreatedView.java）。 */
export interface PaymentCreated {
  paymentNo: string;
  amount: number;
  channel: string;
  status: string;
}

/**
 * 支付域接口。
 *
 * 注意：后端首版只注册了 MockPaymentGateway，PaymentApplicationService 里会校验
 * gateway.channel().equals(channel)，传 WECHAT / ALIPAY 会直接 400 PAYMENT_CHANNEL_DISABLED。
 * 所以 UI 上可以展示多种支付方式，实际提交的 channel 一律是 MOCK。
 */
export class PaymentApi {
  static readonly CHANNEL_MOCK: string = 'MOCK';

  static pay(orderNo: string, channel: string, idemKey: string): Promise<PaymentCreated> {
    const body: PayBody = { channel: channel };
    return ApiClient.post<PaymentCreated>(
      '/customer/bookings/' + encodeURIComponent(orderNo) + '/pay', body, idemKey);
  }
}
