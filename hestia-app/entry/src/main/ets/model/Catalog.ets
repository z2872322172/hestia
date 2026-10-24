/** 服务分类（catalog/application/view/CategoryView.java）。 */
export interface Category {
  categoryNo: string;
  categoryName: string;
  iconUrl: string;
  description: string;
  sortOrder: number;
  status: string;
  children: Category[];
}

/** 服务项目摘要（ServiceItemSummaryView.java）。 */
export interface ServiceItemSummary {
  itemNo: string;
  itemName: string;
  shortDescription: string;
  coverUrl: string;
  categoryNo: string;
  categoryName: string;
  serviceUnit: string;
  pricingMode: string;
  basePrice: number;
  durationMinutes: number;
  version: number;
  status: string;
}

/** 服务项目详情（ServiceItemDetailView.java），包含摘要全部字段 + 明细。 */
export interface ServiceItemDetail {
  itemNo: string;
  itemName: string;
  shortDescription: string;
  detailDescription: string;
  coverUrl: string;
  categoryNo: string;
  categoryName: string;
  serviceUnit: string;
  pricingMode: string;
  basePrice: number;
  durationMinutes: number;
  minQuantity: number;
  maxQuantity: number;
  serviceScope: string;
  serviceStandardSummary: string;
  version: number;
  status: string;
  options: ServiceOption[];
  tiers: ServiceTier[];
  standards: StandardItem[];
}

export interface ServiceOption {
  optionCode: string;
  optionName: string;
  selectionType: string;
  required: boolean;
  minSelect: number;
  maxSelect: number;
  values: ServiceOptionValue[];
}

export interface ServiceOptionValue {
  valueCode: string;
  valueName: string;
  numericValue: number;
  extraPrice: number;
  durationDelta: number;
}

export interface ServiceTier {
  tierType: string;
  minValue: number;
  maxValue: number;
  fixedPrice: number;
  unitPrice: number;
}

export interface StandardItem {
  standardGroup: string;
  itemName: string;
  description: string;
  required: boolean;
  evidenceRequired: boolean;
  sortOrder: number;
}

/** 报价明细（PricePreviewView.java），金额全部由服务端计算。 */
export interface PricePreview {
  serviceItemVersion: number;
  baseAmount: number;
  addonAmount: number;
  materialAmount: number;
  travelFee: number;
  discountAmount: number;
  payableAmount: number;
  durationMinutes: number;
  pricingExplanation: string[];
}

/** 规格选择（与后端 PriceQuoteQuery.OptionSelection 对应）。 */
export interface OptionSelection {
  optionCode: string;
  valueCode: string;
}

/** 按 standardGroup 聚合后的服务标准分组，供详情页渲染。 */
export interface StandardGroup {
  groupName: string;
  items: StandardItem[];
}
