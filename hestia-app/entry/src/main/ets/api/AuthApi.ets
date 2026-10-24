import { ApiClient } from './ApiClient';
import { Account, LoginResult } from '../model/Customer';

export interface LoginBody {
  countryCode: string;
  phone: string;
  password: string;
}

export interface RegisterBody {
  countryCode: string;
  phone: string;
  password: string;
  displayName: string;
}

/** 身份域接口（/api/v1/auth）。 */
export class AuthApi {
  static login(phone: string, password: string): Promise<LoginResult> {
    const body: LoginBody = {
      countryCode: '86',
      phone: phone,
      password: password
    };
    return ApiClient.post<LoginResult>('/auth/login', body);
  }

  /** 注册即登录，后端直接返回 LoginView。 */
  static register(phone: string, password: string, displayName: string): Promise<LoginResult> {
    const body: RegisterBody = {
      countryCode: '86',
      phone: phone,
      password: password,
      displayName: displayName
    };
    return ApiClient.post<LoginResult>('/auth/customer/register', body);
  }

  static me(): Promise<Account> {
    return ApiClient.get<Account>('/auth/me');
  }

  static logout(): Promise<Object> {
    return ApiClient.post<Object>('/auth/logout');
  }
}
