import http from '@ohos.net.http';
import { ApiResult } from '../model/ApiTypes';
import { Storage } from '../common/Storage';

/** 业务错误。code 为后端 ErrorCode，traceId 便于把前端报错和服务端日志对上。 */
export class ApiError extends Error {
  code: string;
  traceId: string;

  constructor(code: string, message: string, traceId: string) {
    super(message);
    this.code = code;
    this.traceId = traceId;
  }
}

/**
 * 请求头。
 *
 * 用固定字段的 interface 而不是 Record<string, string>：
 * ArkTS 严格模式禁止动态索引赋值（headers[key] = value），
 * 而带连字符的 'Idempotency-Key' 只能用字符串字面量索引访问已声明的属性。
 */
interface RequestHeader {
  'Content-Type': string;
  'Accept': string;
  'Authorization'?: string;
  'Idempotency-Key'?: string;
}

/** 统一 HTTP 客户端：拼 URL、加 token、解析 Result、401 清 token。 */
export class ApiClient {
  /**
   * 后端基址（无 context-path，端口 8080）。
   *
   * - DevEco 模拟器访问宿主机：http://10.0.2.2:8080/api/v1
   * - 真机同 WiFi：http://<电脑局域网IP>:8080/api/v1
   */
  static readonly BASE_URL: string = 'http://10.0.2.2:8080/api/v1';

  static async request<T>(method: http.RequestMethod, path: string,
                          body?: object, idemKey?: string): Promise<T> {
    const token: string = await Storage.getToken();
    const header: RequestHeader = {
      'Content-Type': 'application/json',
      'Accept': 'application/json'
    };
    if (token.length > 0) {
      header.Authorization = 'Bearer ' + token;
    }
    if (idemKey !== undefined && idemKey.length > 0) {
      header['Idempotency-Key'] = idemKey;
    }

    const options: http.HttpRequestOptions = {
      method: method,
      header: header as Object,
      connectTimeout: 8000,
      readTimeout: 8000,
      expectDataType: http.HttpDataType.STRING
    };
    if (body !== undefined) {
      options.extraData = JSON.stringify(body);
    }

    const request = http.createHttp();
    try {
      const resp: http.HttpResponse = await request.request(ApiClient.BASE_URL + path, options);
      const raw: string = resp.result as string;

      // 令牌失效：HTTP 状态码走 401，不是业务码
      if (resp.responseCode === 401) {
        await Storage.clearToken();
        throw new ApiError('AUTH_TOKEN_EXPIRED', '登录已过期，请重新登录', '');
      }

      if (raw === undefined || raw === null || raw.length === 0) {
        throw new ApiError('EMPTY_RESPONSE', '服务无响应（' + resp.responseCode + '）', '');
      }

      let parsed: ApiResult<T>;
      try {
        parsed = JSON.parse(raw) as ApiResult<T>;
      } catch (parseErr) {
        // 非 JSON 响应通常是网关错误页或后端异常栈
        throw new ApiError('HTTP_' + resp.responseCode,
          '服务异常（HTTP ' + resp.responseCode + '）', '');
      }

      if (parsed.code !== 'OK') {
        throw new ApiError(parsed.code, parsed.message, parsed.traceId);
      }
      return parsed.data;
    } finally {
      request.destroy(); // 不销毁会泄漏连接，反复切页面后请求会挂住
    }
  }

  static get<T>(path: string): Promise<T> {
    return ApiClient.request<T>(http.RequestMethod.GET, path);
  }

  static post<T>(path: string, body?: object, idemKey?: string): Promise<T> {
    return ApiClient.request<T>(http.RequestMethod.POST, path, body, idemKey);
  }

  static put<T>(path: string, body?: object): Promise<T> {
    return ApiClient.request<T>(http.RequestMethod.PUT, path, body);
  }

  static delete<T>(path: string): Promise<T> {
    return ApiClient.request<T>(http.RequestMethod.DELETE, path);
  }

  /** 判断是否为业务错误（业务错误不降级 Mock，只有网络异常才降级）。 */
  static isBusinessError(e: Object): boolean {
    return e instanceof ApiError;
  }

  /** 把任意异常转成可展示的文案。 */
  static messageOf(e: Object): string {
    if (e instanceof ApiError) {
      return e.message;
    }
    return '网络异常，请检查后端是否已启动';
  }
}
