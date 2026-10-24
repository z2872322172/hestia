/** 地址中的行政区（AddressRegionView.java）。 */
export interface AddressRegion {
  cityName: string;
  districtName: string;
}

/** 收货/服务地址（AddressView.java）。 */
export interface Address {
  addressNo: string;
  label: string;
  contactName: string;
  contactPhone: string;
  region: AddressRegion;
  communityName: string;
  buildingNo: string;
  unitNo: string;
  floorNo: string;
  roomNo: string;
  detailAddress: string;
  accessNote: string;
  isDefault: boolean;
  /** MATCHED / OUT_OF_RANGE / 其他 */
  areaMatchStatus: string;
  serviceAreaId: number;
  version: number;
}

/** 当前账号（AccountView.java）。 */
export interface Account {
  accountNo: string;
  displayName: string;
  activeRole: string;
  status: string;
  phoneMasked: string;
  roles: string[];
}

/** 登录结果（LoginView.java）。 */
export interface LoginResult {
  accessToken: string;
  expiresIn: number;
  account: Account;
}

/** 客户档案（CustomerProfileView.java）。 */
export interface CustomerProfile {
  customerNo: string;
  status: string;
  createdAt: string;
}

/** 新增/修改地址请求（CustomerAddressRequest.java）。 */
export interface AddressRequest {
  label: string;
  contactName: string;
  contactCountryCode: string;
  contactPhone: string;
  cityCode: string;
  districtCode: string;
  communityName: string;
  buildingNo: string;
  unitNo: string;
  floorNo: string;
  roomNo: string;
  detailAddress: string;
  accessNote: string;
  isDefault: boolean;
  /** 修改时必须回传，后端乐观锁校验 */
  version?: number;
}
