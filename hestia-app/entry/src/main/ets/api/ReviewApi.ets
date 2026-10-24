import { ApiClient } from './ApiClient';
import { PageResult } from '../model/ApiTypes';
import { Review, SubmitReviewBody } from '../model/Review';

/** 评价域接口。 */
export class ReviewApi {
  /** 提交评价：必须覆盖全部启用维度，各维度 0-5 分。 */
  static submit(orderNo: string, body: SubmitReviewBody): Promise<Review> {
    return ApiClient.post<Review>(
      '/customer/bookings/' + encodeURIComponent(orderNo) + '/reviews', body);
  }

  static myReviews(page: number, size: number): Promise<PageResult<Review>> {
    return ApiClient.get<PageResult<Review>>(
      '/customer/reviews?page=' + page.toString() + '&size=' + size.toString());
  }
}
