import { OptionSelection } from '../model/Catalog';

/** 路由名常量。所有跳转只用这里的字符串，避免手写拼错。 */
export class RouteName {
  static readonly WELCOME: string = 'WELCOME';
  static readonly MAIN: string = 'MAIN';
  static readonly LOGIN: string = 'LOGIN';
  static readonly SERVICE_LIST: string = 'SERVICE_LIST';
  static readonly SERVICE_DETAIL: string = 'SERVICE_DETAIL';
  static readonly BOOKING: string = 'BOOKING';
  static readonly PAYMENT: string = 'PAYMENT';
  static readonly ORDER_DETAIL: string = 'ORDER_DETAIL';
  static readonly REVIEW: string = 'REVIEW';
  static readonly ADDRESS: string = 'ADDRESS';
}

export interface ServiceListParam {
  categoryNo?: string;
  title?: string;
}

export interface ServiceDetailParam {
  itemNo: string;
}

export interface BookingParam {
  itemNo: string;
  selectedOptions: OptionSelection[];
}

export interface PaymentParam {
  orderNo: string;
  payableAmount: number;
  deadline: string;
}

export interface OrderDetailParam {
  orderNo: string;
}

export interface ReviewParam {
  orderNo: string;
  serviceName: string;
}

export interface AddressParam {
  pickMode?: boolean;
}
