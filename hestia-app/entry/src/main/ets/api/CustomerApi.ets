import { ApiClient } from './ApiClient';
import { Address, AddressRequest, CustomerProfile } from '../model/Customer';

/** 客户域接口（/api/v1/customer）。 */
export class CustomerApi {
  static addresses(): Promise<Address[]> {
    return ApiClient.get<Address[]>('/customer/addresses');
  }

  static createAddress(body: AddressRequest): Promise<Address> {
    return ApiClient.post<Address>('/customer/addresses', body);
  }

  /** 修改地址必须带 version，后端乐观锁校验，不带会 409。 */
  static updateAddress(addressNo: string, body: AddressRequest): Promise<Address> {
    return ApiClient.put<Address>(
      '/customer/addresses/' + encodeURIComponent(addressNo), body);
  }

  static deleteAddress(addressNo: string): Promise<Object> {
    return ApiClient.delete<Object>('/customer/addresses/' + encodeURIComponent(addressNo));
  }

  static setDefault(addressNo: string): Promise<Object> {
    return ApiClient.put<Object>(
      '/customer/addresses/' + encodeURIComponent(addressNo) + '/default');
  }

  /** 重新匹配服务区域，回写 areaMatchStatus。 */
  static availabilityCheck(addressNo: string): Promise<Address> {
    return ApiClient.post<Address>(
      '/customer/addresses/' + encodeURIComponent(addressNo) + '/availability-check');
  }

  static profile(): Promise<CustomerProfile> {
    return ApiClient.get<CustomerProfile>('/customer/profile');
  }
}
