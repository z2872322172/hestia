import preferences from '@ohos.data.preferences';
import common from '@ohos.app.ability.common';

const STORE_NAME: string = 'hestia_store';
const KEY_TOKEN: string = 'access_token';
const KEY_ACCOUNT: string = 'account_json';
const KEY_PHONE: string = 'remember_phone';
const KEY_DEGRADED: string = 'mock_degraded';

/**
 * 本地存储封装。
 *
 * 必须在 EntryAbility.onCreate 里调用一次 init，不要在页面里重复 getPreferences
 * （重复打开同一个 store 会抛异常）。
 */
export class Storage {
  private static store: preferences.Preferences | null = null;

  static async init(context: common.Context): Promise<void> {
    try {
      Storage.store = await preferences.getPreferences(context, STORE_NAME);
    } catch (err) {
      Storage.store = null;
    }
  }

  static async getString(key: string, defValue: string): Promise<string> {
    if (Storage.store === null) {
      return defValue;
    }
    try {
      const value: preferences.ValueType = await Storage.store.get(key, defValue);
      return value as string;
    } catch (err) {
      return defValue;
    }
  }

  static async putString(key: string, value: string): Promise<void> {
    if (Storage.store === null) {
      return;
    }
    await Storage.store.put(key, value);
    await Storage.store.flush();
  }

  static async remove(key: string): Promise<void> {
    if (Storage.store === null) {
      return;
    }
    await Storage.store.delete(key);
    await Storage.store.flush();
  }

  // ---------- 业务封装 ----------

  static getToken(): Promise<string> {
    return Storage.getString(KEY_TOKEN, '');
  }

  static setToken(token: string): Promise<void> {
    return Storage.putString(KEY_TOKEN, token);
  }

  static clearToken(): Promise<void> {
    return Storage.remove(KEY_TOKEN);
  }

  static getAccountJson(): Promise<string> {
    return Storage.getString(KEY_ACCOUNT, '');
  }

  static setAccountJson(json: string): Promise<void> {
    return Storage.putString(KEY_ACCOUNT, json);
  }

  static clearAccount(): Promise<void> {
    return Storage.remove(KEY_ACCOUNT);
  }

  static getRememberPhone(): Promise<string> {
    return Storage.getString(KEY_PHONE, '');
  }

  static setRememberPhone(phone: string): Promise<void> {
    return Storage.putString(KEY_PHONE, phone);
  }

  static async getDegraded(): Promise<boolean> {
    const value: string = await Storage.getString(KEY_DEGRADED, '0');
    return value === '1';
  }

  static setDegraded(degraded: boolean): Promise<void> {
    return Storage.putString(KEY_DEGRADED, degraded ? '1' : '0');
  }

  /** 退出登录：清 token 与账号，保留记住的手机号。 */
  static async logout(): Promise<void> {
    await Storage.clearToken();
    await Storage.clearAccount();
    await Storage.setDegraded(false);
  }
}
