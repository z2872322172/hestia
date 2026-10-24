import { ApiClient } from './ApiClient';
import {
  Category,
  OptionSelection,
  PricePreview,
  ServiceItemDetail,
  ServiceItemSummary
} from '../model/Catalog';

/** 报价请求体（PricePreviewRequest.java，字段名是 selectedOptions）。 */
export interface PricePreviewBody {
  quantity: number;
  selectedOptions: OptionSelection[];
}

/** 目录域公开接口（/api/v1/public）。 */
export class CatalogApi {
  static categories(): Promise<Category[]> {
    return ApiClient.get<Category[]>('/public/service-categories');
  }

  /** categoryNo 为空串表示全部；后端不分页，返回全量已上架服务项。 */
  static items(categoryNo: string): Promise<ServiceItemSummary[]> {
    if (categoryNo.length === 0) {
      return ApiClient.get<ServiceItemSummary[]>('/public/service-items');
    }
    return ApiClient.get<ServiceItemSummary[]>(
      '/public/service-items?categoryNo=' + encodeURIComponent(categoryNo));
  }

  static detail(itemNo: string): Promise<ServiceItemDetail> {
    return ApiClient.get<ServiceItemDetail>('/public/service-items/' + encodeURIComponent(itemNo));
  }

  /** 报价试算：金额一律服务端计算，请求体里没有金额字段。 */
  static pricePreview(itemNo: string, quantity: number,
                      options: OptionSelection[]): Promise<PricePreview> {
    const body: PricePreviewBody = {
      quantity: quantity,
      selectedOptions: options
    };
    return ApiClient.post<PricePreview>(
      '/public/service-items/' + encodeURIComponent(itemNo) + '/price-preview', body);
  }
}
